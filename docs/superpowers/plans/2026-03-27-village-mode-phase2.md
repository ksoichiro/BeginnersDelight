# Village Mode Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add decorative buildings (well, shed, storehouse, farm) that are automatically placed between player houses in the village grid.

**Architecture:** Extend existing PlotState, VillageData, VillageHouseGenerator, and VillageManager. The `place()` method gains a structure name parameter. VillageManager tracks house count and triggers decoration placement every 2 houses. NBT structures and loot tables are created manually.

**Tech Stack:** Java 21, Minecraft 1.21.11, NeoForge/Fabric, Brigadier, Codec serialization

**Spec:** `docs/superpowers/specs/2026-03-27-village-mode-phase2-design.md`

---

### Task 1: PlotState Extension

**Files:**
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/PlotState.java`

- [ ] **Step 1: Add DECORATION value**

Add `DECORATION` between `OCCUPIED` and `UNSUITABLE`:

```java
package com.beginnersdelight.village;

public enum PlotState {
    RESERVED,
    AVAILABLE,
    OCCUPIED,
    DECORATION,
    UNSUITABLE
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/PlotState.java
git commit -m "feat(village): add DECORATION plot state for Phase 2"
```

---

### Task 2: VillageData Extensions

**Files:**
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageData.java`

- [ ] **Step 1: Add new fields**

Add after the `doorPositions` field declaration (line ~91):

```java
private int houseCountSinceLastDecoration;
private int decorationCount;
```

Initialize in constructor:

```java
public VillageData() {
    this.enabled = false;
    this.centerPos = null;
    this.houseCountSinceLastDecoration = 0;
    this.decorationCount = 0;
}
```

- [ ] **Step 2: Add getters and setters**

Add after the existing `getPlayerCount()` method:

```java
public int getHouseCountSinceLastDecoration() { return houseCountSinceLastDecoration; }

public void setHouseCountSinceLastDecoration(int count) {
    this.houseCountSinceLastDecoration = count;
    setDirty();
}

public void incrementHouseCountSinceLastDecoration() {
    this.houseCountSinceLastDecoration++;
    setDirty();
}

public int getDecorationCount() { return decorationCount; }

public void incrementDecorationCount() {
    this.decorationCount++;
    setDirty();
}
```

- [ ] **Step 3: Extend Codec**

The main CODEC needs two additional fields. Modify the `RecordCodecBuilder.create` call — add after `door_positions`:

```java
Codec.INT.fieldOf("house_count_since_last_decoration").forGetter(d -> d.houseCountSinceLastDecoration),
Codec.INT.fieldOf("decoration_count").forGetter(d -> d.decorationCount)
```

Update the `.apply` lambda signature to include the 2 new parameters (8 total):

```java
).apply(instance, (enabled, centerPos, plots, playerHouses, housePositions, doorPositions, houseCountSinceLastDecoration, decorationCount) -> {
    VillageData data = new VillageData();
    data.enabled = enabled;
    data.centerPos = centerPos.orElse(null);
    plots.forEach(e -> data.plots.put(e.pos(), e.state()));
    playerHouses.forEach(e -> data.playerHouses.put(e.uuid(), e.gridPos()));
    housePositions.forEach(e -> data.housePositions.put(e.gridPos(), e.blockPos()));
    doorPositions.forEach(e -> data.doorPositions.put(e.gridPos(), e.blockPos()));
    data.houseCountSinceLastDecoration = houseCountSinceLastDecoration;
    data.decorationCount = decorationCount;
    return data;
})
```

Note: `RecordCodecBuilder` supports up to 16 fields. After this change we have 8 fields.

- [ ] **Step 4: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageData.java
git commit -m "feat(village): add decoration counter fields to VillageData"
```

---

### Task 3: VillageHouseGenerator — Structure Name Parameter

**Files:**
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java`

- [ ] **Step 1: Add decoration loot table constants**

Add after the existing `STARTER_HOUSE_LOOT` constant:

```java
private static final ResourceKey<LootTable> VILLAGE_STOREHOUSE_LOOT = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/village_storehouse"));

private static final ResourceKey<LootTable> VILLAGE_FARM_LOOT = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/village_farm"));

private static final Map<String, ResourceKey<LootTable>> DECORATION_LOOT_TABLES = Map.of(
        "village_storehouse", VILLAGE_STOREHOUSE_LOOT,
        "village_farm", VILLAGE_FARM_LOOT
);

private static final String[] DECORATION_VARIANTS = {
        "village_shed", "village_storehouse", "village_farm"
};
```

- [ ] **Step 2: Add placeStructure method with explicit structure name**

Add a new public method below the existing `place()`:

```java
/**
 * Places a specific named structure at the given plot center.
 * Used for decoration buildings.
 */
public static Optional<PlacementResult> placeDecoration(ServerLevel level, BlockPos plotCenter, String structureName) {
    StructureTemplateManager templateManager = level.getStructureManager();
    RandomSource random = level.getRandom();

    Identifier structureId = Identifier.fromNamespaceAndPath(BeginnersDelight.MOD_ID, structureName);

    Optional<StructureTemplate> templateOpt = templateManager.get(structureId);
    if (templateOpt.isEmpty()) {
        BeginnersDelight.LOGGER.error("Structure template not found: {}", structureId);
        return Optional.empty();
    }

    StructureTemplate template = templateOpt.get();
    StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false);

    BlockPos placePos = findSurfacePosition(level, plotCenter, template.getSize());
    if (placePos == null) {
        BeginnersDelight.LOGGER.warn("Could not find suitable surface position for {}", structureName);
        return Optional.empty();
    }

    BeginnersDelight.LOGGER.info("Placing decoration '{}' at {}", structureName, placePos);

    Vec3i size = template.getSize();
    removeMobs(level, placePos, size);
    clearVegetation(level, placePos, size);
    template.placeInWorld(level, placePos, placePos, settings, random, 2 | 16);
    removeDroppedItems(level, placePos, size);

    // Assign loot table only if this decoration type has one
    ResourceKey<LootTable> lootTable = DECORATION_LOOT_TABLES.get(structureName);
    if (lootTable != null) {
        assignLootTablesWithKey(level, placePos, size, random, lootTable);
    }

    fillFoundation(level, placePos, size);
    blendSurroundingTerrain(level, placePos, size);
    removeDroppedItems(level, placePos, size);

    BlockPos interiorPos = placePos.offset(size.getX() / 2, 1, size.getZ() / 2);
    BlockPos doorFrontPos = new BlockPos(
            placePos.getX() + size.getX() / 2,
            placePos.getY(),
            placePos.getZ() + size.getZ());

    return Optional.of(new PlacementResult(interiorPos, doorFrontPos));
}

/**
 * Selects a random decoration type (excluding well).
 */
public static String selectRandomDecoration(RandomSource random) {
    return DECORATION_VARIANTS[random.nextInt(DECORATION_VARIANTS.length)];
}
```

- [ ] **Step 3: Add assignLootTablesWithKey helper**

Add a new private method (similar to existing `assignLootTables` but accepts a key parameter):

```java
private static void assignLootTablesWithKey(ServerLevel level, BlockPos placePos, Vec3i structureSize,
                                             RandomSource random, ResourceKey<LootTable> lootKey) {
    for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
        for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
            for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                if (be instanceof RandomizableContainerBlockEntity container) {
                    container.setLootTable(lootKey, random.nextLong());
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java
git commit -m "feat(village): add decoration placement support to VillageHouseGenerator"
```

---

### Task 4: VillageManager — Decoration Placement Logic

**Files:**
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageManager.java`

- [ ] **Step 1: Add decoration placement method**

Add after the `assignHouse` method:

```java
private static void tryPlaceDecoration(ServerLevel overworld, VillageData data) {
    if (data.getCenterPos() == null) return;

    VillageGrid grid = new VillageGrid(data, config);

    // Determine decoration type
    String structureName;
    if (data.getDecorationCount() == 0) {
        structureName = "village_well";
    } else {
        structureName = VillageHouseGenerator.selectRandomDecoration(overworld.getRandom());
    }

    // Find suitable plot (up to 10 attempts)
    int maxAttempts = 10;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
        Optional<GridPos> candidate = grid.findNextAvailablePlot();
        if (candidate.isEmpty()) {
            BeginnersDelight.LOGGER.warn("No available plots for decoration");
            return;
        }
        GridPos candidatePos = candidate.get();
        BlockPos worldPos = grid.gridToWorld(candidatePos);

        if (!VillageHouseGenerator.isSuitable(overworld, worldPos, config.getMaxHeightDifference())) {
            data.setPlotState(candidatePos, PlotState.UNSUITABLE);
            continue;
        }

        Optional<VillageHouseGenerator.PlacementResult> result =
                VillageHouseGenerator.placeDecoration(overworld, worldPos, structureName);
        if (result.isEmpty()) {
            data.setPlotState(candidatePos, PlotState.UNSUITABLE);
            continue;
        }

        VillageHouseGenerator.PlacementResult placement = result.get();

        // Record in data
        data.setPlotState(candidatePos, PlotState.DECORATION);
        data.setDoorPosition(candidatePos, placement.doorFrontPos());
        data.incrementDecorationCount();
        data.setHouseCountSinceLastDecoration(0);

        // Generate path to nearest building
        if (config.isGeneratePaths()) {
            Optional<GridPos> nearestOpt = grid.findNearestOccupiedPlot(candidatePos);
            if (nearestOpt.isPresent()) {
                BlockPos nearestDoor = data.getDoorPosition(nearestOpt.get());
                if (nearestDoor != null) {
                    VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), nearestDoor);
                }
            } else {
                BlockPos center = data.getCenterPos();
                VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), center);
            }
        }

        BeginnersDelight.LOGGER.info("Placed decoration '{}' at grid {}", structureName, candidatePos);
        return;
    }

    // All attempts failed — skip this round, counter stays >= 2 for retry on next house
    BeginnersDelight.LOGGER.warn("Failed to place decoration after {} attempts", maxAttempts);
}
```

- [ ] **Step 2: Modify assignHouse to trigger decoration**

At the end of the `assignHouse` method, after the teleport call (line ~180), add:

```java
// Check if decoration should be placed
data.incrementHouseCountSinceLastDecoration();
if (data.getHouseCountSinceLastDecoration() >= 2) {
    tryPlaceDecoration(overworld, data);
}
```

- [ ] **Step 3: Update findNearestOccupiedPlot usage**

The `VillageGrid.findNearestOccupiedPlot` currently only searches `getAllHousePositions()`. Decorations also need to be found as path targets. We need to update VillageGrid to include decoration plots.

Modify `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageGrid.java` — update `findNearestOccupiedPlot` to also search door positions (which include both houses and decorations):

```java
public Optional<GridPos> findNearestOccupiedPlot(GridPos from) {
    GridPos nearest = null;
    int minDist = Integer.MAX_VALUE;

    // Search all plots with door positions (houses and decorations)
    for (var entry : data.getAllDoorPositions().entrySet()) {
        GridPos candidate = entry.getKey();
        if (candidate.equals(from)) continue;
        int dist = from.manhattanDistanceTo(candidate);
        if (dist < minDist) {
            minDist = dist;
            nearest = candidate;
        }
    }

    return Optional.ofNullable(nearest);
}
```

- [ ] **Step 4: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageManager.java
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageGrid.java
git commit -m "feat(village): add decoration placement logic to VillageManager"
```

---

### Task 5: Loot Table JSON Files

**Files:**
- Create: `common/1.21.11/src/main/resources/data/beginnersdelight/loot_table/chests/village_storehouse.json`
- Create: `common/1.21.11/src/main/resources/data/beginnersdelight/loot_table/chests/village_farm.json`

- [ ] **Step 1: Create village_storehouse loot table**

Reference the existing `starter_house.json` for format. Create:

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:oak_planks",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "min": 4, "max": 8, "type": "minecraft:uniform" }
            }
          ]
        }
      ]
    },
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:cobblestone",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "min": 4, "max": 8, "type": "minecraft:uniform" }
            }
          ]
        }
      ]
    },
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:stick",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "min": 4, "max": 6, "type": "minecraft:uniform" }
            }
          ]
        }
      ]
    }
  ]
}
```

- [ ] **Step 2: Create village_farm loot table**

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:wheat_seeds",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "min": 3, "max": 6, "type": "minecraft:uniform" }
            }
          ]
        }
      ]
    },
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:carrot",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "min": 2, "max": 4, "type": "minecraft:uniform" }
            }
          ]
        }
      ]
    },
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:bone_meal",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "min": 2, "max": 4, "type": "minecraft:uniform" }
            }
          ]
        }
      ]
    }
  ]
}
```

- [ ] **Step 3: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add common/1.21.11/src/main/resources/data/beginnersdelight/loot_table/chests/village_storehouse.json
git add common/1.21.11/src/main/resources/data/beginnersdelight/loot_table/chests/village_farm.json
git commit -m "feat(village): add loot tables for storehouse and farm decorations"
```

---

### Task 6: NBT Structure Files (Manual Work)

**This task requires manual work in Minecraft.**

- [ ] **Step 1: Launch Minecraft client**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew :neoforge:runClient #allow-compound`

Create a new Creative Mode world.

- [ ] **Step 2: Build and export village_well**

Build a ~5x5 well (stone bricks, water source block). Use Structure Block (Save mode) to export as `beginnersdelight:village_well`. The NBT file will be saved to the world's `generated/` directory.

- [ ] **Step 3: Build and export village_shed**

Build a ~5x5 shed (bed, crafting table). Export as `beginnersdelight:village_shed`.

- [ ] **Step 4: Build and export village_storehouse**

Build a ~7x7 storehouse (chests, barrels). Export as `beginnersdelight:village_storehouse`.

- [ ] **Step 5: Build and export village_farm**

Build a ~7x7 farm (fenced area, farmland, water, crops). Include a chest for the loot table. Export as `beginnersdelight:village_farm`.

- [ ] **Step 6: Copy NBT files to resources**

Copy exported NBT files from the world's `generated/structures/` directory to:
`common/1.21.11/src/main/resources/data/beginnersdelight/structure/`

Files: `village_well.nbt`, `village_shed.nbt`, `village_storehouse.nbt`, `village_farm.nbt`

- [ ] **Step 7: Commit**

```
git add common/1.21.11/src/main/resources/data/beginnersdelight/structure/village_well.nbt
git add common/1.21.11/src/main/resources/data/beginnersdelight/structure/village_shed.nbt
git add common/1.21.11/src/main/resources/data/beginnersdelight/structure/village_storehouse.nbt
git add common/1.21.11/src/main/resources/data/beginnersdelight/structure/village_farm.nbt
git commit -m "feat(village): add NBT structure templates for decoration buildings"
```

---

### Task 7: Build Verification and Manual Testing

- [ ] **Step 1: Full build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew build #allow-compound`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew :neoforge:runClient #allow-compound`

1. Create new world (Creative, cheats ON)
2. `/beginnersdelight village enable`
3. `/beginnersdelight village test` — house 1
4. `/beginnersdelight village test` — house 2 → decoration (well) should auto-place
5. `/beginnersdelight village status` — verify house count = 2
6. `/beginnersdelight village test` — house 3
7. `/beginnersdelight village test` — house 4 → decoration (shed/storehouse/farm) should auto-place
8. Verify: decorations are connected with dirt paths
9. Verify: storehouse/farm chests have loot

- [ ] **Step 3: Commit any fixes**

---

### Task 8: All-Version Deployment

After 1.21.11 is verified, deploy changes to all other versions.

**Code changes to propagate:**
- `PlotState.java` — add `DECORATION` (all versions)
- `VillageData.java` — add fields + serialization (version-specific: Codec vs CompoundTag)
- `VillageHouseGenerator.java` — add `placeDecoration()` + loot table constants (version-specific: Identifier vs ResourceLocation, API differences)
- `VillageManager.java` — add `tryPlaceDecoration()` + modify `assignHouse()` (version-specific: teleport API, server reference)
- `VillageGrid.java` — update `findNearestOccupiedPlot()` (all versions identical change)

**Resource files to copy:**
- Loot table JSONs → all version resource directories
- NBT structure files → all version resource directories

- [ ] **Step 1: Deploy to 1.21.5-1.21.10 (Group A)**
- [ ] **Step 2: Deploy to 1.21.1-1.21.4 (Group B1)**
- [ ] **Step 3: Deploy to 1.17.1-1.20.1 (Group B2/B3)**
- [ ] **Step 4: Deploy to 1.16.5 (Group C)**
- [ ] **Step 5: Run buildAll to verify**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight/.worktrees/village-mode && ./gradlew buildAll #allow-compound`
Expected: All 15 versions BUILD SUCCESSFUL

- [ ] **Step 6: Commit all version deployments**
