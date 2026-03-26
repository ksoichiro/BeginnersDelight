package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates dirt paths between village houses.
 * Traces L-shaped paths (X-axis first, then Z-axis) and replaces
 * grass/dirt surface blocks with Grass Path blocks.
 */
public class VillagePathGenerator {

    /**
     * Generates a dirt path between two positions.
     * Traces X-axis first, then Z-axis (L-shaped path).
     */
    public static void generatePath(ServerLevel level, BlockPos from, BlockPos to) {
        BeginnersDelight.LOGGER.debug("Generating path from {} to {}", from, to);

        int x = from.getX();
        int z = from.getZ();
        int targetX = to.getX();
        int targetZ = to.getZ();

        // Trace along X-axis
        int stepX = x < targetX ? 1 : -1;
        while (x != targetX) {
            placePathBlock(level, x, z);
            x += stepX;
        }

        // Trace along Z-axis
        int stepZ = z < targetZ ? 1 : -1;
        while (z != targetZ) {
            placePathBlock(level, x, z);
            z += stepZ;
        }

        // Place at final position
        placePathBlock(level, x, z);
    }

    private static void placePathBlock(ServerLevel level, int x, int z) {
        int y = findPathSurface(level, x, z);
        if (y == -1) return;

        BlockPos surfacePos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(surfacePos);

        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
            // In 1.16.5, DIRT_PATH is named GRASS_PATH
            level.setBlock(surfacePos, Blocks.GRASS_PATH.defaultBlockState(), 2);

            BlockPos above = surfacePos.above();
            BlockState aboveState = level.getBlockState(above);
            if (isRemovableVegetation(aboveState)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static int findPathSurface(ServerLevel level, int x, int z) {
        int maxY = level.getMaxBuildHeight() - 1;
        // 1.16.5 world height is fixed at Y=0..256 (no getMinBuildHeight())
        int minY = 0;
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty()) return -1;
            if (isRemovableVegetation(state)) continue;
            return y;
        }
        return -1;
    }

    // 1.16.5: No BlockTags.REPLACEABLE_BY_TREES — use explicit block/tag checks instead
    private static boolean isRemovableVegetation(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.GRASS);
    }
}
