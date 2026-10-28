package studio.modroll.horseman.gametest;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import studio.modroll.critfall.api.RollService;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.entity.HorsemanEntities;

/** Smoke tests. The loader shims pass in their own "is this mod loaded" check. */
public final class SmokeScenarios {

    private SmokeScenarios() {}

    public static void horsemanLoads(GameTestHelper helper, Predicate<String> modLoaded) {
        expect(helper, modLoaded.test(CritfallHorseman.MOD_ID), "the Horseman must be loaded");
        expect(helper, CritfallHorseman.config() != null, "the Horseman must have read his config");
        helper.succeed();
    }

    public static void critfallPresent(GameTestHelper helper, Predicate<String> modLoaded) {
        expect(helper, modLoaded.test("critfall"), "Critfall must be loaded");
        int total = RollService.roll("1d1").total();
        expect(helper, total == 1, "Critfall must roll 1d1 as 1, rolled " + total);
        helper.succeed();
    }

    public static void geckolibPresent(GameTestHelper helper, Predicate<String> modLoaded) {
        expect(helper, modLoaded.test("geckolib"), "GeckoLib must be loaded");
        helper.succeed();
    }

    public static void summonedMounted(GameTestHelper helper) {
        Horseman horseman = helper.spawn(HorsemanEntities.HORSEMAN, new BlockPos(1, 2, 1));
        horseman.finalizeSpawn(
                helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(horseman.blockPosition()),
                MobSpawnType.COMMAND,
                null);
        expect(
                helper,
                horseman.getVehicle() instanceof GallowsSteed,
                "the Horseman must ride his steed, rides " + horseman.getVehicle());
        horseman.getVehicle().discard();
        horseman.discard();
        helper.succeed();
    }

    public static void facesWithSteed(GameTestHelper helper) {
        Horseman horseman = helper.spawn(HorsemanEntities.HORSEMAN, new BlockPos(1, 2, 1));
        horseman.finalizeSpawn(
                helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(horseman.blockPosition()),
                MobSpawnType.COMMAND,
                null);
        GallowsSteed steed = (GallowsSteed) horseman.getVehicle();
        steed.setYRot(90.0F);
        steed.setYBodyRot(90.0F);
        horseman.setYRot(0.0F);
        horseman.setYBodyRot(0.0F);
        horseman.rideTick();
        expect(
                helper,
                horseman.getYRot() == 90.0F && horseman.yBodyRot == 90.0F,
                "the Horseman must face where his steed faces, faces " + horseman.yBodyRot);
        steed.discard();
        horseman.discard();
        helper.succeed();
    }

    static void expect(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
