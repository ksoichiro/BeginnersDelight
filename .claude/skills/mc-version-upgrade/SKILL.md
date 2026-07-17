---
name: mc-version-upgrade
description: Add support for a new Minecraft version. Use when adding a new MC version to the mod (e.g., "add 26.3 support").
user_invocable: true
---

# Minecraft Version Upgrade Procedure

Add support for a new Minecraft version to the Beginner's Delight mod.

## Required Input

Gather the following before starting (research on the web / maven metadata if not given):

| Parameter | Example | Description |
|-----------|---------|-------------|
| MC version | 26.2 | Target Minecraft version |
| Base version | 26.1.2 | Closest existing version to copy from |
| NeoForge version | 26.2.0.16-beta | From `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` (4-part `{mc.major}.{mc.minor}.{mc.patch}.{build}` since MC 26.x) |
| Fabric API version | 0.154.2+26.2 | **Verify against `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml` directly** (do not trust summarized web answers — a nonexistent version fails at dependency resolution) |
| pack_format | [107, 1] | Data pack format from minecraft.wiki (Pack format page). `[major, minor]` array form since 1.21.9 |

Fabric Loader: 0.17.3 for ≤1.21.11, 0.19.3 (or latest stable) for 26.x.

## Step-by-step Procedure

### 1. Create `props/{version}.properties`

Copy from `props/{base_version}.properties` and update:
- `minecraft_version`
- `pack_format`
- `neoforge_version`
- `fabric_api_version`

For MC 26.1+ the props file must also contain the unobfuscated toolchain keys
(copy from an existing 26.x props file):

```properties
java_version=25
fabric_loader_version=0.19.3
loom_plugin=dev.architectury.loom-no-remap
loom_version=1.17.491
architectury_plugin_version=3.5-SNAPSHOT
shadow_version=9.4.2
```

### 2. Create `common/{version}/`

Copy `src/` and `build.gradle` from `common/{base_version}/` (do NOT copy `build/`):
- `build.gradle` (usually no changes needed)
- `src/main/java/com/beginnersdelight/` (entry point, worldgen/, village/, util/)
- `src/main/resources/pack.mcmeta` (template, expanded from `pack_format`)
- `src/main/resources/beginnersdelight-default-config.toml`
- `src/main/resources/data/beginnersdelight/` (structures, loot tables)

### 3. Create `fabric/{version}/`

- `build.gradle`: Copy from base, change `commonModule = ':common-{version}'`
- `gradle.properties`: Empty file (just a newline)
- `src/main/resources/fabric.mod.json`: Update `minecraft` constraint (`~{version}`); for 26.x also `fabricloader >=0.18.4` and `java >=25`

### 4. Create `neoforge/{version}/`

- `build.gradle`: Copy from base, change `commonModule = ':common-{version}'`
- `gradle.properties`: Contains only `loom.platform=neoforge` (still required with loom-no-remap)
- `src/main/resources/META-INF/neoforge.mods.toml`: Update `neoforge` versionRange (`[{mc_version_3part}.0,)`-style, e.g. `[26.2.0,)`) and `minecraft` versionRange (`[{version}]`). Since 26.x there is no `modLoader`/`loaderVersion` header

### 5. Update `gradle.properties` (root)

Change `target_mc_version` to the new version (new default).

### 6. Update `gradle/multi-version-tasks.gradle`

Add the new version to the `supportedVersions` list.

### 7. Update documentation

All of these need the new version added in the appropriate places:

- **CLAUDE.md**: Active Technologies, Project Structure, Mod Info, Build Configuration, Commands (Build/Run), Development Notes (if API changes)
- **README.md**: Supported Versions table, Requirements, Output Files (default version), Run Commands, Installation section, Project Structure, Technical Notes path, footer
- **CHANGELOG.md**: Add entry under `[Unreleased]`
- **docs/modrinth_description.md**: Version list, Requirements, footer
- **docs/curseforge_description.md**: Version list, Requirements, footer

### 8. Build and verify

```bash
./gradlew build -Ptarget_mc_version={version}
```

Fix any compilation errors from API changes. Recent examples:
- MC 26.1: `SavedDataType` takes `Identifier` instead of String (see `SavedDataMigration` for the legacy file migration)
- MC 26.2: `BlockTags.SAPLINGS` constant removed (data tag still exists → `ModBlockTags.SAPLINGS`)
- `pack.mcmeta` format changes (`min_format`/`max_format` since format >= 82)

Also verify the built jars: final jar name `beginnersdelight-{mod_version}+{version}-{platform}.jar`, `fabric.mod.json`/`pack.mcmeta`/`neoforge.mods.toml` expansion, and (Fabric) nested `META-INF/jars/` night-config jars.

### 9. Record API changes

If API changes were needed, add a Development Notes entry to `CLAUDE.md` documenting the changes (following the existing format of "MC {version} API differences from {base_version}").

## Important Notes

- Java source code is often identical to the base version — API changes are the exception, not the rule
- Always check `pack.mcmeta` format requirements for the new MC version
- The `settings.gradle` dynamically resolves modules from `target_mc_version`, so it does not need editing. It also resolves per-version plugin versions from the props file (`loom_version`, `architectury_plugin_version`, `shadow_version`)
- NeoForge subprojects **must** have `loom.platform=neoforge` in `gradle.properties`
- MC 26.1+ is unobfuscated: root `build.gradle` applies `dev.architectury.loom-no-remap`, skips the `mappings` dependency, and the final artifact comes from `shadowJar` (no `remapJar`). Fabric/NeoForge `build.gradle` for 26.x already reflect this — copy from a 26.x base, not from 1.21.x
- Gradle 9 removed `Project.exec()` — use the `ExecOperations` injection pattern (see `gradle/multi-version-tasks.gradle`) in any new build logic that spawns processes
- The Gradle daemon runs on Java 25 via `gradle/gradle-daemon-jvm.properties` (required for 26.x); old versions still compile with their own toolchains (Java 8/16/17/21)
