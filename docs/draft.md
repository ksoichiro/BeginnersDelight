# Beginner's Delight

## Overview
- A mod to help beginners proceed safely through early-game adventures
- The mod name is "Beginner's Delight"
- Upon initial spawn, generates a small structure (base) at the world spawn point
- Provides items that are helpful to have initially, such as beds and chests

## Design
- Structures are prepared as NBT files, with multiple options randomly selected for generation
- No regeneration occurs
- Supports multiplayer
    - Generated at world creation or when the first player joins
    - Spawn point is fixed so that second and subsequent players spawn at the same location

## Architecture
- Minecraft mod using Architectury
- Compatible with both Fabric and NeoForge
- Initially implemented for Minecraft version 1.21.1
- Aims to continue working after mod removal
    - Accepts that some features like spawn point fixing will not function when the mod is removed
- The project will be configured with Gradle as a multi-project setup

## Directory

- common/shared
    - Common code without loader dependencies or version dependencies. Not a Gradle subproject, but incorporated as one of the srcDirs from each version-specific subproject
- common/1.21.1
    - Common code for Minecraft 1.21.1 without loader dependencies. Gradle subproject.
- fabric/base
    - Code for Fabric without Minecraft version dependencies. Gradle subproject.
- fabric/1.21.1
    - Code for Fabric and Minecraft 1.21.1. Gradle subproject. Depends on fabric/base.
- neoforge/base
    - Code for NeoForge without Minecraft version dependencies. Gradle subproject.
- neoforge/1.21.1
    - Code for NeoForge and Minecraft 1.21.1. Gradle subproject. Depends on neoforge/base.

## Misc

- License is LGPL-3.0-only
