package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists whether the starter house has already been generated,
 * preventing regeneration on subsequent world loads.
 * Also stores the interior spawn position for teleporting new players.
 */
public class StarterHouseData extends SavedData {

    private static final String DATA_NAME = BeginnersDelight.MOD_ID + "_starter_house";
    private static final String TAG_GENERATED = "generated";
    private static final String TAG_SPAWN_X = "spawn_x";
    private static final String TAG_SPAWN_Y = "spawn_y";
    private static final String TAG_SPAWN_Z = "spawn_z";

    private boolean generated;
    private BlockPos spawnPos;

    public StarterHouseData() {
        this.generated = false;
        this.spawnPos = null;
    }

    public static StarterHouseData load(CompoundTag tag, HolderLookup.Provider registries) {
        StarterHouseData data = new StarterHouseData();
        data.generated = tag.getBoolean(TAG_GENERATED);
        if (tag.contains(TAG_SPAWN_X)) {
            data.spawnPos = new BlockPos(
                    tag.getInt(TAG_SPAWN_X),
                    tag.getInt(TAG_SPAWN_Y),
                    tag.getInt(TAG_SPAWN_Z)
            );
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_GENERATED, generated);
        if (spawnPos != null) {
            tag.putInt(TAG_SPAWN_X, spawnPos.getX());
            tag.putInt(TAG_SPAWN_Y, spawnPos.getY());
            tag.putInt(TAG_SPAWN_Z, spawnPos.getZ());
        }
        return tag;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
        setDirty();
    }

    public BlockPos getSpawnPos() {
        return spawnPos;
    }

    public void setSpawnPos(BlockPos spawnPos) {
        this.spawnPos = spawnPos;
        setDirty();
    }

    public static StarterHouseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(StarterHouseData::new, StarterHouseData::load, null),
                DATA_NAME
        );
    }
}

