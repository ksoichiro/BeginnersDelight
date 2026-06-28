# Dirt-to-Grass Surface Naturalization — Design

Date: 2026-06-28

## Problem

During Village Mode / Starter House terrain leveling (整地) and interpolation
(補間), the generator fills the foundation and blends the surrounding terrain.
The surface (top) block is chosen by `detectDominantSurfaceBlock()` +
`mapToSurfaceBlock()`. When the dominant detected block is `DIRT`, dirt is placed
on the visible top surface even though the surrounding natural terrain is grass.
Naturally generated terrain has grass on top with dirt beneath, so exposed dirt
mixed among grass looks unnatural.

## Goal

After leveling/blending, any **dirt** block placed on the top surface that has a
**grass block** in its surroundings should be replaced with a **grass block**, so
the result blends naturally with the surrounding grassland.

## Decisions (confirmed with user)

1. **Detection granularity:** per-block neighbor check (local), NOT structure-wide
   substitution of the detected surface block.
2. **Scope:** both 整地 (`fillFoundation`) and 補間 (`blendSurroundingTerrain`),
   plus corner output (`blendCornerPillars`) — i.e. the entire leveled/blended
   surface.
3. **Features:** both `StarterHouseGenerator` (starter house) and
   `VillageHouseGenerator` (village mode).
4. **Neighbor range:** 8 horizontal directions (cardinals + diagonals).
5. **Height handling:** height-agnostic. Compare the top-of-column surface block at
   each (x,z); a neighboring grass top counts even if it sits at a different Y
   (slopes / blend steps).

## Approach: single post-process method (not inline edits)

Surface blocks are placed in 4 distinct sites across `fillFoundation`,
`blendSurroundingTerrain` (carve cap + fill top), and `blendCornerPillars`.
Editing each site inline and checking the *current* world while placing would
cause grass to **cascade**: a dirt block flipped to grass makes the next iterated
neighbor flip too, eventually turning the whole surface to grass — which violates
the chosen "local per-block" semantics.

Instead, add one independent method `naturalizeDirtSurface()` that runs **after**
all surface placement is complete. It is the minimal, centralized change
(one new method + one call site per sequence) and stays identical across all
versions because it only uses APIs present in every version.

### Algorithm (snapshot-then-convert, prevents cascade)

Target region = structure footprint + `margin` (2) + `blendRadius` (3), matching
the area modified by leveling/blending:

```
extend = margin + blendRadius            // 5
minX = placePos.x - extend
maxX = placePos.x + structureSize.x + extend
minZ = placePos.z - extend
maxZ = placePos.z + structureSize.z + extend
```

Use local-coordinate arrays (avoid long packing); region is small.

1. **Snapshot pass** — for each (x,z):
   - `groundY = findGroundY(level, x, z)`; skip if `-1`.
   - `topY = groundY - 1`; `top = level.getBlockState(x, topY, z)`.
   - if `top.is(GRASS_BLOCK)` → mark `grass[x][z] = true`.
   - else if `top.is(DIRT)` → record `dirtTopY[x][z] = topY`.

2. **Convert pass** — for each recorded dirt top:
   - check the 8 horizontal neighbors; if any has `grass[nx][nz] == true`
     (from the snapshot), `setBlock(x, topY, z, GRASS_BLOCK, 2)`.

Because grass membership is read from the snapshot taken before any conversion,
flips never feed back into later decisions → strictly local, no cascade.

### Why footprint interior is safe

Within the building footprint, `findGroundY()` finds the house roof/wall (the
highest non-vegetation solid), which is neither dirt nor grass, so those columns
are naturally ignored. The hidden foundation surface under the house floor is not
scanned and not relevant (not visible).

### Call site

Insert `naturalizeDirtSurface(level, placePos, size)` immediately **after**
`blendCornerPillars(...)` (before the final `removeDroppedItems`).

- `StarterHouseGenerator`: 1 call site.
- `VillageHouseGenerator`: 2 call sites (main house sequence + additional-structure
  sequence).

## Affected files

Existing logic is unchanged; only a new method (and call line(s)) are added.

- `common/shared/.../worldgen/StarterHouseGenerator.java` (reference copy)
- `common/{1.16.5 … 1.21.11}/.../worldgen/StarterHouseGenerator.java` (15)
- `common/{1.16.5 … 1.21.11}/.../village/VillageHouseGenerator.java` (15)

## API compatibility

The new method uses only:
- `findGroundY(level, x, z)` — already defined per-version (handles version Y-bound
  API differences internally).
- `Blocks.GRASS_BLOCK`, `Blocks.DIRT` — present in all versions.
- `BlockState.is(Block)` — present since 1.16.
- `level.getBlockState(BlockPos)` / `level.setBlock(BlockPos, BlockState, int)`.

So the method body is identical across every version; only the surrounding file's
pre-existing API usage differs.

## Out of scope (YAGNI)

- No config toggle (behavior is always-on naturalization).
- No substitution for non-dirt surface blocks (sand/gravel mapping unchanged).
- No change to `detectDominantSurfaceBlock` / `mapToSurfaceBlock` /
  `mapToSubsurfaceBlock`.

## Verification

Build for a representative set of versions and run a client to place a
house/village on a grass biome with a nearby dirt patch; confirm the blended ring
no longer shows isolated dirt blocks adjacent to grass.
- `./gradlew build` (default 1.21.11)
- `./gradlew build -Ptarget_mc_version=1.16.5` (oldest API)
- `./gradlew :neoforge:runClient` / `:fabric:runClient` for manual visual check.
