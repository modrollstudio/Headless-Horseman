package studio.modroll.horseman.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Charges kept on an item stack that all come back at the next in game dawn. The stack stores how many are left, how
 * many it holds, and the day they were counted on; a stack with none stored is full. An item in a chest doesn't tick,
 * so every read first refills a stack whose day is past.
 */
public final class Charges {

    private static final String LEFT = "Charges";
    private static final String MAX = "MaxCharges";
    private static final String DAY = "ChargeDay";
    private static final int BAR_COLOUR = 0xF08A24;

    private Charges() {}

    /** The in game day, which turns over at dawn. */
    public static long day(Level level) {
        return Math.floorDiv(level.getDayTime(), 24000L);
    }

    /** Charges left today, out of {@code max}. */
    public static int left(ItemStack stack, Level level, int max) {
        CompoundTag tag = tag(stack);
        if (!tag.contains(LEFT) || tag.getLong(DAY) != day(level)) {
            return max;
        }
        return Mth.clamp(tag.getInt(LEFT), 0, max);
    }

    /** Spends one charge; false, spending nothing, if none are left. */
    public static boolean spend(ItemStack stack, Level level, int max) {
        int left = left(stack, level, max);
        if (left <= 0) {
            return false;
        }
        store(stack, left - 1, max, day(level));
        return true;
    }

    /** Writes the full charges back once the day has turned, so the bar and the tooltip show them. */
    public static void refresh(ItemStack stack, Level level, int max) {
        CompoundTag tag = tag(stack);
        if (tag.contains(LEFT) && (tag.getLong(DAY) != day(level) || tag.getInt(MAX) != max)) {
            store(stack, left(stack, level, max), max, day(level));
        }
    }

    private static void store(ItemStack stack, int left, int max, long day) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(LEFT, left);
            tag.putInt(MAX, max);
            tag.putLong(DAY, day);
        });
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** As stored, without a level, for the bar and tooltip on the client. */
    private static int storedLeft(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.contains(LEFT) ? tag.getInt(LEFT) : storedMax(stack);
    }

    private static int storedMax(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.contains(MAX) ? Math.max(1, tag.getInt(MAX)) : 1;
    }

    public static boolean barVisible(ItemStack stack) {
        return tag(stack).contains(LEFT) && storedLeft(stack) < storedMax(stack);
    }

    public static int barWidth(ItemStack stack) {
        return Math.round(13.0F * storedLeft(stack) / storedMax(stack));
    }

    public static int barColour() {
        return BAR_COLOUR;
    }

    /** "Charges: 2 of 3", plus a line that they come back at dawn while any are spent. */
    public static void tooltip(ItemStack stack, List<Component> lines) {
        CompoundTag tag = tag(stack);
        if (!tag.contains(LEFT)) {
            lines.add(Component.translatable("item.critfall_horseman.charges.full")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.translatable("item.critfall_horseman.charges", storedLeft(stack), storedMax(stack))
                .withStyle(ChatFormatting.GRAY));
        if (storedLeft(stack) < storedMax(stack)) {
            lines.add(Component.translatable("item.critfall_horseman.charges.dawn")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
