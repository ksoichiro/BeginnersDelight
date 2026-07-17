# Beginner's Delight Development Guidelines

## License Compliance

**Project License**: LGPL-3.0-only (GNU Lesser General Public License v3.0)

**Quick Reference**:
- Compatible licenses: MIT, Apache 2.0, BSD
- Incompatible licenses: CC-BY-NC, proprietary

---

## Active Technologies

- Java 25 (Minecraft Java Edition 26.2) + NeoForge 26.2.0.x, Fabric Loader
- Java 25 (Minecraft Java Edition 26.1.2) + NeoForge 26.1.2.x, Fabric Loader
- Java 25 (Minecraft Java Edition 26.1.1) + NeoForge 26.1.1.x, Fabric Loader
- Java 25 (Minecraft Java Edition 26.1) + NeoForge 26.1.0.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.11) + NeoForge 21.11.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.10) + NeoForge 21.10.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.9) + NeoForge 21.9.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.8) + NeoForge 21.8.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.7) + NeoForge 21.7.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.6) + NeoForge 21.6.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.5) + NeoForge 21.5.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.4) + NeoForge 21.4.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.3) + NeoForge 21.3.x, Fabric Loader
- Java 21 (Minecraft Java Edition 1.21.1) + NeoForge 21.1.x, Fabric Loader
- Java 17 (Minecraft Java Edition 1.20.1) + Forge 47.4.x, Fabric Loader
- Java 17 (Minecraft Java Edition 1.19.2) + Forge 43.4.x, Fabric Loader
- Java 17 (Minecraft Java Edition 1.18.2) + Forge 40.2.x, Fabric Loader
- Java 16 (Minecraft Java Edition 1.17.1) + Forge 37.1.x, Fabric Loader
- Java 8 (Minecraft Java Edition 1.16.5) + Forge 36.2.x, Fabric Loader

## Project Structure

```
common/
  shared/             (shared version-agnostic sources, NOT a Gradle subproject)
  26.2/               (version-specific common module)
  26.1.2/             (version-specific common module)
  ...                 (26.1.1, 26.1, 1.21.11, ..., 1.16.5)
fabric/
  base/               (shared Fabric sources, NOT a Gradle subproject)
  26.2/               (version-specific Fabric subproject)
  26.1.2/             (version-specific Fabric subproject)
  ...                 (26.1.1, 26.1, 1.21.11, ..., 1.16.5)
neoforge/
  base/               (shared NeoForge sources, NOT a Gradle subproject)
  26.2/               (version-specific NeoForge subproject)
  26.1.2/             (version-specific NeoForge subproject)
  ...                 (26.1.1, 26.1, 1.21.11, ..., 1.21.1)
forge/
  base/               (shared Forge sources, NOT a Gradle subproject)
  1.20.1/             (version-specific Forge subproject)
  1.19.2/             (version-specific Forge subproject)
  ...                 (1.18.2, 1.17.1, 1.16.5)
props/                (version-specific properties)
docs/                 (documentation)
```

## Mod Info

- **Mod ID**: `beginnersdelight`
- **Package**: `com.beginnersdelight`
- **Minecraft**: 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.3, 1.21.1, 1.20.1, 1.19.2, 1.18.2, 1.17.1, 1.16.5
- **Fabric Loader**: 0.19.3 (26.x), 0.17.3 (1.16.5–1.21.11)
- **Fabric API**: 0.154.2+26.2 (26.2), 0.154.2+26.1.2 (26.1.2), 0.145.4+26.1.1 (26.1.1), 0.145.1+26.1 (26.1), 0.141.3+1.21.11 (1.21.11), 0.138.4+1.21.10 (1.21.10), 0.134.1+1.21.9 (1.21.9), 0.136.1+1.21.8 (1.21.8), 0.129.0+1.21.7 (1.21.7), 0.128.1+1.21.6 (1.21.6), 0.128.1+1.21.5 (1.21.5), 0.119.4+1.21.4 (1.21.4), 0.112.1+1.21.3 (1.21.3), 0.116.7+1.21.1 (1.21.1), 0.92.2+1.20.1 (1.20.1), 0.77.0+1.19.2 (1.19.2), 0.76.0+1.18.2 (1.18.2), 0.46.1+1.17 (1.17.1), 0.42.0+1.16 (1.16.5)
- **NeoForge**: 26.2.0.16-beta (26.2), 26.1.2.80 (26.1.2), 26.1.1.15-beta (26.1.1), 26.1.0.19-beta (26.1), 21.11.37-beta (1.21.11), 21.10.64 (1.21.10), 21.9.16-beta (1.21.9), 21.8.52 (1.21.8), 21.7.2-beta (1.21.7), 21.6.20-beta (1.21.6), 21.5.96 (1.21.5), 21.4.156 (1.21.4), 21.3.95 (1.21.3), 21.1.209 (1.21.1)
- **Forge**: 47.4.0 (1.20.1), 43.4.0 (1.19.2), 40.2.0 (1.18.2), 37.1.1 (1.17.1), 36.2.34 (1.16.5)

## Build Configuration

- **Build DSL**: Groovy DSL (not Kotlin DSL) - for compatibility with Architectury Loom
- **Mappings**: Mojang mappings (not Yarn) - code uses official Minecraft class names (e.g., `net.minecraft.core.Registry`). MC 26.1+ is unobfuscated: no `mappings` dependency, no remapping
- **Shadow Plugin**: com.gradleup.shadow - for bundling common module into platform-specific JARs. On MC 26.1+ `shadowJar` (not `remapJar`) produces the final artifact
- **Gradle**: 9.5.1 wrapper. `gradle/gradle-daemon-jvm.properties` pins the daemon JVM to Java 25 (auto-provisioned via foojay resolver), required for MC 26.1+ development
- **Per-version toolchain**: `props/{version}.properties` can override `loom_plugin` (`dev.architectury.loom` [default] or `dev.architectury.loom-no-remap` for 26.1+), `loom_version`, `architectury_plugin_version`, and `shadow_version`. Versions are resolved in `settings.gradle` (pluginManagement) and the Loom plugin marker is put on the buildscript classpath in the root `build.gradle`

## Commands

**Build**:
- `./gradlew build` - Build for default version (26.2)
- `./gradlew build -Ptarget_mc_version=1.20.1` - Build for specific version
- `./gradlew buildAll` - Build for all supported versions (1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2)

**Clean**:
- `./gradlew cleanAll` - Clean all supported versions

**Run Client** (26.2):
- Fabric: `./gradlew :fabric:runClient`
- NeoForge: `./gradlew :neoforge:runClient`

**Run Client** (26.1.2):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=26.1.2`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=26.1.2`

**Run Client** (26.1.1):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=26.1.1`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=26.1.1`

**Run Client** (26.1):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=26.1`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=26.1`

**Run Client** (1.21.11):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.21.11`
- NeoForge: `./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.11`

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
- NBT structure files are placed in `common/{version}/src/main/resources/data/beginnersdelight/structure/`
- **MC 1.17.1 `runClient` limitation (macOS)**: `runClient` does not work for MC 1.17.1 on macOS due to two issues: (1) Forge: LWJGL 3.2.x `glfwSetWindowIcon` crash, and Loom's DLI-based native management prevents overriding; (2) Fabric: Architectury Loom 1.11's `fabric-loom-native-support` requires Java 17+, incompatible with 1.17.1's Java 16 toolchain. Use Prism Launcher for testing 1.17.1 on macOS
- **Fabric 1.17.1 duplicate loader**: Fabric API 0.46.1+1.17 pulls in a remapped `fabric-loader` 0.13.2 via Loom, conflicting with the direct 0.17.3 dependency. Excluded via `runtimeClasspath { exclude group: 'remapped.net.fabricmc', module: 'fabric-loader-bf9e8a5d' }` in `fabric/1.17.1/build.gradle`
- **MC 1.21.3 API differences from 1.21.1**: `LevelHeightAccessor.getMaxBuildHeight()`/`getMinBuildHeight()` removed — use `getHeight()`/`getMinY()` instead. `ServerPlayer.teleportTo(ServerLevel, double, double, double, float, float)` removed — use `teleportTo(ServerLevel, double, double, double, Set<Relative>, float, float, boolean)` with `Set.of()` for absolute coordinates.
- **MC 1.21.5 API differences from 1.21.4**: `SavedData` now uses Codec-based serialization via `SavedDataType` record instead of `SavedData.Factory` and `save(CompoundTag)`. `CompoundTag` getters (`getBoolean()`, `getInt()`, `getLong()`, etc.) return `Optional<T>` — use `getBooleanOr()`, `getIntOr()` etc. for defaults, or `getCompoundOrEmpty()`/`getListOrEmpty()` for non-null access. `getList(String, int)` simplified to `getList(String)`. `ServerPlayer.getRespawnPosition()` replaced by `getRespawnConfig()`.
- **NeoForge 1.21.7 version pin**: NeoForge 21.7.2-beta is pinned with `versionRange="[21.7.0,21.7.3)"` due to `@OnlyIn` removal in 21.7.3-beta causing issues with Architectury Loom's remapping.
- **MC 1.21.9 API differences from 1.21.8**: `ServerLevel.setDefaultSpawnPos(BlockPos, float)` removed — use `setRespawnData(LevelData.RespawnData.of(dimension, pos, yaw, pitch))`. `ServerLevel.getSharedSpawnPos()` removed — use `getRespawnData().pos()`. `ServerPlayer.getServer()` removed — use `player.level().getServer()` instead. `pack.mcmeta` format changed — `pack_format` replaced by mandatory `min_format`/`max_format` fields for data packs with format ≥ 82.
- **MC 1.16.5 API differences**: SLF4J is unavailable (use Log4j2). `SavedData` constructor requires a String name argument, `load()` is an instance method (not static factory). `StructureManager.get()` returns `StructureTemplate` directly (not `Optional`). `Entity.getYRot()`/`getXRot()` don't exist (use `yRot`/`xRot` fields). `StructureTemplate.placeInWorld()` takes 4 params (no pivotPos/flags). `--release` javac flag is unavailable in Java 8. Forge event: `net.minecraftforge.fml.event.server.FMLServerStartedEvent` (not `ServerStartedEvent`). Forge `PlayerEvent` uses `getEntity()` (not `getPlayer()`) with Mojang mappings.
- **MC 1.17.1 Forge API differences from 1.16.5**: `FMLServerStartedEvent` moved to `net.minecraftforge.fmlserverevents` package. Forge `PlayerEvent` uses `getEntity()` (not `getPlayer()`) with Mojang mappings.
- **MC 1.18.2+ Forge API differences from 1.17.1**: `FMLServerStartedEvent` replaced by `net.minecraftforge.event.server.ServerStartedEvent`.
- **MC 1.21.11 API differences from 1.21.10**: `ResourceLocation` renamed to `Identifier` (same package `net.minecraft.resources`). `GameRules` moved from `net.minecraft.world.level.GameRules` to `net.minecraft.world.level.gamerules.GameRules`. `RULE_SPAWN_RADIUS` renamed to `RESPAWN_RADIUS`. GameRules API changed: `getGameRules().getRule(key).set(value, server)` → `getGameRules().set(key, value, server)`.
- **MC 26.1 differences from 1.21.11 (unobfuscated Minecraft)**: MC 26.1+ ships unobfuscated and requires Java 25 (also for the Gradle JVM). Build changes: use `dev.architectury.loom-no-remap` (1.17.x) + architectury-plugin 3.5-SNAPSHOT + Shadow 9.x, no `mappings` dependency, `modImplementation`/`modApi` → `implementation`/`api`, no `remapJar`/`remapSourcesJar` (final jar = `shadowJar`; Loom's `include()` for jar-in-jar still works and is applied by the plain `jar` task), common project dependency uses the default configuration (not `namedElements`). Fabric Loader minimum is 0.18.4. NeoForge versioning is now 4-part (`26.1.0.19-beta` for MC 26.1) and `neoforge.mods.toml` no longer declares `modLoader`/`loaderVersion`. API: `SavedDataType` takes an `Identifier` instead of a String name, and saved data files move from flat `data/<name>.dat` to `data/<namespace>/<path>.dat` — `com.beginnersdelight.util.SavedDataMigration` copies the legacy file on first access so upgraded worlds do not regenerate structures.
- **MC 26.2 API differences from 26.1.2**: `BlockTags.SAPLINGS` constant removed (the vanilla `minecraft:saplings` data tag still exists) — use `com.beginnersdelight.util.ModBlockTags.SAPLINGS`. pack_format is `[107, 1]` (26.1–26.1.2 use `[101, 1]`).
- **Known dev-only issue: "Client shutdown from post-main" watchdog crash on MC 26.x Fabric `runClient` exit**: MC 26.1+ added `ClientShutdownWatchdog`, which dumps a crash report if the JVM does not exit shortly after main returns. In the dev environment, Fabric Loader's runtime remapper (TinyRemapper bundled in `net.fabricmc.loader.impl` — `RuntimeModRemapper`/`GameProviderHelper`) leaves non-daemon `pool-N-thread-M` fixed thread pools (sized to CPU cores) running, which blocks JVM exit. Happens only in dev, only after the world is fully saved ("Stopping!" already logged), and not on every run (cached remap results skip pool creation). Not caused by this mod (mod code, night-config, and Architectury Transformer create no executors — verified 2026-07). Production players are unaffected. No action needed; ignore these crash reports.

## Workflow Guidelines

### Pre-Commit Verification

- **Verification Check**: Before committing changes, determine if the changes are testable/verifiable
- **Present Verification Steps**: When changes are verifiable, present to the user:
  1. Verification method (build, run, test command, etc.)
  2. Step-by-step instructions
  3. Expected results/success criteria
- **Wait for User Decision**: Allow user to decide whether to proceed with verification before committing
