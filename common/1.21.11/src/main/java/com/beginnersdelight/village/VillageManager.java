package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

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
        ServerLevel overworld = player.level().getServer().overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;

        if (data.hasHouse(player.getUUID())) {
            // Returning player — teleport to existing house
            GridPos gridPos = data.getPlayerHouse(player.getUUID());
            BlockPos housePos = data.getHousePosition(gridPos);
            if (housePos != null) {
                player.teleportTo(overworld,
                        housePos.getX() + 0.5, housePos.getY(), housePos.getZ() + 0.5,
                        Set.of(), player.getYRot(), player.getXRot(), false);
                BeginnersDelight.LOGGER.debug("Teleported returning player {} to village house",
                        player.getName().getString());
            }
            return;
        }

        // New player — assign a house
        assignHouse(overworld, player, data);
    }

    /**
     * Handles player respawn after death.
     * If respawnAtHouse is enabled and the player has no bed, teleport to their house.
     */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (player.getRespawnConfig() != null) return;
        if (!config.isRespawnAtHouse()) return;

        ServerLevel overworld = player.level().getServer().overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;
        if (!data.hasHouse(player.getUUID())) return;

        GridPos gridPos = data.getPlayerHouse(player.getUUID());
        BlockPos housePos = data.getHousePosition(gridPos);
        if (housePos != null) {
            player.teleportTo(overworld,
                    housePos.getX() + 0.5, housePos.getY(), housePos.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot(), false);
            BeginnersDelight.LOGGER.debug("Respawned player {} at village house",
                    player.getName().getString());
        }
    }

    public static VillageConfig getConfig() {
        return config;
    }

    private static void initializeGrid(ServerLevel overworld, VillageData data) {
        BlockPos spawnPos = overworld.getRespawnData().pos();
        VillageGrid grid = new VillageGrid(data, config);
        grid.initialize(spawnPos);
        BeginnersDelight.LOGGER.info("Village grid initialized at center: {}", spawnPos);
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
                Set.of(), player.getYRot(), player.getXRot(), false);
        BeginnersDelight.LOGGER.info("Assigned village house to player {} at grid {}",
                player.getName().getString(), gridPos);
    }
}
