# Village Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Village Mode feature that dynamically places houses for new players joining a server, forming a grid-based village with dirt path connections near world spawn.

**Architecture:** New `village` package in common module with VillageManager as orchestrator. VillageData persists state via SavedData (Codec-based for 1.21.11). Platform entry points (NeoForge/Fabric) wire events and command registration. VillageHouseGenerator contains terrain handling logic (adapted from StarterHouseGenerator patterns). Config uses Java Properties file.

**Tech Stack:** Java 21, Minecraft 1.21.11, NeoForge 21.11.x, Fabric Loader, Brigadier (command framework), Codec (serialization)

**Spec:** `docs/superpowers/specs/2026-03-25-village-mode-design.md`

---

### Task 1: GridPos Record

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/GridPos.java`

- [ ] **Step 1: Create GridPos record**

```java
package com.beginnersdelight.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Represents a position on the village grid (not world coordinates).
 */
public record GridPos(int x, int z) {

    public static final Codec<GridPos> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("x").forGetter(GridPos::x),
                    Codec.INT.fieldOf("z").forGetter(GridPos::z)
            ).apply(instance, GridPos::new)
    );

    /**
     * Manhattan distance from origin.
     */
    public int manhattanDistance() {
        return Math.abs(x) + Math.abs(z);
    }

    /**
     * Manhattan distance to another grid position.
     */
    public int manhattanDistanceTo(GridPos other) {
        return Math.abs(x - other.x) + Math.abs(z - other.z);
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/GridPos.java
git commit -m "feat(village): add GridPos record for grid coordinate system"
```

---

### Task 2: VillageConfig

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageConfig.java`

- [ ] **Step 1: Create VillageConfig**

```java
package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages village mode configuration stored in a properties file.
 * Loaded at server startup; changes via command are written immediately.
 */
public class VillageConfig {

    private static final String FILE_NAME = "beginnersdelight-village.properties";

    private int plotSize = 20;
    private int maxHeightDifference = 10;
    private boolean generatePaths = true;
    private boolean respawnAtHouse = true;

    private Path configPath;

    public void load(Path configDir) {
        this.configPath = configDir.resolve(FILE_NAME);
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                Properties props = new Properties();
                props.load(in);
                plotSize = Integer.parseInt(props.getProperty("plotSize", "20"));
                maxHeightDifference = Integer.parseInt(props.getProperty("maxHeightDifference", "10"));
                generatePaths = Boolean.parseBoolean(props.getProperty("generatePaths", "true"));
                respawnAtHouse = Boolean.parseBoolean(props.getProperty("respawnAtHouse", "true"));
            } catch (IOException e) {
                BeginnersDelight.LOGGER.warn("Failed to load village config, using defaults", e);
            }
        } else {
            save();
        }
    }

    public void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            Properties props = new Properties();
            props.setProperty("plotSize", String.valueOf(plotSize));
            props.setProperty("maxHeightDifference", String.valueOf(maxHeightDifference));
            props.setProperty("generatePaths", String.valueOf(generatePaths));
            props.setProperty("respawnAtHouse", String.valueOf(respawnAtHouse));
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "Beginner's Delight - Village Mode Configuration");
            }
        } catch (IOException e) {
            BeginnersDelight.LOGGER.error("Failed to save village config", e);
        }
    }

    public boolean set(String key, String value) {
        switch (key) {
            case "plotSize" -> plotSize = Integer.parseInt(value);
            case "maxHeightDifference" -> maxHeightDifference = Integer.parseInt(value);
            case "generatePaths" -> generatePaths = Boolean.parseBoolean(value);
            case "respawnAtHouse" -> respawnAtHouse = Boolean.parseBoolean(value);
            default -> { return false; }
        }
        save();
        return true;
    }

    public int getPlotSize() { return plotSize; }
    public int getMaxHeightDifference() { return maxHeightDifference; }
    public boolean isGeneratePaths() { return generatePaths; }
    public boolean isRespawnAtHouse() { return respawnAtHouse; }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageConfig.java
git commit -m "feat(village): add VillageConfig for properties-based configuration"
```

---

### Task 3: VillageData (SavedData)

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageData.java`

**Context:** Follow the Codec-based pattern from `common/1.21.11/src/main/java/com/beginnersdelight/worldgen/StarterHouseData.java`. Use `SavedDataType` record and `RecordCodecBuilder`.

- [ ] **Step 1: Create PlotState enum**

Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/PlotState.java`

```java
package com.beginnersdelight.village;

public enum PlotState {
    RESERVED,
    AVAILABLE,
    OCCUPIED,
    UNSUITABLE
}
```

- [ ] **Step 2: Create VillageData**

```java
package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists village mode state: enabled flag, plot states,
 * player-house bindings, and house/door positions.
 */
public class VillageData extends SavedData {

    private static final String DATA_NAME = BeginnersDelight.MOD_ID + "_village";

    /**
     * Codec for serializing a single plot entry (GridPos + PlotState).
     */
    private static final Codec<PlotEntry> PLOT_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    GridPos.CODEC.fieldOf("pos").forGetter(PlotEntry::pos),
                    Codec.STRING.fieldOf("state").forGetter(e -> e.state().name())
            ).apply(instance, (pos, state) -> new PlotEntry(pos, PlotState.valueOf(state)))
    );

    /**
     * Codec for serializing a single player-house binding (UUID + GridPos).
     */
    private static final Codec<PlayerHouseEntry> PLAYER_HOUSE_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerHouseEntry::uuid),
                    GridPos.CODEC.fieldOf("grid_pos").forGetter(PlayerHouseEntry::gridPos)
            ).apply(instance, PlayerHouseEntry::new)
    );

    /**
     * Codec for serializing a single grid-to-BlockPos mapping.
     */
    private static final Codec<GridBlockPosEntry> GRID_BLOCK_POS_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    GridPos.CODEC.fieldOf("grid_pos").forGetter(GridBlockPosEntry::gridPos),
                    BlockPos.CODEC.fieldOf("block_pos").forGetter(GridBlockPosEntry::blockPos)
            ).apply(instance, GridBlockPosEntry::new)
    );

    public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("enabled").forGetter(d -> d.enabled),
                    BlockPos.CODEC.optionalFieldOf("center_pos").forGetter(d -> Optional.ofNullable(d.centerPos)),
                    PLOT_ENTRY_CODEC.listOf().fieldOf("plots").forGetter(d ->
                            d.plots.entrySet().stream()
                                    .map(e -> new PlotEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    PLAYER_HOUSE_ENTRY_CODEC.listOf().fieldOf("player_houses").forGetter(d ->
                            d.playerHouses.entrySet().stream()
                                    .map(e -> new PlayerHouseEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    GRID_BLOCK_POS_ENTRY_CODEC.listOf().fieldOf("house_positions").forGetter(d ->
                            d.housePositions.entrySet().stream()
                                    .map(e -> new GridBlockPosEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList())),
                    GRID_BLOCK_POS_ENTRY_CODEC.listOf().fieldOf("door_positions").forGetter(d ->
                            d.doorPositions.entrySet().stream()
                                    .map(e -> new GridBlockPosEntry(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList()))
            ).apply(instance, (enabled, centerPos, plots, playerHouses, housePositions, doorPositions) -> {
                VillageData data = new VillageData();
                data.enabled = enabled;
                data.centerPos = centerPos.orElse(null);
                plots.forEach(e -> data.plots.put(e.pos(), e.state()));
                playerHouses.forEach(e -> data.playerHouses.put(e.uuid(), e.gridPos()));
                housePositions.forEach(e -> data.housePositions.put(e.gridPos(), e.blockPos()));
                doorPositions.forEach(e -> data.doorPositions.put(e.gridPos(), e.blockPos()));
                return data;
            })
    );

    public static final SavedDataType<VillageData> TYPE = new SavedDataType<>(
            DATA_NAME,
            VillageData::new,
            CODEC,
            null
    );

    private boolean enabled;
    private BlockPos centerPos;
    private final Map<GridPos, PlotState> plots = new HashMap<>();
    private final Map<UUID, GridPos> playerHouses = new HashMap<>();
    private final Map<GridPos, BlockPos> housePositions = new HashMap<>();
    private final Map<GridPos, BlockPos> doorPositions = new HashMap<>();

    public VillageData() {
        this.enabled = false;
        this.centerPos = null;
    }

    // --- Getters and setters ---

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setDirty();
    }

    public BlockPos getCenterPos() { return centerPos; }

    public void setCenterPos(BlockPos centerPos) {
        this.centerPos = centerPos;
        setDirty();
    }

    public PlotState getPlotState(GridPos pos) {
        return plots.getOrDefault(pos, PlotState.AVAILABLE);
    }

    public void setPlotState(GridPos pos, PlotState state) {
        plots.put(pos, state);
        setDirty();
    }

    public boolean hasHouse(UUID playerUuid) {
        return playerHouses.containsKey(playerUuid);
    }

    public GridPos getPlayerHouse(UUID playerUuid) {
        return playerHouses.get(playerUuid);
    }

    public void setPlayerHouse(UUID playerUuid, GridPos gridPos) {
        playerHouses.put(playerUuid, gridPos);
        setDirty();
    }

    public BlockPos getHousePosition(GridPos gridPos) {
        return housePositions.get(gridPos);
    }

    public void setHousePosition(GridPos gridPos, BlockPos worldPos) {
        housePositions.put(gridPos, worldPos);
        setDirty();
    }

    public BlockPos getDoorPosition(GridPos gridPos) {
        return doorPositions.get(gridPos);
    }

    public void setDoorPosition(GridPos gridPos, BlockPos doorPos) {
        doorPositions.put(gridPos, doorPos);
        setDirty();
    }

    public Map<GridPos, BlockPos> getAllHousePositions() {
        return Map.copyOf(housePositions);
    }

    public Map<GridPos, BlockPos> getAllDoorPositions() {
        return Map.copyOf(doorPositions);
    }

    public int getHouseCount() {
        return (int) plots.values().stream().filter(s -> s == PlotState.OCCUPIED).count();
    }

    public int getPlayerCount() {
        return playerHouses.size();
    }

    public static VillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // --- Helper records for Codec serialization ---

    private record PlotEntry(GridPos pos, PlotState state) {}
    private record PlayerHouseEntry(UUID uuid, GridPos gridPos) {}
    private record GridBlockPosEntry(GridPos gridPos, BlockPos blockPos) {}
}
```

- [ ] **Step 3: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/PlotState.java
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageData.java
git commit -m "feat(village): add VillageData SavedData with Codec serialization"
```

---

### Task 4: VillageGrid

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageGrid.java`

**Context:** Manages the spiral grid layout. The spiral order starts from grid position (1,0) and spirals outward counter-clockwise. Center (0,0) is reserved for the starter house.

- [ ] **Step 1: Create VillageGrid**

```java
package com.beginnersdelight.village;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Manages the village grid layout. Determines which plot to assign next
 * using a spiral pattern outward from the center.
 */
public class VillageGrid {

    private final VillageData data;
    private final VillageConfig config;

    public VillageGrid(VillageData data, VillageConfig config) {
        this.data = data;
        this.config = config;
    }

    /**
     * Initializes the grid center and reserves the center plot for the starter house.
     */
    public void initialize(BlockPos worldCenter) {
        data.setCenterPos(worldCenter);
        data.setPlotState(new GridPos(0, 0), PlotState.RESERVED);
    }

    /**
     * Finds the next available plot in spiral order.
     * Returns empty if no suitable plot is found within the search limit.
     */
    public Optional<GridPos> findNextAvailablePlot() {
        // Search up to 200 plots in spiral order.
        // Generates positions in spiral: (1,0), (1,-1), (0,-1), (-1,-1), (-1,0),
        // (-1,1), (0,1), (1,1), (2,1), (2,0), (2,-1), (2,-2), ...
        int maxSearch = 200;
        int x = 0, z = 0;
        int dx = 1, dz = 0;
        int segmentLength = 1;
        int segmentPassed = 0;
        int turnsMade = 0;

        // Move to first position (skip center which is RESERVED)
        x += dx;
        z += dz;

        for (int i = 0; i < maxSearch; i++) {
            GridPos pos = new GridPos(x, z);
            PlotState state = data.getPlotState(pos);
            if (state == PlotState.AVAILABLE) {
                return Optional.of(pos);
            }

            // Advance spiral
            segmentPassed++;
            if (segmentPassed == segmentLength) {
                segmentPassed = 0;
                // Turn: (1,0) → (0,-1) → (-1,0) → (0,1)
                int temp = dx;
                dx = dz;
                dz = -temp;
                turnsMade++;
                if (turnsMade % 2 == 0) {
                    segmentLength++;
                }
            }
            x += dx;
            z += dz;
        }
        return Optional.empty();
    }

    /**
     * Converts a grid position to world coordinates (corner of the plot).
     */
    public BlockPos gridToWorld(GridPos gridPos) {
        BlockPos center = data.getCenterPos();
        int plotSize = config.getPlotSize();
        return new BlockPos(
                center.getX() + (gridPos.x() * plotSize),
                center.getY(),
                center.getZ() + (gridPos.z() * plotSize)
        );
    }

    /**
     * Finds the nearest occupied plot to the given position (by Manhattan distance).
     * Used for path connection targets.
     */
    public Optional<GridPos> findNearestOccupiedPlot(GridPos from) {
        GridPos nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (var entry : data.getAllHousePositions().entrySet()) {
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
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageGrid.java
git commit -m "feat(village): add VillageGrid with spiral plot allocation"
```

---

### Task 5: VillageHouseGenerator

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java`

**Context:** Adapts terrain handling from `StarterHouseGenerator` (lines 172-683 in `common/1.21.11/.../StarterHouseGenerator.java`). Since those methods are private, this class contains equivalent logic. Key differences from StarterHouseGenerator: (1) does not set world spawn, (2) checks height difference to determine suitability, (3) returns placement result with interior and door positions.

- [ ] **Step 1: Create VillageHouseGenerator**

```java
package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places village house structures with terrain handling.
 * Adapted from StarterHouseGenerator's placement logic.
 */
public class VillageHouseGenerator {

    private static final ResourceKey<LootTable> STARTER_HOUSE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/starter_house"));

    private static final String[] STRUCTURE_VARIANTS = {
            "starter_house1", "starter_house2", "starter_house3",
            "starter_house4", "starter_house5", "starter_house6"
    };

    /**
     * Result of a successful house placement.
     */
    public record PlacementResult(BlockPos interiorPos, BlockPos doorFrontPos) {}

    /**
     * Checks whether a plot location is suitable for house placement.
     * Returns false if height difference exceeds the threshold or center is underwater.
     */
    public static boolean isSuitable(ServerLevel level, BlockPos plotCenter, int maxHeightDiff) {
        // Check if center is underwater: scan from sea level upward for water
        int centerX = plotCenter.getX();
        int centerZ = plotCenter.getZ();
        int seaLevel = level.getSeaLevel();
        int centerY = findGroundY(level, centerX, centerZ);
        if (centerY == -1) return false;

        // If ground level is below sea level, check for water above ground
        if (centerY <= seaLevel) {
            for (int y = centerY; y <= seaLevel; y++) {
                BlockState state = level.getBlockState(new BlockPos(centerX, y, centerZ));
                if (!state.getFluidState().isEmpty()) return false;
            }
        }

        // Sample corners to check height difference
        int halfSize = 7; // approximate half of structure footprint
        int[][] corners = {
                {centerX - halfSize, centerZ - halfSize},
                {centerX + halfSize, centerZ - halfSize},
                {centerX - halfSize, centerZ + halfSize},
                {centerX + halfSize, centerZ + halfSize}
        };
        int minY = centerY, maxY = centerY;
        for (int[] corner : corners) {
            int y = findGroundY(level, corner[0], corner[1]);
            if (y == -1) return false;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        return (maxY - minY) <= maxHeightDiff;
    }

    /**
     * Places a randomly selected house structure at the given plot center.
     * Returns the placement result with interior and door positions, or empty if placement failed.
     */
    public static Optional<PlacementResult> place(ServerLevel level, BlockPos plotCenter) {
        StructureTemplateManager templateManager = level.getStructureManager();
        RandomSource random = level.getRandom();

        String variant = STRUCTURE_VARIANTS[random.nextInt(STRUCTURE_VARIANTS.length)];
        Identifier structureId = Identifier.fromNamespaceAndPath(BeginnersDelight.MOD_ID, variant);

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
            BeginnersDelight.LOGGER.warn("Could not find suitable surface position for village house");
            return Optional.empty();
        }

        BeginnersDelight.LOGGER.info("Placing village house '{}' at {}", variant, placePos);

        Vec3i size = template.getSize();
        removeMobs(level, placePos, size);
        clearVegetation(level, placePos, size);
        template.placeInWorld(level, placePos, placePos, settings, random, 2 | 16);
        removeDroppedItems(level, placePos, size);
        assignLootTables(level, placePos, size, random);
        fillFoundation(level, placePos, size);
        blendSurroundingTerrain(level, placePos, size);
        removeDroppedItems(level, placePos, size);

        // Interior position: center of structure, one block above floor
        BlockPos interiorPos = placePos.offset(size.getX() / 2, 1, size.getZ() / 2);

        // Door front position: south side center, one block outside the structure
        BlockPos doorFrontPos = new BlockPos(
                placePos.getX() + size.getX() / 2,
                placePos.getY(),
                placePos.getZ() + size.getZ());

        return Optional.of(new PlacementResult(interiorPos, doorFrontPos));
    }

    // --- Terrain handling methods (adapted from StarterHouseGenerator) ---

    private static BlockPos findSurfacePosition(ServerLevel level, BlockPos center, Vec3i structureSize) {
        int halfX = structureSize.getX() / 2;
        int halfZ = structureSize.getZ() / 2;
        int startX = center.getX() - halfX;
        int startZ = center.getZ() - halfZ;
        int endX = startX + structureSize.getX() - 1;
        int endZ = startZ + structureSize.getZ() - 1;

        int[][] samplePoints = {
                {center.getX(), center.getZ()},
                {startX, startZ}, {endX, startZ},
                {startX, endZ}, {endX, endZ}
        };

        int resultY = Integer.MAX_VALUE;
        for (int[] point : samplePoints) {
            int y = findGroundY(level, point[0], point[1]);
            if (y == -1) return null;
            if (y < resultY) resultY = y;
        }
        if (resultY == Integer.MAX_VALUE) return null;
        resultY = Math.max(resultY, level.getSeaLevel());
        return new BlockPos(startX, resultY, startZ);
    }

    private static int findGroundY(ServerLevel level, int x, int z) {
        int maxY = level.getHeight() - 1;
        int minY = level.getMinY();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || state.is(BlockTags.REPLACEABLE_BY_TREES)
                    || isThinGroundCover(state)) {
                continue;
            }
            return y + 1;
        }
        return -1;
    }

    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.PINK_PETALS) || state.is(Blocks.PALE_MOSS_CARPET);
    }

    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS) || isThinGroundCover(state);
    }

    private static void clearVegetation(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int extend = 6; // margin(2) + blendRadius(3) + 1
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;
        int minY = placePos.getY();
        int maxY = placePos.getY() + structureSize.getY() + 10;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && isVegetation(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    private static void removeMobs(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int extend = 6;
        AABB area = new AABB(
                placePos.getX() - extend, placePos.getY() - 10, placePos.getZ() - extend,
                placePos.getX() + structureSize.getX() + extend,
                placePos.getY() + structureSize.getY() + 10,
                placePos.getZ() + structureSize.getZ() + extend);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            mob.discard();
        }
    }

    private static void removeDroppedItems(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int extend = 6;
        AABB area = new AABB(
                placePos.getX() - extend, placePos.getY() - 10, placePos.getZ() - extend,
                placePos.getX() + structureSize.getX() + extend,
                placePos.getY() + structureSize.getY() + 10,
                placePos.getZ() + structureSize.getZ() + extend);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            item.discard();
        }
    }

    private static void assignLootTables(ServerLevel level, BlockPos placePos, Vec3i structureSize,
                                          RandomSource random) {
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                    if (be instanceof RandomizableContainerBlockEntity container) {
                        container.setLootTable(STARTER_HOUSE_LOOT, random.nextLong());
                    }
                }
            }
        }
    }

    private static void fillFoundation(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;
        int strMinX = placePos.getX();
        int strMaxX = placePos.getX() + structureSize.getX();
        int strMinZ = placePos.getZ();
        int strMaxZ = placePos.getZ() + structureSize.getZ();

        // Phase 1: Clear above floor and convert exposed dirt to grass
        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) continue;
                boolean inMargin = x < strMinX || x >= strMaxX || z < strMinZ || z >= strMaxZ;
                int clearFrom = inMargin ? floorY : floorY + structureSize.getY();
                for (int y = clearFrom; y < floorY + structureSize.getY() + 10; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir()) {
                        if (inMargin && isThinGroundCover(existing)) continue;
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                if (inMargin) {
                    BlockPos surfacePos = new BlockPos(x, floorY - 1, z);
                    if (level.getBlockState(surfacePos).is(Blocks.DIRT)) {
                        level.setBlock(surfacePos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    }
                }
            }
        }

        // Phase 2: Detect dominant surface block
        BlockState surfaceBlock = mapToSurfaceBlock(detectDominantSurfaceBlock(level, placePos, structureSize, margin));
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        // Phase 3: Fill foundation downward
        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) continue;
                for (int y = floorY - 1; y >= floorY - 10; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && existing.getFluidState().isEmpty()) break;
                    level.setBlock(pos, (y == floorY - 1) ? surfaceBlock : subsurfaceBlock, 2);
                }
            }
        }
    }

    private static void blendSurroundingTerrain(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;
        int blendRadius = 3;
        BlockState surfaceBlock = mapToSurfaceBlock(detectDominantSurfaceBlock(level, placePos, structureSize, margin));
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        int innerMinX = placePos.getX() - margin;
        int innerMaxX = placePos.getX() + structureSize.getX() + margin - 1;
        int innerMinZ = placePos.getZ() - margin;
        int innerMaxZ = placePos.getZ() + structureSize.getZ() + margin - 1;

        for (int x = innerMinX - blendRadius; x <= innerMaxX + blendRadius; x++) {
            for (int z = innerMinZ - blendRadius; z <= innerMaxZ + blendRadius; z++) {
                if (x >= innerMinX && x <= innerMaxX && z >= innerMinZ && z <= innerMaxZ) continue;
                int distX = 0;
                if (x < innerMinX) distX = innerMinX - x;
                else if (x > innerMaxX) distX = x - innerMaxX;
                int distZ = 0;
                if (z < innerMinZ) distZ = innerMinZ - z;
                else if (z > innerMaxZ) distZ = z - innerMaxZ;
                int dist = Math.max(distX, distZ);
                if (dist <= 0 || dist > blendRadius) continue;

                int naturalY = findGroundY(level, x, z);
                if (naturalY == -1) continue;
                double ratio = (double) dist / blendRadius;
                int targetY = floorY + (int) Math.round((naturalY - floorY) * ratio);

                if (naturalY > targetY) {
                    for (int y = targetY; y < naturalY; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    if (targetY > level.getMinY()) {
                        level.setBlock(new BlockPos(x, targetY - 1, z), surfaceBlock, 2);
                    }
                } else if (naturalY < targetY) {
                    for (int y = naturalY; y < targetY; y++) {
                        level.setBlock(new BlockPos(x, y, z), (y == targetY - 1) ? surfaceBlock : subsurfaceBlock, 2);
                    }
                }
            }
        }
    }

    private static BlockState detectDominantSurfaceBlock(ServerLevel level, BlockPos placePos,
                                                          Vec3i structureSize, int margin) {
        Map<net.minecraft.world.level.block.Block, Integer> counts = new HashMap<>();
        int sampleY = placePos.getY();
        int minX = placePos.getX() - margin - 1;
        int maxX = placePos.getX() + structureSize.getX() + margin;
        int minZ = placePos.getZ() - margin - 1;
        int maxZ = placePos.getZ() + structureSize.getZ() + margin;
        for (int x = minX; x <= maxX; x++) {
            sampleColumn(level, x, minZ, sampleY, counts);
            sampleColumn(level, x, maxZ, sampleY, counts);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            sampleColumn(level, minX, z, sampleY, counts);
            sampleColumn(level, maxX, z, sampleY, counts);
        }
        net.minecraft.world.level.block.Block dominant = null;
        int maxCount = 0;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant != null ? dominant.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
    }

    private static void sampleColumn(ServerLevel level, int x, int z, int startY,
                                      Map<net.minecraft.world.level.block.Block, Integer> counts) {
        for (int y = startY; y >= startY - 5; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || isThinGroundCover(state)) continue;
            counts.merge(state.getBlock(), 1, Integer::sum);
            return;
        }
    }

    private static boolean isOutsideChamfer(int x, int z, int strMinX, int strMaxX,
                                             int strMinZ, int strMaxZ, int margin) {
        int distX = 0;
        if (x < strMinX) distX = strMinX - x;
        else if (x >= strMaxX) distX = x - strMaxX + 1;
        int distZ = 0;
        if (z < strMinZ) distZ = strMinZ - z;
        else if (z >= strMaxZ) distZ = z - strMaxZ + 1;
        return distX + distZ > 2 * margin - 1;
    }

    private static BlockState mapToSurfaceBlock(BlockState detected) {
        var block = detected.getBlock();
        if (block == Blocks.SAND) return Blocks.SANDSTONE.defaultBlockState();
        if (block == Blocks.RED_SAND) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (block == Blocks.GRAVEL) return Blocks.STONE.defaultBlockState();
        return detected;
    }

    private static BlockState mapToSubsurfaceBlock(BlockState surfaceBlock) {
        if (surfaceBlock.is(Blocks.GRASS_BLOCK)) return Blocks.DIRT.defaultBlockState();
        return surfaceBlock;
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java
git commit -m "feat(village): add VillageHouseGenerator for structure placement"
```

---

### Task 6: VillagePathGenerator

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillagePathGenerator.java`

- [ ] **Step 1: Create VillagePathGenerator**

```java
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

        // Trace along X-axis
        int stepX = x < targetX ? 1 : -1;
        while (x != targetX) {
            placePathBlock(level, x, z);
            x += stepX;
        }

        // Trace along Z-axis
        int stepZ = z < targetZ ? 1 : -1;
        while (z != targetZ) {
            placePathBlock(level, x, z);
            z += stepZ;
        }

        // Place at final position
        placePathBlock(level, x, z);
    }

    /**
     * Finds the ground surface at the given XZ and replaces it with Dirt Path
     * if it is a suitable block (grass or dirt).
     */
    private static void placePathBlock(ServerLevel level, int x, int z) {
        int y = findPathSurface(level, x, z);
        if (y == -1) return;

        BlockPos surfacePos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(surfacePos);

        // Only replace grass blocks and dirt with dirt path
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
            level.setBlock(surfacePos, Blocks.DIRT_PATH.defaultBlockState(), 2);

            // Remove vegetation above the path
            BlockPos above = surfacePos.above();
            BlockState aboveState = level.getBlockState(above);
            if (isRemovableVegetation(aboveState)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /**
     * Scans downward to find the surface block suitable for path placement.
     * Returns the Y of the surface block, or -1 if not found.
     */
    private static int findPathSurface(ServerLevel level, int x, int z) {
        int maxY = level.getHeight() - 1;
        int minY = level.getMinY();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty()) return -1; // Water/lava — skip
            if (isRemovableVegetation(state)) continue;
            // Found solid ground
            return y;
        }
        return -1;
    }

    private static boolean isRemovableVegetation(BlockState state) {
        return state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.SHORT_GRASS);
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillagePathGenerator.java
git commit -m "feat(village): add VillagePathGenerator for dirt path connections"
```

---

### Task 7: VillageManager

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageManager.java`

**Context:** Orchestrates village operations. Called from platform event listeners. Manages config loading and grid initialization.

- [ ] **Step 1: Create VillageManager**

```java
package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates village mode operations.
 * Called from platform-specific event listeners.
 */
public class VillageManager {

    private static final VillageConfig config = new VillageConfig();

    /**
     * Initializes the village system on server start.
     * Loads config and initializes the grid center if village mode is enabled
     * but no center has been set yet.
     */
    public static void onServerStarted(MinecraftServer server) {
        Path configDir = server.getServerDirectory().resolve("config");
        config.load(configDir);

        ServerLevel overworld = server.overworld();
        VillageData data = VillageData.get(overworld);

        if (data.isEnabled() && data.getCenterPos() == null) {
            initializeGrid(overworld, data);
        }
    }

    /**
     * Handles a player joining the server.
     * If village mode is enabled and the player has no house, assigns one.
     * If the player already has a house, teleports them to it.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;

        if (data.hasHouse(player.getUUID())) {
            // Returning player — teleport to existing house
            GridPos gridPos = data.getPlayerHouse(player.getUUID());
            BlockPos housePos = data.getHousePosition(gridPos);
            if (housePos != null) {
                player.teleportTo(overworld,
                        housePos.getX() + 0.5, housePos.getY(), housePos.getZ() + 0.5,
                        Set.of(), player.getYRot(), player.getXRot(), false);
                BeginnersDelight.LOGGER.debug("Teleported returning player {} to village house",
                        player.getName().getString());
            }
            return;
        }

        // New player — assign a house
        assignHouse(overworld, player, data);
    }

    /**
     * Handles player respawn after death.
     * If respawnAtHouse is enabled and the player has no bed, teleport to their house.
     */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (player.getRespawnConfig() != null) return;
        if (!config.isRespawnAtHouse()) return;

        ServerLevel overworld = player.level().getServer().overworld();
        VillageData data = VillageData.get(overworld);

        if (!data.isEnabled()) return;
        if (!data.hasHouse(player.getUUID())) return;

        GridPos gridPos = data.getPlayerHouse(player.getUUID());
        BlockPos housePos = data.getHousePosition(gridPos);
        if (housePos != null) {
            player.teleportTo(overworld,
                    housePos.getX() + 0.5, housePos.getY(), housePos.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot(), false);
            BeginnersDelight.LOGGER.debug("Respawned player {} at village house",
                    player.getName().getString());
        }
    }

    public static VillageConfig getConfig() {
        return config;
    }

    private static void initializeGrid(ServerLevel overworld, VillageData data) {
        BlockPos spawnPos = overworld.getRespawnData().pos();
        VillageGrid grid = new VillageGrid(data, config);
        grid.initialize(spawnPos);
        BeginnersDelight.LOGGER.info("Village grid initialized at center: {}", spawnPos);
    }

    private static void assignHouse(ServerLevel overworld, ServerPlayer player, VillageData data) {
        if (data.getCenterPos() == null) {
            initializeGrid(overworld, data);
        }

        VillageGrid grid = new VillageGrid(data, config);

        // Find next available plot, checking suitability
        Optional<GridPos> plotOpt = Optional.empty();
        int attempts = 0;
        int maxAttempts = 200;
        while (attempts < maxAttempts) {
            Optional<GridPos> candidate = grid.findNextAvailablePlot();
            if (candidate.isEmpty()) {
                BeginnersDelight.LOGGER.warn("No available plots for village house");
                return;
            }
            GridPos candidatePos = candidate.get();
            BlockPos worldPos = grid.gridToWorld(candidatePos);

            if (VillageHouseGenerator.isSuitable(overworld, worldPos, config.getMaxHeightDifference())) {
                plotOpt = candidate;
                break;
            } else {
                data.setPlotState(candidatePos, PlotState.UNSUITABLE);
                attempts++;
            }
        }

        if (plotOpt.isEmpty()) {
            BeginnersDelight.LOGGER.warn("No suitable plots found after {} attempts for player {}",
                    maxAttempts, player.getName().getString());
            return;
        }

        GridPos gridPos = plotOpt.get();
        BlockPos plotWorldPos = grid.gridToWorld(gridPos);

        // Place the house
        Optional<VillageHouseGenerator.PlacementResult> result =
                VillageHouseGenerator.place(overworld, plotWorldPos);
        if (result.isEmpty()) {
            data.setPlotState(gridPos, PlotState.UNSUITABLE);
            BeginnersDelight.LOGGER.warn("Failed to place village house for player {}",
                    player.getName().getString());
            return;
        }

        VillageHouseGenerator.PlacementResult placement = result.get();

        // Record in data
        data.setPlotState(gridPos, PlotState.OCCUPIED);
        data.setPlayerHouse(player.getUUID(), gridPos);
        data.setHousePosition(gridPos, placement.interiorPos());
        data.setDoorPosition(gridPos, placement.doorFrontPos());

        // Generate path to nearest existing house
        if (config.isGeneratePaths()) {
            Optional<GridPos> nearestOpt = grid.findNearestOccupiedPlot(gridPos);
            if (nearestOpt.isPresent()) {
                BlockPos nearestDoor = data.getDoorPosition(nearestOpt.get());
                if (nearestDoor != null) {
                    VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), nearestDoor);
                }
            } else {
                // First house — connect to village center
                BlockPos center = data.getCenterPos();
                VillagePathGenerator.generatePath(overworld, placement.doorFrontPos(), center);
            }
        }

        // Teleport player to their new house
        player.teleportTo(overworld,
                placement.interiorPos().getX() + 0.5,
                placement.interiorPos().getY(),
                placement.interiorPos().getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), false);
        BeginnersDelight.LOGGER.info("Assigned village house to player {} at grid {}",
                player.getName().getString(), gridPos);
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageManager.java
git commit -m "feat(village): add VillageManager orchestrator"
```

---

### Task 8: VillageCommand

**Files:**
- Create: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageCommand.java`

**Context:** First command registration in this project. Uses Brigadier (Minecraft's built-in command framework). Commands: `/beginnersdelight village enable|disable|status|set`.

- [ ] **Step 1: Create VillageCommand**

```java
package com.beginnersdelight.village;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Registers /beginnersdelight village commands.
 */
public class VillageCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("beginnersdelight")
                        .then(Commands.literal("village")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("enable")
                                        .executes(ctx -> enable(ctx.getSource())))
                                .then(Commands.literal("disable")
                                        .executes(ctx -> disable(ctx.getSource())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> status(ctx.getSource())))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.word())
                                                        .executes(ctx -> set(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "key"),
                                                                StringArgumentType.getString(ctx, "value")))))))
        );
    }

    private static int enable(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        VillageData data = VillageData.get(overworld);
        data.setEnabled(true);

        // Initialize grid if not yet done
        if (data.getCenterPos() == null) {
            VillageGrid grid = new VillageGrid(data, VillageManager.getConfig());
            grid.initialize(overworld.getRespawnData().pos());
        }

        source.sendSuccess(() -> Component.literal("Village mode enabled"), true);
        return 1;
    }

    private static int disable(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        VillageData data = VillageData.get(overworld);
        data.setEnabled(false);
        source.sendSuccess(() -> Component.literal("Village mode disabled (data preserved)"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        VillageData data = VillageData.get(overworld);
        VillageConfig config = VillageManager.getConfig();

        String statusText = String.format(
                "Village Mode: %s\nHouses: %d\nPlayers: %d\nPlot size: %d\nMax height diff: %d\nPaths: %s\nRespawn at house: %s",
                data.isEnabled() ? "enabled" : "disabled",
                data.getHouseCount(),
                data.getPlayerCount(),
                config.getPlotSize(),
                config.getMaxHeightDifference(),
                config.isGeneratePaths() ? "on" : "off",
                config.isRespawnAtHouse() ? "on" : "off"
        );
        source.sendSuccess(() -> Component.literal(statusText), false);
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String value) {
        VillageConfig config = VillageManager.getConfig();
        try {
            if (config.set(key, value)) {
                source.sendSuccess(() -> Component.literal("Set " + key + " = " + value), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("Unknown config key: " + key
                        + ". Valid keys: plotSize, maxHeightDifference, generatePaths, respawnAtHouse"));
                return 0;
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Invalid value for " + key + ": " + value));
            return 0;
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add common/1.21.11/src/main/java/com/beginnersdelight/village/VillageCommand.java
git commit -m "feat(village): add VillageCommand for in-game village management"
```

---

### Task 9: Platform Integration — NeoForge

**Files:**
- Modify: `neoforge/base/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java`

**Context:** Add event listeners for VillageManager and command registration. The current file is 31 lines. Need to add imports and event bindings.

- [ ] **Step 1: Add VillageManager and VillageCommand integration**

Add these imports:
```java
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
```

Add these event listeners inside the constructor, after the existing `bus.addListener` calls:

```java
bus.addListener((ServerStartedEvent event) ->
        VillageManager.onServerStarted(event.getServer()));
bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
    if (event.getEntity() instanceof ServerPlayer serverPlayer)
        VillageManager.onPlayerJoin(serverPlayer);
});
bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
    if (event.getEntity() instanceof ServerPlayer serverPlayer)
        VillageManager.onPlayerRespawn(serverPlayer);
});
bus.addListener((RegisterCommandsEvent event) ->
        VillageCommand.register(event.getDispatcher()));
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add neoforge/base/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java
git commit -m "feat(village): integrate village mode into NeoForge entry point"
```

---

### Task 10: Platform Integration — Fabric

**Files:**
- Modify: `fabric/base/src/main/java/com/beginnersdelight/fabric/BeginnersDelightFabric.java`

**Context:** Add event listeners for VillageManager and command registration. The current file is 23 lines. Need to add imports and event callbacks.

- [ ] **Step 1: Add VillageManager and VillageCommand integration**

Add these imports:
```java
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
```

Add these event registrations inside `onInitialize()`, after the existing registrations:

```java
ServerLifecycleEvents.SERVER_STARTED.register(VillageManager::onServerStarted);
ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
        VillageManager.onPlayerJoin(handler.player));
ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
        VillageManager.onPlayerRespawn(newPlayer));
CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        VillageCommand.register(dispatcher));
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add fabric/base/src/main/java/com/beginnersdelight/fabric/BeginnersDelightFabric.java
git commit -m "feat(village): integrate village mode into Fabric entry point"
```

---

### Task 11: Full Build Verification and Manual Test Plan

- [ ] **Step 1: Full build**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew build`
Expected: BUILD SUCCESSFUL for both NeoForge and Fabric

- [ ] **Step 2: Manual test checklist (NeoForge)**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew :neoforge:runClient`

1. Create a new world
2. Run `/beginnersdelight village status` — should show "disabled"
3. Run `/beginnersdelight village enable` — should show "Village mode enabled"
4. Run `/beginnersdelight village status` — should show "enabled", 0 houses
5. Open to LAN, have another player join — verify house is placed and player is teleported
6. Check that a dirt path connects the house to village center
7. Run `/beginnersdelight village set plotSize 25` — should confirm change
8. Run `/beginnersdelight village disable` — should show "data preserved"
9. Re-enable and verify data is intact

- [ ] **Step 3: Manual test checklist (Fabric)**

Run: `cd /Users/ksoichiro/src/github.com/ksoichiro/BeginnersDelight && ./gradlew :fabric:runClient`

Repeat the same test steps as NeoForge.

- [ ] **Step 4: Verify config file is created**

Check: `<game-dir>/config/beginnersdelight-village.properties` exists with correct default values.

- [ ] **Step 5: Verify respawn behavior**

1. Enable village mode, join as new player, get house assigned
2. Die (e.g., /kill) — verify respawn at own house (not world spawn)
3. Set a bed spawn — verify next respawn uses bed instead

- [ ] **Step 6: Commit any fixes from testing**

If any issues found during testing, fix and commit individually.
