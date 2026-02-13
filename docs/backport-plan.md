# Backport Plan

## Goal

Backport Beginner's Delight to older Minecraft versions to reach more players.
Target: staged backport from 1.19.2 down to 1.16.5.

## Version Usage Statistics

Source: [Blockbase Tools](https://blockbase-tools.com/statistics/most-popular-minecraft-versions/) (MAU share)

| Version | MAU Share | Notes |
|---------|-----------|-------|
| 1.20.x  | 27.7%     | Already supported (1.20.1) |
| 1.19.x  | 26.6%     | High priority — large player base |
| 1.18.x  | 18.5%     | Significant player base |
| 1.17.x  | 10.2%     | Moderate player base |
| 1.16.x  | 6.8%      | Long-lived legacy version |
| 1.21.x  | —         | Already supported (1.21.1) |

Modding ecosystem reference: [MineNest CurseForge analysis](https://www.minenest.com/518954/), [Modrinth Statistics](https://modrinth-statistics.tobinio.dev/charts)

## Current Support Matrix

| MC Version | Fabric | Forge/NeoForge | Java |
|------------|--------|----------------|------|
| 1.21.1     | Yes    | NeoForge       | 21   |
| 1.20.1     | Yes    | Forge          | 17   |

## Backport Phases

### Phase 1: Minecraft 1.19.2

- **Loaders**: Fabric + Forge
- **Java**: 17
- **Architectury API**: 6.6.x
- **Fabric API**: 0.76.x+1.19.2
- **Forge**: 43.4.x
- **Pack format**: 9

Key changes:
- Loot table path: `data/<namespace>/loot_tables/` (plural, same as 1.20.1)
- Structure path: `data/<namespace>/structures/` (plural, same as 1.20.1)
- `SavedData` API compatible with 1.20.1
- NBT DataVersion downgrade needed
- Architectury event API signatures may differ

Tasks:
1. Create `props/1.19.2.properties`
2. Create `common/1.19.2/` with version-adapted sources
3. Create `fabric/1.19.2/` and `forge/1.19.2/`
4. Add NBT conversion for 1.19.2 DataVersion
5. Update `settings.gradle` and `multi-version-tasks.gradle`
6. Build and test

### Phase 2: Minecraft 1.18.2

- **Loaders**: Fabric + Forge
- **Java**: 17
- **Architectury API**: 4.11.x
- **Fabric API**: 0.76.x+1.18.2
- **Forge**: 40.2.x
- **Pack format**: 8

Key changes:
- World height expanded to Y=-64..320 (introduced in 1.18)
- `findSurfacePosition()` should work without major changes (dynamic scan)
- `SavedData` API mostly compatible
- Structure API largely the same as 1.19

Tasks:
1. Create `props/1.18.2.properties`
2. Create `common/1.18.2/` with version-adapted sources
3. Create `fabric/1.18.2/` and `forge/1.18.2/`
4. Add NBT conversion for 1.18.2 DataVersion
5. Update build configuration
6. Build and test — pay attention to Y-coordinate handling

### Phase 3: Minecraft 1.17.1

- **Loaders**: Fabric + Forge
- **Java**: 16
- **Architectury API**: 2.8.x
- **Fabric API**: 0.46.x+1.17
- **Forge**: 37.1.x
- **Pack format**: 7

Key changes:
- Java 16 (not 17) — minor syntax adjustments may be needed
- World height still Y=0..256 (pre-1.18 expansion)
- Architectury API v2 — event API may have different class names/signatures
- `SavedData` API exists but may have different method names

Tasks:
1. Create `props/1.17.1.properties`
2. Create `common/1.17.1/` with version-adapted sources
3. Create `fabric/1.17.1/` and `forge/1.17.1/`
4. Add NBT conversion for 1.17.1 DataVersion
5. Update build configuration
6. Verify Java 16 compatibility (no Java 17 features used)
7. Build and test

### Phase 4: Minecraft 1.16.5

- **Loaders**: Fabric + Forge
- **Java**: 8
- **Architectury API**: 1.32.x (originally called "Architectury")
- **Fabric API**: 0.42.x+1.16
- **Forge**: 36.2.x
- **Pack format**: 6

Key changes:
- Java 8 — significant syntax restrictions (no records, no sealed classes, no text blocks, etc.)
- `StructureTemplate` class may be named differently (older Mojang mappings)
- `SavedData` API differences — method signatures likely differ
- Architectury API v1 — oldest supported version, API surface may be limited
- NBT format differences may be larger

Tasks:
1. Create `props/1.16.5.properties`
2. Create `common/1.16.5/` with Java 8 compatible sources
3. Create `fabric/1.16.5/` and `forge/1.16.5/`
4. Add NBT conversion for 1.16.5 DataVersion
5. Update build configuration
6. Refactor any Java 17+ features to Java 8
7. Build and test — thorough testing needed due to API differences

## Per-Version Checklist

For each backported version, verify:

- [ ] `./gradlew build -Ptarget_mc_version=<version>` succeeds
- [ ] Structure generates correctly at spawn
- [ ] Chest loot table loads properly
- [ ] SavedData persists across world reloads (no regeneration)
- [ ] New player teleportation works
- [ ] Respawn at house works (when no bed set)
- [ ] Foundation fill works on slopes
- [ ] World loads after mod removal (vanilla blocks only)

## Files Modified Per Version

Each backport requires:

| File/Directory | Action |
|---|---|
| `props/<version>.properties` | Create — version-specific dependency versions |
| `common-<version>/build.gradle` | Create — common module config with NBT conversion |
| `common-<version>/src/main/java/...` | Create — version-adapted Java sources |
| `fabric-<version>/build.gradle` | Create — Fabric subproject config |
| `fabric-<version>/src/main/resources/fabric.mod.json` | Create — Fabric mod metadata |
| `forge-<version>/build.gradle` | Create — Forge subproject config |
| `forge-<version>/src/main/resources/META-INF/mods.toml` | Create — Forge mod metadata |
| `settings.gradle` | Update — add version to dynamic project inclusion |
| `gradle/multi-version-tasks.gradle` | Update — add version to supported list |
| `CLAUDE.md` | Update — add version to supported versions |
| `README.md` | Update — add version to compatibility table |

## Risk Assessment

| Phase | Risk | Mitigation |
|-------|------|------------|
| 1 (1.19.2) | Low | APIs very similar to 1.20.1 |
| 2 (1.18.2) | Low | Main concern is world height, but dynamic scan handles it |
| 3 (1.17.1) | Medium | Architectury v2 API differences, Java 16 |
| 4 (1.16.5) | Medium-High | Java 8, Architectury v1, potential mapping differences |
