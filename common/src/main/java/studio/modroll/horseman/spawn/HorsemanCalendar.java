package studio.modroll.horseman.spawn;

import java.time.LocalDate;
import java.time.MonthDay;
import studio.modroll.horseman.config.HorsemanConfig;

/**
 * Date rules, using the server's real date: once on Halloween night for certain, by chance at each midnight during the
 * rest of the season, and never otherwise unless {@code all_year} is on.
 */
public final class HorsemanCalendar {

    public enum Night {
        HALLOWEEN,
        SEASON,
        NONE
    }

    private HorsemanCalendar() {}

    public static Night night(LocalDate date, HorsemanConfig config) {
        MonthDay day = MonthDay.from(date);
        if (day.equals(config.halloweenNight())) {
            return Night.HALLOWEEN;
        }
        if (config.allYear() || within(day, config.seasonStart(), config.seasonEnd())) {
            return Night.SEASON;
        }
        return Night.NONE;
    }

    /** {@code roll} is a random number from 0 to 1, compared against the season chance. */
    public static boolean comes(LocalDate date, HorsemanConfig config, boolean cameThisHalloween, double roll) {
        return switch (night(date, config)) {
            case HALLOWEEN -> !cameThisHalloween;
            case SEASON -> roll < config.seasonChance();
            case NONE -> false;
        };
    }

    /** Inclusive; handles a season that wraps over New Year. */
    static boolean within(MonthDay day, MonthDay start, MonthDay end) {
        if (!start.isAfter(end)) {
            return !day.isBefore(start) && !day.isAfter(end);
        }
        return !day.isBefore(start) || !day.isAfter(end);
    }
}
