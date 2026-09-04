package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.util.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates dirt paths between village houses.
 * Traces L-shaped paths (X-axis first, then Z-axis) and replaces
 * natural ground surface blocks with Dirt Path blocks.
 */
public class VillagePathGenerator {

    // Tallest plant that can stand on a paved column is bamboo at about 16 blocks;
    // leave headroom.
    private static final int MAX_SURFACE_PLANT_HEIGHT = 32;

    // How far above/below the previous column's surface to look for the next one.
    // Scanning from the world top would follow a ravine or cave mouth straight down
    // to its floor, dropping the path dozens of blocks for a single column; bounding
    // the search to the previous column's height instead makes such columns bridge
    // across at that height.
    private static final int SURFACE_SEARCH_RANGE = 8;

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
        int referenceY = from.getY();

        // Determine primary axis (X first, then Z)
        int stepX = x < targetX ? 1 : -1;
        int stepZ = z < targetZ ? 1 : -1;

        // Trace along X-axis (2-block wide: ±1 on Z)
        while (x != targetX) {
            referenceY = placePathBlock(level, x, z, referenceY);
            placePathBlock(level, x, z + 1, referenceY);
            x += stepX;
        }

        // Trace along Z-axis (2-block wide: ±1 on X)
        while (z != targetZ) {
            referenceY = placePathBlock(level, x, z, referenceY);
            placePathBlock(level, x + 1, z, referenceY);
            z += stepZ;
        }

        // Place at final position
        referenceY = placePathBlock(level, x, z, referenceY);
        placePathBlock(level, x + 1, z, referenceY);
    }

    /**
     * Finds the ground surface at the given XZ, near referenceY, and replaces it
     * with Dirt Path if it is a suitable block (natural ground, see
     * {@link #isPavableGround}). If no ground is found within range (a ravine or
     * cave mouth), bridges across at referenceY instead of paving whatever lies
     * far below.
     * Returns the Y the path now sits at, for the next column to reference.
     */
    private static int placePathBlock(ServerLevel level, int x, int z, int referenceY) {
        int y = findPathSurface(level, x, z, referenceY);
        if (y == NO_SURFACE) return referenceY;

        boolean bridging = y == BRIDGE_NEEDED;
        int surfaceY = bridging ? referenceY : y;
        BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        BlockState state = level.getBlockState(surfacePos);

        // Bridging across a gap counts as suitable unconditionally: there is no real
        // surface block to check the type of. Otherwise only replace natural ground.
        if (bridging || isPavableGround(state)) {
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
            return surfaceY;
        }

        return referenceY;
    }

    // Sentinels returned by findPathSurface, distinguished from real Y coordinates
    // (which never reach these magnitudes).
    private static final int NO_SURFACE = Integer.MIN_VALUE;
    private static final int BRIDGE_NEEDED = Integer.MIN_VALUE + 1;

    /**
     * Scans for the surface block near referenceY, bounded by SURFACE_SEARCH_RANGE.
     * Returns the Y of the surface block, NO_SURFACE if water/lava blocks the column,
     * or BRIDGE_NEEDED if nothing solid was found within range.
     */
    private static int findPathSurface(ServerLevel level, int x, int z, int referenceY) {
        int maxY = Math.min(level.getHeight() - 1, referenceY + SURFACE_SEARCH_RANGE);
        int minY = Math.max(level.getMinY(), referenceY - SURFACE_SEARCH_RANGE);
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty()) return NO_SURFACE; // Water/lava — skip
            if (isRemovableVegetation(state)) continue;
            // Found solid ground
            return y;
        }
        return BRIDGE_NEEDED;
    }

    // Grass and dirt cover most biomes, but a desert or badlands (mesa) surface
    // never touches either, so paths generated there would skip almost every
    // column. Sand and terracotta are the natural ground blocks of those biomes.
    private static boolean isPavableGround(BlockState state) {
        // 26.2: the 16 colored terracotta blocks are no longer individual Blocks
        // constants, only reachable via the Blocks.DYED_TERRACOTTA ColorCollection
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.TERRACOTTA)
                || Blocks.DYED_TERRACOTTA.asList().contains(state.getBlock());
    }

    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.PINK_PETALS) || state.is(Blocks.PALE_MOSS_CARPET);
    }

    private static boolean isRemovableVegetation(BlockState state) {
        // Thin ground cover counts: a snowy biome covers every column, so treating a
        // snow layer as ground would leave the village without any paths at all.
        return isThinGroundCover(state)
                || isNonGroundPlant(state)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.FLOWERS)
                || state.is(ModBlockTags.SAPLINGS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.SHORT_GRASS)
                // Only the small mushrooms: a huge mushroom does not rest on the
                // column it happens to overhang, so counting its cap/stem blocks as
                // removable would carve a hole out of one standing next to the path.
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                // House templates stand a potted plant or lantern right beside the
                // door; without this a path can't reach the ground under it and
                // stops one column short of the door.
                || state.getBlock() instanceof FlowerPotBlock
                || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN);
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
