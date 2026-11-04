package studio.modroll.horseman.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Who a player's item powers may catch. */
public final class Targets {

    private Targets() {}

    /** Living, not the user, not a spectator or creative player, and another player only where PvP is on. */
    public static boolean canAffect(ServerPlayer user, LivingEntity target) {
        if (target == user || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other) {
            return !other.isCreative() && user.server.isPvpAllowed();
        }
        return true;
    }
}
