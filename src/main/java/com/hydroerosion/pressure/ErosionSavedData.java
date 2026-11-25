package com.hydroerosion.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent world storage tracking hydrostatic stress fatigue (0.0 - 100.0) on blocks.
 */
public class ErosionSavedData extends SavedData {

    private static final String DATA_NAME = "hydroerosion_erosion";
    private final Map<Long, Float> stressDamage = new HashMap<>();

    public static ErosionSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ErosionSavedData::load,
                ErosionSavedData::new,
                DATA_NAME
        );
    }

    public static ErosionSavedData load(CompoundTag tag) {
        ErosionSavedData data = new ErosionSavedData();
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            long pos = entry.getLong("Pos");
            float damage = entry.getFloat("Damage");
            if (damage > 0.0f) {
                data.stressDamage.put(pos, damage);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<Long, Float> entry : stressDamage.entrySet()) {
            CompoundTag nbt = new CompoundTag();
            nbt.putLong("Pos", entry.getKey());
            nbt.putFloat("Damage", entry.getValue());
            entries.add(nbt);
        }
        tag.put("Entries", entries);
        return tag;
    }

    public Map<Long, Float> getEntriesView() {
        return Collections.unmodifiableMap(stressDamage);
    }

    public float getDamage(BlockPos pos) {
        return stressDamage.getOrDefault(pos.asLong(), 0.0f);
    }

    public int getCrackStage(BlockPos pos) {
        float dmg = getDamage(pos);
        if (dmg <= 0.0f) {
            return -1;
        }
        return Mth.clamp((int) (dmg / 10.0f), 0, 9);
    }

    public void setDamage(BlockPos pos, float damage) {
        long key = pos.asLong();
        if (damage <= 0.0f) {
            if (stressDamage.remove(key) != null) {
                setDirty();
            }
        } else {
            stressDamage.put(key, damage);
            setDirty();
        }
    }

    public void remove(long packedPos) {
        if (stressDamage.remove(packedPos) != null) {
            setDirty();
        }
    }
}
