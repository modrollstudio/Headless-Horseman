package studio.modroll.horseman.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.effect.HorsemanEffects;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * Horseman's Treats. Three a day, eaten one at a time; Critfall rolls a d6 for what each one does: a fright, a short
 * teleport, a burst of speed, passing through creatures, night vision or healing. Every number is in {@code
 * item_stats/horsemans_treats.json}.
 */
public class HorsemansTreatsItem extends Item {

    public static final DiceExpression D6 = DiceExpression.parse("1d6");
    public static final int EAT_TICKS = 24;
    private static final int TELEPORT_TRIES = 16;
    /**
     * How long a treat's message stays before the empty pouch's may replace it. Holding the use button eats on and on,
     * so without this the last treat's message is overwritten at once.
     */
    public static final int MESSAGE_TICKS = 60;

    private static final Map<UUID, Long> LAST_EATEN = new HashMap<>();

    public HorsemansTreatsItem(Properties properties) {
        super(properties);
    }

    private static ItemStats stats(ServerLevel level) {
        return ItemStats.of(level, "horsemans_treats");
    }

    private static int charges(ServerLevel level) {
        return stats(level).integer("charges", 3, 1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CritfallHorseman.config().enabled() || !CritfallHorseman.config().treats()) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server && Charges.left(stack, server, charges(server)) <= 0) {
            Long eaten = LAST_EATEN.get(player.getUUID());
            if (eaten == null || server.getGameTime() - eaten >= MESSAGE_TICKS) {
                player.displayClientMessage(Component.translatable("item.critfall_horseman.charges.none"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return EAT_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        if (level instanceof ServerLevel server
                && eater instanceof ServerPlayer player
                && Charges.spend(stack, server, charges(server))) {
            eat(player);
            LAST_EATEN.put(player.getUUID(), server.getGameTime());
        }
        return stack;
    }

    /** Critfall rolls the d6 and the treat does what it shows. Returns the face. */
    public static int eat(ServerPlayer player) {
        int face = RollService.roll(D6).total();
        ItemStats stats = stats(player.serverLevel());
        switch (face) {
            case 1 -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks(stats, "fright_seconds", 6.0)));
                HorsemanSounds.playTo(player, HorsemanSounds.LAUGH_2);
            }
            case 2 -> teleport(player, stats.integer("teleport_distance", 8, 1));
            case 3 ->
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        ticks(stats, "speed_seconds", 20.0),
                        stats.integer("speed_level", 2, 1) - 1));
            case 4 ->
                player.addEffect(
                        new MobEffectInstance(HorsemanEffects.wraithwalk(), ticks(stats, "wraithwalk_seconds", 10.0)));
            case 5 ->
                player.addEffect(
                        new MobEffectInstance(MobEffects.NIGHT_VISION, ticks(stats, "night_vision_seconds", 180.0)));
            default -> player.heal(RollService.roll(stats.dice("heal", "2d4+2")).total());
        }
        player.displayClientMessage(
                Component.translatable("item.critfall_horseman.horsemans_treats.rolled." + face), true);
        return face;
    }

    private static int ticks(ItemStats stats, String key, double fallback) {
        return Math.round((float) stats.number(key, fallback, 0.05) * 20);
    }

    /** Like a chorus fruit, but Critfall rolls every offset. */
    private static void teleport(ServerPlayer player, int distance) {
        DiceExpression offset = DiceExpression.parse("1d" + (2 * distance + 1));
        Vec3 from = player.position();
        if (player.isPassenger()) {
            player.stopRiding();
        }
        for (int i = 0; i < TELEPORT_TRIES; i++) {
            double x = from.x + RollService.roll(offset).total() - distance - 1;
            double y = from.y + RollService.roll(offset).total() - distance - 1;
            double z = from.z + RollService.roll(offset).total() - distance - 1;
            if (player.randomTeleport(x, y, z, true)) {
                player.level()
                        .playSound(
                                null,
                                from.x,
                                from.y,
                                from.z,
                                SoundEvents.CHORUS_FRUIT_TELEPORT,
                                SoundSource.PLAYERS,
                                1.0F,
                                0.8F);
                player.resetFallDistance();
                return;
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level instanceof ServerLevel server && holder.tickCount % 20 == 0) {
            Charges.refresh(stack, server, charges(server));
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
        lines.add(Component.translatable("item.critfall_horseman.horsemans_treats.lore")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.critfall_horseman.horsemans_treats.use")
                .withStyle(ChatFormatting.GRAY));
        Charges.tooltip(stack, lines);
    }
}
