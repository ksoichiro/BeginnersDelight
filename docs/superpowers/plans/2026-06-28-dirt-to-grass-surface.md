# Dirt-to-Grass Surface Naturalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After terrain leveling/blending, replace dirt blocks on the top surface with grass when a grass block is among their 8 horizontal neighbours, so generated houses/villages blend naturally with surrounding grassland.

**Architecture:** Add one post-process method `naturalizeDirtSurface()` to both generators (`StarterHouseGenerator`, `VillageHouseGenerator`) in every supported MC version. It runs after all surface placement, takes a snapshot of grass vs dirt tops, then converts dirt tops adjacent to snapshot-grass into grass (snapshot prevents cascade — keeps the check local/per-block). Existing leveling logic is unchanged.

**Tech Stack:** Java (8/16/17/21 across versions), Minecraft modding (NeoForge/Forge/Fabric via Architectury Loom), Gradle (Groovy DSL), Mojang mappings.

## Global Constraints

- Design spec: `docs/superpowers/specs/2026-06-28-dirt-to-grass-surface-design.md`.
- The new method uses ONLY: `findGroundY(level, x, z)` (already defined per-version), `Blocks.GRASS_BLOCK`, `Blocks.DIRT`, `BlockState.is(Block)`, `level.getBlockState(BlockPos)`, `level.setBlock(BlockPos, BlockState, int)`. No new imports.
- Method body is identical across all versions; only match each file's `Vec3i` style: use `net.minecraft.core.Vec3i` in `StarterHouseGenerator`, `Vec3i` (already imported) in `VillageHouseGenerator` — mirror the adjacent `blendCornerPillars` signature in the same file.
- Region scanned: footprint + `margin` (2) + `blendRadius` (3); `extend = margin + blendRadius = 5`.
- Neighbour range: 8 horizontal directions. Height-agnostic (compare each column's top surface block; neighbour grass counts at any Y).
- Place call site immediately AFTER the `blendCornerPillars(...)` line, using the SAME arguments as that call.
- Do NOT modify `detectDominantSurfaceBlock` / `mapToSurfaceBlock` / `mapToSubsurfaceBlock` / any existing method.
- No automated test framework exists in this repo. Verification = Gradle build compiling the changed version(s) + optional manual client visual check. There are no JUnit tests to write.
- **Build command pattern (required, from prior build lessons):** run with `dangerouslyDisableSandbox: true`, add `--no-daemon`, and redirect output to an absolute scratchpad log path. Example:
  `./gradlew build -Ptarget_mc_version=1.21.11 --no-daemon > /private/tmp/claude-501/-Users-ksoichiro-src-github-com-ksoichiro-BeginnersDelight/<session>/scratchpad/build-1.21.11.log 2>&1`
  (use the actual session scratchpad dir).
- **Commits require explicit user instruction** (project rule: never commit without the user asking). Commit steps below are the intended grouping; pause and ask the user before running any `git commit`.
- Conventional Commits, English commit messages. End files with a newline.

## Reference: the method to add (verbatim, StarterHouseGenerator form)

```java
    /**
     * Replaces dirt blocks on the leveled/blended top surface with grass when a
     * grass block is present among their 8 horizontal neighbours. Natural terrain
     * has grass on top with dirt beneath, so isolated dirt left by foundation
     * filling or terrain blending looks unnatural next to grass.
     *
     * Runs as a post-process after all surface placement. Grass membership is read
     * from a snapshot taken before any conversion, so a flipped block never causes
     * its neighbour to flip too (keeps the check local/per-block, no cascade).
     */
    private static void naturalizeDirtSurface(ServerLevel level, BlockPos placePos,
                                               net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius;

        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        boolean[][] grass = new boolean[width][depth];
        // Integer.MIN_VALUE marks "no dirt top here"; otherwise the Y of the dirt surface block.
        int[][] dirtTopY = new int[width][depth];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                dirtTopY[i][j] = Integer.MIN_VALUE;
            }
        }

        // Snapshot pass: record grass tops and dirt tops from the current surface.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY == -1) {
                    continue;
                }
                int topY = groundY - 1;
                BlockState top = level.getBlockState(new BlockPos(x, topY, z));
                int i = x - minX;
                int j = z - minZ;
                if (top.is(Blocks.GRASS_BLOCK)) {
                    grass[i][j] = true;
                } else if (top.is(Blocks.DIRT)) {
                    dirtTopY[i][j] = topY;
                }
            }
        }

        // Convert pass: a dirt top adjacent (8-dir) to a snapshot grass top becomes grass.
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                if (dirtTopY[i][j] == Integer.MIN_VALUE) {
                    continue;
                }
                boolean adjacentGrass = false;
                for (int di = -1; di <= 1 && !adjacentGrass; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        if (di == 0 && dj == 0) {
                            continue;
                        }
                        int ni = i + di;
                        int nj = j + dj;
                        if (ni < 0 || ni >= width || nj < 0 || nj >= depth) {
                            continue;
                        }
                        if (grass[ni][nj]) {
                            adjacentGrass = true;
                            break;
                        }
                    }
                }
                if (adjacentGrass) {
                    level.setBlock(new BlockPos(minX + i, dirtTopY[i][j], minZ + j),
                            Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
        }
    }
```

**VillageHouseGenerator form:** identical body, but change the signature line to use the file's imported `Vec3i`:

```java
    private static void naturalizeDirtSurface(ServerLevel level, BlockPos placePos,
                                               Vec3i structureSize) {
```

(The body references `structureSize.getX()` / `.getZ()` which work for both forms.)

---

### Task 1: Implement on default version (1.21.11) + shared reference

**Files:**
- Modify: `common/shared/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java` (reference copy — add method + call site)
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java`
- Modify: `common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java`

**Interfaces:**
- Consumes: existing `findGroundY(ServerLevel, int, int)` returning ground-top Y+1 or -1.
- Produces: `private static void naturalizeDirtSurface(ServerLevel level, BlockPos placePos, <Vec3i> structureSize)` in each generator, invoked after `blendCornerPillars`.

- [ ] **Step 1: Add the method to shared StarterHouseGenerator**

In `common/shared/.../worldgen/StarterHouseGenerator.java`, paste the StarterHouseGenerator-form method (see Reference above) just before `blendCornerPillars` (i.e. anywhere among the private static helpers; place it directly after the `blendCornerPillars` method for locality).

- [ ] **Step 2: Add the call site to shared StarterHouseGenerator**

Find the line `blendCornerPillars(level, placePos, template.getSize());` in `generate(...)`. Add immediately after it:

```java

        // Replace surface dirt next to grass with grass so the leveled/blended
        // ring blends naturally with the surrounding grassland.
        naturalizeDirtSurface(level, placePos, template.getSize());
```

- [ ] **Step 3: Repeat Steps 1-2 for 1.21.11 StarterHouseGenerator**

Apply the identical method (StarterHouseGenerator form) and the identical call site (`naturalizeDirtSurface(level, placePos, template.getSize());`) after `blendCornerPillars(level, placePos, template.getSize());` in `common/1.21.11/.../worldgen/StarterHouseGenerator.java`.

- [ ] **Step 4: Add method + 2 call sites to 1.21.11 VillageHouseGenerator**

In `common/1.21.11/.../village/VillageHouseGenerator.java`:
1. Add the VillageHouseGenerator-form method (signature uses `Vec3i`), placed after the `blendCornerPillars` method.
2. After the FIRST `blendCornerPillars(level, placePos, size);` (main-house sequence), add:

```java

        // Replace surface dirt next to grass with grass for a natural blend.
        naturalizeDirtSurface(level, placePos, size);
```
3. After the SECOND `blendCornerPillars(level, surfacePos, size);` (additional-structure sequence), add:

```java

        // Replace surface dirt next to grass with grass for a natural blend.
        naturalizeDirtSurface(level, surfacePos, size);
```

- [ ] **Step 5: Build 1.21.11 to verify it compiles**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew build -Ptarget_mc_version=1.21.11 --no-daemon > <scratchpad>/build-1.21.11.log 2>&1
```
Expected: `BUILD SUCCESSFUL`. If it fails, inspect the log (compile errors in the two changed files only).

- [ ] **Step 6: Commit (ask user first)**

```bash
git add common/shared/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java \
        common/1.21.11/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java \
        common/1.21.11/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java \
        docs/superpowers/specs/2026-06-28-dirt-to-grass-surface-design.md \
        docs/superpowers/plans/2026-06-28-dirt-to-grass-surface.md
git commit -m "feat(worldgen): replace surface dirt next to grass with grass blocks"
```

---

### Task 2: Implement on oldest version (1.16.5) to validate old API

**Files:**
- Modify: `common/1.16.5/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java`
- Modify: `common/1.16.5/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java`

**Interfaces:**
- Consumes/Produces: same as Task 1.

- [ ] **Step 1: Apply to 1.16.5 StarterHouseGenerator**

Add the StarterHouseGenerator-form method after `blendCornerPillars`, and add the call site after the `blendCornerPillars(level, placePos, template.getSize());` line, mirroring its arguments. (If the 1.16.5 file's `generate` uses a different size expression for `blendCornerPillars`, mirror exactly that expression.)

- [ ] **Step 2: Apply to 1.16.5 VillageHouseGenerator**

Add the VillageHouseGenerator-form method and a `naturalizeDirtSurface(...)` call after each `blendCornerPillars(...)` call, mirroring that call's arguments.

- [ ] **Step 3: Build 1.16.5 to verify old-API compatibility**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew build -Ptarget_mc_version=1.16.5 --no-daemon > <scratchpad>/build-1.16.5.log 2>&1
```
Expected: `BUILD SUCCESSFUL`. This is the highest-risk version (Java 8, oldest mappings). Confirms `BlockState.is(Block)`, `Blocks.GRASS_BLOCK`, `Blocks.DIRT`, `findGroundY`, and 2D-array code all compile on the old API.

- [ ] **Step 4: Commit (ask user first)**

```bash
git add common/1.16.5/src/main/java/com/beginnersdelight/worldgen/StarterHouseGenerator.java \
        common/1.16.5/src/main/java/com/beginnersdelight/village/VillageHouseGenerator.java
git commit -m "feat(worldgen): naturalize surface dirt for 1.16.5"
```

---

### Task 3: Propagate to remaining 13 versions and full build

**Files (Modify — both generators in each):**
- `common/1.21.10`, `common/1.21.9`, `common/1.21.8`, `common/1.21.7`, `common/1.21.6`, `common/1.21.5`, `common/1.21.4`, `common/1.21.3`, `common/1.21.1`, `common/1.20.1`, `common/1.19.2`, `common/1.18.2`, `common/1.17.1` — each `.../worldgen/StarterHouseGenerator.java` and `.../village/VillageHouseGenerator.java`.

**Interfaces:**
- Consumes/Produces: same as Task 1.

- [ ] **Step 1: Apply to each remaining version**

For each version dir above, in BOTH generators:
- StarterHouseGenerator: add the StarterHouseGenerator-form method after `blendCornerPillars`; add `naturalizeDirtSurface(...)` after the `blendCornerPillars(...)` call (mirror its args).
- VillageHouseGenerator: add the VillageHouseGenerator-form method after `blendCornerPillars`; add `naturalizeDirtSurface(...)` after EACH `blendCornerPillars(...)` call (mirror each call's args — typically `placePos`/`size` and `surfacePos`/`size`).

The method body is byte-for-byte identical to Task 1 (only the `Vec3i` form differs by file type). Verify each file's `blendCornerPillars` call argument expression and mirror it exactly.

- [ ] **Step 2: Build a representative mid version (1.20.1)**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew build -Ptarget_mc_version=1.20.1 --no-daemon > <scratchpad>/build-1.20.1.log 2>&1
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build all versions**

Run (with `dangerouslyDisableSandbox: true`):
```bash
./gradlew buildAll --no-daemon > <scratchpad>/build-all.log 2>&1
```
Expected: `BUILD SUCCESSFUL` covering all 15 versions. If a specific version fails, fix that version's file (most likely a mismatched `blendCornerPillars` argument expression) and rebuild just that version.

- [ ] **Step 4: Commit (ask user first)**

```bash
git add common/1.21.10 common/1.21.9 common/1.21.8 common/1.21.7 common/1.21.6 \
        common/1.21.5 common/1.21.4 common/1.21.3 common/1.21.1 common/1.20.1 \
        common/1.19.2 common/1.18.2 common/1.17.1
git commit -m "feat(worldgen): naturalize surface dirt across all remaining versions"
```

---

### Task 4: Documentation check + optional manual verification

**Files:**
- Possibly Modify: `CHANGELOG.md` (Unreleased), `README*`, store descriptions — only if this is treated as a user-facing change.

- [ ] **Step 1: Decide changelog/doc updates**

This changes generated terrain appearance (user-visible). Per the project's CHANGELOG+docs sync rule, add an entry under CHANGELOG Unreleased and, if it qualifies as a feature note, sync README and store descriptions. Use the `minecraft-mod:update-changelog` skill. Confirm wording with the user before editing store descriptions.

- [ ] **Step 2: Optional manual visual verification**

Run a client on a grass biome with a nearby dirt patch and place a house/village:
```bash
./gradlew :neoforge:runClient
```
(or `:fabric:runClient`). Confirm the blended ring no longer shows isolated dirt blocks adjacent to grass, and that flat grassland generation is unchanged.

- [ ] **Step 3: Commit any doc changes (ask user first)**

```bash
git add CHANGELOG.md README*.md docs
git commit -m "docs: note surface dirt naturalization in changelog"
```

---

## Self-Review

- **Spec coverage:** post-process method (Reference + Task 1) ✓; snapshot/no-cascade ✓; both整地・補間 covered by running after all placement ✓; both generators (Tasks 1-3) ✓; all 15 versions (Tasks 1-3) ✓; 8-dir + height-agnostic encoded in method ✓; call site after blendCornerPillars ✓; API compatibility validated on oldest (Task 2) and full build (Task 3) ✓; verification via build + manual client (Tasks 1-4) ✓.
- **Placeholder scan:** the full method code is provided verbatim; build commands and commit groupings are concrete. No TBD/TODO.
- **Type consistency:** method named `naturalizeDirtSurface` everywhere; `Vec3i` form difference documented; relies only on existing `findGroundY` signature. Consistent across tasks.
