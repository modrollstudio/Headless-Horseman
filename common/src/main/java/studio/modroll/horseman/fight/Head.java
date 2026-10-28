package studio.modroll.horseman.fight;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.entity.ThrownHead;
import studio.modroll.horseman.item.HorsemanItems;

/**
 * Helpers for the head while it's out of his hand. The head is a burning head item tagged with the Horseman's UUID
 * and his head generation; it can be a {@link ThrownHead}, an item entity, or in a player's inventory.
 */
public final class Head {

    public static final double SENSE_RANGE = 128.0;

    private static final String OWNER = "Horseman";
    private static final String GENERATION = "Generation";

    private Head() {}

    /** Either {@code entity} (in flight or on the ground) or {@code carrier} is set. */
    public record Whereabouts(
            Vec3 position,
            @Nullable Entity entity,
            @Nullable ServerPlayer carrier) {}

    /** A new head item tagged for {@code horseman}'s current generation. */
    public static ItemStack of(Horseman horseman) {
        ItemStack stack = new ItemStack(HorsemanItems.BURNING_HEAD);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(OWNER, horseman.getUUID());
        tag.putInt(GENERATION, horseman.headGeneration());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Nullable
    public static UUID owner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!stack.is(HorsemanItems.BURNING_HEAD) || data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
    }

    public static int generation(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? -1 : data.copyTag().getInt(GENERATION);
    }

    /** Whether {@code stack} is {@code horseman}'s current head, not one from before he last took it back. */
    public static boolean isHeadOf(ItemStack stack, Horseman horseman) {
        return horseman.getUUID().equals(owner(stack)) && generation(stack) == horseman.headGeneration();
    }

    /** Whether {@code stack} belongs to a Horseman who is gone or who no longer needs it. */
    public static boolean isStale(ItemStack stack, ServerLevel level) {
        UUID owner = owner(stack);
        if (owner == null) {
            return true;
        }
        return !(level.getEntity(owner) instanceof Horseman horseman)
                || !horseman.isAlive()
                || !horseman.isHeadless()
                || generation(stack) != horseman.headGeneration();
    }

    /** Where {@code horseman}'s head is, or null if it isn't within {@link #SENSE_RANGE}. */
    @Nullable
    public static Whereabouts find(Horseman horseman, ServerLevel level) {
        AABB around = horseman.getBoundingBox().inflate(SENSE_RANGE);
        for (ThrownHead thrown :
                level.getEntitiesOfClass(ThrownHead.class, around, t -> isHeadOf(t.getItem(), horseman))) {
            return new Whereabouts(thrown.position(), thrown, null);
        }
        for (ItemEntity item :
                level.getEntitiesOfClass(ItemEntity.class, around, i -> isHeadOf(i.getItem(), horseman))) {
            return new Whereabouts(item.position(), item, null);
        }
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && holds(player, horseman)) {
                return new Whereabouts(player.position(), null, player);
            }
        }
        return null;
    }

    /** Checks the inventory and the stack held on the cursor. */
    private static boolean holds(Player player, Horseman horseman) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isHeadOf(player.getInventory().getItem(i), horseman)) {
                return true;
            }
        }
        return isHeadOf(player.containerMenu.getCarried(), horseman);
    }

    /** Removes the head from the inventory and the cursor. */
    public static void takeFrom(Player player, Horseman horseman) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isHeadOf(player.getInventory().getItem(i), horseman)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (isHeadOf(player.containerMenu.getCarried(), horseman)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    /** Removes every copy of his head, of any generation, in flight or on the ground. */
    public static void putOut(Horseman horseman, ServerLevel level) {
        AABB around = horseman.getBoundingBox().inflate(SENSE_RANGE);
        level.getEntitiesOfClass(
                        ThrownHead.class, around, t -> horseman.getUUID().equals(owner(t.getItem())))
                .forEach(Entity::discard);
        level.getEntitiesOfClass(
                        ItemEntity.class, around, i -> horseman.getUUID().equals(owner(i.getItem())))
                .forEach(Entity::discard);
    }
}
