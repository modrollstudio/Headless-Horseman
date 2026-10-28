package studio.modroll.horseman.gametest;

import static studio.modroll.horseman.gametest.SmokeScenarios.expect;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.event.HazardRollEvent;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.config.ConfigEditor;
import studio.modroll.horseman.config.ConfigSettings;
import studio.modroll.horseman.config.HorsemanConfig;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.fight.Head;
import studio.modroll.horseman.fight.HorsemanCombat;
import studio.modroll.horseman.fight.Lines;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.spawn.HorsemanCalendar;
import studio.modroll.horseman.spawn.HorsemanSavedData;
import studio.modroll.horseman.spawn.HorsemanSpawner;

/** GameTest bodies for the fight and spawning. Each runs in its own batch. */
public final class FightScenarios {

    /** Each hazard roll seen by the listener, as "id saveKey". */
    private static final List<String> HAZARDS = new ArrayList<>();

    private static boolean listening;

    private FightScenarios() {}

    private static void listen() {
        if (!listening) {
            listening = true;
            CritfallEvents.onHazardRoll(event -> HAZARDS.add(describe(event)));
        }
    }

    private static String describe(HazardRollEvent event) {
        String id = event.id().map(ResourceLocation::toString).orElse("<no id>");
        return id + " " + event.save().map(save -> save.saveKey()).orElse("<attack>");
    }

    /** Mounted, with AI off on both, so only the test drives him. */
    static Horseman still(GameTestHelper helper, BlockPos pos) {
        Horseman horseman =
                HorsemanSpawner.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(pos)), null);
        horseman.setNoAi(true);
        if (horseman.steed() != null) {
            horseman.steed().setNoAi(true);
        }
        return horseman;
    }

    private static void cleanUp(GameTestHelper helper, Entity... entities) {
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
        CritfallHorseman.useConfig(HorsemanConfig.DEFAULTS);
    }

    private static List<ItemEntity> headsOnTheGround(GameTestHelper helper, Horseman horseman) {
        AABB around = horseman.getBoundingBox().inflate(32.0);
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, around, i -> Head.isHeadOf(i.getItem(), horseman));
    }

    private static boolean holds(Player player, Horseman horseman) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (Head.isHeadOf(player.getInventory().getItem(i), horseman)) {
                return true;
            }
        }
        return false;
    }

    /** He ignores creative players, but they can carry the head. */
    private static ServerPlayer playerAt(GameTestHelper helper, Vec3 at) {
        return mockPlayer(helper, at, true);
    }

    private static ServerPlayer survivalPlayerAt(GameTestHelper helper, Vec3 at) {
        return mockPlayer(helper, at, false);
    }

    /**
     * Like {@code GameTestHelper.makeMockServerPlayerInLevel}, but in either game mode. The helper's player is in the
     * server's default mode, which is creative here, so it can't take damage.
     */
    static ServerPlayer mockPlayer(GameTestHelper helper, Vec3 at, boolean creative) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-player"), false);
        ServerPlayer player =
                new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
                    @Override
                    public boolean isSpectator() {
                        return false;
                    }

                    @Override
                    public boolean isCreative() {
                        return creative;
                    }
                };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(at);
        player.teleportTo(level, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return player;
    }

    /** Throw, pick up, pass to a second player, and take back, checking his target at each step. */
    public static void headLoop(GameTestHelper helper) {
        Horseman horseman = still(helper, new BlockPos(1, 2, 1));
        ServerPlayer first = playerAt(helper, new Vec3(5.5, 2, 1.5));
        ServerPlayer second = playerAt(helper, new Vec3(6.5, 2, 5.5));
        List<ItemEntity> landed = new ArrayList<>();
        horseman.throwHead(first.position().add(0, 1, 0));
        expect(helper, horseman.isHeadless(), "the Horseman must be headless once he has thrown his head");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    landed.clear();
                    landed.addAll(headsOnTheGround(helper, horseman));
                    expect(helper, !landed.isEmpty(), "his head must come down as an item");
                })
                .thenExecute(() -> {
                    ItemEntity head = landed.get(0);
                    head.setNoPickUpDelay();
                    head.playerTouch(first);
                    expect(helper, holds(first, horseman), "the first player must pick up his head");
                })
                .thenWaitUntil(() -> {
                    Head.Whereabouts at = horseman.whereabouts();
                    expect(helper, at != null && at.carrier() == first, "he must know the first player has his head");
                    expect(helper, horseman.getTarget() == first, "he must ride for the first player");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < first.getInventory().getContainerSize(); i++) {
                        ItemStack stack = first.getInventory().getItem(i);
                        if (Head.isHeadOf(stack, horseman)) {
                            first.getInventory().setItem(i, ItemStack.EMPTY);
                            first.setItemInHand(InteractionHand.MAIN_HAND, stack);
                        }
                    }
                    Vec3 toward = second.position().subtract(first.position());
                    first.setYRot((float) (Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90.0F);
                    first.setXRot(0.0F);
                    first.getMainHandItem().use(helper.getLevel(), first, InteractionHand.MAIN_HAND);
                    expect(helper, !holds(first, horseman), "the first player must have thrown his head on");
                })
                .thenWaitUntil(() -> {
                    if (!holds(second, horseman)) {
                        for (ItemEntity head : headsOnTheGround(helper, horseman)) {
                            head.setNoPickUpDelay();
                            head.playerTouch(second);
                        }
                    }
                    expect(helper, holds(second, horseman), "the second player must end up with his head");
                })
                .thenWaitUntil(() -> {
                    Head.Whereabouts at = horseman.whereabouts();
                    expect(helper, at != null && at.carrier() == second, "he must know the second player has it now");
                    expect(helper, horseman.getTarget() == second, "he must ride for the second player");
                })
                .thenExecute(() -> {
                    GallowsSteed steed = horseman.steed();
                    steed.moveTo(second.getX() + 1.0, second.getY(), second.getZ(), 0.0F, 0.0F);
                })
                .thenWaitUntil(() -> {
                    expect(helper, !horseman.isHeadless(), "reaching the player, he must take his head back");
                    expect(helper, !holds(second, horseman), "the player must not keep his head");
                    expect(helper, headsOnTheGround(helper, horseman).isEmpty(), "no copy of his head may be left");
                })
                .thenExecute(() -> {
                    cleanUp(helper, first, second, horseman);
                    helper.succeed();
                })
                .thenIdle(0);
    }

    /** Both hazards are Dexterity saves through Critfall with their own ids. */
    public static void savesThroughCritfall(GameTestHelper helper) {
        listen();
        HAZARDS.clear();
        Horseman horseman = still(helper, new BlockPos(1, 2, 1));
        Zombie ridden = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 3));
        Zombie burnt = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
        ridden.setNoAi(true);
        burnt.setNoAi(true);
        horseman.trample(ridden);
        horseman.throwHead(burnt.position().add(0, 1, 0));
        String dex = ModifierProvider.DEXTERITY_SAVE;
        helper.startSequence()
                .thenWaitUntil(() -> expect(
                        helper,
                        HAZARDS.contains(HorsemanCombat.HEAD_THROW + " " + dex),
                        "the line of fire must ask a DEX save under " + HorsemanCombat.HEAD_THROW + ", heard "
                                + HAZARDS))
                .thenExecute(() -> {
                    expect(
                            helper,
                            HAZARDS.contains(HorsemanCombat.CHARGE + " " + dex),
                            "his charge must ask a DEX save under " + HorsemanCombat.CHARGE + ", heard " + HAZARDS);
                    expect(
                            helper,
                            HAZARDS.stream().noneMatch(hazard -> hazard.startsWith("<no id>")),
                            "every one of his saves must carry its hazard id, heard " + HAZARDS);
                    cleanUp(helper, horseman, ridden, burnt);
                    headsOnTheGround(helper, horseman).forEach(Entity::discard);
                    helper.succeed();
                })
                .thenIdle(0);
    }

    public static void steedDeathPutsHimOnFoot(GameTestHelper helper) {
        Horseman horseman = still(helper, new BlockPos(2, 2, 2));
        GallowsSteed steed = horseman.steed();
        expect(helper, steed != null, "he must come mounted");
        steed.kill();
        helper.startSequence()
                .thenWaitUntil(() -> expect(helper, !horseman.isPassenger(), "he must be off his dead steed"))
                .thenExecute(() -> {
                    expect(helper, horseman.isAlive(), "he must live on, on foot");
                    expect(helper, !horseman.canCharge(), "on foot, he must not charge");
                    cleanUp(helper, horseman, steed);
                    helper.succeed();
                })
                .thenIdle(0);
    }

    public static void ridesOffAtSunrise(GameTestHelper helper) {
        Horseman horseman = still(helper, new BlockPos(2, 2, 2));
        GallowsSteed steed = horseman.steed();
        horseman.setArrivedAtNight(true);
        long before = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(Math.floorDiv(before, 24000L) * 24000L + 23200L);
        helper.startSequence()
                .thenWaitUntil(() -> expect(helper, horseman.isLeaving(), "at sunrise he must ride off"))
                .thenWaitUntil(() -> {
                    expect(helper, horseman.isRemoved(), "he must be gone once he has ridden off");
                    expect(helper, steed == null || steed.isRemoved(), "his steed must go with him");
                })
                .thenExecute(() -> {
                    helper.getLevel().setDayTime(before);
                    cleanUp(helper);
                    helper.succeed();
                })
                .thenIdle(0);
    }

    /** Date rules on a fixed clock, and that midnight picks a survival player under the sky. */
    public static void dateRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HorsemanConfig defaults = HorsemanConfig.DEFAULTS;
        // midnight() refuses while any Horseman is alive, so clear leftovers from other tests.
        level.getEntities(HorsemanEntities.HORSEMAN, h -> true).forEach(h -> cleanUp(helper, h));
        CritfallHorseman.useConfig(withHoofbeats(defaults, false));
        HorsemanSpawner.clearArrivals();
        HorsemanSavedData.get(level).setLastHalloweenYear(0);
        ServerPlayer player = survivalPlayerAt(helper, new Vec3(3.5, 2, 3.5));
        Map<String, Boolean> seen = new HashMap<>();
        Consumer<String> night = date -> {
            HorsemanSpawner.useClock(
                    Clock.fixed(LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        };
        try {
            night.accept("2026-10-31");
            seen.put("halloween", HorsemanSpawner.midnight(level, 0.99) == player);
            seen.put(
                    "halloween horseman",
                    !level.getEntities(HorsemanEntities.HORSEMAN, Horseman::isAlive)
                            .isEmpty());
            level.getEntities(HorsemanEntities.HORSEMAN, Horseman::isAlive).forEach(h -> cleanUp(helper, h));
            CritfallHorseman.useConfig(withHoofbeats(defaults, false));
            seen.put("halloween again", HorsemanSpawner.midnight(level, 0.0) != null);
            level.getEntities(HorsemanEntities.HORSEMAN, Horseman::isAlive).forEach(h -> cleanUp(helper, h));
            CritfallHorseman.useConfig(withHoofbeats(defaults, false));
            night.accept("2026-10-15");
            seen.put("october lucky", HorsemanSpawner.midnight(level, 0.05) == player);
            level.getEntities(HorsemanEntities.HORSEMAN, Horseman::isAlive).forEach(h -> cleanUp(helper, h));
            CritfallHorseman.useConfig(withHoofbeats(defaults, false));
            seen.put("october unlucky", HorsemanSpawner.midnight(level, 0.5) != null);
            night.accept("2026-11-15");
            seen.put("november", HorsemanSpawner.midnight(level, 0.0) != null);
            seen.put(
                    "march, all year",
                    HorsemanCalendar.comes(LocalDate.parse("2026-03-03"), allYear(defaults), false, 0.05));
            seen.put("march", HorsemanCalendar.comes(LocalDate.parse("2026-03-03"), defaults, false, 0.0));
        } finally {
            HorsemanSpawner.useClock(Clock.systemDefaultZone());
            level.getEntities(HorsemanEntities.HORSEMAN, h -> true).forEach(h -> cleanUp(helper, h));
            cleanUp(helper, player);
        }
        Map<String, Boolean> expected = Map.of(
                "halloween", true,
                "halloween horseman", true,
                "halloween again", false,
                "october lucky", true,
                "october unlucky", false,
                "november", false,
                "march, all year", true,
                "march", false);
        expect(helper, seen.equals(expected), "the date rules must hold: expected " + expected + ", saw " + seen);
        helper.succeed();
    }

    /** What the config screen's Save does, writing the file through the editor and reloading, takes effect at once. */
    public static void configSaveApplies(GameTestHelper helper) {
        Path file = CritfallHorseman.configFile();
        String before = read(file);
        HorsemanConfig saved;
        try {
            ConfigEditor editor = ConfigEditor.server(file);
            for (ConfigSettings.Setting setting : editor.settings()) {
                if (setting.key().equals("natural_spawning") || setting.key().equals("boss_bar")) {
                    editor.set(setting, "false");
                }
            }
            editor.save(file);
            CritfallHorseman.reloadConfig();
            saved = CritfallHorseman.config();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            write(file, before);
            CritfallHorseman.reloadConfig();
        }
        expect(helper, !saved.naturalSpawning() && !saved.bossBar(), "the saved values must be in use at once");
        expect(helper, CritfallHorseman.config().naturalSpawning(), "restoring the file must turn it back on");
        helper.succeed();
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String text) {
        try {
            if (text == null) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, text);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HorsemanConfig withHoofbeats(HorsemanConfig c, boolean hoofbeats) {
        return new HorsemanConfig(
                c.enabled(),
                c.naturalSpawning(),
                c.halloweenNight(),
                c.seasonStart(),
                c.seasonEnd(),
                c.allYear(),
                c.seasonChance(),
                c.sunriseExit(),
                c.fireDamage(),
                c.charge(),
                c.headThrow(),
                c.sweep(),
                c.headPassing(),
                c.bossBar(),
                c.lines(),
                hoofbeats,
                c.drops(),
                c.effects());
    }

    private static HorsemanConfig allYear(HorsemanConfig c) {
        return new HorsemanConfig(
                c.enabled(),
                c.naturalSpawning(),
                c.halloweenNight(),
                c.seasonStart(),
                c.seasonEnd(),
                true,
                c.seasonChance(),
                c.sunriseExit(),
                c.fireDamage(),
                c.charge(),
                c.headThrow(),
                c.sweep(),
                c.headPassing(),
                c.bossBar(),
                c.lines(),
                c.hoofbeats(),
                c.drops(),
                c.effects());
    }

    /** With AI on and a target behind a wall, he charges or throws; no block changes and no fire appears. */
    public static void neverBreaksBlocks(GameTestHelper helper) {
        Map<BlockPos, BlockState> pen = new HashMap<>();
        BlockState[] kinds = {
            Blocks.GLASS.defaultBlockState(),
            Blocks.DIRT.defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.GLASS_PANE.defaultBlockState(),
            Blocks.HAY_BLOCK.defaultBlockState()
        };
        // Two blocks high: low enough for him to see over from the saddle, too high for the steed to step up. Kept
        // inside the test box, which GameTest surrounds with barriers.
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y <= 1; y++) {
                BlockState state = kinds[Math.floorMod(x + y, kinds.length)];
                BlockPos wall = new BlockPos(x, y, 4);
                helper.setBlock(wall, state);
                pen.put(wall, state);
            }
        }
        Horseman horseman = HorsemanSpawner.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(0.8, 0, 0.8)), null);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 0, 7));
        target.setNoAi(true);
        target.setInvulnerable(true);
        horseman.setTarget(target);
        boolean[] fought = {false};
        helper.onEachTick(() -> fought[0] |= horseman.isCharging() || horseman.isHeadless() || horseman.isThrowing());
        helper.runAfterDelay(240, () -> {
            expect(helper, fought[0], "he must have charged or thrown his head at the wall");
            for (Map.Entry<BlockPos, BlockState> block : pen.entrySet()) {
                BlockState now = helper.getBlockState(block.getKey());
                expect(
                        helper,
                        now.getBlock() == block.getValue().getBlock(),
                        "he must not break " + block.getValue() + ", it is " + now);
            }
            BlockPos.betweenClosedStream(new BlockPos(-4, 0, -4), new BlockPos(11, 6, 11))
                    .forEach(pos -> expect(
                            helper,
                            !helper.getBlockState(pos).is(Blocks.FIRE),
                            "he must never set a block alight, fire at " + pos.immutable()));
            cleanUp(helper, horseman, target);
            headsOnTheGround(helper, horseman).forEach(Entity::discard);
            helper.succeed();
        });
    }

    public static void dropsSwordAndHead(GameTestHelper helper) {
        Horseman horseman = still(helper, new BlockPos(2, 2, 2));
        GallowsSteed steed = horseman.steed();
        horseman.kill();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<ItemEntity> drops = helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class, horseman.getBoundingBox().inflate(4.0));
                    expect(
                            helper,
                            drops.stream().anyMatch(i -> i.getItem().is(HorsemanItems.EXECUTIONERS_SWORD)),
                            "he must drop his sword");
                    expect(
                            helper,
                            drops.stream().anyMatch(i -> i.getItem().is(HorsemanItems.HORSEMAN_HEAD)),
                            "he must drop his head");
                    expect(helper, steed == null || steed.isDeadOrDying(), "his steed must die with him");
                })
                .thenExecute(() -> {
                    helper.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class, horseman.getBoundingBox().inflate(4.0))
                            .forEach(Entity::discard);
                    cleanUp(helper, horseman, steed);
                    helper.succeed();
                })
                .thenIdle(0);
    }

    public static void healthFromData(GameTestHelper helper) {
        Horseman horseman = still(helper, new BlockPos(2, 2, 2));
        GallowsSteed steed = horseman.steed();
        expect(helper, horseman.getMaxHealth() == 150.0F, "his health must be 150, is " + horseman.getMaxHealth());
        expect(helper, steed != null && steed.getMaxHealth() == 80.0F, "his steed's must be 80");
        cleanUp(helper, horseman, steed);
        helper.succeed();
    }

    /** Repeating lines wait out their cooldown; once-per-fight lines don't. */
    public static void repeatingLinesCoolDown(GameTestHelper helper) {
        Horseman horseman = still(helper, new BlockPos(2, 2, 2));
        expect(helper, horseman.say(Lines.Line.CHASE), "the first time, he must say it");
        expect(helper, !horseman.say(Lines.Line.CHASE), "straight after, he must hold it back");
        expect(helper, horseman.say(Lines.Line.RETAKE), "another line must not wait on the first");
        expect(helper, horseman.say(Lines.Line.SUNRISE), "a once-a-fight line must always come");
        expect(helper, horseman.say(Lines.Line.SUNRISE), "a once-a-fight line must always come, even again");
        helper.runAfterDelay(Horseman.LINE_COOLDOWN + 1, () -> {
            expect(helper, horseman.say(Lines.Line.CHASE), "after the cooldown, he must say it again");
            cleanUp(helper, horseman);
            helper.succeed();
        });
    }
}
