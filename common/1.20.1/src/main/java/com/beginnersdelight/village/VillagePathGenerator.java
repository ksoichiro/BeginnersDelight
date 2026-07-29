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

    // Tallest plant that can stand on a paved column is bamboo at about 16 blocks;
    // leave headroom.
    private static final int MAX_SURFACE_PLANT_HEIGHT = 32;

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
            // Take whatever stands on the surface away first, bottom-up and with
            // UPDATE_KNOWN_SHAPE so it goes quietly: nothing that grows on grass
            // survives on a path block, so paving first would let the shape update
            // break it into drops -- a snowball for every paved column in a snowy
            // biome, which the village generator never sweeps up. The whole stack
            // has to go: findPathSurface descends past every removable block, so
            // clearing one would leave the top half of a tall plant, or the rest of
            // a bamboo stalk, hanging over the finished path.
            for (int i = 1; i <= MAX_SURFACE_PLANT_HEIGHT; i++) {
                BlockPos above = surfacePos.above(i);
                if (!isRemovableVegetation(level.getBlockState(above))) break;
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2 | 16);
            }

            level.setBlock(surfacePos, Blocks.DIRT_PATH.defaultBlockState(), 2);
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

    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.PINK_PETALS);
    }

    // 1.20.1: No BlockTags.REPLACEABLE_BY_TREES — use explicit block/tag checks instead
    private static boolean isRemovableVegetation(BlockState state) {
        // Thin ground cover counts: a snowy biome covers every column, so treating a
        // snow layer as ground would leave the village without any paths at all.
        return isThinGroundCover(state)
                || isNonGroundPlant(state)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.GRASS)
                // Only the small mushrooms: a huge mushroom does not rest on the
                // column it happens to overhang, so counting its cap/stem blocks as
                // removable would carve a hole out of one standing next to the path.
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM);
    }

    // Plants that grow on top of the ground and must not be mistaken for the ground
    // itself. Bamboo matters most: a stalk reaches about 16 blocks, so counting it as
    // ground makes the path skip the column instead of paving the real surface.
    private static boolean isNonGroundPlant(BlockState state) {
        return state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH);
    }
}
