# Beginner's Delight - Implementation Plan

## Overview

A Minecraft mod that generates a small base structure at the world spawn point to help beginners proceed safely through early-game adventures. Uses Architectury for Fabric/NeoForge cross-platform support. Initial target: Minecraft 1.21.1.

- Mod ID: `beginnersdelight`
- Package: `com.beginnersdelight`
- License: LGPL-3.0-only

## Phase 1: Project Foundation

Set up the multi-loader Gradle project referencing ChronoDawn's configuration.

### 1.1 Gradle Configuration Files

- `settings.gradle` — Plugin repositories, subproject definitions
- `build.gradle` — Architectury Loom, Shadow plugin, shared subproject configuration
- `gradle.properties` — Mod metadata and dependency versions
  - `mod_version=0.1.0`
  - `maven_group=com.beginnersdelight`
  - `archives_name=beginnersdelight`
  - `enabled_platforms=fabric,neoforge`
  - Dependency versions for Minecraft 1.21.1
- `props/1.21.1.properties` — MC 1.21.1 specific version properties
- Gradle Wrapper files

### 1.2 Subproject Structure

Create directories and `build.gradle` for each:

| Directory | Role | Gradle Subproject |
|---|---|---|
| `common-shared` | Loader/version-independent common code | No (included as srcDirs) |
| `common-1.21.1` | Common code for MC 1.21.1 | Yes |
| `fabric-base` | Fabric code without version dependency | Yes |
| `fabric-1.21.1` | Fabric + MC 1.21.1 code | Yes (depends on fabric-base) |
| `neoforge-base` | NeoForge code without version dependency | Yes |
| `neoforge-1.21.1` | NeoForge + MC 1.21.1 code | Yes (depends on neoforge-base) |

### 1.3 Package Structure

```
com.beginnersdelight
├── BeginnersDelight.java          # Main class (MOD_ID, common init)
├── registry/                      # Registry management
├── worldgen/                      # World generation
├── events/                        # Event handlers
├── platform/                      # Platform abstraction layer
└── mixin/                         # Mixin classes
```

### 1.4 Mod Configuration Files

- `fabric-1.21.1/src/main/resources/fabric.mod.json`
- `neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml`
- Mixin configuration JSONs (added as needed)

### 1.5 License and Misc

- `LICENSE` — LGPL-3.0-only
- `pack.mcmeta` — Set `pack_format` for the target version

### 1.6 Build Verification

- Confirm `./gradlew build` succeeds
- Confirm Fabric and NeoForge JARs are generated (empty mod at this stage)

## Phase 2: Structure Preparation

### 2.1 NBT Structure Files

- Create multiple base structure variants using Structure Blocks in-game, export as NBT files
- Location: `common-1.21.1/src/main/resources/data/beginnersdelight/structure/`
- Structure contents (draft):
  - Small wooden shelter (walls, roof, door)
  - 1 bed
  - 1 chest (with starter items)
  - Torch lighting
- Prepare at least 2-3 variants for random selection
- **Note**: NBT files require in-game creation; this phase follows Phase 1 completion and requires manual work in the game client

### 2.2 Chest Loot Table

- Define chest contents using loot tables
- Location: `common-1.21.1/src/main/resources/data/beginnersdelight/loot_table/chests/`
- Candidate items:
  - Food (bread, apples)
  - Wooden tool set
  - Torches
  - Boat (optional)

## Phase 3: Core Feature Implementation

### 3.1 Structure Generation Logic

- Generate the structure at the spawn point on first world creation or first load after mod installation
- Prevent regeneration using a persistent flag
  - Use `SavedData` to store the generation flag
- Load structures from NBT files using `StructureTemplate` and place them
- Randomly select one variant from available patterns
- Adapt placement to terrain (surface detection, Y-coordinate calculation)

### 3.2 Multiplayer Support

- Trigger generation check on server start (world load)
  - Use Architectury `LifecycleEvent.SERVER_STARTING` or equivalent
- Fix the spawn point
  - Set the world spawn point near the structure entrance
  - All subsequent players spawn at the same location

### 3.3 Platform Abstraction

- Implement common event handling in `common-shared`
- Implement platform-specific event registration in `fabric-base` / `neoforge-base`
- Use Architectury API event system for cross-platform compatibility

## Phase 4: Entry Points

### 4.1 Common Initialization (`BeginnersDelight.java`)

- Define MOD_ID
- Call registry initialization
- Register event handlers

### 4.2 Fabric Entry Point

- `BeginnersDelightFabric` — Implements `ModInitializer`
- Call common init, then Fabric-specific registration

### 4.3 NeoForge Entry Point

- `BeginnersDelightNeoForge` — NeoForge mod entry point
- Call common init, then NeoForge-specific registration

## Phase 5: Testing and Adjustment

### 5.1 Verification Environments

- Fabric client (`runClient`)
- NeoForge client (`runClient`)
- Multiplayer (`runServer`)

### 5.2 Checklist

- [x] Structure generates on new world creation
- [x] Structure contains bed and chest
- [x] Chest contains starter items
- [x] Structure does not regenerate on world reload
- [x] Second player spawns at the same location
- [x] World loads normally after mod removal

## Implementation Notes

- **Post-removal compatibility**: Avoid non-vanilla blocks/items. Build structures using only vanilla blocks
- **Regeneration prevention**: Ensure `SavedData` flag persists across server restarts
- **Terrain adaptation**: Validate placement position to avoid generating structures on water or in mid-air
- **Performance**: Generation occurs only once so impact is minimal; keep structures small

## Dependencies

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| Architectury API | 13.0.8 |
| Fabric Loader | 0.17.3 |
| Fabric API | 0.116.7+1.21.1 |
| NeoForge | 21.1.209 |
| Java | 21 |
