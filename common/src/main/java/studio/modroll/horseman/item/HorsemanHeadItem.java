package studio.modroll.horseman.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import studio.modroll.horseman.CritfallHorseman;

/**
 * The head he drops on death, wearable in the helmet slot. Worn, it is a lantern: one key switches its light, another
 * breathes fire once a day (see {@link Lantern}).
 */
public class HorsemanHeadItem extends Item implements Equipable {

    public HorsemanHeadItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    /** Puts the light out while the lantern light is switched off in the config, and refills the breath at dawn. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level instanceof ServerLevel server && holder.tickCount % 20 == 0) {
            if (Lantern.isLit(stack) && !Lantern.lightAllowed()) {
                Lantern.setLit(stack, false);
            }
            Charges.refresh(stack, server, Lantern.BREATH_CHARGES);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Charges.barVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Charges.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Charges.barColour();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (!CritfallHorseman.config().enabled()) {
            return;
        }
        if (CritfallHorseman.config().lanternLight()) {
            lines.add(Component.translatable(
                            Lantern.isLit(stack)
                                    ? "item.critfall_horseman.horseman_head.lit"
                                    : "item.critfall_horseman.horseman_head.unlit",
                            Component.keybind(Lantern.LIGHT_KEY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (CritfallHorseman.config().lanternBreath()) {
            lines.add(Component.translatable(
                            "item.critfall_horseman.horseman_head.breath", Component.keybind(Lantern.BREATH_KEY))
                    .withStyle(ChatFormatting.GRAY));
            if (Charges.barVisible(stack)) {
                lines.add(Component.translatable("item.critfall_horseman.charges.dawn")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
