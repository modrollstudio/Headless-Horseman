package studio.modroll.horseman.reward;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Reward drops still locked to one player, by item entity UUID, with the game time they open up at. Saved with the
 * level, so a drop locked when the server stopped still opens after a restart. A drop whose chunk is unloaded when its
 * time comes opens once it is loaded again; one not seen for a day after is forgotten.
 */
public final class LockedDrops extends SavedData {

    private static final String NAME = "critfall_horseman_locked_drops";
    private static final long FORGET_AFTER = 24000L;

    private final Map<UUID, Long> unlockAt = new LinkedHashMap<>();

    public static LockedDrops get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(LockedDrops::new, LockedDrops::load, null), NAME);
    }

    private static LockedDrops load(CompoundTag tag, HolderLookup.Provider registries) {
        LockedDrops drops = new LockedDrops();
        for (Tag entry : tag.getList("Drops", Tag.TAG_COMPOUND)) {
            CompoundTag drop = (CompoundTag) entry;
            drops.unlockAt.put(drop.getUUID("Item"), drop.getLong("UnlockAt"));
        }
        return drops;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        unlockAt.forEach((item, time) -> {
            CompoundTag drop = new CompoundTag();
            drop.putUUID("Item", item);
            drop.putLong("UnlockAt", time);
            list.add(drop);
        });
        tag.put("Drops", list);
        return tag;
    }

    public void lock(ItemEntity item, long until) {
        unlockAt.put(item.getUUID(), until);
        setDirty();
    }

    public boolean isLocked(ItemEntity item) {
        return unlockAt.containsKey(item.getUUID());
    }

    /** Opens every drop whose time has come to anyone. */
    public void unlockDue(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = unlockAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> drop = it.next();
            if (drop.getValue() > now) {
                continue;
            }
            if (level.getEntity(drop.getKey()) instanceof ItemEntity item) {
                item.setTarget(null);
                it.remove();
                setDirty();
            } else if (now > drop.getValue() + FORGET_AFTER) {
                it.remove();
                setDirty();
            }
        }
    }
}
