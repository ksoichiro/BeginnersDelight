# Beginner's Delight

**A starter base mod for Minecraft beginners**

A multi-loader Minecraft mod that generates a small starter house at the world spawn point, helping new players survive their first night safely.

## Features

- **Starter House at Spawn**: Automatically generates a small shelter at the world spawn point on first world creation
- **Survival Essentials**: The house includes a bed, a chest with starter items (food, tools, torches), and lighting
- **Multiple Variants**: Randomly selects from multiple house designs for variety
- **Multiplayer Support**: All players spawn at the same location with the starter house
- **Safe Removal**: Uses only vanilla blocks, so the structure remains intact even after removing the mod
- **No Regeneration**: The house is generated only once and never duplicated

## Requirements

### For Players
- **Minecraft**: Java Edition 1.21.1
- **Mod Loader**:
  - Fabric Loader 0.17.3+ with Fabric API 0.116.7+1.21.1, OR
  - NeoForge 21.1.209+
- **Dependencies**:
  - Architectury API 13.0.8+

### For Developers
- **Java Development Kit (JDK)**: 21 or higher
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

## Building from Source

```bash
git clone https://github.com/ksoichiro/BeginnersDelight.git
cd BeginnersDelight
./gradlew build
```

**Output Files**:
- `fabric-1.21.1/build/libs/beginnersdelight-0.1.0-fabric.jar` - Fabric loader JAR
- `neoforge-1.21.1/build/libs/beginnersdelight-0.1.0-neoforge.jar` - NeoForge loader JAR

## Development Setup

### Import to IDE

#### IntelliJ IDEA (Recommended)
1. Open IntelliJ IDEA
2. File → Open → Select `build.gradle` in project root
3. Choose "Open as Project"
4. Wait for Gradle sync to complete

### Run in Development Environment

```bash
# Fabric client
./gradlew :fabric:runClient

# NeoForge client
./gradlew :neoforge:runClient
```

## Installation

### For Fabric
1. Install Minecraft 1.21.1
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.116.7+1.21.1
4. Download and install Architectury API 13.0.8+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

### For NeoForge
1. Install Minecraft 1.21.1
2. Install NeoForge 21.1.209+
3. Download and install Architectury API 13.0.8+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

## Project Structure

```
BeginnersDelight/
├── common-shared/           # Shared version-agnostic sources (included via srcDir)
├── common-1.21.1/           # Common module for MC 1.21.1 (~80% of code)
│   └── src/main/
│       ├── java/com/beginnersdelight/
│       │   ├── BeginnersDelight.java    # Common entry point
│       │   ├── worldgen/                # Structure generation logic
│       │   └── registry/                # Registry management
│       └── resources/
│           └── data/beginnersdelight/   # Structures, loot tables
├── fabric-base/             # Shared Fabric sources
├── fabric-1.21.1/           # Fabric subproject for MC 1.21.1
├── neoforge-base/           # Shared NeoForge sources
├── neoforge-1.21.1/         # NeoForge subproject for MC 1.21.1
├── build.gradle             # Root build configuration (Groovy DSL)
├── settings.gradle          # Multi-module settings
└── gradle.properties        # Version configuration
```

## Technical Notes

- **Build DSL**: Groovy DSL (for Architectury Loom compatibility)
- **Mappings**: Mojang mappings (official Minecraft class names)
- **Shadow Plugin**: Bundles common module into loader-specific JARs
- **Structure Files**: NBT format, placed in `common-1.21.1/src/main/resources/data/beginnersdelight/structure/`
- **Persistence**: Uses `SavedData` to prevent structure regeneration across server restarts

## License

This project is licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**.

Copyright (C) 2025 Soichiro Kashima

See the [COPYING](COPYING) and [COPYING.LESSER](COPYING.LESSER) files for full license text.

## Credits

- Built with [Architectury](https://github.com/architectury/architectury-api)

## Support

For issues, feature requests, or questions:
- Open an issue on [GitHub Issues](https://github.com/ksoichiro/BeginnersDelight/issues)

---

**Developed for Minecraft Java Edition 1.21.1**
