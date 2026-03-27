package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists village mode state: enabled flag, plot states,
 * player-house bindings, and house/door positions.
 */
public class VillageData extends SavedData {

    private static final String DATA_NAME = BeginnersDelight.MOD_ID + "_village";

    private static final Codec<PlotEntry> PLOT_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    GridPos.CODEC.fieldOf("pos").forGetter(PlotEntry::pos),
                    Codec.STRING.fieldOf("state").forGetter(e -> e.state().name())
            ).apply(instance, (pos, state) -> new PlotEntry(pos, PlotState.valueOf(state)))
    );

    private static final Codec<PlayerHouseEntry> PLAYER_HOUSE_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerHouseEntry::uuid),
                    GridPos.CODEC.fieldOf("grid_pos").forGetter(PlayerHouseEntry::gridPos)
            ).apply(instance, PlayerHouseEntry::new)
    );

    private static final Codec<GridBlockPosEntry> GRID_BLOCK_POS_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    GridPos.CODEC.fieldOf("grid_pos").forGetter(GridBlockPosEntry::gridPos),
                    BlockPos.CODEC.fieldOf("block_pos").forGetter(GridBlockPosEntry::blockPos)
            ).apply(instance, GridBlockPosEntry::new)
    );

    public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("enabled").forGetter(d -> d.enabled),
                    BlockPos.CODEC.optionalFieldOf("center_pos").forGetter(d -> Optional.ofNullable(d.centerPos)),
                    PLOT_ENTRY_CODEC.listOf().fieldOf("plots").forGetter(d ->
                            d.plots.entrySet().stream()
                                    .map(e -> new PlotEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    PLAYER_HOUSE_ENTRY_CODEC.listOf().fieldOf("player_houses").forGetter(d ->
                            d.playerHouses.entrySet().stream()
                                    .map(e -> new PlayerHouseEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    GRID_BLOCK_POS_ENTRY_CODEC.listOf().fieldOf("house_positions").forGetter(d ->
                            d.housePositions.entrySet().stream()
                                    .map(e -> new GridBlockPosEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    GRID_BLOCK_POS_ENTRY_CODEC.listOf().fieldOf("door_positions").forGetter(d ->
                            d.doorPositions.entrySet().stream()
                                    .map(e -> new GridBlockPosEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    Codec.INT.fieldOf("house_count_since_last_decoration").forGetter(d -> d.houseCountSinceLastDecoration),
                    Codec.INT.fieldOf("decoration_count").forGetter(d -> d.decorationCount)
            ).apply(instance, (enabled, centerPos, plots, playerHouses, housePositions, doorPositions, houseCountSinceLastDecoration, decorationCount) -> {
                VillageData data = new VillageData();
                data.enabled = enabled;
                data.centerPos = centerPos.orElse(null);
                plots.forEach(e -> data.plots.put(e.pos(), e.state()));
                playerHouses.forEach(e -> data.playerHouses.put(e.uuid(), e.gridPos()));
                housePositions.forEach(e -> data.housePositions.put(e.gridPos(), e.blockPos()));
                doorPositions.forEach(e -> data.doorPositions.put(e.gridPos(), e.blockPos()));
                data.houseCountSinceLastDecoration = houseCountSinceLastDecoration;
                data.decorationCount = decorationCount;
                return data;
            })
    );

    public static final SavedDataType<VillageData> TYPE = new SavedDataType<>(
            DATA_NAME,
            VillageData::new,
            CODEC,
            null
    );

    private boolean enabled;
    private BlockPos centerPos;
    private final Map<GridPos, PlotState> plots = new HashMap<>();
    private final Map<UUID, GridPos> playerHouses = new HashMap<>();
    private final Map<GridPos, BlockPos> housePositions = new HashMap<>();
    private final Map<GridPos, BlockPos> doorPositions = new HashMap<>();
    private int houseCountSinceLastDecoration;
    private int decorationCount;

    public VillageData() {
        this.enabled = false;
        this.centerPos = null;
        this.houseCountSinceLastDecoration = 0;
        this.decorationCount = 0;
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
        return Map.copyOf(housePositions);
    }

    public Map<GridPos, BlockPos> getAllDoorPositions() {
        return Map.copyOf(doorPositions);
    }

    public int getHouseCount() {
        return (int) plots.values().stream().filter(s -> s == PlotState.OCCUPIED).count();
    }

    public int getPlayerCount() {
        return playerHouses.size();
    }

    public int getHouseCountSinceLastDecoration() { return houseCountSinceLastDecoration; }

    public void setHouseCountSinceLastDecoration(int count) {
        this.houseCountSinceLastDecoration = count;
        setDirty();
    }

    public void incrementHouseCountSinceLastDecoration() {
        this.houseCountSinceLastDecoration++;
        setDirty();
    }

    public int getDecorationCount() { return decorationCount; }

    public void incrementDecorationCount() {
        this.decorationCount++;
        setDirty();
    }

    public static VillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private record PlotEntry(GridPos pos, PlotState state) {}
    private record PlayerHouseEntry(UUID uuid, GridPos gridPos) {}
    private record GridBlockPosEntry(GridPos gridPos, BlockPos blockPos) {}
}
