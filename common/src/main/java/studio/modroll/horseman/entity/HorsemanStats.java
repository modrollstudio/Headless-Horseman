package studio.modroll.horseman.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.horseman.CritfallHorseman;

/**
 * Fight numbers that aren't part of a Critfall profile, read from
 * {@code data/critfall_horseman/horseman_stats/<entity>.json}. AC, attack bonus, saves, melee dice and crit range are in
 * the Critfall entity profiles instead. Missing or invalid values fall back to the defaults below, with a warning.
 *
 * @param bladeFire dice added to his sword hits while he has his head
 * @param chargeDamage half on a successful save
 * @param headThrowDamage line of fire damage, half on a successful save
 */
public record HorsemanStats(
        double maxHealth,
        DiceExpression bladeFire,
        int chargeDc,
        DiceExpression chargeDamage,
        int headThrowDc,
        DiceExpression headThrowDamage) {

    public static final HorsemanStats HORSEMAN = new HorsemanStats(
            150.0, DiceExpression.parse("1d4"), 13, DiceExpression.parse("1d4+2"), 14, DiceExpression.parse("2d4+1"));

    /** Only the health is used for the steed. */
    public static final HorsemanStats STEED = new HorsemanStats(
            80.0,
            HORSEMAN.bladeFire,
            HORSEMAN.chargeDc,
            HORSEMAN.chargeDamage,
            HORSEMAN.headThrowDc,
            HORSEMAN.headThrowDamage);

    private static ResourceManager cachedFrom;
    private static final Map<String, HorsemanStats> CACHE = new HashMap<>();

    /** Stats for {@code horseman} or {@code gallows_steed}, cached until the next data reload. */
    public static synchronized HorsemanStats of(ServerLevel level, String name) {
        ResourceManager resources = level.getServer().getResourceManager();
        if (resources != cachedFrom) {
            cachedFrom = resources;
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(name, n -> read(resources, n));
    }

    private static HorsemanStats read(ResourceManager resources, String name) {
        HorsemanStats d = name.equals("gallows_steed") ? STEED : HORSEMAN;
        ResourceLocation file = CritfallHorseman.id("horseman_stats/" + name + ".json");
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            return d;
        }
        JsonObject json;
        try (Reader reader = resource.get().openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            CritfallHorseman.LOG.warn("Could not read {}, using the built-in numbers: {}", file, e.getMessage());
            return d;
        }
        JsonObject charge =
                json.has("charge") && json.get("charge").isJsonObject() ? json.getAsJsonObject("charge") : null;
        JsonObject thrown = json.has("head_throw") && json.get("head_throw").isJsonObject()
                ? json.getAsJsonObject("head_throw")
                : null;
        return new HorsemanStats(
                value(file, json, "max_health", d.maxHealth, JsonElement::getAsDouble, h -> h >= 1.0),
                value(file, json, "blade_fire", d.bladeFire, e -> DiceExpression.parse(e.getAsString()), x -> true),
                value(file, charge, "dc", d.chargeDc, JsonElement::getAsInt, dc -> dc >= 1),
                value(file, charge, "damage", d.chargeDamage, e -> DiceExpression.parse(e.getAsString()), x -> true),
                value(file, thrown, "dc", d.headThrowDc, JsonElement::getAsInt, dc -> dc >= 1),
                value(
                        file,
                        thrown,
                        "damage",
                        d.headThrowDamage,
                        e -> DiceExpression.parse(e.getAsString()),
                        x -> true));
    }

    private static <T> T value(
            ResourceLocation file,
            JsonObject json,
            String key,
            T fallback,
            Function<JsonElement, T> parse,
            Predicate<T> valid) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        try {
            T value = parse.apply(json.get(key));
            if (valid.test(value)) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // Reported below.
        }
        CritfallHorseman.LOG.warn("{}: bad \"{}\", using {}", file, key, fallback);
        return fallback;
    }

    /** Sets max health from the stats file and heals to full. */
    public static void applyHealth(LivingEntity entity, ServerLevel level, String name) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(of(level, name).maxHealth());
        }
        entity.setHealth(entity.getMaxHealth());
    }
}
