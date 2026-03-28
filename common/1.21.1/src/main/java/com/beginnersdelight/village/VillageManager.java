package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.worldgen.StarterHouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Orchestrates village mode operations.
 * Called from platform-specific event listeners.
 */
public class VillageManager {

    private static final VillageConfig config = new VillageConfig();

    /**
     * Initializes the village system on server start.
     * Loads config and initializes the grid center if village mode is enabled
     * but no center has been set yet.
     */
    public static void onServerStarted(MinecraftServer server) {
        Path configDir = server.getServerDirectory().resolve("config");
        config.load(configDir);

        ServerLevel overworld = server.overworld();
        VillageData data = VillageData.get(overworld);

        if (data.isEnabled() && data.getCenterPos() == null) {
            initializeGrid(overworld, data);
        }
    }

    /**
     * Handles a player joining the server.
     * If village mode is enabled and the player has no house, assigns one.
     * If the player already has a house, teleports them to it.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;

        if (data.hasHouse(player.getUUID())) return;
        StarterHouseData starterData = StarterHouseData.get(overworld);
        if (starterData.hasBeenTeleported(player.getUUID()) && starterData.getSpawnPos() != null) {
            registerStarterHouseAsVillageHouse(overworld, player, data, starterData.getSpawnPos());
            return;
        }
        assignHouse(overworld, player, data);
    }

    /**
     * Handles player respawn after death.
     * If respawnAtHouse is enabled and the player has no bed, teleport to their house.
     */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (player.getRespawnPosition() != null) return;
        if (!config.isRespawnAtHouse()) return;

        ServerLevel overworld = player.server.overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;
        if (!data.hasHouse(player.getUUID())) return;

        GridPos gridPos = data.getPlayerHouse(player.getUUID());
        BlockPos housePos = data.getHousePosition(gridPos);
        if (housePos != null) {
            player.teleportTo(overworld,
                    housePos.getX() + 0.5, housePos.getY(), housePos.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            BeginnersDelight.LOGGER.debug("Respawned player {} at village house",
                    player.getName().getString());
        }
    }

    public static VillageConfig getConfig() {
        return config;
    }

    private static void registerStarterHouseAsVillageHouse(ServerLevel overworld, ServerPlayer player, VillageData data, BlockPos starterHousePos) {
        if (data.getCenterPos() == null) initializeGrid(overworld, data);
        GridPos centerGrid = new GridPos(0, 0);
        data.setPlotState(centerGrid, PlotState.OCCUPIED); data.setPlayerHouse(player.getUUID(), centerGrid);
        data.setHousePosition(centerGrid, starterHousePos); data.setDoorPosition(centerGrid, starterHousePos);
        data.incrementHouseCountSinceLastDecoration();
        BeginnersDelight.LOGGER.info("Registered starter house as village house for player {}", player.getName().getString());
    }

    private static void initializeGrid(ServerLevel overworld, VillageData data) {
        BlockPos spawnPos = overworld.getSharedSpawnPos();
        VillageGrid grid = new VillageGrid(data, config);
        grid.initialize(spawnPos);
        BeginnersDelight.LOGGER.info("Village grid initialized at center: {}", spawnPos);
    }

    /**
     * Forces a new house assignment for the player, ignoring existing binding.
     * Used by the test command to simulate multiple players joining.
     */
    public static void forceAssignHouse(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        VillageData data = VillageData.get(overworld);
        assignHouse(overworld, player, data);
    }

    private static void assignHouse(ServerLevel overworld, ServerPlayer player, VillageData data) {
        if (data.getCenterPos() == null) {
            initializeGrid(overworld, data);
        }

        VillageGrid grid = new VillageGrid(data, config);

        // Find next available plot, checking suitability
        Optional<GridPos> plotOpt = Optional.empty();
        int attempts = 0;
        int maxAttempts = 200;
        while (attempts < maxAttempts) {
            Optional<GridPos> candidate = grid.findNextAvailablePlot();
            if (candidate.isEmpty()) {
                BeginnersDelight.LOGGER.warn("No available plots for village house");
                return;
            }
            GridPos candidatePos = candidate.get();
            BlockPos worldPos = grid.gridToWorld(candidatePos);

            if (VillageHouseGenerator.isSuitable(overworld, worldPos, config.getMaxHeightDifference())) {
                plotOpt = candidate;
                break;
            } else {
                data.setPlotState(candidatePos, PlotState.UNSUITABLE);
                attempts++;
            }
        }

        if (plotOpt.isEmpty()) {
            BeginnersDelight.LOGGER.warn("No suitable plots found after {} attempts for player {}",
                    maxAttempts, player.getName().getString());
            return;
        }

        GridPos gridPos = plotOpt.get();
        BlockPos plotWorldPos = grid.gridToWorld(gridPos);

        // Place the house
        Optional<VillageHouseGenerator.PlacementResult> result =
                VillageHouseGenerator.place(overworld, plotWorldPos);
        if (result.isEmpty()) {
            data.setPlotState(gridPos, PlotState.UNSUITABLE);
            BeginnersDelight.LOGGER.warn("Failed to place village house for player {}",
                    player.getName().getString());
            return;
        }

        VillageHouseGenerator.PlacementResult placement = result.get();

        // Record in data
        data.setPlotState(gridPos, PlotState.OCCUPIED);
        data.setPlayerHouse(player.getUUID(), gridPos);
        data.setHousePosition(gridPos, placement.interiorPos());
        data.setDoorPosition(gridPos, placement.doorFrontPos());

        // Generate path to nearest existing house
        if (config.isGeneratePaths()) {
            Optional<GridPos> nearestOpt = grid.findNearestOccupiedPlot(gridPos);
            if (nearestOpt.isPresent()) {
                BlockPos nearestDoor = data.getDoorPosition(nearestOpt.get());
                if (nearestDoor != null) {
                    VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), nearestDoor);
                }
            } else {
                // First house — connect to village center
                BlockPos center = data.getCenterPos();
                VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), center);
            }
        }

        // Teleport player to their new house
        player.teleportTo(overworld,
                placement.interiorPos().getX() + 0.5,
                placement.interiorPos().getY(),
                placement.interiorPos().getZ() + 0.5,
                player.getYRot(), player.getXRot());
        BeginnersDelight.LOGGER.info("Assigned village house to player {} at grid {}",
                player.getName().getString(), gridPos);
        data.incrementHouseCountSinceLastDecoration();
        if (data.getHouseCountSinceLastDecoration() >= 2) tryPlaceDecoration(overworld, data);
    }

    private static void tryPlaceDecoration(ServerLevel overworld, VillageData data) {
        if (data.getCenterPos() == null) return;
        VillageGrid grid = new VillageGrid(data, config);
        String structureName = data.getDecorationCount() == 0 ? "village_well" : VillageHouseGenerator.selectRandomDecoration(overworld.getRandom());
        for (int attempt = 0; attempt < 10; attempt++) {
            Optional<GridPos> candidate = grid.findNextAvailablePlot();
            if (candidate.isEmpty()) { BeginnersDelight.LOGGER.warn("No available plots for decoration"); return; }
            GridPos candidatePos = candidate.get(); BlockPos worldPos = grid.gridToWorld(candidatePos);
            if (!VillageHouseGenerator.isSuitable(overworld, worldPos, config.getMaxHeightDifference())) { data.setPlotState(candidatePos, PlotState.UNSUITABLE); continue; }
            Optional<VillageHouseGenerator.PlacementResult> result = VillageHouseGenerator.placeDecoration(overworld, worldPos, structureName);
            if (result.isEmpty()) { data.setPlotState(candidatePos, PlotState.UNSUITABLE); continue; }
            VillageHouseGenerator.PlacementResult placement = result.get();
            data.setPlotState(candidatePos, PlotState.DECORATION); data.setDoorPosition(candidatePos, placement.doorFrontPos());
            data.incrementDecorationCount(); data.setHouseCountSinceLastDecoration(0);
            if (config.isGeneratePaths()) {
                Optional<GridPos> nearestOpt = grid.findNearestOccupiedPlot(candidatePos);
                if (nearestOpt.isPresent()) { BlockPos nearestDoor = data.getDoorPosition(nearestOpt.get()); if (nearestDoor != null) VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), nearestDoor); }
                else VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), data.getCenterPos());
            }
            BeginnersDelight.LOGGER.info("Placed decoration '{}' at grid {}", structureName, candidatePos); return;
        }
        BeginnersDelight.LOGGER.warn("Failed to place decoration after 10 attempts");
    }
}
