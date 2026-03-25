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
        // Search up to 200 plots in spiral order.
        // Generates positions in spiral: (1,0), (1,-1), (0,-1), (-1,-1), (-1,0),
        // (-1,1), (0,1), (1,1), (2,1), (2,0), (2,-1), (2,-2), ...
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
                // Turn: (1,0) → (0,-1) → (-1,0) → (0,1)
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
     * Converts a grid position to world coordinates (corner of the plot).
     */
    public BlockPos gridToWorld(GridPos gridPos) {
        BlockPos center = data.getCenterPos();
        int plotSize = config.getPlotSize();
        return new BlockPos(
                center.getX() + (gridPos.x() * plotSize),
                center.getY(),
                center.getZ() + (gridPos.z() * plotSize)
        );
    }

    /**
     * Finds the nearest occupied plot to the given position (by Manhattan distance).
     * Used for path connection targets.
     */
    public Optional<GridPos> findNearestOccupiedPlot(GridPos from) {
        GridPos nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (var entry : data.getAllHousePositions().entrySet()) {
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
