package studio.modroll.horseman.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.item.Lantern;

/** Client to server: a lantern key was pressed. {@code breath} is the fire key; otherwise the light key. */
public record LanternPayload(boolean breath) implements CustomPacketPayload {

    public static final Type<LanternPayload> TYPE = new Type<>(CritfallHorseman.id("lantern"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LanternPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, LanternPayload::breath, LanternPayload::new);

    @Override
    public Type<LanternPayload> type() {
        return TYPE;
    }

    /** On the server thread. */
    public static void handle(LanternPayload payload, ServerPlayer player) {
        if (payload.breath()) {
            Lantern.breathe(player);
        } else {
            Lantern.switchLight(player);
        }
    }
}
