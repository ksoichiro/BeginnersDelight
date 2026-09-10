#!/usr/bin/env python3
"""One-off propagation of the jungle-floating-leaves fix: drop the
JUNGLE_MAX_LEAF_DISTANCE cap so a removed tree's own canopy is always
captured out to the full MAX_LEAF_DISTANCE, regardless of species.
JUNGLE_INSIDE_SHRINK (trunk classification) is unaffected and kept.
26.2 was hand-edited and verified; this replicates the same edits to the
other 18 version directories.
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

CONST_ANCHOR = """    // Jungle trees are unusually large (megatrees, 2x2 trunks, canopy far from the
    // trunk) and grow densely packed, so the fixed 6-block trunk sweep and 7-block
    // leaf tie-break above turn one nearby trunk into disproportionate canopy loss,
    // and strip shared canopy from undamaged neighboring jungle trees too. Shrink
    // both, for jungle wood only, to keep clearing proportionate to tree size.
    private static final int JUNGLE_INSIDE_SHRINK = 3;
    private static final int JUNGLE_MAX_LEAF_DISTANCE = 4;
"""
CONST_REPLACEMENT = """    // Jungle trees are unusually large (megatrees, 2x2 trunks) and grow densely
    // packed, so the fixed 6-block trunk sweep above turns one nearby trunk into
    // disproportionate canopy loss. Shrink it for jungle wood only, to keep
    // clearing proportionate to tree size. The leaf search above stays at the
    // full MAX_LEAF_DISTANCE for every species, jungle included: a removed
    // tree's own canopy must be captured out to vanilla's real leaf-support
    // distance, or leaves beyond a shorter cap are left floating, belonging to
    // neither the removed tree (out of reach) nor a retained one (none nearby).
    private static final int JUNGLE_INSIDE_SHRINK = 3;
"""

DISTANCE_ANCHOR = """    private static void collectLeafDistances(ServerLevel level, BlockPos log,
                                             Map<BlockPos, Integer> result) {
        int maxDistance = isJungleLog(level.getBlockState(log))
                ? JUNGLE_MAX_LEAF_DISTANCE : MAX_LEAF_DISTANCE;
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        pending.add(log);
        distances.put(log, 0);

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            int distance = distances.get(pos);
            if (distance == maxDistance) {
                continue;
"""
DISTANCE_REPLACEMENT = """    private static void collectLeafDistances(ServerLevel level, BlockPos log,
                                             Map<BlockPos, Integer> result) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        pending.add(log);
        distances.put(log, 0);

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            int distance = distances.get(pos);
            if (distance == MAX_LEAF_DISTANCE) {
                continue;
"""

EDITS = [
    (CONST_ANCHOR, CONST_REPLACEMENT),
    (DISTANCE_ANCHOR, DISTANCE_REPLACEMENT),
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
