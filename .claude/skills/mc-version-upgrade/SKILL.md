---
name: mc-version-upgrade
description: Add support for a new Minecraft version. Use when adding a new MC version to the mod (e.g., "add 1.21.10 support").
user_invocable: true
---

# Minecraft Version Upgrade Procedure

Add support for a new Minecraft version to the Beginner's Delight mod.

## Required Input

Ask the user for the following before starting:

| Parameter | Example | Description |
|-----------|---------|-------------|
| MC version | 1.21.9 | Target Minecraft version |
| Base version | 1.21.8 | Closest existing version to copy from |
| NeoForge version | 21.9.16-beta | NeoForge version for the new MC version |
| Fabric API version | 0.134.1+1.21.9 | Fabric API version |
| Architectury API version | 18.0.5 | Architectury API version |
| pack_format | 88 | Data pack format number |

Fabric Loader version is typically kept at the existing value (currently 0.17.3).

## Step-by-step Procedure

### 1. Create `props/{version}.properties`

Copy from `props/{base_version}.properties` and update:
- `minecraft_version`
- `pack_format`
- `neoforge_version`
- `architectury_api_version`
- `fabric_api_version`

### 2. Create `common-{version}/`

Copy entire `common-{base_version}/` directory. Files included:
- `build.gradle` (version-independent, usually no changes needed)
- `src/main/java/com/beginnersdelight/` (3 Java files)
- `src/main/resources/pack.mcmeta` (check if format changed — see note below)
- `src/main/resources/data/beginnersdelight/loot_table/chests/starter_house.json`
- `src/main/resources/data/beginnersdelight/structure/*.nbt` (6 files)

### 3. Create `fabric-{version}/`

- `build.gradle`: Copy from base, change `commonModule = ':common-{version}'`
- `gradle.properties`: Empty file (just a newline)
- `src/main/resources/fabric.mod.json`: Update `minecraft` version constraint (`~{version}`), `architectury` version constraint (`>={arch_version}`)

### 4. Create `neoforge-{version}/`

- `build.gradle`: Copy from base, change `commonModule = ':common-{version}'`
- `gradle.properties`: Contains only `loom.platform=neoforge`
- `src/main/resources/META-INF/neoforge.mods.toml`: Update `neoforge` versionRange (`[{neoforge_major}.0,)`), `minecraft` versionRange (`[{version}]`), `architectury` versionRange (`[{arch_version},)`)

### 5. Update `gradle.properties` (root)

Change `target_mc_version` to the new version (new default).

### 6. Update `gradle/multi-version-tasks.gradle`

Add the new version to the `supportedVersions` list.

### 7. Update documentation

All of these need the new version added in the appropriate places:

- **CLAUDE.md**: Active Technologies, Project Structure, Mod Info, Commands (Build/Run), Development Notes (if API changes)
- **README.md**: Supported Versions table, Requirements, Output Files (default version), Run Commands, Installation section, Project Structure, Technical Notes path, footer
- **CHANGELOG.md**: Add entry under `[Unreleased]`
- **docs/modrinth_description.md**: Version list, Requirements, footer
- **docs/curseforge_description.md**: Version list, Requirements, footer

### 8. Build and verify

```bash
./gradlew build -Ptarget_mc_version={version}
```

Fix any compilation errors from API changes. Common API changes between MC versions:
- Method renames/removals in `ServerLevel`, `ServerPlayer`, `CompoundTag`, `SavedData`
- `pack.mcmeta` format changes (e.g., MC 1.21.9 requires `min_format`/`max_format` instead of `pack_format` for format >= 82)

### 9. Record API changes

If API changes were needed, add a Development Notes entry to `CLAUDE.md` documenting the changes (following the existing format of "MC {version} API differences from {base_version}").

## Important Notes

- Java source code is often identical to the base version — API changes are the exception, not the rule
- Always check `pack.mcmeta` format requirements for the new MC version
- The `settings.gradle` dynamically resolves modules from `target_mc_version`, so it does not need editing
- NeoForge subprojects **must** have `loom.platform=neoforge` in `gradle.properties`
- Fabric Loader should stay at the current version unless there's a specific reason to update (e.g., 0.18.x has breaking changes)
