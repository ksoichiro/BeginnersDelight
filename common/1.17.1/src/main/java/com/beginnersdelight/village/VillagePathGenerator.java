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
 * grass/dirt surface blocks with Dirt Path blocks.
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

        // Determine primary axis (X first, then Z)
        int stepX = x < targetX ? 1 : -1;
        int stepZ = z < targetZ ? 1 : -1;

        // Trace along X-axis (2-block wide: ±1 on Z)
        while (x != targetX) {
            placePathBlock(level, x, z);
            placePathBlock(level, x, z + 1);
            x += stepX;
        }

        // Trace along Z-axis (2-block wide: ±1 on X)
        while (z != targetZ) {
            placePathBlock(level, x, z);
            placePathBlock(level, x + 1, z);
            z += stepZ;
        }

        // Place at final position
        placePathBlock(level, x, z);
        placePathBlock(level, x + 1, z);
    }

    private static void placePathBlock(ServerLevel level, int x, int z) {
        int y = findPathSurface(level, x, z);
        if (y == -1) return;

        BlockPos surfacePos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(surfacePos);

        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
            level.setBlock(surfacePos, Blocks.DIRT_PATH.defaultBlockState(), 2);

            BlockPos above = surfacePos.above();
            BlockState aboveState = level.getBlockState(above);
            if (isRemovableVegetation(aboveState)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static int findPathSurface(ServerLevel level, int x, int z) {
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty()) return -1;
            if (isRemovableVegetation(state)) continue;
            return y;
        }
        return -1;
    }

    // 1.20.1: No BlockTags.REPLACEABLE_BY_TREES — use explicit block/tag checks instead
    private static boolean isRemovableVegetation(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.GRASS);
    }
}
