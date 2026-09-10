#!/usr/bin/env python3
"""One-off propagation of the unbounded-flood-fill tree-clearing fix to all
version-specific VillageHouseGenerator.java / StarterHouseGenerator.java
copies. 26.2 was hand-edited and verified; this replicates the same edits
to the other 18 version directories. Run after apply_jungle_tree_shrink.py
(the JUNGLE_INSIDE_SHRINK/JUNGLE_MAX_LEAF_DISTANCE constants and their use
in the trunk-classification block are assumed already present).
"""
import sys

VERSIONS = [
    "1.16.5", "1.17.1", "1.18.2", "1.19.2", "1.20.1",
    "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6",
    "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
    "26.1", "26.1.1", "26.1.2",
]

FILES = [
    "village/VillageHouseGenerator.java",
    "worldgen/StarterHouseGenerator.java",
]

# Edit 1: replace clearIntersectingTrees's body + findProtectedTreeParts's
# signature/javadoc with the TreeClearPlan holder, the simplified
# clearIntersectingTrees, and planTreeClearing's new javadoc/signature.
HEADER_ANCHOR = """    private static Set<BlockPos> clearIntersectingTrees(ServerLevel level, BlockPos placePos,
                                                        net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius + 1;
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;
        int minY = placePos.getY();
        int maxY = placePos.getY() + structureSize.getY() + 10;
        Set<BlockPos> protectedParts = findProtectedTreeParts(level, minX, maxX, minZ, maxZ);
        Set<BlockPos> visited = new HashSet<>();

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (visited.contains(pos) || protectedParts.contains(pos)
                            || !isTreeBlock(level.getBlockState(pos))) {
                        continue;
                    }
                    clearTree(level, pos, visited, protectedParts);
                }
            }
        }
        return protectedParts;
    }

    /**
     * Finds the parts of trees whose trunks are outside the altered area. Leaves
     * do not record which tree placed them, so a connected-canopy flood fill alone
     * cannot distinguish adjacent trees. Starting from each outside trunk instead
     * preserves every leaf that vanilla considers supported by that trunk.
     */
    private static Set<BlockPos> findProtectedTreeParts(ServerLevel level, int minX, int maxX,
                                                         int minZ, int maxZ) {
"""
HEADER_REPLACEMENT = """    /**
     * Pairs the tree blocks a removal pass must clear with the ones neighboring
     * trees need kept, so both plans are derived from the same trunk analysis.
     */
    private static final class TreeClearPlan {
        private final Set<BlockPos> toRemove;
        private final Set<BlockPos> protectedParts;

        TreeClearPlan(Set<BlockPos> toRemove, Set<BlockPos> protectedParts) {
            this.toRemove = toRemove;
            this.protectedParts = protectedParts;
        }
    }

    private static Set<BlockPos> clearIntersectingTrees(ServerLevel level, BlockPos placePos,
                                                        net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius + 1;
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;

        TreeClearPlan plan = planTreeClearing(level, minX, maxX, minZ, maxZ);
        removeTreeParts(level, plan.toRemove);
        return plan.protectedParts;
    }

    /**
     * Identifies trees by trunk rather than by flood-filling touching canopy, so
     * a removed tree's leaves cannot cascade into an adjacent, untouched tree
     * through touching leaves the way a canopy-wide flood fill would. Trunks
     * inside the altered area contribute their logs and, up to the leaf-support
     * distance, their canopy to the removal set; trunks outside it are protected
     * the same way, with a leaf shared by both assigned to the nearer trunk.
     * A log/leaf that cannot be traced back to a trunk base (e.g. a stray branch
     * from a player-altered or non-vanilla tree shape) is left untouched; vanilla
     * world generation does not produce such trees, so this trades an unreachable
     * edge case for bounding the removal to the trees actually being cleared.
     */
    private static TreeClearPlan planTreeClearing(ServerLevel level, int minX, int maxX,
                                                   int minZ, int maxZ) {
"""

# Edit 2: replace the tail of the trunk-classification method (tie-break loop
# + its closing brace) with the toRemove-building logic, the new vine-only
# flood-fill helpers, and removeTreeParts.
TAIL_ANCHOR = """            if (entry.getValue() < removedDistance) {
                protectedParts.add(entry.getKey());
            }
        }
        return protectedParts;
    }
"""
TAIL_REPLACEMENT = """            if (entry.getValue() < removedDistance) {
                protectedParts.add(entry.getKey());
            }
        }

        Set<BlockPos> toRemove = new HashSet<>(removedLogs);
        for (BlockPos leaf : removedLeafDistances.keySet()) {
            if (!protectedParts.contains(leaf)) {
                toRemove.add(leaf);
            }
        }
        collectAttachedVines(level, toRemove);

        return new TreeClearPlan(toRemove, protectedParts);
    }

    /**
     * Adds every vine block reachable from the removal set by walking only
     * through other vines, so a hanging strand attached to a removed tree comes
     * down with it without the search crossing into an unrelated tree's logs
     * or leaves.
     */
    private static void collectAttachedVines(ServerLevel level, Set<BlockPos> toRemove) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>(toRemove);
        for (BlockPos pos : new HashSet<>(toRemove)) {
            queueAdjacentVines(level, pos, visited, pending);
        }
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            toRemove.add(pos);
            queueAdjacentVines(level, pos, visited, pending);
        }
    }

    private static void queueAdjacentVines(ServerLevel level, BlockPos pos, Set<BlockPos> visited,
                                           ArrayDeque<BlockPos> pending) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos adjacent = pos.offset(dx, dy, dz);
                    if (visited.add(adjacent) && level.getBlockState(adjacent).is(Blocks.VINE)) {
                        pending.add(adjacent);
                    }
                }
            }
        }
    }

    private static void removeTreeParts(ServerLevel level, Set<BlockPos> toRemove) {
        for (BlockPos pos : toRemove) {
            // UPDATE_KNOWN_SHAPE suppresses the usual support check, so snow resting
            // on a removed leaf/log would otherwise remain floating in the air.
            BlockPos above = pos.above();
            if (isSnowCover(level.getBlockState(above))) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }
"""

# Edit 3: clearDetachedProtectedLeaves's call site, plus deletion of the old
# clearTree/isTreeBlock/isTreePart methods (isTreeLog is kept).
TAIL2_ANCHOR = """        Set<BlockPos> connectedParts = findProtectedTreeParts(level, minX, maxX, minZ, maxZ);

        for (BlockPos pos : protectedParts) {
            if (!connectedParts.contains(pos) && level.getBlockState(pos).is(BlockTags.LEAVES)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static void clearTree(ServerLevel level, BlockPos start, Set<BlockPos> visited,
                                  Set<BlockPos> protectedParts) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> tree = new HashSet<>();
        pending.add(start);

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || protectedParts.contains(pos)
                    || !isTreePart(level.getBlockState(pos))) {
                continue;
            }
            tree.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            pending.add(pos.offset(dx, dy, dz));
                        }
                    }
                }
            }
        }

        for (BlockPos pos : tree) {
            // UPDATE_KNOWN_SHAPE suppresses the usual support check, so snow resting
            // on a removed leaf/log would otherwise remain floating in the air.
            BlockPos above = pos.above();
            if (isSnowCover(level.getBlockState(above))) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    private static boolean isTreeBlock(BlockState state) {
        return state.is(BlockTags.LEAVES) || isTreeLog(state);
    }

    private static boolean isTreeLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    private static boolean isTreePart(BlockState state) {
        return isTreeBlock(state) || state.is(Blocks.VINE);
    }
"""
TAIL2_REPLACEMENT = """        Set<BlockPos> connectedParts = planTreeClearing(level, minX, maxX, minZ, maxZ).protectedParts;

        for (BlockPos pos : protectedParts) {
            if (!connectedParts.contains(pos) && level.getBlockState(pos).is(BlockTags.LEAVES)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static boolean isTreeLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }
"""

EDITS = [
    (HEADER_ANCHOR, HEADER_REPLACEMENT),
    (TAIL_ANCHOR, TAIL_REPLACEMENT),
    (TAIL2_ANCHOR, TAIL2_REPLACEMENT),
]


def apply_edits(text, path):
    for anchor, replacement in EDITS:
        count = text.count(anchor)
        if count != 1:
            raise SystemExit(f"{path}: expected 1 match for anchor, found {count}\n---\n{anchor}")
        text = text.replace(anchor, replacement, 1)
    return text


def main():
    dry_run = "--apply" not in sys.argv
    for version in VERSIONS:
        for rel in FILES:
            path = f"common/{version}/src/main/java/com/beginnersdelight/{rel}"
            with open(path, encoding="utf-8") as fh:
                original = fh.read()
            updated = apply_edits(original, path)
            if dry_run:
                print(f"OK (dry-run): {path}")
            else:
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(updated)
                print(f"WROTE: {path}")


if __name__ == "__main__":
    main()
