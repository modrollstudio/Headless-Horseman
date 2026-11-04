package studio.modroll.horseman.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.horseman.gametest.RewardScenarios;

/** Fabric registration for the shared reward GameTests, each in its own batch. */
public class RewardGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_per_player_rewards", timeoutTicks = 300)
    public void perPlayerRewards(GameTestHelper helper) {
        RewardScenarios.perPlayerRewards(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_lantern_breath_saves", timeoutTicks = 100)
    public void lanternBreathSavesThroughCritfall(GameTestHelper helper) {
        RewardScenarios.lanternBreathSavesThroughCritfall(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_rope_saves", timeoutTicks = 100)
    public void ropeSavesThroughCritfall(GameTestHelper helper) {
        RewardScenarios.ropeSavesThroughCritfall(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_treats_roll", timeoutTicks = 100)
    public void treatsRollThroughCritfall(GameTestHelper helper) {
        RewardScenarios.treatsRollThroughCritfall(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_wraithwalk", timeoutTicks = 100)
    public void wraithwalkPassesThrough(GameTestHelper helper) {
        RewardScenarios.wraithwalkPassesThrough(helper);
    }
}
