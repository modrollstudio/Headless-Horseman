package studio.modroll.horseman.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.item.Lantern;

/** The head item's {@code critfall_horseman:unlit} model property: 1 shows the face with the light out. */
public final class HeadModel {

    public static final ResourceLocation UNLIT = CritfallHorseman.id("unlit");

    private HeadModel() {}

    public static float unlit(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        return Lantern.isLit(stack) ? 0.0F : 1.0F;
    }
}
