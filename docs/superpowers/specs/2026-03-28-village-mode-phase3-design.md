# Village Mode Phase 3 Design Spec — Road-Based Layout

## Overview

Replace the grid-based village layout with a road-based system where roads grow organically following terrain, and houses are placed along the roads with natural spacing. This produces a more village-like appearance compared to the mechanical grid layout.

## Goals

- Replace grid-based placement with terrain-following road-based layout
- Houses placed along roads with random spacing and setback
- Roads branch naturally at terrain features
- Maintain Phase 2 decoration interleaving (2 houses → 1 decoration)

## Non-Goals

- Bridge generation over water (future)
- Biome-specific structures (future)
- Configurable road width or growth parameters (hardcoded for now)

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Road growth model | Segment-based random walk with terrain constraints | Best fit for dynamic growth; simple data structure |
| Direction selection | 8-direction scoring (terrain scan) | Adapts to terrain naturally |
| Branching | Natural, when multiple directions score high | No forced branching points |
| House placement | Both sides of road, random interval and setback (2-5 blocks) | Most natural village appearance |
| Road width | 2 blocks | More village-like than 1-block path |
| Collision detection | Rectangle-based | Replaces grid-based plot system |
| Existing world compat | Existing villages remain; new additions use road-based | No migration needed |
| Growth limit | None | Roads grow indefinitely |
| Grid system | Completely removed | No release yet; no compatibility needed |

## Data Structures

### Removed

- `GridPos.java` — grid coordinate record
- `VillageGrid.java` — grid plot management
- `PlotState.java` — grid plot state enum

### New: RoadSegment

Represents one straight section of road from start to end point.

```java
public class RoadSegment {
    int id;
    BlockPos start;
    BlockPos end;
    List<Integer> childSegmentIds;  // Branch segment IDs
}
```

### New: VillagePlot

Represents a placed building (house or decoration) along a road.

```java
public class VillagePlot {
    int id;
    BlockPos position;       // World coordinates of the structure
    BlockPos doorPosition;   // Door-front coordinate (road-facing side)
    PlotType type;           // HOUSE or DECORATION
    int roadSegmentId;       // Which road segment this plot belongs to
}
```

### New: PlotType

```java
public enum PlotType {
    HOUSE,
    DECORATION
}
```

### VillageData (Modified)

Fields after migration:

| Field | Type | Purpose |
|---|---|---|
| `enabled` | `boolean` | Village mode on/off |
| `centerPos` | `BlockPos` | Village center (road origin) |
| `roads` | `List<RoadSegment>` | All road segments |
| `plots` | `List<VillagePlot>` | All placed buildings |
| `playerHouses` | `Map<UUID, Integer>` | Player UUID → VillagePlot ID (changed from `Map<UUID, GridPos>` in Phase 1; no migration needed since unreleased) |
| `houseCountSinceLastDecoration` | `int` | Decoration trigger counter |
| `decorationCount` | `int` | Total decorations placed |
| `nextSegmentId` | `int` | Auto-increment ID for segments |
| `nextPlotId` | `int` | Auto-increment ID for plots |

## Road Generation Algorithm

### Bootstrap (First Road Segment)

When the village is first enabled and the first player joins (or `village test` is run), the initial road segment is created:

1. `centerPos` (world spawn) is the segment's `start`
2. Direction: scan 8 directions from center using the same scoring algorithm, pick the best
3. Length: standard random (8-15 blocks)
4. This first segment becomes the root of the road tree

### Growth Step

Triggered each time a new house needs to be placed (after the first segment exists):

1. **Select tip segment** — Choose a random "tip" (segment with no children) from the road tree
2. **Scan directions** — From the tip's end point, evaluate 8 directions (N, NE, E, SE, S, SW, W, NW)
3. **Score each direction** (over a probe distance of 10-15 blocks):
   - Low height difference → high score
   - No water/lava → high score
   - No overlap with existing roads/buildings → high score
   - Not too far from center → minor bonus (prevents drift)
4. **Select direction** — Weighted random from top-scoring directions (not always best, for variety)
5. **Generate segment** — Create segment of random length (8-15 blocks) in the selected direction
6. **Branch check** — If 2+ directions have similarly high scores, add a branch segment with some probability
7. **Place road blocks** — Lay Dirt Path (2-block width) along the new segment

### Dead End Handling

If all directions from a tip score too low (surrounded by cliffs/water):
1. Try another tip segment
2. If all tips are dead ends, try branching from the midpoint of an existing segment
3. If still no valid placement, skip house assignment for this player (they spawn normally at world spawn). Log warning. Player will be retried on next join.

### Segment Length

8-15 blocks (random). Each segment accommodates 1-2 buildings along it.

## House Placement Algorithm

After a new road segment is generated:

1. **Side selection** — Randomly choose left or right side of the road
2. **Road-along position** — Random position between segment start and end
3. **Setback distance** — Random 2-5 blocks perpendicular to the road
4. **Surface detection** — Run `findSurfacePosition()` at the placement position
5. **Collision check** — Verify the building's rectangle doesn't overlap:
   - Existing buildings (using position + structure size as rectangle, with margin)
   - Road segments (road width 2 blocks + 1-block margin on each side = 4-block wide collision corridor centered on the segment line)
6. **If collision** — Try the opposite side of the road
7. **If both sides fail** — Try the next road segment tip
8. **Door orientation** — Face the road (determined by road segment direction)

### Building Size for Collision

Structure footprint + 2-block margin on each side:

- Starter houses (~11x11 footprint): 15x15 collision rectangle
- Small decorations (~5x5 footprint): 9x9 collision rectangle
- Large decorations (~7x7 footprint): 11x11 collision rectangle

## Decoration Placement

Same rules as Phase 2:
- Every 2 player houses, 1 decoration is placed
- First decoration is always `village_well`
- Subsequent decorations: random from `village_shed`, `village_storehouse`, `village_farm`
- Decorations are placed along the road using the same algorithm as houses

## Path Generation Changes

Phase 1-2 used `VillagePathGenerator` to draw L-shaped Dirt Path connections between buildings. In Phase 3, road segments themselves serve as the paths — `VillageRoadGenerator` lays Dirt Path blocks when creating each segment. `VillagePathGenerator` is **no longer used for connecting buildings**. It is retained only for its `placePathBlock()` utility, which `VillageRoadGenerator` calls internally.

Buildings are placed along road segments, so they are inherently connected by the road. No separate path connection step is needed.

## Road Block Placement (VillageRoadGenerator)

### Width

2 blocks wide (center line ± 1 block).

### Height Handling

- 1-block step: automatic staircase
- 2+ block gap: avoided by direction scoring; if encountered, road is interrupted at that point

### Water

Avoided by direction scoring. No bridge generation (future work).

## Code Changes

### Files to Delete

- `GridPos.java`
- `VillageGrid.java`
- `PlotState.java`

### Files to Create

| File | Responsibility |
|---|---|
| `RoadSegment.java` | Road segment data + Codec serialization |
| `VillagePlot.java` | Building placement data + Codec serialization |
| `PlotType.java` | HOUSE/DECORATION enum |
| `VillageRoadGenerator.java` | Road growth, direction scoring, branching, segment generation |

### Files to Significantly Modify

| File | Changes |
|---|---|
| `VillageData.java` | Replace GridPos-based maps with RoadSegment/VillagePlot lists. Full serialization rewrite. |
| `VillageManager.java` | Replace grid allocation with road growth + road-side placement. Update assignHouse, tryPlaceDecoration. Update registerStarterHouseAsVillageHouse: create a VillagePlot for the starter house at centerPos with roadSegmentId=0 (first segment), no GridPos. |
| `VillageHouseGenerator.java` | Update `isSuitable()` to rectangle-based collision check against VillagePlot list. |

### Files to Modify (Minor)

| File | Changes |
|---|---|
| `VillagePathGenerator.java` | Support 2-block width. |
| `VillageCommand.java` | Update status display. New output: `Village Mode: enabled/disabled`, `Center: (x,y,z)`, `Roads: N segments`, `Houses: N`, `Decorations: N`, `Players: N`, plus existing config values. |

### No Changes

- `VillageConfig.java` — existing settings remain valid
- Platform entry points — no changes needed

## Version Deployment

Implement in 1.21.11 first, test, then deploy to all 15 versions following the same pattern as Phase 1-2.
