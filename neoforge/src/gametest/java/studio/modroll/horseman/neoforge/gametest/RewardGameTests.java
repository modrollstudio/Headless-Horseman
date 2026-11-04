package studio.modroll.horseman.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.gametest.RewardScenarios;

/** NeoForge registration for the shared reward GameTests, each in its own batch. */
@GameTestHolder(CritfallHorseman.MOD_ID)
@PrefixGameTestTemplate(false)
public class RewardGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE, batch = "horseman_shared_drops", timeoutTicks = 200)
    public void sharedDropsAtHisBody(GameTestHelper helper) {
        RewardScenarios.sharedDropsAtHisBody(helper);
    }

    @GameTest(template = TEMPLATE, batch = "horseman_lantern_breath_saves", timeoutTicks = 100)
    public void lanternBreathSavesThroughCritfall(GameTestHelper helper) {
        RewardScenarios.lanternBreathSavesThroughCritfall(helper);
    }

    @GameTest(template = TEMPLATE, batch = "horseman_rope_saves", timeoutTicks = 100)
    public void ropeSavesThroughCritfall(GameTestHelper helper) {
        RewardScenarios.ropeSavesThroughCritfall(helper);
    }

    @GameTest(template = TEMPLATE, batch = "horseman_treats_roll", timeoutTicks = 100)
    public void treatsRollThroughCritfall(GameTestHelper helper) {
        RewardScenarios.treatsRollThroughCritfall(helper);
    }

    @GameTest(template = TEMPLATE, batch = "horseman_wraithwalk", timeoutTicks = 100)
    public void wraithwalkPassesThrough(GameTestHelper helper) {
        RewardScenarios.wraithwalkPassesThrough(helper);
    }

    @GameTest(template = TEMPLATE, batch = "horseman_last_treat_message", timeoutTicks = 200)
    public void lastTreatShowsItsMessage(GameTestHelper helper) {
        RewardScenarios.lastTreatShowsItsMessage(helper);
    }

    @GameTest(template = TEMPLATE, batch = "horseman_reward_rates", timeoutTicks = 100)
    public void rewardRates(GameTestHelper helper) {
        RewardScenarios.rewardRates(helper);
    }
}
