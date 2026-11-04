package studio.modroll.horseman.gametest;

import static studio.modroll.horseman.gametest.SmokeScenarios.expect;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.horseman.effect.HorsemanEffects;
import studio.modroll.horseman.entity.GallowsRopes;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.fight.Head;
import studio.modroll.horseman.item.Charges;
import studio.modroll.horseman.item.HangmansRopeItem;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.item.Lantern;
import studio.modroll.horseman.network.LanternPayload;
import studio.modroll.horseman.reward.LockedDrops;

/** GameTest bodies for his rewards and their powers. Each runs in its own batch. */
public final class RewardScenarios {

    /** Each hazard save seen, as "id saveKey". */
    private static final List<String> SAVES = new ArrayList<>();

    private static boolean listening;

    /** The face the forced d20 shows, and the d6. */
    private static int d20 = 10;

    private static int d6 = 1;

    private RewardScenarios() {}

    private static void listen() {
        if (!listening) {
            listening = true;
            CritfallEvents.onHazardRoll(event -> event.save()
                    .ifPresent(save -> SAVES.add(
                            event.id().map(ResourceLocation::toString).orElse("<no id>") + " " + save.saveKey())));
        }
    }

    /**
     * Critfall's dice with chosen faces: the d20 and the d6 as set, a d17 (a teleport offset) at 13, which is 4
     * blocks, and every other die in the middle.
     */
    private static final class Forced implements RandomGenerator {
        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public int nextInt(int bound) {
            return switch (bound) {
                case 20 -> d20 - 1;
                case 6 -> d6 - 1;
                case 17 -> 12;
                default -> (bound - 1) / 2;
            };
        }

        @Override
        public int nextInt(int origin, int bound) {
            return origin + nextInt(bound - origin);
        }
    }

    private static void forceDice() {
        RollService.setRoller(new DiceRoller(new Forced()));
    }

    private static void cleanUp(GameTestHelper helper, long dayTime, Entity... entities) {
        RollService.resetRoller();
        d20 = 10;
        d6 = 1;
        helper.getLevel().setDayTime(dayTime);
        for (Entity entity : entities) {
            if (entity instanceof ServerPlayer player) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            } else if (entity != null) {
                if (entity.getVehicle() != null) {
                    entity.getVehicle().discard();
                }
                entity.discard();
            }
        }
        helper.getLevel()
                .getEntitiesOfClass(GallowsRopes.class, helper.getBounds().inflate(8.0))
                .forEach(Entity::discard);
    }

    private static void face(ServerPlayer player, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        player.setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
        player.setXRot((float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
        player.setYHeadRot(player.getYRot());
    }

    /** Right click, through the server's own handling of a use packet. */
    private static void use(ServerPlayer player) {
        player.gameMode.useItem(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    private static int count(Player player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                n += player.getInventory().getItem(i).getCount();
            }
        }
        return n;
    }

    private static int rewards(Player player) {
        return count(player, HorsemanItems.HORSEMAN_HEAD)
                + count(player, HorsemanItems.EXECUTIONERS_SWORD)
                + count(player, HorsemanItems.HANGMANS_ROPE)
                + count(player, HorsemanItems.HORSEMANS_TREATS);
    }

    private static void touchAll(List<ItemEntity> drops, Player player) {
        for (ItemEntity drop : drops) {
            if (drop.isAlive()) {
                drop.setNoPickUpDelay();
                drop.playerTouch(player);
            }
        }
    }

    /**
     * One player hits him and another carries his head; each gets their own head, locked to them, and a bystander can
     * take nothing until the locks open.
     */
    public static void perPlayerRewards(GameTestHelper helper) {
        long dayTime = helper.getLevel().getDayTime();
        Horseman horseman = FightScenarios.still(helper, new BlockPos(2, 2, 2));
        GallowsSteed steed = horseman.steed();
        ServerPlayer hitter = FightScenarios.mockPlayer(helper, new Vec3(4.5, 2, 2.5), false);
        ServerPlayer carrier = FightScenarios.mockPlayer(helper, new Vec3(6.5, 2, 6.5), false);
        ServerPlayer bystander = FightScenarios.mockPlayer(helper, new Vec3(1.5, 2, 6.5), false);
        List<ItemEntity> drops = new ArrayList<>();
        forceDice();
        d20 = 20;
        hitter.attack(horseman);
        RollService.resetRoller();
        horseman.throwHead(carrier.position().add(0, 1, 0));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ItemEntity head : helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class,
                                    horseman.getBoundingBox().inflate(32.0),
                                    i -> Head.isHeadOf(i.getItem(), horseman))) {
                        head.setNoPickUpDelay();
                        head.playerTouch(carrier);
                    }
                    expect(
                            helper,
                            horseman.helpers().contains(carrier.getUUID()),
                            "carrying his head must count as helping");
                })
                .thenExecute(() -> {
                    expect(
                            helper,
                            horseman.helpers().contains(hitter.getUUID()),
                            "hitting him must count as helping, helpers " + horseman.helpers());
                    expect(
                            helper,
                            !horseman.helpers().contains(bystander.getUUID()),
                            "standing by must not count as helping");
                    horseman.kill();
                })
                .thenWaitUntil(() -> {
                    drops.clear();
                    drops.addAll(helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class, horseman.getBoundingBox().inflate(4.0), i -> i.getItem()
                                            .is(HorsemanItems.HORSEMAN_HEAD)));
                    expect(helper, drops.size() == 2, "each helper must get their own head, saw " + drops.size());
                })
                .thenExecute(() -> {
                    List<ItemEntity> all = helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class,
                                    horseman.getBoundingBox().inflate(4.0),
                                    i -> !Head.isHeadOf(i.getItem(), horseman));
                    touchAll(all, bystander);
                    expect(
                            helper,
                            rewards(bystander) == 0,
                            "a bystander must not pick up a helper's reward while it is locked");
                    touchAll(all, hitter);
                    touchAll(all, carrier);
                    expect(
                            helper,
                            count(hitter, HorsemanItems.HORSEMAN_HEAD) == 1
                                    && count(carrier, HorsemanItems.HORSEMAN_HEAD) == 1,
                            "each helper must pick up exactly their own head");
                    // Drop the hitter's head again and lock it with its time already up: the next unlock opens it.
                    ItemStack head = ItemStack.EMPTY;
                    for (int i = 0; i < hitter.getInventory().getContainerSize(); i++) {
                        if (hitter.getInventory().getItem(i).is(HorsemanItems.HORSEMAN_HEAD)) {
                            head = hitter.getInventory().removeItemNoUpdate(i);
                        }
                    }
                    ItemEntity dropped = new ItemEntity(
                            helper.getLevel(), bystander.getX(), bystander.getY(), bystander.getZ(), head);
                    dropped.setTarget(hitter.getUUID());
                    helper.getLevel().addFreshEntity(dropped);
                    LockedDrops.get(helper.getLevel())
                            .lock(dropped, helper.getLevel().getGameTime());
                    drops.clear();
                    drops.add(dropped);
                })
                .thenWaitUntil(() -> {
                    touchAll(drops, bystander);
                    expect(
                            helper,
                            count(bystander, HorsemanItems.HORSEMAN_HEAD) == 1,
                            "once its lock is up, anyone may pick up a reward");
                })
                .thenExecute(() -> {
                    helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class, horseman.getBoundingBox().inflate(8.0))
                            .forEach(Entity::discard);
                    cleanUp(helper, dayTime, hitter, carrier, bystander, horseman, steed);
                    helper.succeed();
                })
                .thenIdle(0);
    }

    /**
     * The worn head's keys: the light switches, and the breath is a Dexterity save through Critfall under its own id,
     * once a day, back at dawn.
     */
    public static void lanternBreathSavesThroughCritfall(GameTestHelper helper) {
        listen();
        SAVES.clear();
        long dayTime = helper.getLevel().getDayTime();
        ServerPlayer player = FightScenarios.mockPlayer(helper, new Vec3(1.5, 2, 1.5), false);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 4));
        zombie.setNoAi(true);
        player.setItemSlot(EquipmentSlot.HEAD, Lantern.litHead());
        face(player, zombie);
        String saw = "critfall_horseman:lantern_breath " + ModifierProvider.DEXTERITY_SAVE;
        try {
            forceDice();
            d20 = 1;
            ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
            LanternPayload.handle(new LanternPayload(false), player);
            expect(helper, !Lantern.isLit(head), "the light key must put the lit head out");
            LanternPayload.handle(new LanternPayload(false), player);
            expect(helper, Lantern.isLit(head), "the light key must light it again");
            float before = zombie.getHealth();
            LanternPayload.handle(new LanternPayload(true), player);
            expect(helper, SAVES.contains(saw), "the breath must ask a DEX save under its id, heard " + SAVES);
            expect(helper, zombie.getHealth() < before, "a failed save must burn");
            expect(
                    helper,
                    zombie.getLastDamageSource() != null
                            && zombie.getLastDamageSource().getEntity() == player
                            && zombie.getLastHurtByMob() == player,
                    "the burn must come from the player who breathed it, so the kill is theirs");
            SAVES.clear();
            LanternPayload.handle(new LanternPayload(true), player);
            expect(helper, SAVES.isEmpty(), "the breath must not come twice in one day, heard " + SAVES);
            helper.getLevel().setDayTime(dayTime + 24000L);
            LanternPayload.handle(new LanternPayload(true), player);
            expect(helper, SAVES.contains(saw), "the breath must come back at dawn, heard " + SAVES);
        } finally {
            cleanUp(helper, dayTime, player, zombie);
        }
        helper.succeed();
    }

    /**
     * Both stand on the floor, as AI-off mobs don't fall. The rope's ropes ask a Strength save through Critfall under their own id; a failed save binds. Three charges,
     * none left after, all back at dawn.
     */
    public static void ropeSavesThroughCritfall(GameTestHelper helper) {
        listen();
        SAVES.clear();
        long dayTime = helper.getLevel().getDayTime();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FightScenarios.mockPlayer(helper, new Vec3(1.5, 0, 1.5), false);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 0, 5));
        zombie.setNoAi(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HorsemanItems.HANGMANS_ROPE));
        face(player, zombie);
        String saw = HangmansRopeItem.HAZARD + " " + ModifierProvider.STRENGTH_SAVE;
        try {
            forceDice();
            d20 = 1;
            Vec3 aim = HangmansRopeItem.aim(player, 16.0);
            expect(
                    helper,
                    aim != null && aim.distanceTo(zombie.position()) < 0.5,
                    "aimed at the zombie, the rope must land under it, landed at " + aim + ", zombie at "
                            + zombie.position());
            use(player);
            expect(helper, SAVES.contains(saw), "the ropes must ask a STR save under their id, heard " + SAVES);
            expect(helper, zombie.hasEffect(HorsemanEffects.bound()), "a failed save must bind the zombie");
            expect(
                    helper,
                    !level.getEntitiesOfClass(
                                    GallowsRopes.class, zombie.getBoundingBox().inflate(3.0))
                            .isEmpty(),
                    "the ropes must burst from the ground");
            zombie.removeAllEffects();
            d20 = 20;
            use(player);
            expect(helper, !zombie.hasEffect(HorsemanEffects.bound()), "a successful save must not bind");
            use(player);
            ItemStack rope = player.getMainHandItem();
            expect(helper, Charges.left(rope, level, 3) == 0, "three uses must spend all three charges");
            SAVES.clear();
            use(player);
            expect(helper, SAVES.isEmpty(), "with no charges left the rope must do nothing, heard " + SAVES);
            level.setDayTime(dayTime + 24000L);
            expect(helper, Charges.left(rope, level, 3) == 3, "all three charges must be back at dawn");
            use(player);
            expect(helper, SAVES.contains(saw), "after dawn the rope must work again, heard " + SAVES);
        } finally {
            cleanUp(helper, dayTime, player, zombie);
        }
        helper.succeed();
    }

    private static void eat(ServerPlayer player) {
        use(player);
        for (int i = 0; i < 30 && player.isUsingItem(); i++) {
            player.doTick();
        }
    }

    /** Each face of Critfall's d6 gives its own effect; three treats a day, back at dawn. */
    public static void treatsRollThroughCritfall(GameTestHelper helper) {
        long dayTime = helper.getLevel().getDayTime();
        ServerPlayer player = FightScenarios.mockPlayer(helper, new Vec3(2.5, 2, 2.5), false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HorsemanItems.HORSEMANS_TREATS));
        List<String> wrong = new ArrayList<>();
        try {
            forceDice();
            for (d6 = 1; d6 <= 6; d6++) {
                if (d6 == 4) {
                    eat(player);
                    if (player.isUsingItem() || player.hasEffect(HorsemanEffects.wraithwalk())) {
                        wrong.add("a fourth treat in one day");
                    }
                    helper.getLevel().setDayTime(dayTime + 24000L);
                }
                Vec3 from = player.position();
                player.removeAllEffects();
                player.setHealth(10.0F);
                eat(player);
                boolean ok =
                        switch (d6) {
                            case 1 -> player.hasEffect(MobEffects.DARKNESS);
                            case 2 -> player.position().distanceTo(from) > 1.0;
                            case 3 -> player.hasEffect(MobEffects.MOVEMENT_SPEED);
                            case 4 -> HorsemanEffects.passesThrough(player);
                            case 5 -> player.hasEffect(MobEffects.NIGHT_VISION);
                            default -> player.getHealth() >= 15.0F;
                        };
                if (!ok) {
                    wrong.add("face " + d6);
                }
            }
        } finally {
            cleanUp(helper, dayTime, player);
        }
        expect(helper, wrong.isEmpty(), "every face of the d6 must do its own thing, wrong: " + wrong);
        helper.succeed();
    }

    /** Wraithwalk: neither pushed nor pushing. */
    public static void wraithwalkPassesThrough(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setNoAi(true);
        expect(helper, zombie.isPushable(), "a zombie must be pushable to start with");
        zombie.addEffect(new MobEffectInstance(HorsemanEffects.wraithwalk(), 100));
        expect(helper, !zombie.isPushable(), "under wraithwalk it must not be pushable");
        zombie.discard();
        helper.succeed();
    }
}
