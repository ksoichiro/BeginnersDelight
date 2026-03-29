package com.beginnersdelight.village;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Manages the village grid layout. Determines which plot to assign next
 * using a spiral pattern outward from the center.
 */
public class VillageGrid {

    private final VillageData data;
    private final VillageConfig config;

    public VillageGrid(VillageData data, VillageConfig config) {
        this.data = data;
        this.config = config;
    }

    /**
     * Initializes the grid center and reserves the center plot for the starter house.
     */
    public void initialize(BlockPos worldCenter) {
        data.setCenterPos(worldCenter);
        data.setPlotState(new GridPos(0, 0), PlotState.RESERVED);
    }

    /**
     * Finds the next available plot in spiral order.
     * Returns empty if no suitable plot is found within the search limit.
     */
    public Optional<GridPos> findNextAvailablePlot() {
        int maxSearch = 200;
        int x = 0, z = 0;
        int dx = 1, dz = 0;
        int segmentLength = 1;
        int segmentPassed = 0;
        int turnsMade = 0;

        // Move to first position (skip center which is RESERVED)
        x += dx;
        z += dz;

        for (int i = 0; i < maxSearch; i++) {
            GridPos pos = new GridPos(x, z);
            PlotState state = data.getPlotState(pos);
            if (state == PlotState.AVAILABLE) {
                return Optional.of(pos);
            }

            // Advance spiral
            segmentPassed++;
            if (segmentPassed == segmentLength) {
                segmentPassed = 0;
                int temp = dx;
                dx = dz;
                dz = -temp;
                turnsMade++;
                if (turnsMade % 2 == 0) {
                    segmentLength++;
                }
            }
            x += dx;
            z += dz;
        }
        return Optional.empty();
    }

    /**
     * Converts a grid position to world coordinates with a random offset
     * applied within the plot for a more natural village appearance.
     * The offset is deterministic per grid position (seeded by grid coords).
     */
    public BlockPos gridToWorld(GridPos gridPos) {
        BlockPos center = data.getCenterPos();
        int plotSize = config.getPlotSize();
        int baseX = center.getX() + (gridPos.x() * plotSize);
        int baseZ = center.getZ() + (gridPos.z() * plotSize);

        // Deterministic random offset based on grid position (±3 blocks)
        // Using a simple hash to avoid needing to store offsets
        int maxOffset = 3;
        long seed = ((long) gridPos.x() * 73856093L) ^ ((long) gridPos.z() * 19349663L);
        int offsetX = (int) ((seed & 0xFF) % (maxOffset * 2 + 1)) - maxOffset;
        int offsetZ = (int) (((seed >> 8) & 0xFF) % (maxOffset * 2 + 1)) - maxOffset;

        return new BlockPos(
                baseX + offsetX,
                center.getY(),
                baseZ + offsetZ
        );
    }

    /**
     * Finds the nearest occupied plot to the given position (by Manhattan distance).
     * Used for path connection targets.
     */
    public Optional<GridPos> findNearestOccupiedPlot(GridPos from) {
        GridPos nearest = null;
        int minDist = Integer.MAX_VALUE;

        // Search all plots with door positions (houses and decorations)
        for (var entry : data.getAllDoorPositions().entrySet()) {
            GridPos candidate = entry.getKey();
            if (candidate.equals(from)) continue;
            int dist = from.manhattanDistanceTo(candidate);
            if (dist < minDist) {
                minDist = dist;
                nearest = candidate;
            }
        }

        return Optional.ofNullable(nearest);
    }
}
