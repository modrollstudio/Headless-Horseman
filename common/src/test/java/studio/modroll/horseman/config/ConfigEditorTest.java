package studio.modroll.horseman.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.modroll.horseman.config.ConfigSettings.Setting;

class ConfigEditorTest {

    private static final Path LANG = Path.of("src/main/resources/assets/critfall_horseman/lang/en_us.json");

    @TempDir
    Path dir;

    @Test
    void untouchedEditorsWriteTheDefaultFiles() {
        assertEquals(
                HorsemanConfigLoader.DEFAULT_FILE,
                ConfigEditor.server(dir.resolve("missing.json")).write());
        assertEquals(
                HorsemanConfigLoader.DEFAULT_CLIENT_FILE,
                ConfigEditor.client(dir.resolve("missing.json")).write());
    }

    @Test
    void changesSurviveSaveAndReload() throws Exception {
        Path file = dir.resolve("critfall_horseman/critfall_horseman.json");
        HorsemanConfigLoader.load(file);

        ConfigEditor editor = ConfigEditor.server(file);
        assertTrue(editor.set(setting("natural_spawning"), "false"));
        assertTrue(editor.set(setting("halloween_night"), "11-01"));
        assertTrue(editor.set(setting("season_start"), " 09-15 "));
        assertTrue(editor.set(setting("season_chance"), "0.25"));
        assertTrue(editor.set(setting("sweep"), "false"));
        Map<String, String> shown = values(editor);
        editor.save(file);

        HorsemanConfig config = HorsemanConfigLoader.load(file);
        assertFalse(config.naturalSpawning());
        assertEquals(MonthDay.of(11, 1), config.halloweenNight());
        assertEquals(MonthDay.of(9, 15), config.seasonStart());
        assertEquals(0.25, config.seasonChance());
        assertFalse(config.sweep());
        assertTrue(config.charge(), "keys left alone keep their values");

        ConfigEditor reopened = ConfigEditor.server(file);
        assertEquals(shown, values(reopened), "the screen shows what it saved");
        assertEquals("09-15", reopened.value(setting("season_start")));
        assertFalse(Files.exists(file.resolveSibling("critfall_horseman.json.tmp")));
        assertEquals(List.of(), warnings(Files.readString(file)));
    }

    @Test
    void clientChangesSurviveSaveAndReload() throws Exception {
        Path file = dir.resolve("critfall_horseman/critfall_horseman-client.json");
        ConfigEditor editor = ConfigEditor.client(file);
        assertTrue(editor.set(ConfigSettings.CLIENT.get(0), "false"));
        editor.save(file);
        assertEquals(new HorsemanClientConfig(false, true, true), HorsemanConfigLoader.loadClient(file));
        assertEquals("false", ConfigEditor.client(file).value(ConfigSettings.CLIENT.get(0)));
    }

    @Test
    void rejectedInputLeavesTheFileAlone() {
        ConfigEditor editor = ConfigEditor.server(dir.resolve("missing.json"));
        String before = editor.write();
        for (String date : List.of("13-01", "10-32", "02-30", "Oct 31", "1031", "")) {
            assertFalse(editor.set(setting("halloween_night"), date), date);
        }
        for (String chance : List.of("-0.1", "1.5", "2", "lots", "NaN", "Infinity", "")) {
            assertFalse(editor.set(setting("season_chance"), chance), chance);
        }
        assertFalse(editor.set(setting("charge"), "yes"));
        assertEquals(before, editor.write());
    }

    @Test
    void rangeEndsAreAccepted() {
        ConfigEditor editor = ConfigEditor.server(dir.resolve("missing.json"));
        assertTrue(editor.set(setting("season_chance"), "0"));
        assertTrue(editor.set(setting("season_chance"), "1"));
        assertTrue(editor.set(setting("season_end"), "02-29"));
        assertTrue(editor.set(setting("season_start"), "12-01"), "a season may run over New Year");
    }

    @Test
    void invalidValuesInTheFileShowTheDefaultsTheLoaderUses() throws Exception {
        Path file = dir.resolve("critfall_horseman.json");
        Files.writeString(file, """
                { "season_chance": 2, "halloween_night": "13-40", "charge": "yes", "lines": false,
                  "fog": false, "my_note": "x" }
                """);
        ConfigEditor editor = ConfigEditor.server(file);
        assertFalse(editor.unreadable());
        assertEquals("0.1", editor.value(setting("season_chance")));
        assertEquals("10-31", editor.value(setting("halloween_night")));
        assertEquals("true", editor.value(setting("charge")));
        assertEquals("false", editor.value(setting("lines")));

        JsonObject saved = JsonParser.parseString(editor.write()).getAsJsonObject();
        assertFalse(saved.has("fog"), "keys the file no longer uses are dropped");
        assertFalse(saved.has("my_note"));
        assertEquals(List.of(), warnings(editor.write()));
    }

    @Test
    void anUnreadableFileShowsTheDefaultsAndSaveReplacesIt() throws Exception {
        Path file = dir.resolve("critfall_horseman.json");
        Files.writeString(file, "{ \"enabled\": false,");
        ConfigEditor editor = ConfigEditor.server(file);
        assertTrue(editor.unreadable());
        assertEquals("true", editor.value(setting("enabled")));
        editor.save(file);
        assertEquals(HorsemanConfigLoader.DEFAULT_FILE, Files.readString(file));
    }

    @Test
    void theScreenCoversEveryKeyAndEveryLabelIsInTheLangFile() throws Exception {
        JsonObject serverKeys =
                JsonParser.parseString(HorsemanConfigLoader.DEFAULT_FILE).getAsJsonObject();
        JsonObject clientKeys =
                JsonParser.parseString(HorsemanConfigLoader.DEFAULT_CLIENT_FILE).getAsJsonObject();
        serverKeys.remove("format_version");
        clientKeys.remove("format_version");
        assertEquals(serverKeys.keySet(), keys(ConfigSettings.SERVER));
        assertEquals(clientKeys.keySet(), keys(ConfigSettings.CLIENT));

        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        Stream.concat(ConfigSettings.SERVER.stream(), ConfigSettings.CLIENT.stream())
                .flatMap(setting -> Stream.of(
                        "key." + setting.key(), "key." + setting.key() + ".tooltip", "section." + setting.section()))
                .forEach(key -> assertTrue(lang.has("critfall_horseman.config." + key), key));
    }

    private static Setting setting(String key) {
        return ConfigSettings.SERVER.stream()
                .filter(setting -> setting.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, String> values(ConfigEditor editor) {
        return editor.settings().stream().collect(Collectors.toMap(Setting::key, editor::value));
    }

    private static Set<String> keys(List<Setting> settings) {
        return settings.stream().map(Setting::key).collect(Collectors.toSet());
    }

    private static List<String> warnings(String text) {
        List<String> warnings = new ArrayList<>();
        HorsemanConfigLoader.parse(JsonParser.parseString(text).getAsJsonObject(), warnings::add);
        return warnings;
    }
}
