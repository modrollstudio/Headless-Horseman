package studio.modroll.horseman.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.gametest.SmokeScenarios;

/** NeoForge registration for the shared smoke GameTests. */
@GameTestHolder(CritfallHorseman.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmokeGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void horsemanLoads(GameTestHelper helper) {
        SmokeScenarios.horsemanLoads(helper, ModList.get()::isLoaded);
    }

    @GameTest(template = TEMPLATE)
    public void critfallPresent(GameTestHelper helper) {
        SmokeScenarios.critfallPresent(helper, ModList.get()::isLoaded);
    }

    @GameTest(template = TEMPLATE)
    public void geckolibPresent(GameTestHelper helper) {
        SmokeScenarios.geckolibPresent(helper, ModList.get()::isLoaded);
    }

    @GameTest(template = TEMPLATE)
    public void summonedMounted(GameTestHelper helper) {
        SmokeScenarios.summonedMounted(helper);
    }

    @GameTest(template = TEMPLATE)
    public void facesWithSteed(GameTestHelper helper) {
        SmokeScenarios.facesWithSteed(helper);
    }
}
