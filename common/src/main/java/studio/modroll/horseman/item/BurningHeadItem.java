package studio.modroll.horseman.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.ThrownHead;
import studio.modroll.horseman.fight.Head;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * The head as players carry it during the fight. Using it throws it a short distance. A copy whose Horseman is gone, or
 * who has his head back, removes itself from the inventory.
 */
public class BurningHeadItem extends Item {

    public static final float THROW_SPEED = 0.9F;

    public BurningHeadItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CritfallHorseman.config().enabled() || !CritfallHorseman.config().headPassing()) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            ThrownHead thrown = new ThrownHead(level, player, stack.copyWithCount(1), false);
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -5.0F, THROW_SPEED, 1.0F);
            level.addFreshEntity(thrown);
            HorsemanSounds.play(player, HorsemanSounds.HEAD_WHOOSH, 0.8F);
        }
        stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level instanceof ServerLevel server && holder.tickCount % 10 == 0 && Head.isStale(stack, server)) {
            stack.setCount(0);
            server.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    holder.getX(),
                    holder.getY() + 1.0,
                    holder.getZ(),
                    8,
                    0.2,
                    0.2,
                    0.2,
                    0.02);
            server.sendParticles(
                    ParticleTypes.WHITE_ASH,
                    holder.getX(),
                    holder.getY() + 1.0,
                    holder.getZ(),
                    20,
                    0.3,
                    0.3,
                    0.3,
                    0.02);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
