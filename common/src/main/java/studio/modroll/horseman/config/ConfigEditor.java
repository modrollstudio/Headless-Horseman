package studio.modroll.horseman.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import studio.modroll.horseman.config.ConfigSettings.Setting;

/**
 * The config screen's model of one config file, edited one {@link Setting} at a time. A value counts as valid when
 * {@link HorsemanConfigLoader} takes it without a warning, so the screen and the loader can't disagree. Each value
 * shown is the one the loader uses: an invalid value in the file shows as its default. Saving writes every key in
 * the default file's order and drops keys the loader doesn't know. No client classes, so it is unit tested directly.
 */
public final class ConfigEditor {

    private final List<Setting> settings;
    private final JsonObject defaults;
    private final BiConsumer<JsonObject, Consumer<String>> loader;
    private final JsonObject root;
    private final boolean unreadable;

    private ConfigEditor(
            List<Setting> settings,
            String defaultFile,
            BiConsumer<JsonObject, Consumer<String>> loader,
            JsonObject root,
            boolean unreadable) {
        this.settings = settings;
        this.defaults = object(defaultFile);
        this.loader = loader;
        this.root = root;
        this.unreadable = unreadable;
    }

    /** {@code critfall_horseman.json}. */
    public static ConfigEditor server(Path file) {
        return read(file, ConfigSettings.SERVER, HorsemanConfigLoader.DEFAULT_FILE, HorsemanConfigLoader::parse);
    }

    /** {@code critfall_horseman-client.json}. */
    public static ConfigEditor client(Path file) {
        return read(
                file,
                ConfigSettings.CLIENT,
                HorsemanConfigLoader.DEFAULT_CLIENT_FILE,
                HorsemanConfigLoader::parseClient);
    }

    /** A missing file reads as the defaults; one that isn't a JSON object reads as the defaults and is unreadable. */
    private static ConfigEditor read(
            Path file, List<Setting> settings, String defaultFile, BiConsumer<JsonObject, Consumer<String>> loader) {
        String text = defaultFile;
        boolean unreadable = false;
        try {
            if (Files.exists(file)) {
                text = Files.readString(file);
                object(text);
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            text = defaultFile;
            unreadable = true;
        }
        return new ConfigEditor(settings, defaultFile, loader, object(text), unreadable);
    }

    public List<Setting> settings() {
        return settings;
    }

    /** The file couldn't be parsed, so the defaults show and saving replaces it. */
    public boolean unreadable() {
        return unreadable;
    }

    /** The value the loader uses, as the screen shows it. */
    public String value(Setting setting) {
        JsonElement value = root.get(setting.key());
        return text(value != null && accepted(setting, value) ? value : defaults.get(setting.key()));
    }

    /** Stores {@code input} if the loader would take it; otherwise leaves the file as it was and returns false. */
    public boolean set(Setting setting, String input) {
        JsonElement value = json(setting, input.trim());
        if (value == null || !accepted(setting, value)) {
            return false;
        }
        root.add(setting.key(), value);
        return true;
    }

    public String write() {
        JsonObject out = new JsonObject();
        for (String key : defaults.keySet()) {
            Setting setting = settings.stream()
                    .filter(s -> s.key().equals(key))
                    .findFirst()
                    .orElse(null);
            out.add(key, setting == null ? defaults.get(key) : json(setting, value(setting)));
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(out) + "\n";
    }

    /** Writes through a temporary file, so the game never reads half a file. */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, write());
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean accepted(Setting setting, JsonElement value) {
        JsonObject probe = new JsonObject();
        probe.add(setting.key(), value);
        List<String> warnings = new ArrayList<>();
        loader.accept(probe, warnings::add);
        return warnings.isEmpty();
    }

    /** The JSON for typed text, or null if it can't be that kind of value at all. */
    private static JsonElement json(Setting setting, String text) {
        return switch (setting.kind()) {
            case TOGGLE -> text.equals("true") || text.equals("false") ? new JsonPrimitive(text.equals("true")) : null;
            case DATE -> new JsonPrimitive(text);
            case CHANCE -> {
                try {
                    yield new JsonPrimitive(new BigDecimal(text));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    private static String text(JsonElement value) {
        return value.getAsJsonPrimitive().isNumber()
                ? new BigDecimal(value.getAsString()).stripTrailingZeros().toPlainString()
                : value.getAsString();
    }

    private static JsonObject object(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
