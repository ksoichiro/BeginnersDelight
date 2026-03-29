package com.beginnersdelight.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates dirt paths between village houses.
 * Traces L-shaped paths (X-axis first, then Z-axis) and replaces
 * grass/dirt surface blocks with Dirt Path blocks.
 */
public class VillagePathGenerator {

    /**
     * Finds the ground surface at the given XZ and replaces it with Dirt Path
     * if it is a suitable block (grass or dirt).
     */
    static void placePathBlock(ServerLevel level, int x, int z) {
        int y = findPathSurface(level, x, z);
        if (y == -1) return;

        BlockPos surfacePos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(surfacePos);

        // Only replace grass blocks and dirt with dirt path
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
            level.setBlock(surfacePos, Blocks.DIRT_PATH.defaultBlockState(), 2);

            // Remove vegetation above the path
            BlockPos above = surfacePos.above();
            BlockState aboveState = level.getBlockState(above);
            if (isRemovableVegetation(aboveState)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /**
     * Places a 2-block-wide dirt path perpendicular to the given direction.
     * @param dx direction X component (0 or ±1)
     * @param dz direction Z component (0 or ±1)
     */
    static void placePathBlockWide(ServerLevel level, int x, int z, int dx, int dz) {
        placePathBlock(level, x, z);
        // Place the adjacent block perpendicular to the direction
        if (dx != 0) {
            // Road goes east/west — widen north/south
            placePathBlock(level, x, z + 1);
        } else if (dz != 0) {
            // Road goes north/south — widen east/west
            placePathBlock(level, x + 1, z);
        } else {
            // Diagonal — widen in both directions
            placePathBlock(level, x + 1, z);
        }
    }

    /**
     * Scans downward to find the surface block suitable for path placement.
     * Returns the Y of the surface block, or -1 if not found.
     */
    static int findPathSurface(ServerLevel level, int x, int z) {
        int maxY = level.getHeight() - 1;
        int minY = level.getMinY();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty()) return -1; // Water/lava — skip
            if (isRemovableVegetation(state)) continue;
            // Found solid ground
            return y;
        }
        return -1;
    }

    static boolean isRemovableVegetation(BlockState state) {
        return state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.SHORT_GRASS);
    }
}
