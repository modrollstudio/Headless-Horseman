package studio.modroll.horseman.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.horseman.gametest.SmokeScenarios;

/** Fabric registration for the shared smoke GameTests. */
public class SmokeGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void horsemanLoads(GameTestHelper helper) {
        SmokeScenarios.horsemanLoads(helper, FabricLoader.getInstance()::isModLoaded);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void critfallPresent(GameTestHelper helper) {
        SmokeScenarios.critfallPresent(helper, FabricLoader.getInstance()::isModLoaded);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void geckolibPresent(GameTestHelper helper) {
        SmokeScenarios.geckolibPresent(helper, FabricLoader.getInstance()::isModLoaded);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void summonedMounted(GameTestHelper helper) {
        SmokeScenarios.summonedMounted(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void facesWithSteed(GameTestHelper helper) {
        SmokeScenarios.facesWithSteed(helper);
    }
}
