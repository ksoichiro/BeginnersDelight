# Starter House Game Rule Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-world custom game rule `beginnersDelightGenerateStarterHouse` (default configurable) that controls whether the starter house auto-generates, so it can be enabled/disabled per world at world-creation time.

**Architecture:** A common class holds the game rule `Key`. Each loader registers the rule at mod init, using the mod config's `starter_house.auto_generate` value as the registered default. `StarterHouseGenerator.tryGenerate()` gates its *initial* generation path on that rule. Config is the default source; the per-world game rule is the authority.

**Tech Stack:** Java (8–25 across versions), Architectury (common/fabric/neoforge/forge), night-config (TOML), Fabric API game-rule-api-v1, NeoForge/Forge `GameRules.register`.

## Global Constraints

- Default MC version for dev/build: **26.2**. Build a version with `./gradlew build -Ptarget_mc_version=<ver>`.
- Game rule name (verbatim, camelCase, no namespace): **`beginnersDelightGenerateStarterHouse`**.
- Config key: section **`[starter_house]`**, key **`auto_generate`**, boolean, **default `true`**.
- `schema_version` in `beginnersdelight-default-config.toml` and `VillageConfigDefaults.CURRENT_SCHEMA_VERSION`: bump **1 → 2**.
- Minimize changes to existing logic; only gate the *not-yet-generated* path in `tryGenerate`. Do NOT change the `isGenerated()` restore path.
- `common/shared` is NOT wired into the build; every code change must be applied to each **per-version** common module (and each loader module) that ships. Cohorts and API deltas are in Phase 2.
- Structures use only vanilla blocks (unchanged here). No new runtime dependency is added (game-rule APIs are provided by the loaders / already-present Fabric API).
- **Commits require the user's explicit go-ahead per project policy (CLAUDE.md).** Commit steps below are checkpoints; pause for approval before running them.
- No mod-side unit-test harness exists. "Verify" means a successful build plus manual in-game (`runClient`) confirmation.

---

## Phase 1 — 26.2 implementation + verification (Fabric + NeoForge)

### Task 1: Add `starter_house.auto_generate` to the mod config (26.2)

**Files:**
- Modify: `common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigDefaults.java`
- Modify: `common/26.2/src/main/java/com/beginnersdelight/village/VillageConfig.java`
- Modify: `common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigLoader.java`
- Modify: `common/26.2/src/main/resources/beginnersdelight-default-config.toml`

**Interfaces:**
- Produces: `VillageConfig#isAutoGenerateStarterHouse() : boolean`; `VillageConfigDefaults.AUTO_GENERATE_STARTER_HOUSE : boolean` (=`true`); `VillageConfigDefaults.CURRENT_SCHEMA_VERSION` (=`2`).

- [ ] **Step 1: Add the default constant and bump schema version**

In `VillageConfigDefaults.java`, change the schema version and add the constant:
```java
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public static final int PLOT_SIZE = 20;
    public static final int MAX_HEIGHT_DIFFERENCE = 10;
    public static final boolean GENERATE_PATHS = true;
    public static final boolean RESPAWN_AT_HOUSE = true;
    public static final boolean AUTO_GENERATE_STARTER_HOUSE = true;
```
And extend the factory to pass the new value:
```java
    public static VillageConfig defaults() {
        return new VillageConfig(PLOT_SIZE, MAX_HEIGHT_DIFFERENCE, GENERATE_PATHS, RESPAWN_AT_HOUSE,
                AUTO_GENERATE_STARTER_HOUSE);
    }
```

- [ ] **Step 2: Add the field and getter to `VillageConfig`**

In `VillageConfig.java`, add the field, extend the constructor, add the getter:
```java
    private final boolean respawnAtHouse;
    private final boolean autoGenerateStarterHouse;

    public VillageConfig(int plotSize, int maxHeightDifference, boolean generatePaths, boolean respawnAtHouse,
                         boolean autoGenerateStarterHouse) {
        this.plotSize = plotSize;
        this.maxHeightDifference = maxHeightDifference;
        this.generatePaths = generatePaths;
        this.respawnAtHouse = respawnAtHouse;
        this.autoGenerateStarterHouse = autoGenerateStarterHouse;
    }
```
```java
    public boolean isAutoGenerateStarterHouse() {
        return autoGenerateStarterHouse;
    }
```

- [ ] **Step 3: Parse the new key in `VillageConfigLoader`**

In `VillageConfigLoader.java`, add the key constants near the others:
```java
    private static final String K_STARTER_HOUSE = "starter_house";
    private static final String K_AUTO_GENERATE = "auto_generate";
```
Read it in `parseOrDefaults` (before the `return new VillageConfig(...)`):
```java
        boolean autoGenerateStarterHouse = readBoolean(parsed, K_STARTER_HOUSE + "." + K_AUTO_GENERATE,
                VillageConfigDefaults.AUTO_GENERATE_STARTER_HOUSE);
```
Extend the unknown-top-level-key check to allow the new section:
```java
        for (CommentedConfig.Entry entry : parsed.entrySet()) {
            String key = entry.getKey();
            if (!key.equals(K_SCHEMA_VERSION) && !key.equals(K_VILLAGE) && !key.equals(K_STARTER_HOUSE)) {
                BeginnersDelight.LOGGER.warn("Unknown top-level key in {}: {}", CONFIG_FILE_NAME, key);
            }
        }
```
Update the constructor call:
```java
        return new VillageConfig(plotSize, maxHeightDifference, generatePaths, respawnAtHouse,
                autoGenerateStarterHouse);
```

- [ ] **Step 4: Update the bundled default TOML**

In `beginnersdelight-default-config.toml`, set `schema_version = 2` and append a new section:
```toml
schema_version = 2
```
```toml

[starter_house]
# Default value of the "beginnersDelightGenerateStarterHouse" game rule for newly
# created worlds. When true, new worlds generate the starter house at spawn (the
# classic behavior). Set to false to make new worlds skip it by default; you can
# still enable it per world from the "Game Rules" screen when creating a world, or
# with: /gamerule beginnersDelightGenerateStarterHouse true
# This does not affect worlds that already exist.
auto_generate = true
```

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew :common:build -Ptarget_mc_version=26.2` (or `./gradlew build -Ptarget_mc_version=26.2`)
Expected: BUILD SUCCESSFUL. No references to the old 4-arg `VillageConfig` constructor remain (compiler would flag them).

- [ ] **Step 6: Commit (await approval)**

```bash
git add common/26.2/src/main/java/com/beginnersdelight/village/ common/26.2/src/main/resources/beginnersdelight-default-config.toml
git commit -m "feat(config): add starter_house.auto_generate option (26.2)"
```

---

### Task 2: Add the game rule holder and gate `tryGenerate` (26.2)

**Files:**
- Create: `common/26.2/src/main/java/com/beginnersdelight/worldgen/ModGameRules.java`
- Modify: `common/26.2/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java`

**Interfaces:**
- Produces: `ModGameRules.GENERATE_STARTER_HOUSE : GameRules.Key<GameRules.BooleanValue>` (public static, assigned by each loader at init); `ModGameRules.RULE_NAME : String`.
- Consumes: nothing yet (Task 3 assigns the field).

- [ ] **Step 1: Create the common holder**

`ModGameRules.java`:
```java
package com.beginnersdelight.worldgen;

import net.minecraft.world.level.gamerules.GameRules;

/**
 * Holds the mod's custom game rule keys. The {@link net.minecraft.world.level.gamerules.GameRules.Key}
 * is created by each loader's initializer (Fabric/NeoForge/Forge) and stored here so the
 * version-agnostic generation code can read it. Kept as a mutable static because game rule
 * registration is loader-specific and happens once at mod init before any world loads.
 */
public final class ModGameRules {

    /** Vanilla game rules share one global namespace, so this is prefixed with the mod name. */
    public static final String RULE_NAME = "beginnersDelightGenerateStarterHouse";

    /** Assigned by the loader initializer after registering the rule. */
    public static GameRule<Boolean> GENERATE_STARTER_HOUSE;

    private ModGameRules() {
    }
}
```
> **26.2 (confirmed by spike):** import `net.minecraft.world.level.gamerules.GameRule`; field type `GameRule<Boolean>`.
> Note: for MC ≤ 1.21.10 the game-rule API differs again (old `GameRules.Key<GameRules.BooleanValue>` in `net.minecraft.world.level.GameRules`) — see Phase 2 cohorts and confirm per version.

- [ ] **Step 2: Gate the not-yet-generated path in `StarterHouseGenerator.tryGenerate`**

In `tryGenerate(MinecraftServer server)`, immediately after the `if (data.isGenerated()) { ... return; }` block and before `BlockPos spawnPos = overworld.getSharedSpawnPos();`, insert:
```java
        if (ModGameRules.GENERATE_STARTER_HOUSE == null
                || !overworld.getGameRules().get(ModGameRules.GENERATE_STARTER_HOUSE)) {
            BeginnersDelight.LOGGER.debug("Starter house generation disabled by game rule; skipping");
            return;
        }
```
> **26.2 (confirmed by spike):** read via `getGameRules().get(GameRule<T>)` returning `T` (auto-unboxed by `!`). For MC ≤ 1.21.10 the read is `getGameRules().getBoolean(Key)` — confirm per version in Phase 2.
> The `== null` guard makes generation a no-op if the loader failed to register the rule, rather than throwing.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew build -Ptarget_mc_version=26.2`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (await approval)**

```bash
git add common/26.2/src/main/java/com/beginnersdelight/worldgen/ModGameRules.java common/26.2/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java
git commit -m "feat(worldgen): gate starter house generation on game rule (26.2)"
```

---

### Task 3: Register the game rule in the Fabric and NeoForge initializers (26.2)

**Files:**
- Modify: `fabric/base/src/main/java/com/beginnersdelight/fabric/BeginnersDelightFabric.java`
- Modify: `neoforge/base/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java`

**Interfaces:**
- Consumes: `ModGameRules.RULE_NAME`, `ModGameRules.GENERATE_STARTER_HOUSE` (type `net.minecraft.world.level.gamerules.GameRule<Boolean>`); `VillageConfigLoader.load(Path)`; `VillageConfig#isAutoGenerateStarterHouse()`.
- Produces: assigns `ModGameRules.GENERATE_STARTER_HOUSE` at mod init.

> **26.2 game-rule API (confirmed by the Task 2 spike — use these, NOT the old `Key`/`BooleanValue`/`GameRuleRegistry` names):**
> - Rules are `net.minecraft.world.level.gamerules.GameRule<T>` (top-level generic), held as static fields on `GameRules`.
> - Vanilla registration: `GameRules.registerBoolean(String name, GameRuleCategory category, boolean defaultValue)` returns `GameRule<Boolean>`.
> - Category type: `net.minecraft.world.level.gamerules.GameRuleCategory` (use the value equivalent to "misc" — most likely `GameRuleCategory.MISC`; confirm the exact enum constant by inspecting the class if the build fails).
> - `ModGameRules.GENERATE_STARTER_HOUSE` is already typed `GameRule<Boolean>` (Task 2).

- [ ] **Step 1: Register in NeoForge (vanilla registration)**

In `BeginnersDelightNeoForge` constructor, add imports:
```java
import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.worldgen.ModGameRules;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.gamerules.GameRuleCategory;
```
After `BeginnersDelight.init();`, register using the config default:
```java
        boolean starterHouseDefault = VillageConfigLoader
                .load(FMLPaths.CONFIGDIR.get())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = GameRules.registerBoolean(
                ModGameRules.RULE_NAME,
                GameRuleCategory.MISC,
                starterHouseDefault);
```

- [ ] **Step 2: Register in Fabric**

Fabric's registration path for the restructured 26.2 API is UNCONFIRMED. Investigate and pick the approach that compiles and is idiomatic, preferring the Fabric game-rule API if it exists and is compatible:
- **First try** the Fabric game-rule API `net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry` / `GameRuleFactory` (as used pre-26.x). Inspect whether its signatures still work with 26.2's `GameRule`/`GameRuleCategory` types.
- **If that API is absent or incompatible in 26.2**, use the SAME vanilla `GameRules.registerBoolean(ModGameRules.RULE_NAME, GameRuleCategory.MISC, starterHouseDefault)` call as NeoForge (Fabric can call vanilla registration directly).

In `BeginnersDelightFabric.onInitialize()`, after `BeginnersDelight.init();`, read the default and register (vanilla form shown; swap for the Fabric-API form if you confirm it works):
```java
        boolean starterHouseDefault = VillageConfigLoader
                .load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = net.minecraft.world.level.gamerules.GameRules.registerBoolean(
                ModGameRules.RULE_NAME,
                net.minecraft.world.level.gamerules.GameRuleCategory.MISC,
                starterHouseDefault);
```
Add the corresponding imports (`com.beginnersdelight.village.VillageConfigLoader`, `com.beginnersdelight.worldgen.ModGameRules`, and either the fully-qualified names shown or top-level imports). Document in the report which registration path you used and why.

> Registration must be idempotent-safe: `GameRules.registerBoolean` mutates a static registry, so it MUST run exactly once per JVM at mod init (both loaders' initializers already run once). Do not register inside an event that can fire more than once.

- [ ] **Step 3: Build both loaders**

Run: `./gradlew build -Ptarget_mc_version=26.2`
Expected: BUILD SUCCESSFUL (both `:fabric` and `:neoforge` jars produced).

- [ ] **Step 4: Commit (await approval)**

```bash
git add fabric/base/src/main/java/com/beginnersdelight/fabric/BeginnersDelightFabric.java neoforge/base/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java
git commit -m "feat: register beginnersDelightGenerateStarterHouse game rule (26.2 Fabric/NeoForge)"
```

---

### Task 4: Manual verification in-game (26.2) — spike gate

**Files:** none (verification only).

This task confirms the core UX assumption (the custom game rule appears and works in the world-creation screen) BEFORE rolling out to all versions.

- [ ] **Step 1: Fabric — game rule ON (default)**

Run: `./gradlew :fabric:runClient -Ptarget_mc_version=26.2`
Create a new world → "More" / world options → **Game Rules** → confirm `beginnersDelightGenerateStarterHouse` is listed and defaults to ON. Create the world.
Expected: the starter house generates at spawn as before.

- [ ] **Step 2: Fabric — game rule OFF**

Create another new world, toggle `beginnersDelightGenerateStarterHouse` **OFF** in the Game Rules screen, create it.
Expected: NO starter house is generated; the player spawns via vanilla behavior.

- [ ] **Step 3: Fabric — in-game command**

In the ON world, run `/gamerule beginnersDelightGenerateStarterHouse` → reports `true`. Set `false`, restart the world, confirm no *new* generation occurs (the already-built house remains — that is expected, `isGenerated` is sticky).

- [ ] **Step 4: Config default OFF**

Edit `<configdir>/beginnersdelight.toml` → `[starter_house] auto_generate = false`. Launch, create a new world, open Game Rules.
Expected: `beginnersDelightGenerateStarterHouse` now defaults to **OFF**.

- [ ] **Step 5: NeoForge — repeat Steps 1–2**

Run: `./gradlew :neoforge:runClient -Ptarget_mc_version=26.2` and repeat the ON and OFF world checks.

- [ ] **Step 6: Decision checkpoint**

If the game rule does NOT appear/edit in the creation screen on either loader, STOP and report — the approach needs revision before rollout. If both work, proceed to Phase 2.

---

## Phase 2 — Roll out to all remaining versions

Repeat Tasks 1–3's changes in every shipped version's modules, applying the per-cohort API deltas below. The *logic* is identical to Phase 1; only imports and a few API calls differ. Build each version after changing it: `./gradlew build -Ptarget_mc_version=<ver>`.

**Per-version file set (same as Phase 1, per module):**
- `common/<ver>/.../village/VillageConfigDefaults.java`, `VillageConfig.java`, `VillageConfigLoader.java`
- `common/<ver>/src/main/resources/beginnersdelight-default-config.toml`
- `common/<ver>/.../worldgen/ModGameRules.java` (new), `StarterHouseGenerator.java`
- Loader initializer(s) for that version (see cohort's loader column).

### Cohort A — new GameRules package (already done for 26.2)
Versions: **26.1, 26.1.1, 26.1.2, 1.21.11** (26.2 complete).
- `GameRules` import: `net.minecraft.world.level.gamerules.GameRules`.
- Loaders: NeoForge = `neoforge/base` (shared) — **verify** each version compiles against base; Fabric = `fabric/base`.
- Registration + read: identical to Phase 1.
> NeoForge base is shared across NeoForge versions; the Task 3 NeoForge edit is made **once** in `neoforge/base` and must compile for every NeoForge version. Confirm `net.minecraft.world.level.gamerules.GameRules` resolves for 26.1–1.21.11. If 1.21.11 differs from 26.x here, split the initializer per version.

### Cohort B — old GameRules package, NeoForge + Fabric
Versions: **1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.3, 1.21.1**.
- `GameRules` import: `net.minecraft.world.level.GameRules` (both in `ModGameRules.java` and initializers).
- Read API: `getGameRules().getBoolean(ModGameRules.GENERATE_STARTER_HOUSE)` (unchanged).
- Loaders: NeoForge `neoforge/base` (shared — confirm import), Fabric `fabric/base` (shared).
- Registration: NeoForge `GameRules.register(name, GameRules.Category.MISC, GameRules.BooleanValue.create(default))`; Fabric `GameRuleRegistry.register(name, GameRules.Category.MISC, GameRuleFactory.createBooleanRule(default))`.
> Because NeoForge/Fabric bases are shared, the import differs between Cohort A and B. If a single `base` module compiles for both cohorts, the differing `GameRules` import forces a per-version override of `ModGameRules.java` and the initializer. Resolve by putting `ModGameRules.java` in each `common/<ver>` (already the plan) and, if needed, per-version initializer overrides. Verify at build time which versions share `base`.

### Cohort C — Forge (per-version entry) + Fabric, old package
Versions: **1.20.1, 1.19.2, 1.18.2, 1.17.1**.
- `GameRules` import: `net.minecraft.world.level.GameRules`.
- Forge initializer is per version: `forge/<ver>/.../BeginnersDelightForge.java`. Register with `GameRules.register(...)` (same signature as NeoForge) and read the config default via `net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()`.
- Fabric: `fabric/base` for 1.19.2/1.20.1; **`fabric/1.17.1` and `fabric/1.18.2` have per-version overrides** — apply the registration there.
- Category `GameRules.Category.MISC` exists in these versions.

### Cohort D — 1.16.5 (Java 8, oldest APIs)
Version: **1.16.5**.
- `GameRules` import: `net.minecraft.world.level.GameRules`.
- Forge entry: `forge/1.16.5/.../BeginnersDelightForge.java`; Fabric entry: `fabric/1.16.5/...` (per-version override).
- **Verify** Fabric API 0.42.0+1.16 bundles `fabric-game-rule-api-v1` (`GameRuleRegistry`/`GameRuleFactory`). If absent, register via vanilla `GameRules.register(...)` from the Fabric initializer instead (works without the Fabric module).
- Config default read: Forge `FMLPaths.CONFIGDIR.get()`; Fabric `FabricLoader.getInstance().getConfigDir()`.
- Java 8: no `var`, no records — the code above already avoids both.

### Task 5..N: per-version rollout (one task per version, or per cohort batch)

For each version, in order (suggested: 1.21.11, 26.1.2, 26.1.1, 26.1, then 1.21.10 → 1.21.1, then 1.20.1 → 1.16.5):

- [ ] Copy Task 1 changes into that version's `village` package + `beginnersdelight-default-config.toml` (schema_version = 2, `[starter_house] auto_generate = true`).
- [ ] Create that version's `worldgen/ModGameRules.java` with the cohort's `GameRules` import.
- [ ] Insert the Task 2 gate into that version's `StarterHouseGenerator.tryGenerate`.
- [ ] Apply the registration to the correct loader initializer(s) per the cohort's loader column, with the cohort's imports.
- [ ] Build: `./gradlew build -Ptarget_mc_version=<ver>` → BUILD SUCCESSFUL.
- [ ] Commit (await approval): `git commit -m "feat: starter house game rule toggle (<ver>)"`.

- [ ] **Final build sweep:** `./gradlew buildAll` → all versions BUILD SUCCESSFUL.

---

## Task Docs: CHANGELOG, README, store descriptions

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `README.md` (and localized READMEs if present)
- Modify: store descriptions used by release (curseforge, modrinth) — see the CHANGELOG+docs sync memory.

- [ ] **Step 1: CHANGELOG (Unreleased)**

Add an entry describing the new `beginnersDelightGenerateStarterHouse` game rule (per-world, default from `starter_house.auto_generate`, settable at world creation / via `/gamerule`), and that existing worlds are unaffected. Use the `minecraft-mod:update-changelog` skill if preferred.

- [ ] **Step 2: README + store descriptions**

Document: the game rule name, that it is toggled in the world-creation "Game Rules" screen, the `/gamerule` command, and the `[starter_house] auto_generate` config default. Note that toggling ON after a world already generated (or turning it ON later) takes effect on the next server start and never removes an already-built house.

- [ ] **Step 3: Commit (await approval)**

```bash
git add CHANGELOG.md README.md
git commit -m "docs: document starter house game rule toggle"
```

---

## Self-Review Notes

- **Spec coverage:** config default (Task 1) ✓; game rule + gate (Task 2) ✓; per-loader registration (Task 3) ✓; world-creation-screen verification (Task 4) ✓; cross-version rollout with 1.21.11 package move + 1.16.5 Fabric-API risk (Phase 2 cohorts) ✓; docs (final task) ✓; edge cases (isGenerated sticky, null-guard, config-default-off) covered in steps.
- **Open verifications deferred to build/spike (by design, no offline way to confirm):** (1) `getGameRules().getBoolean(Key)` availability per version — fallback documented; (2) whether `fabric/base` and `neoforge/base` compile across both GameRules-package cohorts, which decides if per-version initializer overrides are needed — resolved at build time in Phase 2; (3) Fabric game-rule module presence in 0.42.0+1.16 — fallback documented.
