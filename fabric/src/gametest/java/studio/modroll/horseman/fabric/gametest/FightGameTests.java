package studio.modroll.horseman.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.horseman.gametest.BalanceScenarios;
import studio.modroll.horseman.gametest.FightScenarios;

/** Fabric registration for the shared fight and balance GameTests, each in its own batch. */
public class FightGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_head_loop", timeoutTicks = 400)
    public void headLoop(GameTestHelper helper) {
        FightScenarios.headLoop(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_saves_through_critfall", timeoutTicks = 300)
    public void savesThroughCritfall(GameTestHelper helper) {
        FightScenarios.savesThroughCritfall(helper);
    }

    @GameTest(
            template = FabricGameTest.EMPTY_STRUCTURE,
            batch = "horseman_steed_death_puts_him_on_foot",
            timeoutTicks = 200)
    public void steedDeathPutsHimOnFoot(GameTestHelper helper) {
        FightScenarios.steedDeathPutsHimOnFoot(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_rides_off_at_sunrise", timeoutTicks = 300)
    public void ridesOffAtSunrise(GameTestHelper helper) {
        FightScenarios.ridesOffAtSunrise(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_date_rules", timeoutTicks = 100)
    public void dateRules(GameTestHelper helper) {
        FightScenarios.dateRules(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_never_breaks_blocks", timeoutTicks = 400)
    public void neverBreaksBlocks(GameTestHelper helper) {
        FightScenarios.neverBreaksBlocks(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_drops_sword_and_head", timeoutTicks = 200)
    public void dropsSwordAndHead(GameTestHelper helper) {
        FightScenarios.dropsSwordAndHead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_health_from_data", timeoutTicks = 100)
    public void healthFromData(GameTestHelper helper) {
        FightScenarios.healthFromData(helper);
    }

    @GameTest(
            template = FabricGameTest.EMPTY_STRUCTURE,
            batch = "horseman_repeating_lines_cool_down",
            timeoutTicks = 200)
    public void repeatingLinesCoolDown(GameTestHelper helper) {
        FightScenarios.repeatingLinesCoolDown(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_damage_within_targets", timeoutTicks = 200)
    public void damageWithinTargets(GameTestHelper helper) {
        BalanceScenarios.damageWithinTargets(helper);
    }

    @GameTest(
            template = FabricGameTest.EMPTY_STRUCTURE,
            batch = "horseman_head_comes_back_from_water",
            timeoutTicks = 300)
    public void headComesBackFromWater(GameTestHelper helper) {
        BalanceScenarios.headComesBackFromWater(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "horseman_reach_and_sweep", timeoutTicks = 200)
    public void reachAndSweep(GameTestHelper helper) {
        BalanceScenarios.reachAndSweep(helper);
    }
}
