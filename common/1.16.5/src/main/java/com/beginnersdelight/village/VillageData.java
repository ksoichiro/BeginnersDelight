package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists village mode state: enabled flag, plot states,
 * player-house bindings, and house/door positions.
 */
public class VillageData extends SavedData {

    private static final String DATA_NAME = BeginnersDelight.MOD_ID + "_village";

    private boolean enabled;
    private BlockPos centerPos;
    private final Map<GridPos, PlotState> plots = new HashMap<>();
    private final Map<UUID, GridPos> playerHouses = new HashMap<>();
    private final Map<GridPos, BlockPos> housePositions = new HashMap<>();
    private final Map<GridPos, BlockPos> doorPositions = new HashMap<>();

    public VillageData() {
        super(DATA_NAME);
        this.enabled = false;
        this.centerPos = null;
    }

    // In 1.16.5, load() is an instance method that must be overridden
    @Override
    public void load(CompoundTag tag) {
        enabled = tag.getBoolean("enabled");

        if (tag.contains("center_x")) {
            centerPos = new BlockPos(
                    tag.getInt("center_x"),
                    tag.getInt("center_y"),
                    tag.getInt("center_z")
            );
        }

        if (tag.contains("plots")) {
            // TAG_COMPOUND = 10
            ListTag plotList = tag.getList("plots", 10);
            for (int i = 0; i < plotList.size(); i++) {
                CompoundTag entry = plotList.getCompound(i);
                GridPos pos = new GridPos(entry.getInt("x"), entry.getInt("z"));
                PlotState state = PlotState.valueOf(entry.getString("state"));
                plots.put(pos, state);
            }
        }

        if (tag.contains("player_houses")) {
            ListTag houseList = tag.getList("player_houses", 10);
            for (int i = 0; i < houseList.size(); i++) {
                CompoundTag entry = houseList.getCompound(i);
                UUID uuid = new UUID(entry.getLong("uuid_most"), entry.getLong("uuid_least"));
                GridPos gridPos = new GridPos(entry.getInt("grid_x"), entry.getInt("grid_z"));
                playerHouses.put(uuid, gridPos);
            }
        }

        if (tag.contains("house_positions")) {
            ListTag posList = tag.getList("house_positions", 10);
            for (int i = 0; i < posList.size(); i++) {
                CompoundTag entry = posList.getCompound(i);
                GridPos gridPos = new GridPos(entry.getInt("grid_x"), entry.getInt("grid_z"));
                BlockPos blockPos = new BlockPos(entry.getInt("block_x"), entry.getInt("block_y"), entry.getInt("block_z"));
                housePositions.put(gridPos, blockPos);
            }
        }

        if (tag.contains("door_positions")) {
            ListTag posList = tag.getList("door_positions", 10);
            for (int i = 0; i < posList.size(); i++) {
                CompoundTag entry = posList.getCompound(i);
                GridPos gridPos = new GridPos(entry.getInt("grid_x"), entry.getInt("grid_z"));
                BlockPos blockPos = new BlockPos(entry.getInt("block_x"), entry.getInt("block_y"), entry.getInt("block_z"));
                doorPositions.put(gridPos, blockPos);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", enabled);

        if (centerPos != null) {
            tag.putInt("center_x", centerPos.getX());
            tag.putInt("center_y", centerPos.getY());
            tag.putInt("center_z", centerPos.getZ());
        }

        ListTag plotList = new ListTag();
        for (Map.Entry<GridPos, PlotState> entry : plots.entrySet()) {
            CompoundTag plotTag = new CompoundTag();
            plotTag.putInt("x", entry.getKey().x());
            plotTag.putInt("z", entry.getKey().z());
            plotTag.putString("state", entry.getValue().name());
            plotList.add(plotTag);
        }
        tag.put("plots", plotList);

        ListTag houseList = new ListTag();
        for (Map.Entry<UUID, GridPos> entry : playerHouses.entrySet()) {
            CompoundTag houseTag = new CompoundTag();
            houseTag.putLong("uuid_most", entry.getKey().getMostSignificantBits());
            houseTag.putLong("uuid_least", entry.getKey().getLeastSignificantBits());
            houseTag.putInt("grid_x", entry.getValue().x());
            houseTag.putInt("grid_z", entry.getValue().z());
            houseList.add(houseTag);
        }
        tag.put("player_houses", houseList);

        ListTag housePosListTag = new ListTag();
        for (Map.Entry<GridPos, BlockPos> entry : housePositions.entrySet()) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("grid_x", entry.getKey().x());
            posTag.putInt("grid_z", entry.getKey().z());
            posTag.putInt("block_x", entry.getValue().getX());
            posTag.putInt("block_y", entry.getValue().getY());
            posTag.putInt("block_z", entry.getValue().getZ());
            housePosListTag.add(posTag);
        }
        tag.put("house_positions", housePosListTag);

        ListTag doorPosListTag = new ListTag();
        for (Map.Entry<GridPos, BlockPos> entry : doorPositions.entrySet()) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("grid_x", entry.getKey().x());
            posTag.putInt("grid_z", entry.getKey().z());
            posTag.putInt("block_x", entry.getValue().getX());
            posTag.putInt("block_y", entry.getValue().getY());
            posTag.putInt("block_z", entry.getValue().getZ());
            doorPosListTag.add(posTag);
        }
        tag.put("door_positions", doorPosListTag);

        return tag;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setDirty();
    }

    public BlockPos getCenterPos() { return centerPos; }

    public void setCenterPos(BlockPos centerPos) {
        this.centerPos = centerPos;
        setDirty();
    }

    public PlotState getPlotState(GridPos pos) {
        return plots.getOrDefault(pos, PlotState.AVAILABLE);
    }

    public void setPlotState(GridPos pos, PlotState state) {
        plots.put(pos, state);
        setDirty();
    }

    public boolean hasHouse(UUID playerUuid) {
        return playerHouses.containsKey(playerUuid);
    }

    public GridPos getPlayerHouse(UUID playerUuid) {
        return playerHouses.get(playerUuid);
    }

    public void setPlayerHouse(UUID playerUuid, GridPos gridPos) {
        playerHouses.put(playerUuid, gridPos);
        setDirty();
    }

    public BlockPos getHousePosition(GridPos gridPos) {
        return housePositions.get(gridPos);
    }

    public void setHousePosition(GridPos gridPos, BlockPos worldPos) {
        housePositions.put(gridPos, worldPos);
        setDirty();
    }

    public BlockPos getDoorPosition(GridPos gridPos) {
        return doorPositions.get(gridPos);
    }

    public void setDoorPosition(GridPos gridPos, BlockPos doorPos) {
        doorPositions.put(gridPos, doorPos);
        setDirty();
    }

    public Map<GridPos, BlockPos> getAllHousePositions() {
        return Collections.unmodifiableMap(new HashMap<>(housePositions));
    }

    public Map<GridPos, BlockPos> getAllDoorPositions() {
        return Collections.unmodifiableMap(new HashMap<>(doorPositions));
    }

    public int getHouseCount() {
        int count = 0;
        for (PlotState state : plots.values()) {
            if (state == PlotState.OCCUPIED) {
                count++;
            }
        }
        return count;
    }

    public int getPlayerCount() {
        return playerHouses.size();
    }

    // In 1.16.5, computeIfAbsent takes (Supplier<T>, String)
    public static VillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                VillageData::new, DATA_NAME
        );
    }
}
