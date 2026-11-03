package studio.modroll.horseman.gametest;

import static studio.modroll.horseman.gametest.SmokeScenarios.expect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.fight.Head;
import studio.modroll.horseman.fight.HorsemanCombat;

/** GameTest bodies for damage, reach and the sweep, and the head recall from water. */
public final class BalanceScenarios {

    /** The d20 face for the next rolls; every other die rolls its average, rounded down. */
    private static int d20 = 15;

    private BalanceScenarios() {}

    /** A fixed random source for Critfall's dice roller. */
    private static final class Held implements RandomGenerator {
        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public int nextInt(int bound) {
            return bound == 20 ? d20 - 1 : (bound - 1) / 2;
        }

        @Override
        public int nextInt(int origin, int bound) {
            return origin + nextInt(bound - origin);
        }
    }

    /**
     * Health lost on Normal, with every die but the d20 at its average rounded down, in full iron and in full netherite.
     * His sword skips armor reduction, as his attack roll against AC already counts the armor, so a hit costs the same
     * in both: 6 with his head, 12 on a crit, 4 headless. His charge (7), line of fire (9) and his steed (3) are armor
     * reduced. With his attack bonus of +5 a natural 8 hits AC 13 (iron) and a natural 13 hits AC 18 (netherite): in
     * iron the 65% he had on Critfall 0.2.17. A natural 19 must not crit.
     */
    public static void damageWithinTargets(GameTestHelper helper) {
        Horseman horseman = FightScenarios.still(helper, new BlockPos(1, 2, 1));
        GallowsSteed steed = horseman.steed();
        List<ServerPlayer> players = new ArrayList<>();
        for (Armor armor : Armor.values()) {
            for (int i = 0; i < 8; i++) {
                ServerPlayer player = FightScenarios.mockPlayer(helper, new Vec3(3.5, 1, 1.5), false);
                for (int slot = 0; slot < ARMOR_SLOTS.length; slot++) {
                    player.setItemSlot(ARMOR_SLOTS[slot], new ItemStack(armor.pieces[slot]));
                }
                players.add(player);
            }
        }
        // New players are invulnerable for their first 60 ticks.
        helper.runAfterDelay(80, () -> {
            Map<String, Float> lost = new LinkedHashMap<>();
            Map<String, AttackOutcome> outcomes = new LinkedHashMap<>();
            Map<String, Integer> armorClasses = new LinkedHashMap<>();
            Difficulty before = helper.getLevel().getDifficulty();
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            RollService.setRoller(new DiceRoller(new Held()));
            try {
                int[] next = {0};
                java.util.function.Supplier<ServerPlayer> fresh = () -> {
                    ServerPlayer player = players.get(next[0]++);
                    player.setHealth(20.0F);
                    player.removeAllEffects();
                    return player;
                };
                for (Armor armor : Armor.values()) {
                    String a = armor.name + " ";
                    players.subList(next[0], next[0] + 8).forEach(ServerPlayer::doTick);
                    ServerPlayer first = players.get(next[0]);
                    expect(
                            helper,
                            first.getArmorValue() == armor.points
                                    && first.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == armor.toughness,
                            "full " + armor.name + " must give " + armor.points + " armor and " + armor.toughness
                                    + " toughness, gave " + first.getArmorValue());
                    armorClasses.put(
                            armor.name, RollService.effectiveEntity(first).armorClass());
                    d20 = armor.lowestHit - 1;
                    ServerPlayer missed = fresh.get();
                    outcomes.put(a + "natural " + d20, swing(horseman, missed));
                    d20 = armor.lowestHit;
                    ServerPlayer hit = fresh.get();
                    outcomes.put(a + "natural " + d20, swing(horseman, hit));
                    lost.put(a + "hit", 20.0F - hit.getHealth());
                    d20 = 19;
                    ServerPlayer nineteen = fresh.get();
                    outcomes.put(a + "natural 19", swing(horseman, nineteen));
                    d20 = 20;
                    ServerPlayer crit = fresh.get();
                    outcomes.put(a + "natural 20", swing(horseman, crit));
                    lost.put(a + "crit", 20.0F - crit.getHealth());
                    d20 = 15;
                    horseman.setHeadless(true);
                    ServerPlayer headless = fresh.get();
                    swing(horseman, headless);
                    lost.put(a + "headless", 20.0F - headless.getHealth());
                    horseman.setHeadless(false);
                    d20 = 2;
                    ServerPlayer charged = fresh.get();
                    HorsemanCombat.trample(charged, steed);
                    lost.put(a + "charge", 20.0F - charged.getHealth());
                    ServerPlayer burnt = fresh.get();
                    HorsemanCombat.burn(burnt);
                    lost.put(a + "line of fire", 20.0F - burnt.getHealth());
                    d20 = 15;
                    ServerPlayer kicked = fresh.get();
                    RollService.performAttack(
                            steed,
                            kicked,
                            AttackContext.melee(steed.damageSources().mobAttack(steed), ItemStack.EMPTY));
                    lost.put(a + "steed", 20.0F - kicked.getHealth());
                }
            } finally {
                RollService.resetRoller();
                d20 = 15;
                helper.getLevel().getServer().setDifficulty(before, true);
            }
            for (Armor armor : Armor.values()) {
                String a = armor.name + " ";
                check(helper, lost, a + "hit", 6.0F);
                check(helper, lost, a + "crit", 12.0F);
                check(helper, lost, a + "headless", 4.0F);
                check(helper, lost, a + "charge", armor.charge);
                check(helper, lost, a + "line of fire", armor.lineOfFire);
                check(helper, lost, a + "steed", armor.steed);
                int ac = armorClasses.get(armor.name);
                expect(
                        helper,
                        ac == armor.armorClass,
                        "full " + armor.name + " must be AC " + armor.armorClass + ", is " + ac);
                expect(
                        helper,
                        outcomes.get(a + "natural " + (armor.lowestHit - 1)) == AttackOutcome.MISS
                                && outcomes.get(a + "natural " + armor.lowestHit) == AttackOutcome.HIT,
                        "against " + armor.name + " his lowest hit must be a natural " + armor.lowestHit + ", was "
                                + outcomes);
                expect(
                        helper,
                        outcomes.get(a + "natural 20") == AttackOutcome.CRIT,
                        "a natural 20 must crit, was " + outcomes);
                expect(
                        helper,
                        outcomes.get(a + "natural 19") == AttackOutcome.HIT,
                        "a natural 19 must not crit, was " + outcomes);
            }
            int bonus = RollService.effectiveEntity(horseman).attackBonus();
            expect(helper, bonus == 5, "his attack bonus must be +5, is " + bonus);
            players.forEach(p -> helper.getLevel().getServer().getPlayerList().remove(p));
            steed.discard();
            horseman.discard();
            helper.succeed();
        });
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Full sets, with Critfall 0.2.18's AC and vanilla's armor reduction of the held dice. */
    private enum Armor {
        IRON(
                "iron",
                new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
                15,
                0,
                13,
                8,
                3.78F,
                5.22F,
                1.38F),
        NETHERITE(
                "netherite",
                new Item[] {
                    Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
                },
                20,
                12,
                18,
                13,
                1.79F,
                2.45F,
                0.67F);

        final String name;
        final Item[] pieces;
        final int points;
        final double toughness;
        final int armorClass;
        final int lowestHit;
        final float charge;
        final float lineOfFire;
        final float steed;

        Armor(
                String name,
                Item[] pieces,
                int points,
                double toughness,
                int armorClass,
                int lowestHit,
                float charge,
                float lineOfFire,
                float steed) {
            this.name = name;
            this.pieces = pieces;
            this.points = points;
            this.toughness = toughness;
            this.armorClass = armorClass;
            this.lowestHit = lowestHit;
            this.charge = charge;
            this.lineOfFire = lineOfFire;
            this.steed = steed;
        }
    }

    private static AttackOutcome swing(Horseman horseman, ServerPlayer target) {
        return RollService.performAttack(horseman, target, HorsemanCombat.swordAttack(horseman))
                .outcome();
    }

    private static void check(GameTestHelper helper, Map<String, Float> lost, String what, float expected) {
        float amount = lost.get(what);
        expect(
                helper,
                Math.abs(amount - expected) <= 0.05F,
                "his " + what + " must take " + expected + " health on Normal, took " + amount + " " + lost);
    }

    /** Mounted reach covers 3.6 blocks behind him but not 5 blocks out; standing behind him triggers a sweep. */
    public static void reachAndSweep(GameTestHelper helper) {
        Horseman horseman = FightScenarios.still(helper, new BlockPos(4, 1, 4));
        GallowsSteed steed = horseman.steed();
        steed.setYRot(0.0F);
        steed.setYBodyRot(0.0F);
        Zombie behind = helper.spawn(EntityType.ZOMBIE, new Vec3(4.5, 1, 4.5 - 3.6));
        Zombie far = helper.spawn(EntityType.ZOMBIE, new Vec3(4.5 + 5.6, 1, 4.5));
        for (Zombie zombie : new Zombie[] {behind, far}) {
            zombie.setNoAi(true);
            zombie.setInvulnerable(true);
        }
        horseman.setTarget(behind);
        helper.startSequence()
                .thenExecute(() -> {
                    expect(helper, horseman.swordReaches(behind), "his sword must reach 3.6 blocks behind him");
                    expect(helper, !horseman.swordReaches(far), "his sword must not reach 5 blocks");
                })
                .thenWaitUntil(() -> expect(helper, horseman.isSweeping(), "crowded from behind, he must sweep"))
                .thenExecute(() -> {
                    behind.discard();
                    far.discard();
                    steed.discard();
                    horseman.discard();
                    helper.succeed();
                })
                .thenIdle(0);
    }

    /** A head thrown into water never becomes an item, and flies back to him. Neither of them can drown. */
    public static void headComesBackFromWater(GameTestHelper helper) {
        for (int x = 2; x <= 7; x++) {
            for (int z = 2; z <= 7; z++) {
                boolean wall = x == 2 || x == 7 || z == 2 || z == 7;
                helper.setBlock(new BlockPos(x, 0, z), wall ? Blocks.GLASS : Blocks.WATER);
            }
        }
        Horseman horseman = FightScenarios.still(helper, new BlockPos(0, 2, 0));
        GallowsSteed steed = horseman.steed();
        expect(helper, horseman.canBreatheUnderwater(), "he must not drown");
        expect(helper, steed != null && steed.canBreatheUnderwater(), "his steed must not drown");
        expect(helper, steed.getPathfindingMalus(PathType.WATER) < 0, "his steed must never path into water");
        boolean[] seen = {false, false};
        helper.onEachTick(() -> {
            List<ItemEntity> lying = helper.getLevel()
                    .getEntitiesOfClass(
                            ItemEntity.class,
                            horseman.getBoundingBox().inflate(24.0),
                            i -> Head.owner(i.getItem()) != null);
            seen[0] |= !lying.isEmpty();
            seen[1] |= horseman.recallingHead();
        });
        horseman.throwHead(helper.absoluteVec(new Vec3(4.5, 0.2, 4.5)));
        helper.startSequence()
                .thenWaitUntil(() -> expect(helper, seen[1], "his head must hiss out in the water and be called back"))
                .thenWaitUntil(() -> expect(helper, !horseman.isHeadless(), "his head must fly back to him"))
                .thenExecute(() -> {
                    expect(helper, !seen[0], "his head must never lie in the water as an item");
                    steed.discard();
                    horseman.discard();
                    helper.succeed();
                })
                .thenIdle(0);
    }
}
