# Modpack Integration Guide

This guide covers the settings that a modpack author needs when including Beginner's Delight. The mod runs on both dedicated servers and integrated singleplayer servers. Use the same server configuration for both.

## Quick decisions

| Desired behavior | Action |
| --- | --- |
| Use the bundled starter house | No action. New worlds generate it once at world spawn. |
| Disable it for every new world in the pack | Ship `config/beginnersdelight.toml` with `starter_house.auto_generate = false`. |
| Choose it for one world only | Set `beginnersdelight:generate_starter_house` in the world-creation Game Rules screen, or run `/gamerule beginnersdelight:generate_starter_house <true|false>`. |
| Use only the pack's house templates | Add a starter-house-pool data pack with `replace: true`. |
| Use the bundled house but change its starter items | Override the relevant Beginner's Delight loot table in the pack data. |
| Add player houses around spawn | Run `/beginnersdelight village enable`. Village Mode is off until this command is run. |

The starter house is placed once. Turning the game rule off never removes an existing house. Turning it on for a world that has not generated a house makes the server place one on its next start. Test generation changes in a new world.

## Server configuration

The configuration file is `config/beginnersdelight.toml`. It controls the default value used only when a new world is created. It does not overwrite the game rule of an existing world.

To ship a pack with starter-house generation disabled by default, copy [`examples/modpack-config/no-starter-house.toml`](examples/modpack-config/no-starter-house.toml) to `config/beginnersdelight.toml` in the server or instance. The same file also holds the Village Mode settings. Reload changes on a running server with:

```text
/beginnersdelight config reload
```

Server operators should edit this file or use the command. The in-game config screen does not change the configuration of a remote dedicated server.

## Custom starter houses

Starter-house candidates are defined by a data pack resource at:

```text
data/beginnersdelight/beginners_delight/starter_house_pool.json
```

The bundled pool can be replaced, extended, pruned, or weighted. The same pool also supplies Village Mode player houses. Copy the layout in [`examples/replace-starter-house`](examples/replace-starter-house) and replace `my_pack:starter_houses/cottage` with the structure template supplied by the pack. Put that template at:

```text
data/my_pack/structure/starter_houses/cottage.nbt
```

Set the `pack_format` in `pack.mcmeta` to the target Minecraft version. The example uses `6` so it remains readable by Minecraft 1.16.5, but newer versions may show a compatibility warning. See the [full structure-pool guide](datapack-structure-pools.md) for the schema, loot modes, version gates, and additional examples.

## Starter loot

The bundled templates use these loot tables:

- `data/beginnersdelight/loot_table/chests/starter_house.json` for the first eligible chest or barrel
- `data/beginnersdelight/loot_table/chests/starter_house_supplies.json` for additional empty containers

A data pack with a higher priority can provide files at the same resource locations to replace these tables. For custom templates, use `"loot": "preserve"` to retain the inventories and loot tables authored in the template, or `"loot": "starter"` to opt into the behavior above.

## Compatibility checks before release

Beginner's Delight reshapes a small area around a placed house and clears intersecting vegetation. It is designed to coexist with vanilla-style world generation, but a pack that changes any of the following should be tested in a new world before release:

- world spawn position or spawn structures
- terrain generation around spawn
- structure placement or protection at spawn
- loot tables that the pack overrides

Record the Minecraft version, loader, full mod list, seed, and generated house template when reporting an issue. This makes world-generation problems reproducible.
