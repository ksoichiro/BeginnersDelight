# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Minecraft 1.21.6 support (Fabric + NeoForge)
- Minecraft 1.21.7 support (Fabric + NeoForge)
- Minecraft 1.21.8 support (Fabric + NeoForge)

## [0.2.0] - 2026-02-02

### Added

#### Structure Generation Improvements
- Match foundation fill blocks to surrounding terrain (sand→sandstone, gravel→stone, grass→grass block, etc.)
- Blend surrounding terrain for gradual transitions instead of abrupt cliffs
- Remove item entities dropped by destroyed vegetation during placement
- Convert exposed dirt to grass in foundation margins
- Extend foundation fill 2 blocks beyond structure footprint

#### New Minecraft Version Support
- Minecraft 1.21.5 support (Fabric + NeoForge) — Codec-based SavedData serialization, Optional CompoundTag getters, new respawn API
- Minecraft 1.21.4 support (Fabric + NeoForge)
- Minecraft 1.21.3 support (Fabric + NeoForge) — adapted to API changes (`getHeight()`/`getMinY()`, new `teleportTo()` signature)
- Minecraft 1.19.2 support (Fabric + Forge) — Architectury API 6.6.92, Forge 43.4.0
- Minecraft 1.18.2 support (Fabric + Forge) — Architectury API 4.12.94, Forge 40.2.0
- Minecraft 1.17.1 support (Fabric + Forge) — Java 16, Architectury API 2.10.12
- Minecraft 1.16.5 support (Fabric + Forge) — Java 8, Architectury API v1, Log4j2

### Fixed
- Prevent starter house from generating below sea level by clamping placement Y to at least sea level
- Replace water blocks with dirt in foundation area for natural appearance
- Fix Forge runClient toolchain to use Java toolchain launcher (prevents ASM errors with Java 21)

## [0.1.0] - 2026-01-31

### Added
- Initial release
- Starter house structure that generates near new player spawn points
- 6 house variants using only vanilla blocks for safe mod removal
- Multi-loader support: Fabric and Forge (Minecraft 1.20.1), Fabric and NeoForge (Minecraft 1.21.1)
- Structure generation state persistence using SavedData to prevent regeneration
- One-time generation per world with per-player tracking

[Unreleased]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/ksoichiro/BeginnersDelight/releases/tag/v0.1.0
