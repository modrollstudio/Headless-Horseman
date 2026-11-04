package studio.modroll.horseman.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.time.MonthDay;
import org.junit.jupiter.api.Test;
import studio.modroll.horseman.config.HorsemanConfig;
import studio.modroll.horseman.config.HorsemanConfigLoader;

class HorsemanCalendarTest {

    private static final HorsemanConfig DEFAULTS = HorsemanConfig.DEFAULTS;

    private static HorsemanConfig config(String json) {
        return HorsemanConfigLoader.parse(JsonParser.parseString(json).getAsJsonObject(), warning -> {});
    }

    @Test
    void halloweenBringsHimAtEveryMidnight() {
        LocalDate halloween = LocalDate.of(2026, 10, 31);
        assertEquals(HorsemanCalendar.Night.HALLOWEEN, HorsemanCalendar.night(halloween, DEFAULTS));
        assertTrue(HorsemanCalendar.comes(halloween, DEFAULTS, false, 0.99));
        assertTrue(HorsemanCalendar.comes(halloween, DEFAULTS, true, 0.99));
    }

    @Test
    void withEveryMidnightOffHalloweenBringsHimOnce() {
        LocalDate halloween = LocalDate.of(2026, 10, 31);
        HorsemanConfig once = config("{\"every_halloween_midnight\": false}");
        assertTrue(HorsemanCalendar.comes(halloween, once, false, 0.99));
        assertFalse(HorsemanCalendar.comes(halloween, once, true, 0.0));
    }

    @Test
    void otherOctoberNightsAreAChance() {
        LocalDate october = LocalDate.of(2026, 10, 12);
        assertEquals(HorsemanCalendar.Night.SEASON, HorsemanCalendar.night(october, DEFAULTS));
        assertTrue(HorsemanCalendar.comes(october, DEFAULTS, false, 0.09));
        assertFalse(HorsemanCalendar.comes(october, DEFAULTS, false, 0.1));
    }

    @Test
    void outOfSeasonHeNeverComesUnlessAllYear() {
        LocalDate march = LocalDate.of(2026, 3, 3);
        assertFalse(HorsemanCalendar.comes(march, DEFAULTS, false, 0.0));
        HorsemanConfig allYear = config("{\"all_year\": true}");
        assertTrue(HorsemanCalendar.comes(march, allYear, false, 0.05));
    }

    @Test
    void aSeasonMayRunOverNewYear() {
        assertTrue(HorsemanCalendar.within(MonthDay.of(1, 2), MonthDay.of(12, 20), MonthDay.of(1, 5)));
        assertTrue(HorsemanCalendar.within(MonthDay.of(12, 25), MonthDay.of(12, 20), MonthDay.of(1, 5)));
        assertFalse(HorsemanCalendar.within(MonthDay.of(6, 1), MonthDay.of(12, 20), MonthDay.of(1, 5)));
    }
}
