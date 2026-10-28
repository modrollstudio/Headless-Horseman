package studio.modroll.horseman.spawn;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-world spawn state: the last midnight that was checked (so a frozen daylight cycle doesn't roll every tick) and
 * the last year he came on Halloween.
 */
public final class HorsemanSavedData extends SavedData {

    private static final String NAME = "critfall_horseman";

    private long lastMidnight = Long.MIN_VALUE;
    private int lastHalloweenYear = Integer.MIN_VALUE;

    public static HorsemanSavedData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(HorsemanSavedData::new, HorsemanSavedData::load, null), NAME);
    }

    private static HorsemanSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HorsemanSavedData data = new HorsemanSavedData();
        data.lastMidnight = tag.getLong("LastMidnight");
        data.lastHalloweenYear = tag.getInt("LastHalloweenYear");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("LastMidnight", lastMidnight);
        tag.putInt("LastHalloweenYear", lastHalloweenYear);
        return tag;
    }

    public long lastMidnight() {
        return lastMidnight;
    }

    public void setLastMidnight(long day) {
        lastMidnight = day;
        setDirty();
    }

    public int lastHalloweenYear() {
        return lastHalloweenYear;
    }

    public void setLastHalloweenYear(int year) {
        lastHalloweenYear = year;
        setDirty();
    }
}
