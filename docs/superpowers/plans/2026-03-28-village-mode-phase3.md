# Village Mode Phase 3 Implementation Plan — Road-Based Layout

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the grid-based village layout with a road-based system where roads grow organically following terrain, and houses are placed along the roads with natural spacing.

**Architecture:** Delete GridPos/VillageGrid/PlotState. Create RoadSegment, VillagePlot, PlotType, VillageRoadGenerator. Rewrite VillageData serialization. Rewrite VillageManager to use road growth + road-side placement. Update VillagePathGenerator for 2-block width. Update VillageCommand status display.

**Tech Stack:** Java 21, Minecraft 1.21.11, NeoForge/Fabric, Codec serialization

**Spec:** `docs/superpowers/specs/2026-03-28-village-mode-phase3-design.md`

---

### Task 1: Create PlotType Enum and Delete PlotState

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/PlotType.java`
- Delete: `common/1.21.11/src/main/java/com/beginnersdelight/village/PlotState.java`

- [ ] **Step 1: Create PlotType.java**

```java
package com.beginnersdelight.village;

public enum PlotType {
    HOUSE,
    DECORATION
}
```

- [ ] **Step 2: Delete PlotState.java**

- [ ] **Step 3: Commit** (will not build yet — dependent files still reference PlotState)

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/PlotType.java
git rm common/1.21.11/src/main/java/com/beginnersdelight/village/PlotState.java
git commit -m "feat(village): add PlotType enum, remove PlotState"
```

---

### Task 2: Create RoadSegment

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/RoadSegment.java`

- [ ] **Step 1: Create RoadSegment.java**

Data class with Codec serialization for a road segment (start, end, child segment IDs).

```java
package com.beginnersdelight.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one straight section of road from start to end point.
 */
public class RoadSegment {

    public static final Codec<RoadSegment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("id").forGetter(s -> s.id),
                    BlockPos.CODEC.fieldOf("start").forGetter(s -> s.start),
                    BlockPos.CODEC.fieldOf("end").forGetter(s -> s.end),
                    Codec.INT.listOf().fieldOf("children").forGetter(s -> s.childSegmentIds)
            ).apply(instance, RoadSegment::new)
    );

    private final int id;
    private final BlockPos start;
    private final BlockPos end;
    private final List<Integer> childSegmentIds;

    /**
     * Constructor always copies the input list to ensure mutability.
     * Codec deserialization may pass an unmodifiable list.
     */
    public RoadSegment(int id, BlockPos start, BlockPos end, List<Integer> childSegmentIds) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.childSegmentIds = new ArrayList<>(childSegmentIds);
    }

    public int getId() { return id; }
    public BlockPos getStart() { return start; }
    public BlockPos getEnd() { return end; }
    public List<Integer> getChildSegmentIds() { return childSegmentIds; }

    public boolean isTip() { return childSegmentIds.isEmpty(); }

    public void addChild(int childId) { childSegmentIds.add(childId); }
}
```

- [ ] **Step 2: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/RoadSegment.java
git commit -m "feat(village): add RoadSegment data class"
```

---

### Task 3: Create VillagePlot

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillagePlot.java`

- [ ] **Step 1: Create VillagePlot.java**

Data class with Codec for a placed building.

```java
package com.beginnersdelight.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * Represents a placed building (house or decoration) along a road.
 */
public class VillagePlot {

    public static final Codec<VillagePlot> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("id").forGetter(p -> p.id),
                    BlockPos.CODEC.fieldOf("position").forGetter(p -> p.position),
                    BlockPos.CODEC.fieldOf("door_position").forGetter(p -> p.doorPosition),
                    Codec.STRING.fieldOf("type").forGetter(p -> p.type.name()),
                    Codec.INT.fieldOf("road_segment_id").forGetter(p -> p.roadSegmentId)
            ).apply(instance, (id, position, doorPosition, type, roadSegmentId) ->
                    new VillagePlot(id, position, doorPosition, PlotType.valueOf(type), roadSegmentId))
    );

    private final int id;
    private final BlockPos position;
    private final BlockPos doorPosition;
    private final PlotType type;
    private final int roadSegmentId;

    public VillagePlot(int id, BlockPos position, BlockPos doorPosition, PlotType type, int roadSegmentId) {
        this.id = id;
        this.position = position;
        this.doorPosition = doorPosition;
        this.type = type;
        this.roadSegmentId = roadSegmentId;
    }

    public int getId() { return id; }
    public BlockPos getPosition() { return position; }
    public BlockPos getDoorPosition() { return doorPosition; }
    public PlotType getType() { return type; }
    public int getRoadSegmentId() { return roadSegmentId; }
}
```

- [ ] **Step 2: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillagePlot.java
git commit -m "feat(village): add VillagePlot data class"
```

---

### Task 4: Rewrite VillageData

**Files:**
- Rewrite: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageData.java`
- Delete: `common/1.21.11/src/main/java/com/beginnersdelight/village/GridPos.java`

**Context:** Complete rewrite. Remove all GridPos-based maps and PlotState references. Replace with RoadSegment list, VillagePlot list, and UUID→Integer playerHouses map.

- [ ] **Step 1: Rewrite VillageData.java**

The new file must:
- Remove all imports/references to GridPos, PlotState
- Replace fields: `plots`, `playerHouses` (Map<UUID,GridPos>), `housePositions`, `doorPositions` → `roads` (List<RoadSegment>), `plots` (List<VillagePlot>), `playerHouses` (Map<UUID,Integer>)
- Add `nextSegmentId` and `nextPlotId` auto-increment counters
- Rewrite CODEC using RoadSegment.CODEC.listOf(), VillagePlot.CODEC.listOf()
- Keep: `enabled`, `centerPos`, `houseCountSinceLastDecoration`, `decorationCount`
- Provide methods: `addRoad(RoadSegment)`, `addPlot(VillagePlot)`, `getRoad(int id)`, `getPlot(int id)`, `getTipSegments()`, `getAllPlots()`, `getHouseCount()`, `getDecorationCount()`, `hasHouse(UUID)`, `getPlayerPlotId(UUID)`, `setPlayerHouse(UUID, int plotId)`, `allocateSegmentId()`, `allocatePlotId()`, `get(ServerLevel)`

Read the spec at `docs/superpowers/specs/2026-03-28-village-mode-phase3-design.md` for the complete field list.

- [ ] **Step 2: Delete GridPos.java**

- [ ] **Step 3: Delete VillageGrid.java**

- [ ] **Step 4: Attempt build (expected failure)**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Note: This WILL FAIL because VillageManager, VillageCommand, and VillageHouseGenerator still reference deleted types (GridPos, VillageGrid, PlotState). This is expected — they will be fixed in subsequent tasks. Do not attempt to fix these errors now.

- [ ] **Step 5: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageData.java
git rm common/1.21.11/src/main/java/com/beginnersdelight/village/GridPos.java
git rm common/1.21.11/src/main/java/com/beginnersdelight/village/VillageGrid.java
git commit -m "feat(village): rewrite VillageData for road-based layout, remove GridPos/VillageGrid"
```

---

### Task 5: Update VillagePathGenerator (must run before VillageRoadGenerator)

**Files:**
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillagePathGenerator.java`

**Changes:**
- Make `placePathBlock()` package-private (remove private modifier) so VillageRoadGenerator can call it
- Add `placePathBlockWide()` package-private method that places a 2-block-wide path (calls `placePathBlock` for center ± 1 block perpendicular to direction)
- Remove `generatePath()` public method (no longer used — roads serve as paths)
- Keep `findPathSurface()` and `isRemovableVegetation()` as package-private utilities

- [ ] **Step 1: Update VillagePathGenerator**

- [ ] **Step 2: Commit** (build will still fail due to other files referencing deleted types)

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillagePathGenerator.java
git commit -m "refactor(village): expose path utilities and add 2-block width support"
```

---

### Task 6: Create VillageRoadGenerator

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageRoadGenerator.java`

**Context:** The core road generation logic. Handles:
- Bootstrap: creating first segment from centerPos
- Growth: selecting tip, scoring 8 directions, generating new segment
- Branching: detecting when multiple directions score well
- Dead end handling: fallback to other tips or midpoint branching
- Road block placement: laying 2-block-wide Dirt Path along segments

Read the spec sections "Bootstrap", "Growth Step", "Dead End Handling" for the algorithm.

- [ ] **Step 1: Create VillageRoadGenerator.java**

The class must have these public static methods:

```java
/**
 * Creates the first road segment from the village center.
 * Called once when village is first enabled and a house is requested.
 */
public static RoadSegment bootstrap(ServerLevel level, VillageData data, BlockPos center)

/**
 * Grows the road network by adding a new segment from a tip.
 * Returns the new segment, or empty if no growth is possible.
 */
public static Optional<RoadSegment> grow(ServerLevel level, VillageData data)

/**
 * Places Dirt Path blocks along a road segment (2-block width).
 */
public static void placeRoadBlocks(ServerLevel level, RoadSegment segment)
```

Internal methods:
- `scoreDirection(ServerLevel, BlockPos, int dx, int dz, int probeDistance, VillageData)` — returns int score
- `findGroundY(ServerLevel, int x, int z)` — reuse logic from VillageHouseGenerator
- `selectTip(VillageData, RandomSource)` — pick random tip segment
- `tryBranch(ServerLevel, RoadSegment, VillageData)` — check if branch should be added

Direction scoring criteria (per spec):
- Probe 10-15 blocks ahead
- Score += for low height difference
- Score += for no water/lava
- Score += for no overlap with roads/buildings
- Score += minor bonus for being closer to center

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Note: May still fail due to VillageManager references.

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageRoadGenerator.java
git commit -m "feat(village): add VillageRoadGenerator with terrain-following road growth"
```

---

### Task 7: Rewrite VillageManager

**Files:**
- Rewrite: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageManager.java`

**Context:** Major rewrite. Replace all grid-based logic with road-based logic.

Read the current file and the spec carefully. Key changes:

- `onPlayerJoin`: Keep StarterHouseData check. Replace GridPos references with VillagePlot.
- `assignHouse`: Replace grid allocation with:
  1. If no roads exist, call `VillageRoadGenerator.bootstrap()`
  2. Call `VillageRoadGenerator.grow()` to add a new segment
  3. Find placement position along the new segment (side selection, setback, collision check)
  4. Place house using `VillageHouseGenerator.place()`
  5. Create VillagePlot, record in VillageData
  6. Trigger decoration check
- `tryPlaceDecoration`: Same flow but using decoration placement
- `registerStarterHouseAsVillageHouse`: Create VillagePlot at centerPos with roadSegmentId=0
- `forceAssignHouse`: Keep as-is but use new data structures
- `onPlayerRespawn`: Replace GridPos lookup with VillagePlot lookup

Collision check helper method:
- `findPlacementAlongSegment(ServerLevel, RoadSegment, VillageData, VillageConfig)` — tries both sides of the road, returns Optional<BlockPos>

- [ ] **Step 1: Rewrite VillageManager.java**

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageManager.java
git commit -m "feat(village): rewrite VillageManager for road-based layout"
```

---

### Task 8: Update VillageHouseGenerator

**Files:**
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java`

**Changes:**
- Update `isSuitable()`: add rectangle-based collision check against VillagePlot list
- Add `checkCollision(BlockPos pos, int sizeX, int sizeZ, VillageData data)` method that checks overlap with:
  - Existing VillagePlots (position + structure footprint + 2-block margin)
  - Road segments (4-block wide corridor)
- Keep existing `place()` and `placeDecoration()` methods unchanged

- [ ] **Step 1: Add collision check method and update isSuitable**

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java
git commit -m "feat(village): add rectangle-based collision detection"
```

---

### Task 9: Update VillageCommand

**Files:**
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageCommand.java`

**Changes:**
- Update `status` method: display road segment count, house count, decoration count, player count
- Remove any GridPos/PlotState references
- Update `test` command to work with new data structures (check plot count instead of house count)

New status output format:
```
Village Mode: enabled
Center: (x, y, z)
Roads: N segments
Houses: N
Decorations: N
Players: N
Plot size: 20
...
```

- [ ] **Step 1: Update VillageCommand**

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL (all compilation errors should be resolved by now)

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageCommand.java
git commit -m "feat(village): update VillageCommand for road-based layout"
```

---

### Task 10: Build Verification and Manual Testing (1.21.11)

- [ ] **Step 1: Full build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew :neoforge:runClient #allow-compound`

1. Create new world (Creative, cheats ON)
2. `/beginnersdelight village enable`
3. `/beginnersdelight village test` — house 1 (first road segment created + house placed)
4. `/beginnersdelight village test` — house 2 → decoration (well) auto-placed
5. `/beginnersdelight village test` — house 3 (road should grow further)
6. `/beginnersdelight village test` — house 4 → another decoration
7. Repeat several times to verify:
   - Road follows terrain (curves around hills, avoids water)
   - Houses are on both sides of road with varied spacing
   - Branching occurs when terrain allows
   - Decorations are interleaved correctly
8. `/beginnersdelight village status` — verify counts
9. Save and reload — verify no re-login teleport, data persists

- [ ] **Step 3: Commit any fixes**

---

### Task 11: All-Version Deployment

After 1.21.11 is verified, deploy to all other versions.

**Files to propagate per version:**
- Delete: `GridPos.java`, `VillageGrid.java`, `PlotState.java`
- Create: `PlotType.java`, `RoadSegment.java`, `VillagePlot.java`, `VillageRoadGenerator.java`
- Rewrite: `VillageData.java`, `VillageManager.java`
- Modify: `VillageHouseGenerator.java`, `VillagePathGenerator.java`, `VillageCommand.java`

Version-specific adaptations (same groups as Phase 1-2):
- **1.21.5-1.21.10**: ResourceLocation, Codec-based VillageData
- **1.21.3-1.21.4**: CompoundTag+HolderLookup VillageData, 8-arg teleport
- **1.21.1**: CompoundTag+HolderLookup VillageData, 6-arg teleport
- **1.17.1-1.20.1**: CompoundTag VillageData (no HolderLookup), 6-arg teleport, `Random` not `RandomSource` (affects VillageRoadGenerator and VillageHouseGenerator too)
- **1.16.5**: Java 8, legacy SavedData

- [ ] **Step 1: Deploy to 1.21.5-1.21.10**
- [ ] **Step 2: Deploy to 1.21.1-1.21.4**
- [ ] **Step 3: Deploy to 1.17.1-1.20.1**
- [ ] **Step 4: Deploy to 1.16.5**
- [ ] **Step 5: Run buildAll**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew buildAll #allow-compound`
Expected: All 15 versions BUILD SUCCESSFUL
