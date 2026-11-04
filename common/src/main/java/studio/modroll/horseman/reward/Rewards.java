package studio.modroll.horseman.reward;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.HorsemanStats;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.item.Lantern;

/**
 * His drops. Each player who helped kill him rolls {@code loot_table/rewards/horseman.json} on their own, and their
 * drops are locked to them for {@code reward_lock_seconds}, then open to anyone. With no helper, or with {@code
 * per_player_rewards} off, one roll drops for anyone. A dropped head is lit.
 */
public final class Rewards {

    public static final ResourceKey<LootTable> TABLE =
            ResourceKey.create(Registries.LOOT_TABLE, CritfallHorseman.id("rewards/horseman"));

    private Rewards() {}

    /** Drops at {@code at} and returns the item entities. {@code helpers} are player UUIDs. */
    public static List<ItemEntity> drop(ServerLevel level, Vec3 at, Collection<UUID> helpers, long seed) {
        List<ItemEntity> dropped = new ArrayList<>();
        if (!CritfallHorseman.config().perPlayerRewards() || helpers.isEmpty()) {
            roll(level, seed).forEach(stack -> dropped.add(spawn(level, at, stack, null)));
            return dropped;
        }
        int lockTicks = HorsemanStats.of(level, "horseman").rewardLockSeconds() * 20;
        long n = 0;
        for (UUID helper : helpers) {
            for (ItemStack stack : roll(level, seed + 31 * n++)) {
                ItemEntity item = spawn(level, at, stack, helper);
                LockedDrops.get(level).lock(item, level.getGameTime() + lockTicks);
                dropped.add(item);
            }
        }
        return dropped;
    }

    private static List<ItemStack> roll(ServerLevel level, long seed) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(TABLE);
        List<ItemStack> stacks =
                table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY), seed);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            out.add(stack.is(HorsemanItems.HORSEMAN_HEAD) ? Lantern.litHead() : stack);
        }
        return out;
    }

    private static ItemEntity spawn(ServerLevel level, Vec3 at, ItemStack stack, @Nullable UUID owner) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
        item.setDefaultPickUpDelay();
        if (owner != null) {
            item.setTarget(owner);
        }
        level.addFreshEntity(item);
        return item;
    }

    /** Unlocks the drops whose time is up. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            LockedDrops.get(level).unlockDue(level);
        }
    }
}
