package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.worldgen.StarterHouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates village mode operations.
 * Called from platform-specific event listeners.
 */
public class VillageManager {

    private static VillageConfig config = VillageConfigDefaults.defaults();

    // Upper bound only: the build normally starts as soon as the client reports that it
    // has finished loading. The cap is there for a client that never sends that report.
    private static final int JOIN_ASSIGNMENT_MAX_WAIT_TICKS = 200;

    private static final Map<UUID, Integer> pendingHouseAssignments = new HashMap<>();

    /**
     * Initializes the village system on server start.
     * Loads config and initializes the grid center if village mode is enabled
     * but no center has been set yet.
     */
    public static void onServerStarted(MinecraftServer server) {
        pendingHouseAssignments.clear();
        config = VillageConfigLoader.load(resolveConfigDir(server));

        ServerLevel overworld = server.overworld();
        VillageData data = VillageData.get(overworld);

        // In 0.5.0 and 0.6.0, every player who had been teleported to the starter house
        // was bound to it instead of getting a house of their own. Release all but one
        // owner so the rest are assigned a house the next time they join.
        int released = data.releaseDuplicatePlotOwners(new GridPos(0, 0));
        if (released > 0) {
            BeginnersDelight.LOGGER.info("Released {} player(s) from the shared starter house", released);
        }

        if (data.isEnabled() && data.getCenterPos() == null) {
            initializeGrid(overworld, data);
        }
    }

    /**
     * Handles a player joining the server.
     * If village mode is enabled and the player has no house, assigns one.
     * Players who already have a house are left where they are.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        ServerLevel overworld = player.getServer().overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;

        if (data.hasHouse(player.getUUID())) return;
        // Reuse the starter house as this player's village house instead of building a
        // redundant one. It stands on the single reserved center plot, so only the first
        // player can inherit it; everyone else gets a house of their own.
        StarterHouseData starterData = StarterHouseData.get(overworld);
        if (starterData.hasBeenTeleported(player.getUUID()) && starterData.getSpawnPos() != null
                && data.getPlotState(new GridPos(0, 0)) != PlotState.OCCUPIED) {
            registerStarterHouseAsVillageHouse(overworld, player, data,
                    starterData.getSpawnPos(), starterData.getDoorPos());
            return;
        }

        // Only the build is held back, not the decision above. Placing blocks inside the
        // join event leaves the client with stale lighting around the new house and its
        // path: its chunks are still on their way when the blocks change, so the light
        // updates that follow never reach the player.
        pendingHouseAssignments.put(player.getUUID(), JOIN_ASSIGNMENT_MAX_WAIT_TICKS);
    }

    /**
     * Builds the houses whose wait has elapsed.
     */
    public static void onServerTick(MinecraftServer server) {
        if (pendingHouseAssignments.isEmpty()) return;

        for (UUID uuid : new HashSet<>(pendingHouseAssignments.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            // A player who left while waiting is served on their next join instead.
            if (player == null) {
                pendingHouseAssignments.remove(uuid);
                continue;
            }
            int remaining = pendingHouseAssignments.get(uuid) - 1;
            if (remaining > 0 && !clientHasLoaded(player)) {
                pendingHouseAssignments.put(uuid, remaining);
                continue;
            }
            pendingHouseAssignments.remove(uuid);

            ServerLevel overworld = server.overworld();
            VillageData data = VillageData.get(overworld);
            // Village mode can be switched off, or the player housed, during the wait.
            if (!data.isEnabled() || data.hasHouse(uuid)) continue;
            try {
                assignHouse(overworld, player, data);
            } catch (RuntimeException e) {
                // The build runs on the server tick now, so letting this escape would take
                // the server down rather than just one player's login, as it used to.
                BeginnersDelight.LOGGER.error("Failed to assign a village house to player {}",
                        player.getName().getString(), e);
            }
        }
    }

    /**
     * Whether the player's client has reported that it finished loading the world. Once
     * it has, editing the blocks around them no longer strands their lighting.
     */
    private static boolean clientHasLoaded(ServerPlayer player) {
        return player.hasClientLoaded();
    }

    /**
     * Handles player respawn after death.
     * If respawnAtHouse is enabled and the player has no bed, teleport to their house.
     */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (player.getRespawnConfig() != null) return;
        if (!config.isRespawnAtHouse()) return;

        ServerLevel overworld = player.getServer().overworld();
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

    /**
     * Re-reads the config from disk. Invoked by the
     * {@code /beginnersdelight config reload} command.
     */
    public static void reloadConfig(MinecraftServer server) {
        config = VillageConfigLoader.load(resolveConfigDir(server));
        BeginnersDelight.LOGGER.info("Reloaded config");
    }

    /**
     * Resolves the loader-provided config directory. Kept in one place because the
     * {@code getServerDirectory()} return type differs across versions (Path vs File).
     */
    private static Path resolveConfigDir(MinecraftServer server) {
        return server.getServerDirectory().resolve("config");
    }

    private static void registerStarterHouseAsVillageHouse(ServerLevel overworld, ServerPlayer player, VillageData data, BlockPos starterHousePos, BlockPos starterDoorPos) {
        if (data.getCenterPos() == null) initializeGrid(overworld, data);
        GridPos centerGrid = new GridPos(0, 0);
        data.setPlotState(centerGrid, PlotState.OCCUPIED); data.setPlayerHouse(player.getUUID(), centerGrid);
        data.setHousePosition(centerGrid, starterHousePos);
        // Worlds generated before the door position was tracked fall back to the
        // interior spawn point rather than crash.
        data.setDoorPosition(centerGrid, starterDoorPos != null ? starterDoorPos : starterHousePos);
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
        ServerLevel overworld = player.getServer().overworld();
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
                Set.of(), player.getYRot(), player.getXRot(), false);
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
