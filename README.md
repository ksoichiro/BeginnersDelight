# Beginner's Delight

**A starter base mod for Minecraft beginners**

A multi-loader Minecraft mod that generates a small starter house at the world spawn point, helping new players survive their first night safely.

![Beginner's Delight Overview](docs/screenshots/featured-for-readme.png)

## Features

- **Starter House at Spawn**: Automatically generates a small shelter at the world spawn point on first world creation
- **Survival Essentials**: The house includes a bed, a chest with starter items (food, tools, torches), and lighting
- **Multiple Variants**: Randomly selects from multiple house designs for variety
- **Multiplayer Support**: All players spawn at the same location with the starter house
- **Safe Removal**: Uses only vanilla blocks, so the structure remains intact even after removing the mod
- **No Regeneration**: The house is generated only once and never duplicated

## Supported Versions

| Minecraft | Mod Loader | Dependencies |
|-----------|-----------|--------------|
| 1.21.1 | Fabric Loader 0.17.3+ with Fabric API 0.116.7+1.21.1 | Architectury API 13.0.8+ |
| 1.21.1 | NeoForge 21.1.209+ | Architectury API 13.0.8+ |
| 1.20.1 | Fabric Loader 0.17.3+ with Fabric API 0.92.2+1.20.1 | Architectury API 9.2.14+ |
| 1.20.1 | Forge 47.4.0+ | Architectury API 9.2.14+ |
| 1.19.2 | Fabric Loader 0.17.3+ with Fabric API 0.77.0+1.19.2 | Architectury API 6.6.92+ |
| 1.19.2 | Forge 43.4.0+ | Architectury API 6.6.92+ |

## Requirements

### For Players
- **Minecraft**: Java Edition 1.21.1, 1.20.1, or 1.19.2
- **Mod Loader** (choose one for your Minecraft version):
  - **1.21.1**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.1.209+
  - **1.20.1**: Fabric Loader 0.17.3+ with Fabric API, OR Forge 47.4.0+
  - **1.19.2**: Fabric Loader 0.17.3+ with Fabric API, OR Forge 43.4.0+
- **Dependencies**:
  - Architectury API (13.0.8+ for 1.21.1, 9.2.14+ for 1.20.1, 6.6.92+ for 1.19.2)

### For Developers
- **Java Development Kit (JDK)**: 21 or higher
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

## Building from Source

```bash
git clone https://github.com/ksoichiro/BeginnersDelight.git
cd BeginnersDelight
./gradlew build
```

**Build for a specific version**:
```bash
./gradlew build -Ptarget_mc_version=1.20.1
```

**Output Files** (1.21.1):
- `fabric-1.21.1/build/libs/beginnersdelight-0.1.0-fabric.jar` - Fabric loader JAR
- `neoforge-1.21.1/build/libs/beginnersdelight-0.1.0-neoforge.jar` - NeoForge loader JAR

**Output Files** (1.20.1):
- `fabric-1.20.1/build/libs/beginnersdelight-0.1.0-fabric.jar` - Fabric loader JAR
- `forge-1.20.1/build/libs/beginnersdelight-0.1.0-forge.jar` - Forge loader JAR

## Development Setup

### Import to IDE

#### IntelliJ IDEA (Recommended)
1. Open IntelliJ IDEA
2. File → Open → Select `build.gradle` in project root
3. Choose "Open as Project"
4. Wait for Gradle sync to complete

### Run in Development Environment

```bash
# Fabric client (1.21.1)
./gradlew :fabric:runClient

# NeoForge client (1.21.1)
./gradlew :neoforge:runClient

# Fabric client (1.20.1)
./gradlew :fabric:runClient -Ptarget_mc_version=1.20.1

# Forge client (1.20.1)
./gradlew :forge:runClient -Ptarget_mc_version=1.20.1

# Fabric client (1.19.2)
./gradlew :fabric:runClient -Ptarget_mc_version=1.19.2

# Forge client (1.19.2)
./gradlew :forge:runClient -Ptarget_mc_version=1.19.2
```

## Installation

### For Minecraft 1.21.1

#### Fabric
1. Install Minecraft 1.21.1
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.116.7+1.21.1
4. Download and install Architectury API 13.0.8+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.1
2. Install NeoForge 21.1.209+
3. Download and install Architectury API 13.0.8+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.20.1

#### Fabric
1. Install Minecraft 1.20.1
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.92.2+1.20.1
4. Download and install Architectury API 9.2.14+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### Forge
1. Install Minecraft 1.20.1
2. Install Forge 47.4.0+
3. Download and install Architectury API 9.2.14+
4. Copy the Forge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with Forge profile

### For Minecraft 1.19.2

#### Fabric
1. Install Minecraft 1.19.2
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.77.0+1.19.2
4. Download and install Architectury API 6.6.92+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### Forge
1. Install Minecraft 1.19.2
2. Install Forge 43.4.0+
3. Download and install Architectury API 6.6.92+
4. Copy the Forge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with Forge profile

## Project Structure

```
BeginnersDelight/
├── common-shared/           # Shared version-agnostic sources (included via srcDir)
├── common-1.21.1/           # Common module for MC 1.21.1
│   └── src/main/
│       ├── java/com/beginnersdelight/
│       │   ├── BeginnersDelight.java    # Common entry point
│       │   ├── worldgen/                # Structure generation logic
│       │   └── registry/                # Registry management
│       └── resources/
│           └── data/beginnersdelight/   # Structures, loot tables
├── common-1.20.1/           # Common module for MC 1.20.1
├── common-1.19.2/           # Common module for MC 1.19.2
├── fabric-base/             # Shared Fabric sources
├── fabric-1.21.1/           # Fabric subproject for MC 1.21.1
├── fabric-1.20.1/           # Fabric subproject for MC 1.20.1
├── fabric-1.19.2/           # Fabric subproject for MC 1.19.2
├── neoforge-base/           # Shared NeoForge sources
├── neoforge-1.21.1/         # NeoForge subproject for MC 1.21.1
├── forge-base/              # Shared Forge sources
├── forge-1.20.1/            # Forge subproject for MC 1.20.1
├── forge-1.19.2/            # Forge subproject for MC 1.19.2
├── props/                   # Version-specific properties
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

**Developed for Minecraft Java Edition 1.21.1 / 1.20.1 / 1.19.2**
