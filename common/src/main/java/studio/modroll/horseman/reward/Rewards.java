package studio.modroll.horseman.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.HorsemanStats;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.item.Lantern;

/**
 * His drops: one set per kill at his body, like any boss loot, for the party to share. A dropped head is lit. See
 * {@link #roll}.
 */
public final class Rewards {

    public static final ResourceKey<LootTable> TABLE =
            ResourceKey.create(Registries.LOOT_TABLE, CritfallHorseman.id("rewards/horseman"));
    public static final ResourceKey<LootTable> PICK =
            ResourceKey.create(Registries.LOOT_TABLE, CritfallHorseman.id("rewards/horseman_pick"));

    /** Rerolls for a second pick that differs from the first. */
    private static final int SECOND_TRIES = 16;

    private Rewards() {}

    /** Drops one roll at {@code at} and returns the item entities. */
    public static List<ItemEntity> drop(ServerLevel level, Vec3 at, long seed) {
        List<ItemEntity> dropped = new ArrayList<>();
        for (ItemStack stack : roll(level, seed)) {
            ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
            dropped.add(item);
        }
        return dropped;
    }

    /**
     * One kill's rewards: everything in {@code rewards/horseman} (his head), one pick from {@code rewards/horseman_pick}
     * by its weights, and with {@code second_reward_chance} a second pick, rolled again until it differs from the first,
     * so it falls on the others by their weights. If no other item can come up, there is no second. Each table gets
     * its own seed from one {@code java.util.Random}: vanilla's loot random gives nearly the same first draws for
     * nearby seeds.
     */
    public static List<ItemStack> roll(ServerLevel level, long seed) {
        Random random = new Random(seed);
        List<ItemStack> out = table(level, TABLE, random.nextLong());
        List<ItemStack> first = table(level, PICK, random.nextLong());
        out.addAll(first);
        if (!first.isEmpty()
                && random.nextDouble() < HorsemanStats.of(level, "horseman").secondRewardChance()) {
            for (int i = 0; i < SECOND_TRIES; i++) {
                List<ItemStack> second = table(level, PICK, random.nextLong());
                if (!second.isEmpty() && !ItemStack.isSameItem(second.get(0), first.get(0))) {
                    out.addAll(second);
                    break;
                }
            }
        }
        out.replaceAll(stack -> stack.is(HorsemanItems.HORSEMAN_HEAD) ? Lantern.litHead() : stack);
        return out;
    }

    private static List<ItemStack> table(ServerLevel level, ResourceKey<LootTable> key, long seed) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        return new ArrayList<>(
                table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY), seed));
    }
}
