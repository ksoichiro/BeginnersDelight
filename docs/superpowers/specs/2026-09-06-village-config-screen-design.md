# Village config in-game settings screen — design

## Background

`VillageConfig` / `VillageConfigLoader` / `VillageConfigDefaults`
(`common/{version}/src/main/java/com/beginnersdelight/village/`) already load
`config/beginnersdelight.toml` at server start and support a runtime
`/beginnersdelight config reload` command (`VillageManager.reloadConfig`).

Editing this file directly is a server-admin-level task. For singleplayer and
small multiplayer sessions, the host player should be able to change these
values from an in-game screen instead.

**MinersMarket** (`~/src/github.com/ksoichiro/MinersMarket`) already ships this
kind of screen with no external config-mod dependency (no Cloth Config, no
Forge Config API — vanilla widgets only). This design ports that pattern to
BeginnersDelight's village config.

## Scope

- First implemented and verified on MC **26.2** only.
- Rolled out to the remaining 18 versions afterward via the existing
  anchor-based rollout process (see `feedback-worldgen-empirical-verification`
  memory), once 26.2 behavior is confirmed in-game.
- Covers all 5 existing `VillageConfig` fields: `plot_size`,
  `max_height_difference`, `generate_paths`, `respawn_at_house`,
  `starter_house.auto_generate`.
- No new config values are introduced. No network sync protocol is added.

## Design

### Storage layer changes (`common/26.2/.../village/`)

- **`VillageConfigRanges`** (new): extracts `MIN_PLOT_SIZE`, `MAX_PLOT_SIZE`,
  `MIN_HEIGHT_DIFFERENCE`, `MAX_HEIGHT_DIFFERENCE` out of
  `VillageConfigLoader` into shared constants, so the loader and the screen
  enforce identical validation (single source of truth, mirrors MinersMarket's
  `ConfigRanges`).
- **`VillageConfigWriter`** (new): loads the existing file with
  `CommentedFileConfig.load(...)`, calls `set(key, value)` per field, then
  `save()`. This preserves the comments already in
  `beginnersdelight-default-config.toml` / the on-disk file instead of
  rewriting it from scratch.
- **`VillageManager.setConfig(VillageConfig)`** (new): replaces the static
  `config` holder directly, without touching disk. Because the client and the
  integrated server share one JVM in singleplayer, calling this from the
  screen makes the change take effect immediately for singleplayer/LAN-hosted
  games. On a dedicated server, the client-side call only affects the
  requesting client's own process, which has no server; this is what makes
  the "local-only" warning (below) necessary and safe.

### Screen (`common/26.2/.../village/client/VillageConfigScreen.java`)

Follows MinersMarket's `ConfigScreen` structure:

- `extends Screen`, with a `ContainerObjectSelectionList<Entry>` for a
  scrolling list of `HeaderEntry` / `NumberEntry` (`EditBox`) / `ToggleEntry`
  (`CycleButton.onOffBuilder`) rows, one per field.
- Live validation via `EditBox#setResponder`: an out-of-range value turns the
  field red and disables the Done button. Tooltips are appended with the
  valid range pulled from `VillageConfigRanges`.
- Footer buttons: **Reset** (to `VillageConfigDefaults.defaults()`),
  **Cancel** (discard, close), **Done** (validate → `VillageManager.setConfig`
  → `VillageConfigWriter.save` → close).
- `resize()` snapshots unsaved field values before `init()` rebuilds widgets.
- If `minecraft.level != null && !minecraft.hasSingleplayerServer()`, shows a
  warning header: these are the player's local values and do not affect the
  actual hosting server's session (identical rationale to MinersMarket).

### Client-side config directory

The screen and writer need the config directory without a `MinecraftServer`
reference (unlike `VillageManager.resolveConfigDir`, which takes one). Each
loader's client entrypoint stores the loader-provided config dir in a small
static holder at init time (mirrors `MinersMarket.getConfigDir()`):

- Fabric: `FabricLoader.getInstance().getConfigDir()`
- NeoForge: `FMLPaths.CONFIGDIR.get()`
- Forge: same (`FMLPaths.CONFIGDIR.get()`)

This is the same physical directory `VillageManager.resolveConfigDir` resolves
to for an integrated server, since the config directory is per-installation,
not per-world.

### Entry points (all three loaders, matching MinersMarket)

- **NeoForge**: `container.registerExtensionPoint(IConfigScreenFactory.class,
  (ignored, parent) -> new VillageConfigScreen(parent))`.
- **Forge**: `ModLoadingContext.get().registerExtensionPoint(
  ConfigScreenHandler.ConfigScreenFactory.class, ...)`.
- **Fabric**: ModMenu stays an optional (`compileOnly`) dependency. A
  `ModMenuIntegration implements ModMenuApi` class is added to a
  `fabric/base-modmenu`-equivalent source set, included only by ModMenu when
  present (inert otherwise, same technique MinersMarket uses).
- **All loaders**: an unbound `KeyMapping`/`KeyBinding` ("Open Village Config
  Screen") checked on the client tick, in a nested client-only holder class so
  it is never loaded on a dedicated server — matching the existing
  `ClientConfigHooks`-style pattern used for other client-only members in
  this codebase.

## Testing

- Unit-testable pieces (if the project's test setup covers this package):
  `VillageConfigRanges` bounds, `VillageConfigWriter` round-trip
  (write → reload → same values), `VillageManager.setConfig` visibility.
- The screen itself requires manual verification via `runClient` on 26.2:
  open via mod list / ModMenu / keybinding, edit each field (including
  boundary and out-of-range values), Reset, Cancel, Done, and confirm the
  non-host warning banner appears when connected to a non-singleplayer,
  non-hosted server.

## Out of scope

- Any config value beyond the existing 5 `VillageConfig` fields.
- Network sync of config between server and non-host clients.
- Cloth Config / Forge Config API / any other external config-screen
  dependency.
- Rolling out to versions other than 26.2 (tracked as a follow-up once 26.2
  is verified).
