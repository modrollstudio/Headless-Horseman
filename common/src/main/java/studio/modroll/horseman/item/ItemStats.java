package studio.modroll.horseman.item;

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
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.horseman.CritfallHorseman;

/**
 * The numbers of an item's powers, read from {@code data/critfall_horseman/item_stats/<item>.json} and cached until the
 * next data reload. Each getter takes the built-in default, used with a warning when the value is missing or invalid.
 */
public final class ItemStats {

    private static ResourceManager cachedFrom;
    private static final Map<String, ItemStats> CACHE = new HashMap<>();

    private final ResourceLocation file;
    private final JsonObject json;

    private ItemStats(ResourceLocation file, JsonObject json) {
        this.file = file;
        this.json = json;
    }

    public static synchronized ItemStats of(ServerLevel level, String item) {
        ResourceManager resources = level.getServer().getResourceManager();
        if (resources != cachedFrom) {
            cachedFrom = resources;
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(item, name -> read(resources, name));
    }

    private static ItemStats read(ResourceManager resources, String item) {
        ResourceLocation file = CritfallHorseman.id("item_stats/" + item + ".json");
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                return new ItemStats(file, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                CritfallHorseman.LOG.warn("Could not read {}, using the built-in numbers: {}", file, e.getMessage());
            }
        }
        return new ItemStats(file, new JsonObject());
    }

    /** A whole number of at least {@code min}. */
    public int integer(String key, int fallback, int min) {
        return value(key, fallback, JsonElement::getAsInt, v -> v >= min);
    }

    /** A number of at least {@code min}. */
    public double number(String key, double fallback, double min) {
        return value(key, fallback, JsonElement::getAsDouble, v -> v >= min);
    }

    public DiceExpression dice(String key, String fallback) {
        return value(key, DiceExpression.parse(fallback), e -> DiceExpression.parse(e.getAsString()), d -> true);
    }

    private <T> T value(String key, T fallback, Function<JsonElement, T> parse, Predicate<T> valid) {
        if (!json.has(key)) {
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
}
