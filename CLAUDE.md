# Beginner's Delight Development Guidelines

## License Compliance

**Project License**: LGPL-3.0-only (GNU Lesser General Public License v3.0)

**Quick Reference**:
- Compatible licenses: MIT, Apache 2.0, BSD
- Incompatible licenses: CC-BY-NC, proprietary

---

## Active Technologies

- Java 21 (Minecraft Java Edition 1.21.1) + NeoForge 21.1.x, Fabric Loader, Architectury API
- Java 17 (Minecraft Java Edition 1.20.1) + Forge 47.4.x, Fabric Loader, Architectury API
- Java 17 (Minecraft Java Edition 1.19.2) + Forge 43.4.x, Fabric Loader, Architectury API
- Java 17 (Minecraft Java Edition 1.18.2) + Forge 40.2.x, Fabric Loader, Architectury API

## Project Structure

```
common-shared/        (shared version-agnostic sources, NOT a Gradle subproject)
common-1.21.1/        (version-specific common module for 1.21.1)
common-1.20.1/        (version-specific common module for 1.20.1)
common-1.19.2/        (version-specific common module for 1.19.2)
common-1.18.2/        (version-specific common module for 1.18.2)
fabric-base/          (shared Fabric sources, NOT a Gradle subproject)
fabric-1.21.1/        (version-specific Fabric subproject)
fabric-1.20.1/        (version-specific Fabric subproject)
fabric-1.19.2/        (version-specific Fabric subproject)
fabric-1.18.2/        (version-specific Fabric subproject)
neoforge-base/        (shared NeoForge sources, NOT a Gradle subproject)
neoforge-1.21.1/      (version-specific NeoForge subproject)
forge-base/           (shared Forge sources, NOT a Gradle subproject)
forge-1.20.1/         (version-specific Forge subproject)
forge-1.19.2/         (version-specific Forge subproject)
forge-1.18.2/         (version-specific Forge subproject)
props/                (version-specific properties)
docs/                 (documentation)
```

## Mod Info

- **Mod ID**: `beginnersdelight`
- **Package**: `com.beginnersdelight`
- **Minecraft**: 1.21.1, 1.20.1, 1.19.2, 1.18.2
- **Architectury API**: 13.0.8 (1.21.1), 9.2.14 (1.20.1), 6.6.92 (1.19.2), 4.12.94 (1.18.2)
- **Fabric Loader**: 0.17.3
- **Fabric API**: 0.116.7+1.21.1 (1.21.1), 0.92.2+1.20.1 (1.20.1), 0.77.0+1.19.2 (1.19.2), 0.76.0+1.18.2 (1.18.2)
- **NeoForge**: 21.1.209 (1.21.1 only)
- **Forge**: 47.4.0 (1.20.1), 43.4.0 (1.19.2), 40.2.0 (1.18.2)

## Build Configuration

- **Build DSL**: Groovy DSL (not Kotlin DSL) - for compatibility with Architectury Loom
- **Mappings**: Mojang mappings (not Yarn) - code uses official Minecraft class names (e.g., `net.minecraft.core.Registry`)
- **Shadow Plugin**: com.gradleup.shadow - for bundling common module into platform-specific JARs

## Commands

**Build**:
- `./gradlew build` - Build for default version (1.21.1)
- `./gradlew build -Ptarget_mc_version=1.20.1` - Build for specific version
- `./gradlew buildAll` - Build for all supported versions (1.18.2, 1.19.2, 1.20.1, 1.21.1)

**Clean**:
- `./gradlew cleanAll` - Clean all supported versions

**Run Client** (1.21.1):
- Fabric: `./gradlew :fabric:runClient`
- NeoForge: `./gradlew :neoforge:runClient`

**Run Client** (1.20.1):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.20.1`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.20.1`

**Run Client** (1.19.2):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.19.2`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.19.2`

**Run Client** (1.18.2):
- Fabric: `./gradlew :fabric:runClient -Ptarget_mc_version=1.18.2`
- Forge: `./gradlew :forge:runClient -Ptarget_mc_version=1.18.2`

## Code Style

- Java 21: Follow standard conventions
- Use Mojang mapping names (e.g., `net.minecraft.world.level.Level`, not Yarn's `class_XXXX`)
- Build files use Groovy syntax (e.g., `maven { url 'https://...' }`, not `maven { url = "https://..." }`)
- Common module code is bundled into platform JARs using Shadow plugin

## Development Notes

- NeoForge subprojects require `loom.platform=neoforge` in their `gradle.properties` — without this, Architectury Loom does not create the `neoForge` dependency configuration
- Structures use only vanilla blocks to ensure compatibility after mod removal
- Structure generation state is persisted using `SavedData` to prevent regeneration
- NBT structure files are placed in `common-1.21.1/src/main/resources/data/beginnersdelight/structure/`

## Workflow Guidelines

### Pre-Commit Verification

- **Verification Check**: Before committing changes, determine if the changes are testable/verifiable
- **Present Verification Steps**: When changes are verifiable, present to the user:
  1. Verification method (build, run, test command, etc.)
  2. Step-by-step instructions
  3. Expected results/success criteria
- **Wait for User Decision**: Allow user to decide whether to proceed with verification before committing
