# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Build a house for every player in Village Mode. Players teleported to the starter house were bound to it instead, so no player house, path or decoration was ever generated. Affected worlds are repaired on the next server start, moving those players into their own house when they rejoin
- Stop a brand-new player from taking over the starter house before a returning resident who lived there earlier gets a chance to rejoin. The join handlers ran in an order that made a first-time joiner look like a returning resident
- Stop village paths from plunging to the bottom of a ravine or cave mouth they cross. Each column was paved at whatever ground it found scanning straight down from the world top, so an opening to the sky sent the path dozens of blocks below its neighbors; it now bridges across at the surrounding path height instead

## [0.6.0] - 2026-08-25

### Added

- Per-world control over starter house generation via a new `beginnersdelight:generate_starter_house` game rule. Turn it off in the "Game Rules" screen while creating a world, or change it later with `/gamerule beginnersdelight:generate_starter_house <true|false>`. New worlds default to on; set `[starter_house] auto_generate = false` in `config/beginnersdelight.toml` to make them default to off instead. Existing worlds are unaffected — a house that has already been generated is never removed, and turning the rule on later generates one on the next server start. Available on all supported versions (Fabric, NeoForge, and Forge)

### Changed

- Fill the extra containers of a starter house (and village-mode player house) with early-game supplies (coal, oak planks, torches, wheat seeds) instead of a duplicate set of wooden tools. The starter kit goes to the first chest — or to the first barrel in the one design that has no chest — and every other container holds supplies

### Fixed

- Stop the starter house and village-mode buildings from being built below the waterline, and flooding, when they generate on the shore of an ocean, lake or river
- Stop dirt and grass blocks from being left floating in mid-air when a building generates in a bamboo jungle. Bamboo, sugar cane, cactus and sweet berry bushes are no longer mistaken for the ground surface, so the starter house also stays at the world spawn there instead of relocating away from it, and village paths pave columns where bamboo grows
- Stop bamboo from collapsing with break sounds and scattered drops around a freshly generated building. Stalks standing where the ground is reshaped are now taken down cleanly, and bamboo outside that area is left standing
- Stop mushroom items from being left floating around a freshly generated building. Mushrooms (and huge mushrooms) are now cleared along with the rest of the vegetation, and are no longer mistaken for the ground surface
- Stop a building in a snowy biome from standing in a sharply outlined snow-free rectangle. Snow and other thin ground cover (moss carpet, pink petals, pale moss carpet) is now laid back down after the terrain is reshaped, and the ground underneath keeps its snowed-over look in step with what lies on top. Columns that had no cover to begin with stay bare, so the ground keeps its natural patchiness, and ground sheltered under a roof overhang stays clear like it does around a vanilla village
- Let village-mode paths pave through snow and other thin ground cover. A snow layer counted as the ground surface, so in a snowy biome almost no path block was placed at all; the cover is now removed before the column is paved, without leaving snowballs behind

## [0.5.0] - 2026-07-17

### Added

- Village Mode — an opt-in feature that grows a village around the world spawn as players join. Enable it with `/beginnersdelight village enable`; each new player gets their own house connected by dirt paths, with decoration buildings (well, shed, storehouse, farm) placed periodically. Includes per-player house binding with respawn-at-house support, `status`/`test` commands, and a `config/beginnersdelight.toml` config file (plot size, height tolerance, path generation, respawn behavior) that can be re-read at runtime with `/beginnersdelight config reload`. Available on all supported versions (Fabric, NeoForge, and Forge)
- Minecraft 26.2 support (Fabric + NeoForge)
- Minecraft 26.1.2 support (Fabric + NeoForge)
- Minecraft 26.1.1 support (Fabric + NeoForge)
- Minecraft 26.1 support (Fabric + NeoForge)
- Migrate saved generation state to the new `data/<namespace>/<path>.dat` layout used by MC 26.1+, copying the legacy flat file so worlds upgraded from older versions do not regenerate the starter house or village

### Fixed

- Improve starter house terrain blending: flatten the surrounding terrain before filling the foundation and carve down corner pillars to match the surrounding ground, preventing tall corner pillars on uneven terrain
- Relocate the starter house to nearby flat, solid ground when the world spawn lands over a large void such as a dripstone cave (or on uneven pit/ravine edges), where the detected ground was only a thin ceiling or spike above empty space and terrain blending produced a broken, spike-covered foundation (the world spawn follows the house, so the player still spawns inside)
- Stop houses from floating: only raise placement to sea level when the ground is actually submerged (ocean/lake). Dry ground that merely sits below sea level (deep valleys, cave areas) now keeps its real height instead of being lifted to sea level over an open void
- Avoid placing village houses over large voids such as dripstone caves, where the detected ground is only a thin ceiling or spike above empty space and terrain blending produced broken, spike-covered foundations
- Stop terrain blending and foundation filling from extending dirt into adjacent voids (cave edges next to a house), which produced unnatural dirt pillars, floating slabs, and lone floating surface blocks hanging into the cave; such columns are now left as a natural cliff and surface caps are only placed where there is solid support beneath them
- Replace bare dirt left on the surface when leveling and blending terrain around starter houses and village buildings with grass: any surface dirt connected to surrounding grass now becomes grass, so the generated ground blends naturally instead of showing patches of dirt next to grass

## [0.4.0] - 2026-02-22

### Changed

- Remove Architectury API runtime dependency — mod now depends only on vanilla Minecraft APIs

### Fixed

- Clear tree trunks and thin covers with leaves in vegetation clearing during structure placement
- Remove mobs before structure placement to prevent trapping inside structures

## [0.3.0] - 2026-02-06

### Added

- Minecraft 1.21.11 support (Fabric + NeoForge)
- Minecraft 1.21.10 support (Fabric + NeoForge)
- Minecraft 1.21.9 support (Fabric + NeoForge)
- Minecraft 1.21.8 support (Fabric + NeoForge)
- Minecraft 1.21.7 support (Fabric + NeoForge)
- Minecraft 1.21.6 support (Fabric + NeoForge)

### Fixed

- Prevent structures from floating on snow layers by placing foundation beneath snow
- Preserve surrounding snow blocks during structure placement
- Prevent item drops during structure placement (vegetation removal)

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

[Unreleased]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/ksoichiro/BeginnersDelight/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/ksoichiro/BeginnersDelight/releases/tag/v0.1.0
