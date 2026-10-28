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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
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
     * Health lost by an unarmoured player on Normal, each within 1.5 of the target average: hit with head 7.5, crit 12,
     * headless hit 5, line of fire 6, charge 4.5, steed 3.5. A natural 19 must not crit, and his attack bonus is +9.
     */
    public static void damageWithinTargets(GameTestHelper helper) {
        Horseman horseman = FightScenarios.still(helper, new BlockPos(1, 2, 1));
        GallowsSteed steed = horseman.steed();
        List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            players.add(FightScenarios.mockPlayer(helper, new Vec3(3.5, 1, 1.5), false));
        }
        // New players are invulnerable for their first 60 ticks.
        helper.runAfterDelay(80, () -> {
            Map<String, Float> lost = new LinkedHashMap<>();
            Map<String, AttackOutcome> outcomes = new LinkedHashMap<>();
            Difficulty before = helper.getLevel().getDifficulty();
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            RollService.setRoller(new DiceRoller(new Held()));
            try {
                int[] next = {0};
                java.util.function.Function<String, ServerPlayer> fresh = name -> {
                    ServerPlayer player = players.get(next[0]++);
                    player.setHealth(20.0F);
                    player.removeAllEffects();
                    return player;
                };
                d20 = 15;
                ServerPlayer hit = fresh.apply("hit");
                outcomes.put("hit", swing(horseman, hit));
                lost.put("hit", 20.0F - hit.getHealth());
                d20 = 19;
                ServerPlayer nineteen = fresh.apply("19");
                outcomes.put("19", swing(horseman, nineteen));
                d20 = 20;
                ServerPlayer crit = fresh.apply("crit");
                outcomes.put("crit", swing(horseman, crit));
                lost.put("crit", 20.0F - crit.getHealth());
                d20 = 15;
                horseman.setHeadless(true);
                ServerPlayer headless = fresh.apply("headless");
                swing(horseman, headless);
                lost.put("headless", 20.0F - headless.getHealth());
                horseman.setHeadless(false);
                d20 = 2;
                ServerPlayer charged = fresh.apply("charge");
                HorsemanCombat.trample(charged, steed);
                lost.put("charge", 20.0F - charged.getHealth());
                ServerPlayer burnt = fresh.apply("line of fire");
                HorsemanCombat.burn(burnt);
                lost.put("line of fire", 20.0F - burnt.getHealth());
                d20 = 15;
                ServerPlayer kicked = fresh.apply("steed");
                RollService.performAttack(
                        steed, kicked, AttackContext.melee(steed.damageSources().mobAttack(steed), ItemStack.EMPTY));
                lost.put("steed", 20.0F - kicked.getHealth());
            } finally {
                RollService.resetRoller();
                d20 = 15;
                helper.getLevel().getServer().setDifficulty(before, true);
            }
            check(helper, lost, "hit", 7.5F);
            check(helper, lost, "crit", 12.0F);
            check(helper, lost, "headless", 5.0F);
            check(helper, lost, "charge", 4.5F);
            check(helper, lost, "line of fire", 6.0F);
            check(helper, lost, "steed", 3.5F);
            int bonus = RollService.effectiveEntity(horseman).attackBonus();
            expect(helper, bonus == 9, "his attack bonus must be +9, is " + bonus);
            expect(helper, outcomes.get("crit") == AttackOutcome.CRIT, "a natural 20 must crit, was " + outcomes);
            expect(helper, outcomes.get("19") == AttackOutcome.HIT, "a natural 19 must not crit, was " + outcomes);
            players.forEach(p -> helper.getLevel().getServer().getPlayerList().remove(p));
            steed.discard();
            horseman.discard();
            helper.succeed();
        });
    }

    private static AttackOutcome swing(Horseman horseman, ServerPlayer target) {
        AttackContext context = AttackContext.melee(horseman.damageSources().mobAttack(horseman), ItemStack.EMPTY);
        if (!horseman.isHeadless()) {
            context = context.withDamageDice(HorsemanCombat.burningSwordDice(horseman));
        }
        AttackResult result = RollService.performAttack(horseman, target, context);
        return result.outcome();
    }

    private static void check(GameTestHelper helper, Map<String, Float> lost, String what, float average) {
        float amount = lost.get(what);
        expect(
                helper,
                Math.abs(amount - average) <= 1.5F,
                "his " + what + " must take about " + average + " health on Normal, took " + amount + " " + lost);
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
