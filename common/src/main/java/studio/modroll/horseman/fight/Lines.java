package studio.modroll.horseman.fight;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * The Horseman's spoken lines, shown in dark red with a sound sting. Lines that happen once per fight are shown as a
 * title and subtitle; lines that can repeat go on the action bar instead so they don't fill the screen.
 */
public final class Lines {

    public static final double RANGE = 64.0;

    public enum Line {
        ARRIVAL("arrival", HorsemanSounds.STING_ARRIVAL, true),
        HEADLESS("headless", HorsemanSounds.STING_HEADLESS, false),
        CHASE("chase", HorsemanSounds.STING_CHASE, false),
        RETAKE("retake", HorsemanSounds.STING_RETAKE, false),
        STEED("steed", HorsemanSounds.STING_STEED, true),
        SUNRISE("sunrise", HorsemanSounds.STING_SUNRISE, true),
        KILL("kill", HorsemanSounds.STING_KILL, false);

        final String key;
        final HorsemanSounds.Cue sting;
        final boolean once;

        Line(String key, HorsemanSounds.Cue sting, boolean once) {
            this.key = key;
            this.sting = sting;
            this.once = once;
        }

        /** Once per fight: shown as a title. Otherwise on the action bar. */
        public boolean once() {
            return once;
        }

        public String titleKey() {
            return "line.critfall_horseman." + key + ".title";
        }

        public String subtitleKey() {
            return "line.critfall_horseman." + key + ".subtitle";
        }
    }

    private Lines() {}

    /** {@code speaker} says {@code line} to everyone within {@link #RANGE}, unless lines are switched off. */
    public static void say(Entity speaker, Line line) {
        if (!CritfallHorseman.config().lines() || !(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        Component title =
                Component.translatable(line.titleKey()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable(line.subtitleKey()).withStyle(ChatFormatting.DARK_RED);
        Component bar = Component.empty().append(title).append(" ").append(subtitle);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) > RANGE * RANGE) {
                continue;
            }
            if (line.once) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 50, 20));
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                player.connection.send(new ClientboundSetTitleTextPacket(title));
            } else {
                player.connection.send(new ClientboundSetActionBarTextPacket(bar));
            }
            HorsemanSounds.playTo(player, line.sting);
        }
    }
}
