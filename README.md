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
| 1.21.11 | Fabric Loader 0.17.3+ with Fabric API 0.141.3+1.21.11 | Architectury API 19.0.1+ |
| 1.21.11 | NeoForge 21.11.37-beta+ | Architectury API 19.0.1+ |
| 1.21.10 | Fabric Loader 0.17.3+ with Fabric API 0.138.4+1.21.10 | Architectury API 18.0.8+ |
| 1.21.10 | NeoForge 21.10.64+ | Architectury API 18.0.8+ |
| 1.21.9 | Fabric Loader 0.17.3+ with Fabric API 0.134.1+1.21.9 | Architectury API 18.0.5+ |
| 1.21.9 | NeoForge 21.9.16-beta+ | Architectury API 18.0.5+ |
| 1.21.8 | Fabric Loader 0.17.3+ with Fabric API 0.136.1+1.21.8 | Architectury API 17.0.8+ |
| 1.21.8 | NeoForge 21.8.52+ | Architectury API 17.0.8+ |
| 1.21.7 | Fabric Loader 0.17.3+ with Fabric API 0.129.0+1.21.7 | Architectury API 17.0.8+ |
| 1.21.7 | NeoForge 21.7.2-beta+ | Architectury API 17.0.8+ |
| 1.21.6 | Fabric Loader 0.17.3+ with Fabric API 0.128.1+1.21.6 | Architectury API 17.0.6+ |
| 1.21.6 | NeoForge 21.6.20-beta+ | Architectury API 17.0.6+ |
| 1.21.5 | Fabric Loader 0.17.3+ with Fabric API 0.128.1+1.21.5 | Architectury API 16.1.4+ |
| 1.21.5 | NeoForge 21.5.96+ | Architectury API 16.1.4+ |
| 1.21.4 | Fabric Loader 0.17.3+ with Fabric API 0.119.4+1.21.4 | Architectury API 15.0.1+ |
| 1.21.4 | NeoForge 21.4.156+ | Architectury API 15.0.1+ |
| 1.21.3 | Fabric Loader 0.17.3+ with Fabric API 0.112.1+1.21.3 | Architectury API 14.0.4+ |
| 1.21.3 | NeoForge 21.3.95+ | Architectury API 14.0.4+ |
| 1.21.1 | Fabric Loader 0.17.3+ with Fabric API 0.116.7+1.21.1 | Architectury API 13.0.8+ |
| 1.21.1 | NeoForge 21.1.209+ | Architectury API 13.0.8+ |
| 1.20.1 | Fabric Loader 0.17.3+ with Fabric API 0.92.2+1.20.1 | Architectury API 9.2.14+ |
| 1.20.1 | Forge 47.4.0+ | Architectury API 9.2.14+ |
| 1.19.2 | Fabric Loader 0.17.3+ with Fabric API 0.77.0+1.19.2 | Architectury API 6.6.92+ |
| 1.19.2 | Forge 43.4.0+ | Architectury API 6.6.92+ |
| 1.18.2 | Fabric Loader 0.17.3+ with Fabric API 0.76.0+1.18.2 | Architectury API 4.12.94+ |
| 1.18.2 | Forge 40.2.0+ | Architectury API 4.12.94+ |
| 1.17.1 | Fabric Loader 0.17.3+ with Fabric API 0.46.1+1.17 | Architectury API 2.10.12+ |
| 1.17.1 | Forge 37.1.1+ | Architectury API 2.10.12+ |
| 1.16.5 | Fabric Loader 0.17.3+ with Fabric API 0.42.0+1.16 | Architectury API 1.32.68+ |
| 1.16.5 | Forge 36.2.34+ | Architectury API 1.32.68+ |

## Requirements

### For Players
- **Minecraft**: Java Edition 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.3, 1.21.1, 1.20.1, 1.19.2, 1.18.2, 1.17.1, or 1.16.5
- **Mod Loader** (choose one for your Minecraft version):
  - **1.21.11**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.11.37-beta+
  - **1.21.10**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.10.64+
  - **1.21.9**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.9.16-beta+
  - **1.21.8**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.8.52+
  - **1.21.7**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.7.2-beta+
  - **1.21.6**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.6.20-beta+
  - **1.21.5**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.5.96+
  - **1.21.4**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.4.156+
  - **1.21.3**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.3.95+
  - **1.21.1**: Fabric Loader 0.17.3+ with Fabric API, OR NeoForge 21.1.209+
  - **1.20.1**: Fabric Loader 0.17.3+ with Fabric API, OR Forge 47.4.0+
  - **1.19.2**: Fabric Loader 0.17.3+ with Fabric API, OR Forge 43.4.0+
  - **1.18.2**: Fabric Loader 0.17.3+ with Fabric API, OR Forge 40.2.0+
  - **1.17.1**: Fabric Loader 0.17.3+ with Fabric API, OR Forge 37.1.1+
  - **1.16.5**: Fabric Loader 0.17.3+ with Fabric API, OR Forge 36.2.34+
- **Dependencies**:
  - Architectury API (19.0.1+ for 1.21.11, 18.0.8+ for 1.21.10, 18.0.5+ for 1.21.9, 17.0.8+ for 1.21.8, 17.0.8+ for 1.21.7, 17.0.6+ for 1.21.6, 16.1.4+ for 1.21.5, 15.0.1+ for 1.21.4, 14.0.4+ for 1.21.3, 13.0.8+ for 1.21.1, 9.2.14+ for 1.20.1, 6.6.92+ for 1.19.2, 4.12.94+ for 1.18.2, 2.10.12+ for 1.17.1, 1.32.68+ for 1.16.5)

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

**Output Files** (1.21.11):
- `fabric-1.21.11/build/libs/beginnersdelight-0.2.0-fabric.jar` - Fabric loader JAR
- `neoforge-1.21.11/build/libs/beginnersdelight-0.2.0-neoforge.jar` - NeoForge loader JAR

**Output Files** (1.20.1):
- `fabric-1.20.1/build/libs/beginnersdelight-0.2.0-fabric.jar` - Fabric loader JAR
- `forge-1.20.1/build/libs/beginnersdelight-0.2.0-forge.jar` - Forge loader JAR

## Development Setup

### Import to IDE

#### IntelliJ IDEA (Recommended)
1. Open IntelliJ IDEA
2. File → Open → Select `build.gradle` in project root
3. Choose "Open as Project"
4. Wait for Gradle sync to complete

### Run in Development Environment

```bash
# Fabric client (1.21.11)
./gradlew :fabric:runClient

# NeoForge client (1.21.11)
./gradlew :neoforge:runClient

# Fabric client (1.21.10)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.10

# NeoForge client (1.21.10)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.10

# Fabric client (1.21.9)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.9

# NeoForge client (1.21.9)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.9

# Fabric client (1.21.8)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.8

# NeoForge client (1.21.8)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.8

# Fabric client (1.21.7)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.7

# NeoForge client (1.21.7)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.7

# Fabric client (1.21.6)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.6

# NeoForge client (1.21.6)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.6

# Fabric client (1.21.5)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.5

# NeoForge client (1.21.5)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.5

# Fabric client (1.21.4)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.4

# NeoForge client (1.21.4)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.4

# Fabric client (1.21.3)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.3

# NeoForge client (1.21.3)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.3

# Fabric client (1.21.1)
./gradlew :fabric:runClient -Ptarget_mc_version=1.21.1

# NeoForge client (1.21.1)
./gradlew :neoforge:runClient -Ptarget_mc_version=1.21.1

# Fabric client (1.20.1)
./gradlew :fabric:runClient -Ptarget_mc_version=1.20.1

# Forge client (1.20.1)
./gradlew :forge:runClient -Ptarget_mc_version=1.20.1

# Fabric client (1.19.2)
./gradlew :fabric:runClient -Ptarget_mc_version=1.19.2

# Forge client (1.19.2)
./gradlew :forge:runClient -Ptarget_mc_version=1.19.2

# Fabric client (1.18.2)
./gradlew :fabric:runClient -Ptarget_mc_version=1.18.2

# Forge client (1.18.2)
./gradlew :forge:runClient -Ptarget_mc_version=1.18.2

# Fabric client (1.17.1)
./gradlew :fabric:runClient -Ptarget_mc_version=1.17.1

# Forge client (1.17.1)
./gradlew :forge:runClient -Ptarget_mc_version=1.17.1

# Fabric client (1.16.5)
./gradlew :fabric:runClient -Ptarget_mc_version=1.16.5

# Forge client (1.16.5)
./gradlew :forge:runClient -Ptarget_mc_version=1.16.5
```

## Installation

### For Minecraft 1.21.11

#### Fabric
1. Install Minecraft 1.21.11
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.141.3+1.21.11
4. Download and install Architectury API 19.0.1+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.11
2. Install NeoForge 21.11.37-beta+
3. Download and install Architectury API 19.0.1+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.10

#### Fabric
1. Install Minecraft 1.21.10
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.138.4+1.21.10
4. Download and install Architectury API 18.0.8+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.10
2. Install NeoForge 21.10.64+
3. Download and install Architectury API 18.0.8+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.9

#### Fabric
1. Install Minecraft 1.21.9
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.134.1+1.21.9
4. Download and install Architectury API 18.0.5+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.9
2. Install NeoForge 21.9.16-beta+
3. Download and install Architectury API 18.0.5+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.8

#### Fabric
1. Install Minecraft 1.21.8
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.136.1+1.21.8
4. Download and install Architectury API 17.0.8+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.8
2. Install NeoForge 21.8.52+
3. Download and install Architectury API 17.0.8+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.7

#### Fabric
1. Install Minecraft 1.21.7
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.129.0+1.21.7
4. Download and install Architectury API 17.0.8+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.7
2. Install NeoForge 21.7.2-beta+
3. Download and install Architectury API 17.0.8+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.6

#### Fabric
1. Install Minecraft 1.21.6
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.128.1+1.21.6
4. Download and install Architectury API 17.0.6+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.6
2. Install NeoForge 21.6.20-beta+
3. Download and install Architectury API 17.0.6+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.5

#### Fabric
1. Install Minecraft 1.21.5
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.128.1+1.21.5
4. Download and install Architectury API 16.1.4+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.5
2. Install NeoForge 21.5.96+
3. Download and install Architectury API 16.1.4+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.4

#### Fabric
1. Install Minecraft 1.21.4
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.119.4+1.21.4
4. Download and install Architectury API 15.0.1+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.4
2. Install NeoForge 21.4.156+
3. Download and install Architectury API 15.0.1+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

### For Minecraft 1.21.3

#### Fabric
1. Install Minecraft 1.21.3
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.112.1+1.21.3
4. Download and install Architectury API 14.0.4+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### NeoForge
1. Install Minecraft 1.21.3
2. Install NeoForge 21.3.95+
3. Download and install Architectury API 14.0.4+
4. Copy the NeoForge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with NeoForge profile

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

### For Minecraft 1.18.2

#### Fabric
1. Install Minecraft 1.18.2
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.76.0+1.18.2
4. Download and install Architectury API 4.12.94+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### Forge
1. Install Minecraft 1.18.2
2. Install Forge 40.2.0+
3. Download and install Architectury API 4.12.94+
4. Copy the Forge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with Forge profile

### For Minecraft 1.17.1

#### Fabric
1. Install Minecraft 1.17.1
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.46.1+1.17
4. Download and install Architectury API 2.10.12+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### Forge
1. Install Minecraft 1.17.1
2. Install Forge 37.1.1+
3. Download and install Architectury API 2.10.12+
4. Copy the Forge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with Forge profile

### For Minecraft 1.16.5

#### Fabric
1. Install Minecraft 1.16.5
2. Install Fabric Loader 0.17.3+
3. Download and install Fabric API 0.42.0+1.16
4. Download and install Architectury API 1.32.68+
5. Copy the Fabric JAR to `.minecraft/mods/` folder
6. Launch Minecraft with Fabric profile

#### Forge
1. Install Minecraft 1.16.5
2. Install Forge 36.2.34+
3. Download and install Architectury API 1.32.68+
4. Copy the Forge JAR to `.minecraft/mods/` folder
5. Launch Minecraft with Forge profile

## Project Structure

```
BeginnersDelight/
├── common-shared/           # Shared version-agnostic sources (included via srcDir)
├── common-1.21.11/          # Common module for MC 1.21.11
├── common-1.21.10/          # Common module for MC 1.21.10
├── common-1.21.9/           # Common module for MC 1.21.9
├── common-1.21.8/           # Common module for MC 1.21.8
├── common-1.21.7/           # Common module for MC 1.21.7
│   └── src/main/
│       ├── java/com/beginnersdelight/
│       │   ├── BeginnersDelight.java    # Common entry point
│       │   ├── worldgen/                # Structure generation logic
│       │   └── registry/                # Registry management
│       └── resources/
│           └── data/beginnersdelight/   # Structures, loot tables
├── common-1.21.6/           # Common module for MC 1.21.6
├── common-1.21.5/           # Common module for MC 1.21.5
│   └── src/main/
│       ├── java/com/beginnersdelight/
│       │   ├── BeginnersDelight.java    # Common entry point
│       │   ├── worldgen/                # Structure generation logic
│       │   └── registry/                # Registry management
│       └── resources/
│           └── data/beginnersdelight/   # Structures, loot tables
├── common-1.21.4/           # Common module for MC 1.21.4
├── common-1.21.3/           # Common module for MC 1.21.3
├── common-1.21.1/           # Common module for MC 1.21.1
├── common-1.20.1/           # Common module for MC 1.20.1
├── common-1.19.2/           # Common module for MC 1.19.2
├── common-1.18.2/           # Common module for MC 1.18.2
├── common-1.17.1/           # Common module for MC 1.17.1
├── common-1.16.5/           # Common module for MC 1.16.5
├── fabric-base/             # Shared Fabric sources
├── fabric-1.21.11/          # Fabric subproject for MC 1.21.11
├── fabric-1.21.10/          # Fabric subproject for MC 1.21.10
├── fabric-1.21.9/           # Fabric subproject for MC 1.21.9
├── fabric-1.21.8/           # Fabric subproject for MC 1.21.8
├── fabric-1.21.7/           # Fabric subproject for MC 1.21.7
├── fabric-1.21.6/           # Fabric subproject for MC 1.21.6
├── fabric-1.21.5/           # Fabric subproject for MC 1.21.5
├── fabric-1.21.4/           # Fabric subproject for MC 1.21.4
├── fabric-1.21.3/           # Fabric subproject for MC 1.21.3
├── fabric-1.21.1/           # Fabric subproject for MC 1.21.1
├── fabric-1.20.1/           # Fabric subproject for MC 1.20.1
├── fabric-1.19.2/           # Fabric subproject for MC 1.19.2
├── fabric-1.18.2/           # Fabric subproject for MC 1.18.2
├── fabric-1.17.1/           # Fabric subproject for MC 1.17.1
├── fabric-1.16.5/           # Fabric subproject for MC 1.16.5
├── neoforge-base/           # Shared NeoForge sources
├── neoforge-1.21.11/        # NeoForge subproject for MC 1.21.11
├── neoforge-1.21.10/        # NeoForge subproject for MC 1.21.10
├── neoforge-1.21.9/         # NeoForge subproject for MC 1.21.9
├── neoforge-1.21.8/         # NeoForge subproject for MC 1.21.8
├── neoforge-1.21.7/         # NeoForge subproject for MC 1.21.7
├── neoforge-1.21.6/         # NeoForge subproject for MC 1.21.6
├── neoforge-1.21.5/         # NeoForge subproject for MC 1.21.5
├── neoforge-1.21.4/         # NeoForge subproject for MC 1.21.4
├── neoforge-1.21.3/         # NeoForge subproject for MC 1.21.3
├── neoforge-1.21.1/         # NeoForge subproject for MC 1.21.1
├── forge-base/              # Shared Forge sources
├── forge-1.20.1/            # Forge subproject for MC 1.20.1
├── forge-1.19.2/            # Forge subproject for MC 1.19.2
├── forge-1.18.2/            # Forge subproject for MC 1.18.2
├── forge-1.17.1/            # Forge subproject for MC 1.17.1
├── forge-1.16.5/            # Forge subproject for MC 1.16.5
├── props/                   # Version-specific properties
├── build.gradle             # Root build configuration (Groovy DSL)
├── settings.gradle          # Multi-module settings
└── gradle.properties        # Version configuration
```

## Technical Notes

- **Build DSL**: Groovy DSL (for Architectury Loom compatibility)
- **Mappings**: Mojang mappings (official Minecraft class names)
- **Shadow Plugin**: Bundles common module into loader-specific JARs
- **Structure Files**: NBT format, placed in `common-1.21.11/src/main/resources/data/beginnersdelight/structure/`
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

**Developed for Minecraft Java Edition 1.21.11 / 1.21.10 / 1.21.9 / 1.21.8 / 1.21.7 / 1.21.6 / 1.21.5 / 1.21.4 / 1.21.3 / 1.21.1 / 1.20.1 / 1.19.2 / 1.18.2 / 1.17.1 / 1.16.5**
