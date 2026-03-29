package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.worldgen.StarterHouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

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
     * Loads config. Road bootstrap is deferred until the first house is needed.
     */
    public static void onServerStarted(MinecraftServer server) {
        Path configDir = server.getServerDirectory().resolve("config");
        config.load(configDir);
    }

    /**
     * Handles a player joining the server.
     * If village mode is enabled and the player has no house, assigns one.
     * Players who already have a house are not teleported (they spawn at their last position).
     */
    public static void onPlayerJoin(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;

        // Player already has a village house — do nothing (spawn at last position)
        if (data.hasHouse(player.getUUID())) return;

        // Check if this player was already teleported to the starter house.
        // If so, register the starter house as their village house instead of building a new one.
        StarterHouseData starterData = StarterHouseData.get(overworld);
        if (starterData.hasBeenTeleported(player.getUUID()) && starterData.getSpawnPos() != null) {
            registerStarterHouseAsVillageHouse(overworld, player, data, starterData.getSpawnPos());
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

        int plotId = data.getPlayerPlotId(player.getUUID());
        VillagePlot plot = data.getPlot(plotId);
        if (plot != null) {
            BlockPos pos = plot.getPosition();
            player.teleportTo(overworld,
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot(), false);
            BeginnersDelight.LOGGER.debug("Respawned player {} at village house",
                    player.getName().getString());
        }
    }

    public static VillageConfig getConfig() {
        return config;
    }

    /**
     * Forces a new house assignment for the player, ignoring existing binding.
     * Used by the test command to simulate multiple players joining.
     */
    public static void forceAssignHouse(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        VillageData data = VillageData.get(overworld);
        assignHouse(overworld, player, data);
    }

    /**
     * Registers the existing starter house as the player's village house.
     * This avoids generating a redundant house for players who already have the starter house.
     */
    private static void registerStarterHouseAsVillageHouse(ServerLevel overworld, ServerPlayer player,
                                                            VillageData data, BlockPos starterHousePos) {
        // Bootstrap roads if none exist yet
        if (data.getAllRoads().isEmpty()) {
            BlockPos center = overworld.getRespawnData().pos();
            data.setCenterPos(center);
            VillageRoadGenerator.bootstrap(overworld, data, center);
        }

        // Create a VillagePlot for the starter house, associated with the first road segment
        RoadSegment firstSegment = data.getAllRoads().getFirst();
        int plotId = data.allocatePlotId();
        VillagePlot plot = new VillagePlot(plotId, starterHousePos, starterHousePos,
                PlotType.HOUSE, firstSegment.getId());
        data.addPlot(plot);
        data.setPlayerHouse(player.getUUID(), plotId);

        // Count as a house for decoration tracking
        data.incrementHouseCountSinceLastDecoration();

        BeginnersDelight.LOGGER.info("Registered starter house as village house for player {}",
                player.getName().getString());
    }

    private static void assignHouse(ServerLevel overworld, ServerPlayer player, VillageData data) {
        // Bootstrap roads if none exist yet
        if (data.getAllRoads().isEmpty()) {
            BlockPos center = overworld.getRespawnData().pos();
            data.setCenterPos(center);
            VillageRoadGenerator.bootstrap(overworld, data, center);
        }

        // Ensure the starter house is registered as a plot for collision detection,
        // even when called via forceAssignHouse (test command)
        ensureStarterHouseRegistered(overworld, data);

        // Try to grow road and find placement along segments (max 5 attempts)
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Grow a new road segment
            Optional<RoadSegment> segmentOpt = VillageRoadGenerator.grow(overworld, data);
            if (segmentOpt.isEmpty()) {
                BeginnersDelight.LOGGER.warn("Failed to grow road segment (attempt {}/{})",
                        attempt + 1, maxAttempts);
                continue;
            }

            RoadSegment segment = segmentOpt.get();
            Optional<BlockPos> placementOpt = findPlacementAlongSegment(overworld, segment, data);
            if (placementOpt.isEmpty()) {
                BeginnersDelight.LOGGER.debug("No suitable placement along segment {} (attempt {}/{})",
                        segment.getId(), attempt + 1, maxAttempts);
                continue;
            }

            BlockPos plotCenter = placementOpt.get();

            // Place the house
            Optional<VillageHouseGenerator.PlacementResult> result =
                    VillageHouseGenerator.place(overworld, plotCenter);
            if (result.isEmpty()) {
                BeginnersDelight.LOGGER.warn("Failed to place village house at {} for player {}",
                        plotCenter, player.getName().getString());
                continue;
            }

            VillageHouseGenerator.PlacementResult placement = result.get();

            // Record in data
            int plotId = data.allocatePlotId();
            VillagePlot plot = new VillagePlot(plotId, placement.interiorPos(), placement.doorFrontPos(),
                    PlotType.HOUSE, segment.getId());
            data.addPlot(plot);
            data.setPlayerHouse(player.getUUID(), plotId);

            // Teleport player to their new house
            player.teleportTo(overworld,
                    placement.interiorPos().getX() + 0.5,
                    placement.interiorPos().getY(),
                    placement.interiorPos().getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot(), false);
            BeginnersDelight.LOGGER.info("Assigned village house to player {} at {}",
                    player.getName().getString(), plotCenter);

            // Check if decoration should be placed
            data.incrementHouseCountSinceLastDecoration();
            if (data.getHouseCountSinceLastDecoration() >= 2) {
                tryPlaceDecoration(overworld, data);
            }
            return;
        }

        BeginnersDelight.LOGGER.warn("No suitable house placement found after {} attempts for player {}",
                maxAttempts, player.getName().getString());
    }

    private static void tryPlaceDecoration(ServerLevel overworld, VillageData data) {
        if (data.getCenterPos() == null) return;

        // Determine decoration type
        String structureName;
        if (data.getDecorationCount() == 0) {
            structureName = "village_well";
        } else {
            structureName = VillageHouseGenerator.selectRandomDecoration(overworld.getRandom());
        }

        // Try to grow road and find placement (up to 5 attempts)
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Optional<RoadSegment> segmentOpt = VillageRoadGenerator.grow(overworld, data);
            if (segmentOpt.isEmpty()) continue;

            RoadSegment segment = segmentOpt.get();
            Optional<BlockPos> placementOpt = findPlacementAlongSegment(overworld, segment, data);
            if (placementOpt.isEmpty()) continue;

            BlockPos plotCenter = placementOpt.get();

            Optional<VillageHouseGenerator.PlacementResult> result =
                    VillageHouseGenerator.placeDecoration(overworld, plotCenter, structureName);
            if (result.isEmpty()) continue;

            VillageHouseGenerator.PlacementResult placement = result.get();

            // Record in data
            int plotId = data.allocatePlotId();
            VillagePlot plot = new VillagePlot(plotId, placement.interiorPos(), placement.doorFrontPos(),
                    PlotType.DECORATION, segment.getId());
            data.addPlot(plot);
            data.incrementDecorationCount();
            data.setHouseCountSinceLastDecoration(0);

            BeginnersDelight.LOGGER.info("Placed decoration '{}' at {}", structureName, plotCenter);
            return;
        }

        // All attempts failed — skip this round, counter stays >= 2 for retry on next house
        BeginnersDelight.LOGGER.warn("Failed to place decoration after {} attempts", maxAttempts);
    }

    /**
     * Ensures the starter house is registered as a VillagePlot for collision detection.
     * This is needed even for the test command (forceAssignHouse) to prevent new houses
     * from overlapping with the starter house.
     */
    private static void ensureStarterHouseRegistered(ServerLevel overworld, VillageData data) {
        StarterHouseData starterData = StarterHouseData.get(overworld);
        BlockPos starterPos = starterData.getSpawnPos();
        if (starterPos == null || !starterData.isGenerated()) return;

        // Check if any existing plot is already at the starter house position
        for (VillagePlot plot : data.getAllPlots()) {
            if (plot.getPosition().equals(starterPos)) return; // Already registered
        }

        // Register the starter house as a plot (no player binding)
        RoadSegment firstSegment = data.getAllRoads().isEmpty() ? null : data.getAllRoads().getFirst();
        int segmentId = firstSegment != null ? firstSegment.getId() : 0;
        int plotId = data.allocatePlotId();
        VillagePlot plot = new VillagePlot(plotId, starterPos, starterPos, PlotType.HOUSE, segmentId);
        data.addPlot(plot);
        BeginnersDelight.LOGGER.debug("Registered starter house as collision plot at {}", starterPos);
    }

    /**
     * Finds a suitable house placement position along a road segment.
     * Tries a random position on one side of the road, then the opposite side.
     */
    private static Optional<BlockPos> findPlacementAlongSegment(ServerLevel level, RoadSegment segment,
                                                                  VillageData data) {
        RandomSource random = level.getRandom();

        BlockPos start = segment.getStart();
        BlockPos end = segment.getEnd();
        int totalDx = end.getX() - start.getX();
        int totalDz = end.getZ() - start.getZ();

        // Direction along the segment
        int dx = Integer.signum(totalDx);
        int dz = Integer.signum(totalDz);

        // Perpendicular direction (rotate 90 degrees: (dx,dz) -> (-dz,dx))
        int perpDx = -dz;
        int perpDz = dx;
        // If segment is purely diagonal, perpendicular is still valid
        // If segment is zero-length, skip
        if (dx == 0 && dz == 0) return Optional.empty();

        // Pick a random position along the segment (0.0 to 1.0)
        double t = 0.2 + random.nextDouble() * 0.6; // avoid very start/end
        int alongX = start.getX() + (int) (totalDx * t);
        int alongZ = start.getZ() + (int) (totalDz * t);

        // Random setback distance (2-5 blocks)
        int setback = 2 + random.nextInt(4);

        // Try one side
        int candidateX = alongX + perpDx * setback;
        int candidateZ = alongZ + perpDz * setback;
        BlockPos candidate = new BlockPos(candidateX, start.getY(), candidateZ);

        if (VillageHouseGenerator.isSuitable(level, candidate, config.getMaxHeightDifference(), data)) {
            return Optional.of(candidate);
        }

        // Try opposite side
        candidateX = alongX - perpDx * setback;
        candidateZ = alongZ - perpDz * setback;
        candidate = new BlockPos(candidateX, start.getY(), candidateZ);

        if (VillageHouseGenerator.isSuitable(level, candidate, config.getMaxHeightDifference(), data)) {
            return Optional.of(candidate);
        }

        return Optional.empty();
    }
}
