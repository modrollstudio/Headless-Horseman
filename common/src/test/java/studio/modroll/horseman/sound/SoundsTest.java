package studio.modroll.horseman.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import studio.modroll.horseman.fight.Lines;

/** Checks that HorsemanSounds, sounds.json, the generated sound files and the lang file agree. */
class SoundsTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/critfall_horseman");

    private static JsonObject json(String file) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve(file))).getAsJsonObject();
    }

    @Test
    void everyEventPlayedIsDefinedAndEveryDefinedEventIsPlayed() throws IOException {
        Set<String> registered = HorsemanSounds.events().keySet().stream()
                .map(id -> id.getPath())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(json("sounds.json").keySet()), registered);
    }

    @Test
    void everySoundMadeForTheModIsThere() throws IOException {
        for (var event : json("sounds.json").entrySet()) {
            for (JsonElement sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.getAsJsonObject().get("name").getAsString();
                if (name.startsWith("critfall_horseman:")) {
                    Path file = ASSETS.resolve("sounds/" + name.substring("critfall_horseman:".length()) + ".ogg");
                    assertTrue(Files.exists(file), event.getKey() + " names " + file + ", which is missing");
                }
            }
        }
    }

    @Test
    void everyThreatHasItsTitleAndSubtitle() throws IOException {
        JsonObject lang = json("lang/en_us.json");
        for (Lines.Line line : Lines.Line.values()) {
            assertTrue(lang.has(line.titleKey()), line.titleKey());
            assertTrue(lang.has(line.subtitleKey()), line.subtitleKey());
            if (line.once()) {
                assertTrue(
                        lang.get(line.titleKey()).getAsString().length() <= 16,
                        line.titleKey() + " is too long for a title");
            }
        }
    }
}
