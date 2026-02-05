# Beginner's Delight Development Guidelines

## License Compliance

**Project License**: LGPL-3.0-only (GNU Lesser General Public License v3.0)

**Quick Reference**:
- Compatible licenses: MIT, Apache 2.0, BSD
- Incompatible licenses: CC-BY-NC, proprietary

---

## Active Technologies

- Java 21 (Minecraft Java Edition 1.21.11) + NeoForge 21.11.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.10) + NeoForge 21.10.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.9) + NeoForge 21.9.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.8) + NeoForge 21.8.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.7) + NeoForge 21.7.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.6) + NeoForge 21.6.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.5) + NeoForge 21.5.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.4) + NeoForge 21.4.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.3) + NeoForge 21.3.x, Fabric Loader, Architectury API
- Java 21 (Minecraft Java Edition 1.21.1) + NeoForge 21.1.x, Fabric Loader, Architectury API
- Java 17 (Minecraft Java Edition 1.20.1) + Forge 47.4.x, Fabric Loader, Architectury API
- Java 17 (Minecraft Java Edition 1.19.2) + Forge 43.4.x, Fabric Loader, Architectury API
- Java 17 (Minecraft Java Edition 1.18.2) + Forge 40.2.x, Fabric Loader, Architectury API
- Java 16 (Minecraft Java Edition 1.17.1) + Forge 37.1.x, Fabric Loader, Architectury API
- Java 8 (Minecraft Java Edition 1.16.5) + Forge 36.2.x, Fabric Loader, Architectury API

## Project Structure

```
common-shared/        (shared version-agnostic sources, NOT a Gradle subproject)
common-1.21.11/       (version-specific common module for 1.21.11)
common-1.21.10/       (version-specific common module for 1.21.10)
common-1.21.9/        (version-specific common module for 1.21.9)
common-1.21.8/        (version-specific common module for 1.21.8)
common-1.21.7/        (version-specific common module for 1.21.7)
common-1.21.6/        (version-specific common module for 1.21.6)
common-1.21.5/        (version-specific common module for 1.21.5)
common-1.21.4/        (version-specific common module for 1.21.4)
common-1.21.3/        (version-specific common module for 1.21.3)
common-1.21.1/        (version-specific common module for 1.21.1)
common-1.20.1/        (version-specific common module for 1.20.1)
common-1.19.2/        (version-specific common module for 1.19.2)
common-1.18.2/        (version-specific common module for 1.18.2)
common-1.17.1/        (version-specific common module for 1.17.1)
common-1.16.5/        (version-specific common module for 1.16.5)
fabric-base/          (shared Fabric sources, NOT a Gradle subproject)
fabric-1.21.11/       (version-specific Fabric subproject)
fabric-1.21.10/       (version-specific Fabric subproject)
fabric-1.21.9/        (version-specific Fabric subproject)
fabric-1.21.8/        (version-specific Fabric subproject)
fabric-1.21.7/        (version-specific Fabric subproject)
fabric-1.21.6/        (version-specific Fabric subproject)
fabric-1.21.5/        (version-specific Fabric subproject)
fabric-1.21.4/        (version-specific Fabric subproject)
fabric-1.21.3/        (version-specific Fabric subproject)
fabric-1.21.1/        (version-specific Fabric subproject)
fabric-1.20.1/        (version-specific Fabric subproject)
fabric-1.19.2/        (version-specific Fabric subproject)
fabric-1.18.2/        (version-specific Fabric subproject)
fabric-1.17.1/        (version-specific Fabric subproject)
fabric-1.16.5/        (version-specific Fabric subproject)
neoforge-base/        (shared NeoForge sources, NOT a Gradle subproject)
neoforge-1.21.11/     (version-specific NeoForge subproject)
neoforge-1.21.10/     (version-specific NeoForge subproject)
neoforge-1.21.9/      (version-specific NeoForge subproject)
neoforge-1.21.8/      (version-specific NeoForge subproject)
neoforge-1.21.7/      (version-specific NeoForge subproject)
neoforge-1.21.6/      (version-specific NeoForge subproject)
neoforge-1.21.5/      (version-specific NeoForge subproject)
neoforge-1.21.4/      (version-specific NeoForge subproject)
neoforge-1.21.3/      (version-specific NeoForge subproject)
neoforge-1.21.1/      (version-specific NeoForge subproject)
forge-base/           (shared Forge sources, NOT a Gradle subproject)
forge-1.20.1/         (version-specific Forge subproject)
forge-1.19.2/         (version-specific Forge subproject)
forge-1.18.2/         (version-specific Forge subproject)
forge-1.17.1/         (version-specific Forge subproject)
forge-1.16.5/         (version-specific Forge subproject)
props/                (version-specific properties)
docs/                 (documentation)
```

## Mod Info

- **Mod ID**: `beginnersdelight`
- **Package**: `com.beginnersdelight`
- **Minecraft**: 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.3, 1.21.1, 1.20.1, 1.19.2, 1.18.2, 1.17.1, 1.16.5
- **Architectury API**: 19.0.1 (1.21.11), 18.0.8 (1.21.10), 18.0.5 (1.21.9), 17.0.8 (1.21.8), 17.0.8 (1.21.7), 17.0.6 (1.21.6), 16.1.4 (1.21.5), 15.0.1 (1.21.4), 14.0.4 (1.21.3), 13.0.8 (1.21.1), 9.2.14 (1.20.1), 6.6.92 (1.19.2), 4.12.94 (1.18.2), 2.10.12 (1.17.1), 1.32.68 (1.16.5)
- **Fabric Loader**: 0.17.3
- **Fabric API**: 0.141.3+1.21.11 (1.21.11), 0.138.4+1.21.10 (1.21.10), 0.134.1+1.21.9 (1.21.9), 0.136.1+1.21.8 (1.21.8), 0.129.0+1.21.7 (1.21.7), 0.128.1+1.21.6 (1.21.6), 0.128.1+1.21.5 (1.21.5), 0.119.4+1.21.4 (1.21.4), 0.112.1+1.21.3 (1.21.3), 0.116.7+1.21.1 (1.21.1), 0.92.2+1.20.1 (1.20.1), 0.77.0+1.19.2 (1.19.2), 0.76.0+1.18.2 (1.18.2), 0.46.1+1.17 (1.17.1), 0.42.0+1.16 (1.16.5)
- **NeoForge**: 21.11.37-beta (1.21.11), 21.10.64 (1.21.10), 21.9.16-beta (1.21.9), 21.8.52 (1.21.8), 21.7.2-beta (1.21.7), 21.6.20-beta (1.21.6), 21.5.96 (1.21.5), 21.4.156 (1.21.4), 21.3.95 (1.21.3), 21.1.209 (1.21.1)
- **Forge**: 47.4.0 (1.20.1), 43.4.0 (1.19.2), 40.2.0 (1.18.2), 37.1.1 (1.17.1), 36.2.34 (1.16.5)

## Build Configuration

- **Build DSL**: Groovy DSL (not Kotlin DSL) - for compatibility with Architectury Loom
- **Mappings**: Mojang mappings (not Yarn) - code uses official Minecraft class names (e.g., `net.minecraft.core.Registry`)
- **Shadow Plugin**: com.gradleup.shadow - for bundling common module into platform-specific JARs

## Commands

**Build**:
- `./gradlew build` - Build for default version (1.21.11)
- `./gradlew build -Ptarget_mc_version=1.20.1` - Build for specific version
- `./gradlew buildAll` - Build for all supported versions (1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11)

**Clean**:
- `./gradlew cleanAll` - Clean all supported versions

**Run Client** (1.21.11):
- Fabric: `./gradlew :fabric:runClient`
- NeoForge: `./gradlew :neoforge:runClient`

**Run Client** (1.21.10):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.10`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.10`

**Run Client** (1.21.9):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.9`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.9`

**Run Client** (1.21.8):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.8`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.8`

**Run Client** (1.21.7):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.7`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.7`

**Run Client** (1.21.6):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.6`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.6`

**Run Client** (1.21.5):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.5`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.5`

**Run Client** (1.21.4):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.4`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.4`

**Run Client** (1.21.3):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.3`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.3`

**Run Client** (1.21.1):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.1`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.1`

**Run Client** (1.20.1):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.20.1`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.20.1`

**Run Client** (1.19.2):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.19.2`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.19.2`

**Run Client** (1.18.2):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.18.2`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.18.2`

**Run Client** (1.17.1):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.17.1`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.17.1`

**Run Client** (1.16.5):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.16.5`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.16.5`

## Code Style

- Java 21: Follow standard conventions
- Use Mojang mapping names (e.g., `net.minecraft.world.level.Level`, not Yarn's `class_XXXX`)
- Build files use Groovy syntax (e.g., `maven { url 'https://...' }`, not `maven { url = "https://..." }`)
- Common module code is bundled into platform JARs using Shadow plugin

## Development Notes

- NeoForge subprojects require `loom.platform=neoforge` in their `gradle.properties` — without this, Architectury Loom does not create the `neoForge` dependency configuration
- Structures use only vanilla blocks to ensure compatibility after mod removal
- Structure generation state is persisted using `SavedData` to prevent regeneration
- NBT structure files are placed in `common-{version}/src/main/resources/data/beginnersdelight/structure/`
- **MC 1.17.1 `runClient` limitation (macOS)**: `runClient` does not work for MC 1.17.1 on macOS due to two issues: (1) Forge: LWJGL 3.2.x `glfwSetWindowIcon` crash, and Loom's DLI-based native management prevents overriding; (2) Fabric: Architectury Loom 1.11's `fabric-loom-native-support` requires Java 17+, incompatible with 1.17.1's Java 16 toolchain. Use Prism Launcher for testing 1.17.1 on macOS
- **Fabric 1.17.1 duplicate loader**: Fabric API 0.46.1+1.17 pulls in a remapped `fabric-loader` 0.13.2 via Loom, conflicting with the direct 0.17.3 dependency. Excluded via `runtimeClasspath { exclude group: 'remapped.net.fabricmc', module: 'fabric-loader-bf9e8a5d' }` in `fabric-1.17.1/build.gradle`
- **MC 1.21.3 API differences from 1.21.1**: `LevelHeightAccessor.getMaxBuildHeight()`/`getMinBuildHeight()` removed — use `getHeight()`/`getMinY()` instead. `ServerPlayer.teleportTo(ServerLevel, double, double, double, float, float)` removed — use `teleportTo(ServerLevel, double, double, double, Set<Relative>, float, float, boolean)` with `Set.of()` for absolute coordinates.
- **MC 1.21.5 API differences from 1.21.4**: `SavedData` now uses Codec-based serialization via `SavedDataType` record instead of `SavedData.Factory` and `save(CompoundTag)`. `CompoundTag` getters (`getBoolean()`, `getInt()`, `getLong()`, etc.) return `Optional<T>` — use `getBooleanOr()`, `getIntOr()` etc. for defaults, or `getCompoundOrEmpty()`/`getListOrEmpty()` for non-null access. `getList(String, int)` simplified to `getList(String)`. `ServerPlayer.getRespawnPosition()` replaced by `getRespawnConfig()`.
- **NeoForge 1.21.7 `@OnlyIn` limitation**: NeoForge 21.7.3-beta removed the `@OnlyIn` annotation, causing Architectury API (which still uses it) to crash on startup. Pinned to NeoForge 21.7.2-beta with `versionRange="[21.7.0,21.7.3)"` until Architectury API is fixed ([architectury/architectury-api#649](https://github.com/architectury/architectury-api/issues/649)).
- **NeoForge 1.21.8+ `@OnlyIn` warning**: NeoForge 21.8.x onwards shows `@OnlyIn` warnings from Architectury API in development environment only (`runClient`). Production JARs are unaffected — `OnlyInWarningsHandler` checks `!FMLEnvironment.isProduction()`. To suppress the warning screen in dev, add `-Dneoforge.warnings.onlyin.hide=true` as a JVM argument. ERROR-level logs cannot be suppressed but are harmless.
- **MC 1.21.9 API differences from 1.21.8**: `ServerLevel.setDefaultSpawnPos(BlockPos, float)` removed — use `setRespawnData(LevelData.RespawnData.of(dimension, pos, yaw, pitch))`. `ServerLevel.getSharedSpawnPos()` removed — use `getRespawnData().pos()`. `ServerPlayer.getServer()` removed — use `player.level().getServer()` instead. `pack.mcmeta` format changed — `pack_format` replaced by mandatory `min_format`/`max_format` fields for data packs with format ≥ 82.
- **MC 1.16.5 API differences**: Architectury API v1 uses `me.shedaniel:architectury` maven coordinates and `me.shedaniel.architectury.event.events.*` package (not `dev.architectury`). SLF4J is unavailable (use Log4j2). `SavedData` constructor requires a String name argument, `load()` is an instance method (not static factory). `StructureManager.get()` returns `StructureTemplate` directly (not `Optional`). `Entity.getYRot()`/`getXRot()` don't exist (use `yRot`/`xRot` fields). `StructureTemplate.placeInWorld()` takes 4 params (no pivotPos/flags). `--release` javac flag is unavailable in Java 8.
- **MC 1.21.11 API differences from 1.21.10**: `ResourceLocation` renamed to `Identifier` (same package `net.minecraft.resources`). `GameRules` moved from `net.minecraft.world.level.GameRules` to `net.minecraft.world.level.gamerules.GameRules`. `RULE_SPAWN_RADIUS` renamed to `RESPAWN_RADIUS`. GameRules API changed: `getGameRules().getRule(key).set(value, server)` → `getGameRules().set(key, value, server)`.

## Workflow Guidelines

### Pre-Commit Verification

- **Verification Check**: Before committing changes, determine if the changes are testable/verifiable
- **Present Verification Steps**: When changes are verifiable, present to the user:
  1. Verification method (build, run, test command, etc.)
  2. Step-by-step instructions
  3. Expected results/success criteria
- **Wait for User Decision**: Allow user to decide whether to proceed with verification before committing
