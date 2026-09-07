# Starter House Pool Validation Data Packs

Each subdirectory is an independent data pack. Enable only one of them while
creating a new test world. The starter house is placed only once per world, so
use a new world for every case.

The packs use Minecraft's old `pack_format` value of `6` so they can also be
opened by 1.16.5. Newer Minecraft versions may show a compatibility warning.
If the game refuses to enable a pack, copy the `pack_format` value from the
target version's `common/<version>/src/main/resources/pack.mcmeta` file.

On Minecraft 1.16.5, use a short test-world name. Forge may shorten long save
directory names, which would place a manually copied data pack in a different
directory from the world it opens.

## Cases

1. `01-add-remove` removes every bundled candidate, then adds
   `starter_house2` without using `replace`. The server log must report only
   `beginnersdelight:starter_house2`.
2. `02-replace-starter` replaces the pool with house 2 and uses `starter`
   loot. The generated container must receive the usual starter items.
3. `03-replace-preserve` replaces the pool with house 2 and uses `preserve`.
   The generated containers must remain as stored in the structure NBT. Its
   indoor chest stays empty, while its outdoor barrels retain their template
   items. No container receives the normal starter kit.

## Existing LootTable preservation

The bundled NBT templates do not contain a preconfigured LootTable. To verify
that branch, create a test template with a chest or barrel that has a
`LootTable` tag, put it in a test data pack, and change the `template` in
`02-replace-starter` to its resource location. Use a new world. Its container
must keep that table rather than receiving Beginner's Delight starter loot.

For Village Mode, enable the same pack before creating the world, then run
`/beginnersdelight village enable` and join with another player. The new
village house must follow the selected case as well.
