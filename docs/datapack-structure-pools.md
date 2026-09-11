# Data Pack Structure Pools

Beginner's Delight reads starter-house candidates from a data pack resource. The
resource controls which templates can be selected, their relative weights, and
whether Beginner's Delight supplies starter loot after placement. Templates and
loot tables themselves remain standard Minecraft data pack resources.

## Resource location

Each data pack can contribute one file at:

```text
data/beginnersdelight/beginners_delight/starter_house_pool.json
```

Files are read in data pack priority order, from the lowest priority pack to
the highest. This lets a modpack add or remove candidates without copying the
mod's full default list.

## Schema version 1

Every pool file must contain `schema_version: 1`. Beginner's Delight rejects a
file with an unsupported version and keeps processing other files. New schema
versions will use a new number. Version 1 fields keep their meaning in later
versions unless the release notes explicitly state otherwise.

```json
{
  "schema_version": 1,
  "replace": false,
  "remove": [
    "beginnersdelight:starter_house2"
  ],
  "entries": [
    {
      "template": "architecturemod:starter_houses/brick_cottage",
      "weight": 3,
      "loot": "preserve",
      "minimum_minecraft_version": "1.21.9"
    }
  ]
}
```

All fields except `schema_version` are optional.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema_version` | integer | required | Pool schema version. Use `1`. |
| `replace` | boolean | `false` | Clears candidates accumulated from lower-priority packs before applying this file. |
| `remove` | array of resource locations | `[]` | Removes every existing candidate with the given template ID. |
| `entries` | array | `[]` | Adds candidates after `remove` has been applied. |

An entry contains the following fields.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `template` | resource location | required | The structure template ID, such as `example:starter_houses/cottage`. The NBT file belongs at `data/example/structure/starter_houses/cottage.nbt`. |
| `weight` | positive integer | `1` | Relative selection weight. A candidate with weight `2` is selected twice as often as one with weight `1`. |
| `loot` | `preserve` or `starter` | `preserve` | Controls Beginner's Delight's post-placement loot handling. |
| `minimum_minecraft_version` | numeric version string | none | The oldest Minecraft version that may select this candidate, such as `"1.21.9"`. The entry is skipped on older versions. |

Unknown fields are ignored. This allows a newer Beginner's Delight release to
add optional fields without invalidating existing modpacks. Invalid resource
locations, non-positive weights, unknown loot modes, and malformed minimum
Minecraft versions cause only the affected entry to be skipped and are reported
in the server log.

## Adding a house

Create a data pack with the structure NBT in its own namespace, then add an
entry to the pool file:

```json
{
  "schema_version": 1,
  "entries": [
    {
      "template": "my_pack:starter_houses/spruce_cottage",
      "weight": 2,
      "minimum_minecraft_version": "1.21.9"
    }
  ]
}
```

The default `preserve` mode leaves every container in the template unchanged.
The template may contain items, a vanilla `LootTable` tag, or empty decorative
containers.

Omit `minimum_minecraft_version` when the template works on every Minecraft
version supported by the installed Beginner's Delight build. Use it when the
template uses blocks, entities, or NBT features that were introduced later.

## Replacing or removing houses

To remove one bundled house, list its template ID in `remove`:

```json
{
  "schema_version": 1,
  "remove": ["beginnersdelight:starter_house4"]
}
```

To replace the entire pool, set `replace` to `true` and define the new entries:

```json
{
  "schema_version": 1,
  "replace": true,
  "entries": [
    {
      "template": "my_pack:starter_houses/only_house",
      "weight": 1,
      "loot": "starter"
    }
  ]
}
```

To change the weight of an existing candidate, remove it and add it again with
the desired weight in the same file.

## Loot modes

`preserve` is the safe default for external templates. Beginner's Delight does
not inspect or change their containers.

`starter` asks Beginner's Delight to provide the normal starter-house loot.
Existing inventories and containers that already have a loot table remain
unchanged. Among otherwise empty containers, the first chest receives the
starter kit and later containers receive the supplies loot table. Use this mode
only when the template is intended to participate in that behavior.

The bundled Beginner's Delight entries use `starter`, preserving the existing
experience without requiring their NBT files to embed loot table data.

## Reloading

The pool is read when Beginner's Delight places a starter or village house.
Use `/reload` after changing a data pack. Existing placed structures are never
changed.

## Validation packs

Repository contributors can use the example packs in
[`docs/test-datapacks/starter-house-pool-validation`](test-datapacks/starter-house-pool-validation/).
They cover adding and removing candidates, complete replacement, and both loot
modes.
