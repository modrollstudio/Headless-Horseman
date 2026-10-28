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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HorsemanConfigLoaderTest {

    @TempDir
    Path dir;

    @Test
    void defaultFileParsesToTheDefaultsWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        assertEquals(
                HorsemanConfig.DEFAULTS,
                HorsemanConfigLoader.parse(json(HorsemanConfigLoader.DEFAULT_FILE), warnings::add));
        assertEquals(List.of(), warnings);
    }

    @Test
    void missingKeysKeepTheirDefaults() {
        assertEquals(HorsemanConfig.DEFAULTS, HorsemanConfigLoader.parse(json("{}"), warning -> {}));
    }

    @Test
    void masterSwitchTurnsNaturalSpawningOff() {
        HorsemanConfig config = HorsemanConfigLoader.parse(json("{\"enabled\": false}"), warning -> {});
        assertFalse(config.enabled());
        assertFalse(config.naturalSpawningOn());
    }

    @Test
    void readsDatesAndTheAllYearSwitch() {
        HorsemanConfig config = HorsemanConfigLoader.parse(
                json("{\"halloween_night\": \"11-01\", \"season_start\": \"09-15\", \"all_year\": true}"),
                warning -> {});
        assertEquals(MonthDay.of(11, 1), config.halloweenNight());
        assertEquals(MonthDay.of(9, 15), config.seasonStart());
        assertTrue(config.allYear());
    }

    @Test
    void badValuesFallBackWithAWarning() {
        List<String> warnings = new ArrayList<>();
        HorsemanConfig config = HorsemanConfigLoader.parse(
                json("{\"season_chance\": 2, \"halloween_night\": \"13-40\", \"charge\": \"yes\", \"extra\": 1}"),
                warnings::add);
        assertEquals(HorsemanConfig.DEFAULTS, config);
        assertEquals(4, warnings.size());
    }

    @Test
    void writesTheDefaultsWhenTheFileIsMissing() throws Exception {
        Path file = dir.resolve("critfall_horseman").resolve("critfall_horseman.json");
        assertEquals(HorsemanConfig.DEFAULTS, HorsemanConfigLoader.load(file));
        assertEquals(HorsemanConfigLoader.DEFAULT_FILE, Files.readString(file));
    }

    @Test
    void fogAndCameraShakeAreReadFromTheClientFile() throws Exception {
        List<String> warnings = new ArrayList<>();
        HorsemanConfigLoader.parse(json("{\"fog\": false, \"camera_shake\": false}"), warnings::add);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("critfall_horseman-client.json"), warnings.get(0));

        Path file = dir.resolve("critfall_horseman").resolve("critfall_horseman-client.json");
        assertEquals(HorsemanClientConfig.DEFAULTS, HorsemanConfigLoader.loadClient(file));
        assertEquals(HorsemanConfigLoader.DEFAULT_CLIENT_FILE, Files.readString(file));
        Files.writeString(file, "{\"camera_shake\": false}");
        assertEquals(new HorsemanClientConfig(true, false, true), HorsemanConfigLoader.loadClient(file));
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
