package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists village mode state: enabled flag, road segments,
 * building plots, player-house bindings, and counters.
 */
public class VillageData extends SavedData {

    private static final String DATA_NAME = BeginnersDelight.MOD_ID + "_village";

    private record PlayerHouseEntry(UUID uuid, int plotId) {}

    private static final Codec<PlayerHouseEntry> PLAYER_HOUSE_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerHouseEntry::uuid),
                    Codec.INT.fieldOf("plot_id").forGetter(PlayerHouseEntry::plotId)
            ).apply(instance, PlayerHouseEntry::new)
    );

    public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("enabled").forGetter(d -> d.enabled),
                    BlockPos.CODEC.optionalFieldOf("center_pos").forGetter(d -> Optional.ofNullable(d.centerPos)),
                    RoadSegment.CODEC.listOf().fieldOf("roads").forGetter(d -> d.roads),
                    VillagePlot.CODEC.listOf().fieldOf("plots").forGetter(d -> d.plots),
                    PLAYER_HOUSE_ENTRY_CODEC.listOf().fieldOf("player_houses").forGetter(d ->
                            d.playerHouses.entrySet().stream()
                                    .map(e -> new PlayerHouseEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    Codec.INT.fieldOf("house_count_since_last_decoration").forGetter(d -> d.houseCountSinceLastDecoration),
                    Codec.INT.fieldOf("decoration_count").forGetter(d -> d.decorationCount),
                    Codec.INT.fieldOf("next_segment_id").forGetter(d -> d.nextSegmentId),
                    Codec.INT.fieldOf("next_plot_id").forGetter(d -> d.nextPlotId)
            ).apply(instance, (enabled, centerPos, roads, plots, playerHouses,
                               houseCountSinceLastDecoration, decorationCount, nextSegmentId, nextPlotId) -> {
                VillageData data = new VillageData();
                data.enabled = enabled;
                data.centerPos = centerPos.orElse(null);
                data.roads.addAll(roads);
                data.plots.addAll(plots);
                playerHouses.forEach(e -> data.playerHouses.put(e.uuid(), e.plotId()));
                data.houseCountSinceLastDecoration = houseCountSinceLastDecoration;
                data.decorationCount = decorationCount;
                data.nextSegmentId = nextSegmentId;
                data.nextPlotId = nextPlotId;
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
    private final List<RoadSegment> roads = new ArrayList<>();
    private final List<VillagePlot> plots = new ArrayList<>();
    private final Map<UUID, Integer> playerHouses = new HashMap<>();
    private int houseCountSinceLastDecoration;
    private int decorationCount;
    private int nextSegmentId;
    private int nextPlotId;

    public VillageData() {
        this.enabled = false;
        this.centerPos = null;
        this.houseCountSinceLastDecoration = 0;
        this.decorationCount = 0;
        this.nextSegmentId = 0;
        this.nextPlotId = 0;
    }

    // --- Enabled ---

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setDirty();
    }

    // --- Center position ---

    public BlockPos getCenterPos() { return centerPos; }

    public void setCenterPos(BlockPos centerPos) {
        this.centerPos = centerPos;
        setDirty();
    }

    // --- Roads ---

    public void addRoad(RoadSegment segment) {
        roads.add(segment);
        setDirty();
    }

    public RoadSegment getRoad(int id) {
        for (RoadSegment segment : roads) {
            if (segment.getId() == id) {
                return segment;
            }
        }
        return null;
    }

    public List<RoadSegment> getAllRoads() {
        return List.copyOf(roads);
    }

    /**
     * Returns segments with no children (tips of the road tree).
     */
    public List<RoadSegment> getTipSegments() {
        return roads.stream().filter(RoadSegment::isTip).collect(Collectors.toList());
    }

    // --- Plots ---

    public void addPlot(VillagePlot plot) {
        plots.add(plot);
        setDirty();
    }

    public VillagePlot getPlot(int id) {
        for (VillagePlot plot : plots) {
            if (plot.getId() == id) {
                return plot;
            }
        }
        return null;
    }

    public List<VillagePlot> getAllPlots() {
        return List.copyOf(plots);
    }

    // --- House / decoration counts ---

    public int getHouseCount() {
        return (int) plots.stream().filter(p -> p.getType() == PlotType.HOUSE).count();
    }

    public int getDecorationCount() { return decorationCount; }

    public int getHouseCountSinceLastDecoration() { return houseCountSinceLastDecoration; }

    public void setHouseCountSinceLastDecoration(int count) {
        this.houseCountSinceLastDecoration = count;
        setDirty();
    }

    public void incrementHouseCountSinceLastDecoration() {
        this.houseCountSinceLastDecoration++;
        setDirty();
    }

    public void incrementDecorationCount() {
        this.decorationCount++;
        setDirty();
    }

    // --- Player houses ---

    public boolean hasHouse(UUID playerUuid) {
        return playerHouses.containsKey(playerUuid);
    }

    public int getPlayerPlotId(UUID playerUuid) {
        return playerHouses.getOrDefault(playerUuid, -1);
    }

    public void setPlayerHouse(UUID playerUuid, int plotId) {
        playerHouses.put(playerUuid, plotId);
        setDirty();
    }

    public int getPlayerCount() {
        return playerHouses.size();
    }

    // --- ID allocation ---

    public int allocateSegmentId() {
        int id = nextSegmentId++;
        setDirty();
        return id;
    }

    public int allocatePlotId() {
        int id = nextPlotId++;
        setDirty();
        return id;
    }

    // --- Persistence ---

    public static VillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
