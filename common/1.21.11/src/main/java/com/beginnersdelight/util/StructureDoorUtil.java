package com.beginnersdelight.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class StructureDoorUtil {

    private StructureDoorUtil() {
    }

    /**
     * Scans the already-placed structure's footprint for a door block and returns
     * the position where the exterior side it opens onto clears the structure's
     * footprint, at ground level. Walks outward past any porch/entryway recess
     * rather than assuming the door sits flush with the outer wall. Falls back to
     * the south wall (the previous fixed assumption) if no door is found.
     */
    public static BlockPos findDoorFrontPos(ServerLevel level, BlockPos placePos, Vec3i size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos pos = placePos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof DoorBlock
                            && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                        Direction exterior = state.getValue(DoorBlock.FACING).getOpposite();
                        BlockPos outward = pos;
                        int maxSteps = size.getX() + size.getY() + size.getZ();
                        for (int step = 0; step < maxSteps && isWithinFootprint(outward, placePos, size); step++) {
                            outward = outward.relative(exterior);
                        }
                        return new BlockPos(outward.getX(), placePos.getY(), outward.getZ());
                    }
                }
            }
        }
        return new BlockPos(
                placePos.getX() + size.getX() / 2,
                placePos.getY(),
                placePos.getZ() + size.getZ());
    }

    private static boolean isWithinFootprint(BlockPos pos, BlockPos placePos, Vec3i size) {
        int localX = pos.getX() - placePos.getX();
        int localZ = pos.getZ() - placePos.getZ();
        return localX >= 0 && localX < size.getX() && localZ >= 0 && localZ < size.getZ();
    }
}
