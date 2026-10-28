package studio.modroll.horseman.uishots;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.spawn.HorsemanSpawner;

/**
 * Dev-only screenshot run ({@code ./gradlew :fabric:runUiShots}). Builds a small scene in a flat world at midnight,
 * stages the fight by calling the mod on the server thread, saves each shot to {@code critfall_horseman.uishots.dir}
 * and quits.
 */
public final class UiShots {

    public static final String DIRECTORY_PROPERTY = "critfall_horseman.uishots.dir";

    private static final String WORLD = "uishots";
    private static final int SETTLE_TICKS = 40;
    private static final int GROUND = -60;
    private static final String HORSEMAN = "@e[type=critfall_horseman:horseman,limit=1]";

    private static ShotScript script;

    private UiShots() {}

    public static Optional<Path> directory() {
        return Optional.ofNullable(System.getProperty(DIRECTORY_PROPERTY)).map(Path::of);
    }

    public static void tick() {
        if (script == null) {
            script = script(new Camera(directory().orElseThrow()));
        }
        Camera.forceSize();
        // Checks opens its character creation screen when the player joins.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.screen != null
                && minecraft.screen.getClass().getName().startsWith("studio.modroll.checks")) {
            minecraft.setScreen(null);
        }
        script.tick();
    }

    private static ShotScript script(Camera camera) {
        return new ShotScript()
                .waitFor(
                        "the title screen",
                        () -> Minecraft.getInstance().getOverlay() == null && Minecraft.getInstance().screen != null)
                .then(UiShots::quietOptions)
                .then(UiShots::createWorld)
                .waitFor(
                        "the world",
                        () -> Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null)
                .then(() -> command("forceload add -32 -32 31 31"))
                .then(() -> command("time set 18000"))
                .then(() -> command("weather clear"))
                .then(() -> command("gamerule sendCommandFeedback false"))
                .then(UiShots::scenery)
                .then(() -> command("tp @p 0.5 " + GROUND + " -8.5 0 2"))
                .waitTicks(SETTLE_TICKS)
                .then(() -> onServer(level -> HorsemanSpawner.spawn(
                        level,
                        new Vec3(0.5, GROUND, 3.5),
                        level.getServer().getPlayerList().getPlayers().get(0))))
                .waitTicks(16)
                .then(() -> camera.save("arrival"))
                .then(() -> onServer(level -> horseman(level).forEach(h -> {
                    h.setTarget(null);
                    h.setNoAi(true);
                    if (h.steed() != null) {
                        h.steed().setNoAi(true);
                    }
                })))
                .waitTicks(60)
                .then(() -> Minecraft.getInstance().options.hideGui = true)
                .then(() -> command("tp @p 3.5 " + (GROUND + 1.5) + " -3.5 facing entity " + HORSEMAN + " eyes"))
                .waitTicks(20)
                .then(() -> camera.save("mounted_front"))
                .then(() -> command("tp @p -6.5 " + (GROUND + 1.0) + " 3.0 facing entity " + HORSEMAN + " feet"))
                .waitTicks(20)
                .then(() -> camera.save("mounted_side"))
                .then(() -> command("tp @p -2.0 " + (GROUND + 1.6) + " -0.5 facing entity " + HORSEMAN + " feet"))
                .waitTicks(20)
                .then(() -> camera.save("mounted_detail"))
                .then(() -> command("tp @p -3.0 " + (GROUND + 1.8) + " -2.0 facing entity "
                        + "@e[type=critfall_horseman:gallows_steed,limit=1] eyes"))
                .waitTicks(25)
                .then(() -> camera.save("steed_close"))

                // Charge at an invulnerable zombie down the lane.
                .then(() -> command("tp @p 5.5 " + (GROUND + 0.5) + " -4.5 facing 0.5 " + (GROUND + 1.5) + " -1.0"))
                .then(() -> onServer(level -> horseman(level).forEach(h -> {
                    Zombie dummy = EntityType.ZOMBIE.create(level);
                    dummy.moveTo(0.5, GROUND, -16.5, 0, 0);
                    dummy.setNoAi(true);
                    dummy.setInvulnerable(true);
                    dummy.setSilent(true);
                    level.addFreshEntity(dummy);
                    h.setNoAi(false);
                    h.steed().setNoAi(false);
                    h.setTarget(dummy);
                    h.startCharge(dummy);
                })))
                .waitTicks(9)
                .then(() -> camera.save("charge"))
                .waitTicks(50)
                .then(() -> onServer(level -> {
                    level.getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> z.discard());
                    horseman(level).forEach(h -> {
                        h.setTarget(null);
                        h.setNoAi(true);
                        h.steed().setNoAi(true);
                        h.steed().moveTo(0.5, GROUND, 3.5, 180.0F, 0.0F);
                        h.steed().setYBodyRot(180.0F);
                        h.steed().setYHeadRot(180.0F);
                    });
                }))
                .waitTicks(20)
                .then(() -> command("tp @p 8.5 " + (GROUND + 0.5) + " -3.5 facing 0.5 " + (GROUND + 1) + " -1.5"))
                .then(() -> onServer(level -> horseman(level).forEach(h -> h.throwHead(new Vec3(1.5, GROUND, -7.5)))))
                .waitTicks(5)
                .then(() -> camera.save("head_thrown"))
                .waitTicks(3)
                .then(() -> camera.save("head_lands"))
                .waitTicks(40)
                .then(() -> command("tp @p 4.5 " + (GROUND + 1.0) + " -10.5 facing 0.5 " + (GROUND + 1.5) + " 0.5"))
                .waitTicks(20)
                .then(() -> camera.save("head_on_ground"))
                .then(() -> command("tp @p 5.5 " + (GROUND + 1.0) + " 1.5 facing entity " + HORSEMAN + " eyes"))
                .waitTicks(15)
                .then(() -> camera.save("headless"))
                .then(() -> onServer(level -> horseman(level).forEach(Horseman::takeHeadBack)))
                .waitTicks(3)
                .then(() -> camera.save("head_retaken"))
                .waitTicks(40)
                .then(() -> command("tp @p 7.5 " + (GROUND + 1.5) + " -3.5 facing 9.5 " + (GROUND - 0.5) + " 3.5"))
                .waitTicks(10)
                .then(() ->
                        onServer(level -> horseman(level).forEach(h -> h.throwHead(new Vec3(10.5, GROUND - 1.2, 3.5)))))
                .waitTicks(9)
                .then(() -> camera.save("head_doused"))
                .waitTicks(62)
                .then(() -> camera.save("head_flies_back"))
                .waitTicks(40)
                .then(() -> onServer(level -> horseman(level).forEach(h -> {
                    GallowsSteed steed = h.steed();
                    if (steed != null) {
                        steed.kill();
                    }
                })))
                .waitTicks(10)
                .then(() -> camera.save("steed_falls"))
                .waitTicks(60)
                .then(() -> command("tp @p 3.5 " + (GROUND + 0.5) + " 0.0 facing entity " + HORSEMAN + " eyes"))
                .waitTicks(15)
                .then(() -> camera.save("on_foot"))
                .then(() -> onServer(level -> horseman(level).forEach(h -> h.kill())))
                .waitTicks(22)
                .then(() -> camera.save("death"))
                .waitTicks(32)
                .then(() -> camera.save("crumbles"))
                .waitTicks(30)
                .then(() -> command("kill @e[type=item]"))
                .then(() -> command("item replace entity @p armor.head with critfall_horseman:horseman_head"))
                .then(() -> command("item replace entity @p weapon.mainhand with critfall_horseman:executioners_sword"))
                .then(() -> command("tp @p 0.5 " + GROUND + " -4.5 0 0"))
                .then(() -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT))
                .waitTicks(30)
                .then(() -> camera.save("trophies"))
                .then(() -> Minecraft.getInstance().stop());
    }

    private static List<Horseman> horseman(ServerLevel level) {
        return List.copyOf(level.getEntities(HorsemanEntities.HORSEMAN, Horseman::isAlive));
    }

    private static void scenery() {
        int top = GROUND - 1;
        command("fill -2 " + top + " -24 2 " + top + " 24 minecraft:dirt_path");
        command("fill -4 " + GROUND + " -24 -4 " + GROUND + " 24 minecraft:spruce_fence");
        command("fill 4 " + GROUND + " -24 4 " + GROUND + " 24 minecraft:spruce_fence");
        command("fill 4 " + GROUND + " 0 4 " + GROUND + " 6 minecraft:air");
        command("fill 7 " + (top - 1) + " 0 12 " + top + " 7 minecraft:water");
        for (int z = -20; z <= 20; z += 8) {
            command("setblock -4 " + (GROUND + 1) + " " + z + " minecraft:jack_o_lantern[facing=east]");
            command("setblock 4 " + (GROUND + 1) + " " + (z + 4) + " minecraft:carved_pumpkin[facing=west]");
            command("setblock -7 " + GROUND + " " + (z + 2) + " minecraft:dead_bush");
            command("setblock 8 " + GROUND + " " + (z - 2) + " minecraft:dead_bush");
        }
        // Invisible light blocks so the shots show more than the glowing parts.
        for (int z = -20; z <= 20; z += 5) {
            for (int x = -6; x <= 6; x += 6) {
                command("setblock " + x + " " + (GROUND + 4) + " " + z + " minecraft:light[level=11]");
            }
        }
    }

    private static void quietOptions() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.onboardAccessibility = false;
        minecraft.options.gamma().set(1.0);
        minecraft.options.enableVsync().set(false);
        minecraft.getWindow().updateVsync(false);
    }

    private static void createWorld() {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(
                WORLD, GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT);
        Minecraft.getInstance()
                .createWorldOpenFlows()
                .createFreshLevel(
                        WORLD,
                        settings,
                        new WorldOptions(1L, false, false),
                        registries -> registries
                                .registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT)
                                .value()
                                .createWorldDimensions(),
                        new TitleScreen());
    }

    private static void onServer(Consumer<ServerLevel> action) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> action.accept(server.getLevel(Level.OVERWORLD)));
    }

    private static void command(String command) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
}
