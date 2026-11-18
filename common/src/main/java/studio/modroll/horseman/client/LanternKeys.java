package studio.modroll.horseman.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.item.Lantern;
import studio.modroll.horseman.network.LanternPayload;

/** The worn head's two keys: H switches its light, B breathes fire. Each loader registers them and calls {@link #tick}. */
public final class LanternKeys {

    public static final KeyMapping LIGHT =
            new KeyMapping(Lantern.LIGHT_KEY, InputConstants.Type.KEYSYM, InputConstants.KEY_H, Lantern.KEY_CATEGORY);
    public static final KeyMapping BREATH =
            new KeyMapping(Lantern.BREATH_KEY, InputConstants.Type.KEYSYM, InputConstants.KEY_B, Lantern.KEY_CATEGORY);

    private LanternKeys() {}

    /** Once per client tick; {@code send} sends a payload to the server. */
    public static void tick(Minecraft minecraft, Consumer<LanternPayload> send) {
        boolean wearing = minecraft.player != null
                && minecraft.player.getItemBySlot(EquipmentSlot.HEAD).is(HorsemanItems.HORSEMAN_HEAD);
        while (LIGHT.consumeClick()) {
            if (wearing) {
                send.accept(new LanternPayload(false));
            }
        }
        while (BREATH.consumeClick()) {
            if (wearing) {
                send.accept(new LanternPayload(true));
            }
        }
    }
}
