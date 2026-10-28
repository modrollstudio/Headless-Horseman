package studio.modroll.horseman.entity;

import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import studio.modroll.horseman.CritfallHorseman;

/** Entity types. Each loader registers them through {@link #register} and {@link #attributes}. */
public final class HorsemanEntities {

    public static final ResourceLocation HORSEMAN_ID = CritfallHorseman.id("horseman");
    public static final ResourceLocation STEED_ID = CritfallHorseman.id("gallows_steed");
    public static final ResourceLocation THROWN_HEAD_ID = CritfallHorseman.id("thrown_head");

    public static final EntityType<Horseman> HORSEMAN = EntityType.Builder.of(Horseman::new, MobCategory.MONSTER)
            .sized(0.9F, 2.2F)
            .eyeHeight(1.9F)
            .fireImmune()
            .clientTrackingRange(16)
            .build(HORSEMAN_ID.toString());

    public static final EntityType<GallowsSteed> STEED = EntityType.Builder.of(GallowsSteed::new, MobCategory.MONSTER)
            .sized(1.6F, 2.4F)
            .eyeHeight(2.6F)
            .passengerAttachments(GallowsSteed.SEAT)
            .fireImmune()
            .clientTrackingRange(16)
            .build(STEED_ID.toString());

    public static final EntityType<ThrownHead> THROWN_HEAD = EntityType.Builder.<ThrownHead>of(
                    ThrownHead::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(8)
            .updateInterval(2)
            .build(THROWN_HEAD_ID.toString());

    private HorsemanEntities() {}

    public static void register(BiConsumer<ResourceLocation, EntityType<?>> registrar) {
        registrar.accept(HORSEMAN_ID, HORSEMAN);
        registrar.accept(STEED_ID, STEED);
        registrar.accept(THROWN_HEAD_ID, THROWN_HEAD);
    }

    public static void attributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> registrar) {
        registrar.accept(HORSEMAN, Horseman.createAttributes().build());
        registrar.accept(STEED, GallowsSteed.createAttributes().build());
    }
}
