# Village Mode Phase 2 Design Spec — Decoration Buildings

## Overview

Extend the Phase 1 village grid system with decorative buildings (well, shed, storehouse, farm) that are automatically placed between player houses, giving the village a more lived-in appearance.

## Goals

- Add decorative structures that fill in the village between player houses
- Maintain the existing grid-based placement system
- Connect decoration buildings with dirt paths like player houses

## Non-Goals

- Player-assignable buildings (decorations are not bound to players)
- Biome-specific variants (future consideration)
- Configurable decoration ratio (hardcoded for now)
- Avoiding consecutive same-type decorations (random selection may repeat; acceptable for Phase 2)

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Purpose | Decorative only | Not assigned to players; adds village atmosphere |
| Placement timing | Every 2 player houses | Interleaved with player houses for gradual growth |
| Well placement | First decoration slot (after 2 houses) | Acts as village centerpiece |
| Structure count | 4 types (well, shed, storehouse, farm) | Minimum viable variety |
| Loot tables | Storehouse and farm only | Light resources; well and shed have no loot |
| Path connection | Same as player houses | Reuse existing VillagePathGenerator |
| Config additions | None | Hardcoded ratio; configurable if needed later |

## Structure Templates

### New Structures

| Structure | ID | Size | Contents | Loot Table |
|---|---|---|---|---|
| Well | `village_well` | ~5x5 | Stone bricks + water source | None |
| Shed | `village_shed` | ~5x5 | Bed, crafting table | None |
| Storehouse | `village_storehouse` | ~7x7 | Chests, barrels | `chests/village_storehouse` |
| Farm | `village_farm` | ~7x7 | Fenced farmland, crops | `chests/village_farm` |

### NBT Files

Placed in `common/{version}/src/main/resources/data/beginnersdelight/structure/`:
- `village_well.nbt`
- `village_shed.nbt`
- `village_storehouse.nbt`
- `village_farm.nbt`

These must be created manually in Minecraft using Structure Blocks. All structures must fit within `plotSize - 4` blocks (default: 16x16) to leave room for margin and paths.

### Loot Tables

| Loot Table | Contents |
|---|---|
| `chests/village_storehouse` | Oak planks 4-8, cobblestone 4-8, sticks 4-6 |
| `chests/village_farm` | Wheat seeds 3-6, carrots 2-4, bone meal 2-4 |

JSON files placed in `common/{version}/src/main/resources/data/beginnersdelight/loot_table/chests/`.

## Placement Logic

### Interleaved Placement Sequence

```
House 1 → House 2 → Decoration (well) → House 3 → House 4 → Decoration (shed/storehouse/farm) → ...
```

### Decoration Selection

1. First decoration slot (`decorationCount == 0`): always `village_well`
2. Subsequent slots: random selection from `village_shed`, `village_storehouse`, `village_farm`

### PlotState Extension

```java
enum PlotState {
    RESERVED,
    AVAILABLE,
    OCCUPIED,       // Player house
    DECORATION,     // Decoration building (new)
    UNSUITABLE
}
```

### VillageData Extensions

New fields:

| Field | Type | Purpose |
|---|---|---|
| `houseCountSinceLastDecoration` | int | Houses placed since last decoration |
| `decorationCount` | int | Total decoration buildings placed. When 0, next decoration is the well. |

### Placement Flow (Modified)

```
Player joins → House placed (existing flow)
  → houseCountSinceLastDecoration++
  → if houseCountSinceLastDecoration >= 2:
      → Find next available plot in spiral order
      → Select decoration type (well if first, else random)
      → Check suitability (same as house placement)
      → If unsuitable: mark UNSUITABLE, try next plot (up to 10 attempts)
      → If all attempts fail: skip decoration this round (counter stays at 2, retries on next house)
      → If suitable: place decoration structure using VillageHouseGenerator
      → Record in VillageData (PlotState.DECORATION, doorPosition)
      → Generate path to nearest building
      → decorationCount++
      → houseCountSinceLastDecoration = 0
```

### Structure Placement

Decoration buildings use the same terrain handling as player houses (VillageHouseGenerator): surface detection, vegetation clearing, foundation filling, terrain blending. The `place()` method is extended to accept a structure name parameter.

### Loot Table Assignment

Loot table constants are declared as static fields in `VillageHouseGenerator`, alongside the existing `STARTER_HOUSE_LOOT`:

```java
private static final Map<String, ResourceKey<LootTable>> LOOT_TABLES = Map.of(
    "village_storehouse", VILLAGE_STOREHOUSE_LOOT,
    "village_farm", VILLAGE_FARM_LOOT
);
// Structures not in the map (well, shed) skip loot table assignment
```

## Path Connection

Decoration buildings are connected with dirt paths like player houses. Door-front coordinates are recorded in `doorPositions` in VillageData. The nearest existing building (house or decoration) is used as the connection target.

## Code Changes

### Modified Files (per version)

| File | Change |
|---|---|
| `PlotState.java` | Add `DECORATION` enum value |
| `VillageData.java` | Add counter/flag fields, extend serialization |
| `VillageHouseGenerator.java` | Accept structure name param, loot table selection |
| `VillageManager.java` | Decoration placement logic after house placement |
| `VillageGrid.java` | Update `findNearestOccupiedPlot` to search `doorPositions` (includes both houses and decorations) |

### No Changes Needed

- `GridPos.java` — unchanged
- `VillagePathGenerator.java` — unchanged
- `VillageCommand.java` — unchanged; `status` shows house count via `getHouseCount()` which counts OCCUPIED plots. Decoration count is not shown (decorations are an implementation detail, not user-facing state)
- `VillageConfig.java` — no new config items
- Platform entry points — no changes needed

## Version Deployment

Implement in 1.21.11 first, test, then deploy to all 15 versions. Multi-version deployment is in scope for Phase 2 (same as Phase 1). The code changes are small (PlotState, VillageData, VillageHouseGenerator, VillageManager); NBT structure files and loot table JSONs must be copied to all version resource directories.

## Manual Work Required

1. Build 4 NBT structure files in Minecraft using Structure Blocks
2. Create 2 loot table JSON files
3. Copy NBT files to all version resource directories
