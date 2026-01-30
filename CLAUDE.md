# Beginner's Delight Development Guidelines

## License Compliance

**Project License**: LGPL-3.0-only (GNU Lesser General Public License v3.0)

**Quick Reference**:
- Compatible licenses: MIT, Apache 2.0, BSD
- Incompatible licenses: CC-BY-NC, proprietary

---

## Active Technologies

- Java 21 (Minecraft Java Edition 1.21.1) + NeoForge 21.1.x, Fabric Loader, Architectury API

## Project Structure

```
common-shared/        (shared version-agnostic sources, NOT a Gradle subproject)
common-1.21.1/        (version-specific common module)
fabric-base/          (shared Fabric sources, NOT a Gradle subproject)
fabric-1.21.1/        (version-specific Fabric subproject)
neoforge-base/        (shared NeoForge sources, NOT a Gradle subproject)
neoforge-1.21.1/      (version-specific NeoForge subproject)
props/                (version-specific properties)
docs/                 (documentation)
```

## Mod Info

- **Mod ID**: `beginnersdelight`
- **Package**: `com.beginnersdelight`
- **Minecraft**: 1.21.1
- **Architectury API**: 13.0.8
- **Fabric Loader**: 0.17.3
- **Fabric API**: 0.116.7+1.21.1
- **NeoForge**: 21.1.209

## Build Configuration

- **Build DSL**: Groovy DSL (not Kotlin DSL) - for compatibility with Architectury Loom
- **Mappings**: Mojang mappings (not Yarn) - code uses official Minecraft class names (e.g., `net.minecraft.core.Registry`)
- **Shadow Plugin**: com.gradleup.shadow - for bundling common module into platform-specific JARs

## Commands

**Build**:
- `./gradlew build` - Build for 1.21.1

**Run Client**:
- Fabric: `./gradlew :fabric:runClient`
- NeoForge: `./gradlew :neoforge:runClient`

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
