package studio.modroll.horseman.demo;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.fight.Head;
import studio.modroll.horseman.spawn.HorsemanSpawner;

/**
 * Dev-only {@code /horseman demo}: the head passing phase with three dummy party members, for recording alone. The
 * Horseman runs his real fight logic; only the dummies' choices (when to pass, to whom) are scripted. Delete the
 * {@code demo} source sets to remove it.
 */
public final class HorsemanDemo {

    private static final String[] NAMES = {"Ash", "Bram", "Cole"};
    private static final int THROW_AT_TICK = 60;
    private static final int PASSES = 4;
    /** A carrier passes when his steed gets this close. */
    private static final double PASS_DISTANCE = 7.0;

    private static final double WALK_SPEED = 0.3;

    private static Demo demo;

    private HorsemanDemo() {}

    private static final class Demo {
        final ServerLevel level;
        final Horseman horseman;
        final List<ServerPlayer> dummies;
        int ticks;
        int passesLeft = PASSES;
        boolean thrown;
        boolean done;

        Demo(ServerLevel level, Horseman horseman, List<ServerPlayer> dummies) {
            this.level = level;
            this.horseman = horseman;
            this.dummies = dummies;
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("horseman")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("demo")
                        .executes(context -> start(context.getSource()))
                        .then(Commands.literal("stop").executes(context -> {
                            stop();
                            context.getSource().sendSuccess(() -> Component.literal("Demo cleaned up."), false);
                            return 1;
                        }))));
    }

    private static int start(CommandSourceStack source) {
        stop();
        ServerLevel level = source.getLevel();
        Vec3 forward = Vec3.directionFromRotation(0.0F, source.getRotation().y);
        Vec3 center = source.getPosition();
        List<ServerPlayer> dummies = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            Vec3 spot = ground(
                    level,
                    center.add(
                            forward.yRot((60.0F + 120.0F * i) * Mth.DEG_TO_RAD).scale(6.0)));
            dummies.add(dummy(level, NAMES[i], spot));
        }
        // Far enough that the first carrier can pick the head up (20 ticks after it lands) before he gets there.
        Horseman horseman = HorsemanSpawner.spawn(level, ground(level, center.add(forward.scale(36.0))), null);
        face(horseman, dummies.get(0).position());
        demo = new Demo(level, horseman, dummies);
        source.sendSuccess(
                () -> Component.literal("Demo running. Stay in creative or spectator. /horseman demo stop cleans up."),
                false);
        if (source.getPlayer() instanceof ServerPlayer me && !me.isCreative() && !me.isSpectator()) {
            source.sendFailure(Component.literal("You are not in creative or spectator: he will go for you too."));
        }
        return 1;
    }

    public static void stop() {
        if (demo == null) {
            return;
        }
        Demo old = demo;
        demo = null;
        if (!old.horseman.isRemoved()) {
            Head.putOut(old.horseman, old.level);
            Entity steed = old.horseman.getVehicle();
            old.horseman.stopRiding();
            if (steed != null) {
                steed.discard();
            }
            old.horseman.discard();
        }
        for (ServerPlayer dummy : old.dummies) {
            dummy.getInventory().clearContent();
            old.level.getServer().getPlayerList().remove(dummy);
        }
    }

    public static void tick(MinecraftServer server) {
        if (demo == null) {
            return;
        }
        if (demo.level.getServer() != server) {
            demo = null;
            return;
        }
        // Their connections never tick, so nothing else runs their player tick (item pickup, held item updates).
        for (ServerPlayer dummy : demo.dummies) {
            dummy.doTick();
        }
        Horseman horseman = demo.horseman;
        if (demo.done || !horseman.isAlive()) {
            return;
        }
        demo.ticks++;
        if (demo.ticks == THROW_AT_TICK) {
            horseman.startThrow(demo.dummies.get(0).getEyePosition());
        }
        if (!demo.thrown) {
            demo.thrown = horseman.isHeadless();
            lookAt(horseman);
            return;
        }
        if (!horseman.isHeadless()) {
            demo.done = true;
            demo.level
                    .getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(Component.literal("Demo finished. /horseman demo stop cleans up."), false);
            return;
        }
        Entity body = horseman.getVehicle() != null ? horseman.getVehicle() : horseman;
        ServerPlayer carrier = carrier(horseman);
        if (carrier != null) {
            lookAt(horseman);
            if (demo.passesLeft > 0 && body.distanceTo(carrier) < PASS_DISTANCE) {
                demo.passesLeft--;
                pass(carrier, receiver(carrier, body));
            }
            return;
        }
        lookAt(horseman);
        Head.Whereabouts head = horseman.whereabouts();
        if (head != null && head.entity() instanceof ItemEntity item) {
            ServerPlayer nearest = demo.dummies.stream()
                    .min(Comparator.comparingDouble(d -> d.distanceToSqr(item)))
                    .orElseThrow();
            Vec3 to = item.position().subtract(nearest.position()).multiply(1, 0, 1);
            if (to.length() > 0.8) {
                Vec3 next = nearest.position().add(to.normalize().scale(Math.min(WALK_SPEED, to.length())));
                nearest.teleportTo(demo.level, next.x, item.getY(), next.z, nearest.getYRot(), 0.0F);
            }
        }
    }

    /**
     * A player in creative, so he never targets, tramples or burns them, but still chases them as carriers. Added
     * through the player list, as the GameTests do, so {@link Head#find} sees them.
     */
    private static ServerPlayer dummy(ServerLevel level, String name, Vec3 at) {
        UUID id = UUID.nameUUIDFromBytes(("horseman-demo-" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // NeoForge throws when a mod packet (GeckoLib's animations) goes to a connection that never agreed to mod
        // packets. Nothing reads what is sent to a dummy, so drop it all.
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie) {
            @Override
            public void send(Packet<?> packet) {}

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
        };
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        player.teleportTo(level, at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** From their own inventories: his record of the carrier only refreshes every few ticks. */
    private static ServerPlayer carrier(Horseman horseman) {
        for (ServerPlayer dummy : demo.dummies) {
            if (dummy.getInventory().contains(stack -> Head.isHeadOf(stack, horseman))) {
                return dummy;
            }
        }
        return null;
    }

    /** The other dummy farthest from him. */
    private static ServerPlayer receiver(ServerPlayer carrier, Entity body) {
        return demo.dummies.stream()
                .filter(d -> d != carrier)
                .max(Comparator.comparingDouble(d -> d.distanceToSqr(body)))
                .orElseThrow();
    }

    /** Puts the head in the main hand and throws it with the real item use, aimed to arrive at chest height. */
    private static void pass(ServerPlayer from, ServerPlayer to) {
        for (int i = 0; i < from.getInventory().getContainerSize(); i++) {
            ItemStack stack = from.getInventory().getItem(i);
            if (Head.isHeadOf(stack, demo.horseman)) {
                from.getInventory().setItem(i, ItemStack.EMPTY);
                from.setItemInHand(InteractionHand.MAIN_HAND, stack);
            }
        }
        Vec3 start = from.getEyePosition().subtract(0, 0.1, 0);
        Vec3 aim = to.position().add(0, 1.0, 0).subtract(start);
        float yaw = (float) (Mth.atan2(aim.z, aim.x) * Mth.RAD_TO_DEG) - 90.0F;
        // BurningHeadItem.use throws 5 degrees above where the player looks.
        float pitch = pitchFor(aim.horizontalDistance(), aim.y) + 5.0F;
        from.setYRot(yaw);
        from.setYHeadRot(yaw);
        from.setXRot(pitch);
        from.getMainHandItem().use(demo.level, from, InteractionHand.MAIN_HAND);
        from.swing(InteractionHand.MAIN_HAND, true);
    }

    /** The flattest pitch that lands a player's throw at {@code rise} after {@code distance}, as ThrownHead flies. */
    private static float pitchFor(double distance, double rise) {
        float best = 0.0F;
        double bestError = Double.MAX_VALUE;
        for (float pitch = 10.0F; pitch >= -45.0F; pitch -= 0.5F) {
            double vh = 0.9 * Mth.cos(pitch * Mth.DEG_TO_RAD);
            double vy = -0.9 * Mth.sin(pitch * Mth.DEG_TO_RAD);
            double x = 0.0;
            double y = 0.0;
            for (int t = 0; t < 100 && x < distance; t++) {
                x += vh;
                y += vy;
                vh *= 0.99;
                vy = vy * 0.99 - 0.05;
            }
            double error = Math.abs(y - rise);
            if (x >= distance && error < bestError) {
                best = pitch;
                bestError = error;
            }
        }
        return best;
    }

    private static void lookAt(Horseman horseman) {
        for (ServerPlayer dummy : demo.dummies) {
            Vec3 to = horseman.position().subtract(dummy.position());
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            dummy.setYRot(yaw);
            dummy.setYHeadRot(yaw);
            dummy.setXRot(0.0F);
        }
    }

    private static void face(Horseman horseman, Vec3 at) {
        float yaw = (float) (Mth.atan2(at.z - horseman.getZ(), at.x - horseman.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        GallowsSteed steed = horseman.steed();
        for (Entity e : steed != null ? List.of(horseman, steed) : List.<Entity>of(horseman)) {
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
        }
    }

    private static Vec3 ground(ServerLevel level, Vec3 at) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        return new Vec3(Mth.floor(at.x) + 0.5, y, Mth.floor(at.z) + 0.5);
    }
}
