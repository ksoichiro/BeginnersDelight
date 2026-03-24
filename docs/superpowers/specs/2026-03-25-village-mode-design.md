# Village Mode Design Spec

## Overview

A new "Village Mode" feature that coexists with the existing Starter House feature. When enabled via command, each new player who joins the server gets a house placed near the world spawn, gradually forming a village with paths connecting the houses.

## Goals

- Allow a village to grow dynamically as new players join
- Connect houses with simple dirt paths
- Provide in-game commands and a config file for server operators
- Preserve village data when the feature is disabled and resumed

## Non-Goals (Future Phases)

- Village-specific structure templates (Phase 2)
- Road-based layout, random offset, other MC version support (Phase 3)

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Relationship with Starter House | Coexist independently | No changes to existing feature; village is opt-in. Both features run: Starter House teleports to spawn house, then Village Mode assigns a separate village house. Players get both. |
| Settings interface | Command + config file | Commands for casual play, config for fine-tuning |
| Layout method | Grid-based (spiral order) | Simple, robust, no collision detection needed |
| Structure variants | Reuse existing 6 types initially | No new assets required for Phase 1 |
| Village size limit | None | Unlimited growth |
| Enable/disable behavior | Anytime; data preserved on disable | Safe to toggle without data loss |
| Player-house binding | UUID → GridPos mapping | Enables respawn at own house |
| Respawn behavior | Respawn at own house (no bed) | Tied to player-house binding |
| Target dimension | Overworld only | Same as Starter House |
| Village center | World spawn point | Natural starting point near Starter House |
| Path style | Dirt Path, straight lines between houses | Simple L-shaped connections |
| Target version | 1.21.11 only (initial) | Stabilize before backporting |

## Architecture

### Components

```
VillageManager          — Orchestrator (onPlayerJoin, onPlayerRespawn)
VillageGrid             — Grid plot management, next available plot lookup
VillageHouseGenerator   — Structure placement (terrain handling included)
VillagePathGenerator    — Dirt Path generation between houses
VillageData (SavedData) — Persistence (village state, plots, player-house mapping)
VillageConfig           — Config file read/write
VillageCommand          — Brigadier command definitions
GridPos                 — Grid coordinate record
```

### Data Flow

**New player (no house assigned):**

```
Player joins
  → Event listener (NeoForge/Fabric)
  → VillageManager.onPlayerJoin(player)
    → VillageData: Is village enabled? Does this player already have a house?
    → If no house: VillageGrid: Get next available plot
      → If no suitable plot available: log warning, skip (player spawns normally)
      → VillageHouseGenerator: Place structure (variant chosen randomly)
      → VillagePathGenerator: Connect to nearest existing house toward center
      → VillageData: Record player UUID, house position
      → Teleport player into their house (interior center, floor + 1)
    → If already has house: Teleport player to their existing house
```

**Respawn (death, no bed set):**

```
Player respawns
  → VillageManager.onPlayerRespawn(player)
    → VillageData: Does this player have a house?
    → If yes and respawnAtHouse config is true: Teleport to house interior
    → Otherwise: Normal respawn (world spawn)
```

Note: The `isEndConquered` flag from the respawn event is not used by Village Mode — it is passed through for compatibility but village respawn behavior does not change based on End conquest.

## Grid System

### Structure

- **Center**: World spawn point (starter house area reserved)
- **Plot size**: Structure max footprint + margin (default 20x20 blocks)
  - Structure size + path width (2 blocks) + padding (2 blocks)
- **Plot size is configurable** to accommodate different structures

### Placement Order (Spiral)

Center plot is reserved for the Starter House area. Other plots are filled from nearest to farthest:

```
 13  12  11  10  25
 14   3   2   9  24
 15   4   ★   1  23
 16   5   6   7  22
 17  18  19  20  21
```

(★ = center, reserved for Starter House)

### Plot-to-World Coordinate Conversion

```
worldX = centerX + (gridX * plotSize)
worldZ = centerZ + (gridZ * plotSize)
```

Y coordinate is determined by `findSurfacePosition()` within the plot.

### Terrain Handling

- Same approach as existing `StarterHouseGenerator.findSurfacePosition()`: sample 5 points (4 corners + center of the footprint), find ground Y for each by scanning down while skipping foliage/fluid, then use the lowest Y (clamped to sea level)
- **Skip conditions**: Plot is skipped and marked UNSUITABLE when:
  - Height difference within the plot exceeds threshold (configurable, default 10 blocks)
  - Plot center is underwater
- Skipped plots are not re-evaluated

### Data Structure

```java
Map<GridPos, PlotState> plots;

enum PlotState {
    RESERVED,    // Starter House area
    AVAILABLE,   // Unused
    OCCUPIED,    // House placed
    UNSUITABLE   // Terrain unsuitable
}
```

## Persistence (VillageData)

`DATA_NAME = "beginnersdelight_village"`, stored in overworld DataStorage.

### Fields

| Field | Type | Purpose |
|---|---|---|
| `enabled` | `boolean` | Village mode on/off |
| `centerPos` | `BlockPos` | Village center coordinates |
| `plots` | `Map<GridPos, PlotState>` | Plot states |
| `playerHouses` | `Map<UUID, GridPos>` | Player-to-house binding |
| `housePositions` | `Map<GridPos, BlockPos>` | Plot → world coordinates (for teleport/paths) |
| `doorPositions` | `Map<GridPos, BlockPos>` | Plot → door-front coordinates (for path connections) |

### Respawn Logic

- On player death → respawn, if no bed is set:
  - Look up player's house via `playerHouses` (UUID → GridPos)
  - Get world position via `housePositions` (GridPos → BlockPos)
  - Teleport player to their house
- Players without a house use normal respawn (world spawn)

### Version-Specific Implementation

- 1.21.11: Codec-based serialization (`SavedDataType` record)
- Other versions (future): `CompoundTag`-based `load()`/`save()`

## Commands and Configuration

### Commands

OP permission level 2 required.

```
/beginnersdelight village enable              — Enable village mode
/beginnersdelight village disable             — Disable village mode (data preserved)
/beginnersdelight village status              — Show status (enabled/disabled, house count, player count)
/beginnersdelight village set <key> <value>   — Change a config value
```

### Command Registration

- NeoForge: `RegisterCommandsEvent`
- Fabric: `CommandRegistrationCallback`
- Common logic in `VillageCommand.register(dispatcher)`

### Config File

`config/beginnersdelight-village.properties` (simple Java properties format for cross-platform compatibility)

Loaded at server startup. Changes via `village set` command are written to the file and take effect immediately for subsequent operations. However, `plotSize` changes only affect newly placed houses — existing plot layout is not recalculated.

| Key | Type | Default | Description |
|---|---|---|---|
| `plotSize` | int | 20 | Plot size in blocks |
| `maxHeightDifference` | int | 10 | Max height difference tolerance within a plot |
| `generatePaths` | boolean | true | Enable/disable path generation |
| `respawnAtHouse` | boolean | true | Enable/disable respawn at own house |

### Command vs Config Relationship

- `enable`/`disable` → stored in VillageData (per-world)
- `plotSize` etc. → stored in config file (server-global)
- `village set` command can modify config file values from in-game

## Path Generation (VillagePathGenerator)

### Connection Rule

Each new house connects to the **nearest existing house by grid distance (Manhattan distance)**. If only one house exists (the first village house), it connects to the village center point. This forms a tree structure of paths radiating outward from the center.

### Generation Logic

1. Get door-front coordinate (A) of the new house and door-front coordinate (B) of the target house
2. Trace an L-shaped path from A to B (X-axis first, then Z-axis)
3. For each block along the path:
   - Replace surface grass/dirt blocks with Dirt Path
   - Remove vegetation (grass, flowers) above the path
   - Skip (leave unchanged) water/lava surface blocks — path continues on the other side if possible (bridges are a future phase)
   - Skip (leave unchanged) stone-type blocks (cliffs) — path continues past them
4. Path width: 1 block

### Door-Front Coordinate

- Determined at structure placement time
- South side of the structure is assumed as the front (matching existing starter house orientation)
- Stored in `doorPositions` map in VillageData

### Height Differences in Paths

- When Y coordinate changes, create step-by-step terrain (staircase)
- When a gap of 2+ blocks is encountered (steep cliff), skip those blocks and resume path generation on the other side where the terrain is reachable again

## File Layout

### Common Module

```
common/shared/src/main/java/com/beginnersdelight/village/
  VillageManager.java
  VillageGrid.java
  VillageHouseGenerator.java
  VillagePathGenerator.java
  VillageData.java
  VillageConfig.java
  VillageCommand.java
  GridPos.java

common/1.21.11/src/main/java/com/beginnersdelight/village/
  VillageData.java             — 1.21.11 Codec-based override
```

### Platform Integration

Add to existing event listeners in:
- `neoforge/base/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java`
- `fabric/base/src/main/java/com/beginnersdelight/fabric/BeginnersDelightFabric.java`

```java
// Add alongside existing StarterHouseGenerator calls:
VillageManager.onPlayerJoin(serverPlayer);
VillageManager.onPlayerRespawn(serverPlayer, isEndConquered);
```

Command registration in platform-specific entry points.

## Phased Release Plan

### Phase 1 (Initial Release)

- Grid-based house placement with spiral ordering
- Reuse existing 6 starter house variants
- Simple Dirt Path connections (L-shaped, 1-block width)
- Commands: enable/disable/status/set
- Config file for parameters
- Player-house binding with respawn support
- 1.21.11 only (NeoForge + Fabric)

### Phase 2 (Future)

- Village-specific structure templates (smaller, varied houses)
- Possible well/fountain at village center

### Phase 3 (Future)

- Road-based layout replacing grid
- Random offset for natural appearance
- Bridge generation over water
- Backport to other MC versions
