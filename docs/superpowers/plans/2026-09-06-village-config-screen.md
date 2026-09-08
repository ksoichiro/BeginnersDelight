# Village Config Screen (MC 26.2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-game, vanilla-widget settings screen (no Cloth Config / Forge Config API) for the 5 existing `VillageConfig` fields, reachable from the mod list / ModMenu / an unbound keybinding, on MC 26.2 only (Fabric + NeoForge — 26.2 has no Forge subproject).

**Architecture:** Port MinersMarket's proven "vanilla-widget config screen + TOML write-back" pattern (`~/src/github.com/ksoichiro/MinersMarket`, MC 26.2 reference files linked per task) into `common/26.2/.../village/`. The screen reads/writes the existing `VillageConfig`/`VillageConfigLoader` machinery already used by `/beginnersdelight config reload`; `VillageManager.setConfig()` makes a Done-button save take effect immediately in singleplayer (client and integrated server share one JVM), and `VillageConfigWriter` persists it to `config/beginnersdelight.toml` without discarding existing comments.

**Tech Stack:** Java 25, vanilla Minecraft 26.2 GUI widgets (`Screen`, `ContainerObjectSelectionList`, `EditBox`, `CycleButton`), night-config TOML (already a project dependency), Fabric Loader 0.19.3 + optional ModMenu (compileOnly), NeoForge 26.2.0.16-beta.

**Spec:** `docs/superpowers/specs/2026-09-06-village-config-screen-design.md`

## Global Constraints

- First implementation targets **MC 26.2 only** (`common/26.2`, `fabric/26.2`, `neoforge/26.2`, `props/26.2.properties`). Do not touch other version directories. Rollout to the other 18 versions is separate follow-up work (anchor-based rollout), not part of this plan.
- No external config-screen dependency: vanilla widgets only. ModMenu stays a `compileOnly` dependency, queried only via Fabric's optional `modmenu` entrypoint — never a hard `depends`.
- This repo has **zero unit tests anywhere** (`find . -path '*/src/test/*' -iname '*.java'` returns nothing) — do not introduce a test framework as part of this feature. Each task's verification step is compiling the affected Gradle subproject; final behavior is verified by manually running the client (Task 7).
- `neoforge/base/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java` is shared source included by every NeoForge version subproject (1.21.1 through 26.1.2, plus 26.2) via `srcDir '../base/src/main/java'`. Task 5 needs to add a client-only hook that references `VillageConfigScreen`, which only exists in `common/26.2` — so it **forks** a 26.2-local copy of this file instead of editing the shared one (same technique this codebase already uses for Fabric 1.16.5/1.17.1/1.18.2, see the comment atop `fabric/base/.../BeginnersDelightFabric.java`). Do not edit `neoforge/base` in this plan.
- Per this project's CLAUDE.md, git commits require explicit user instruction. Each task below ends with a "stage and present the diff" step instead of an unattended `git commit` — run the `git add`, show the diff/status, and wait for the user to say go before committing.
- Source comments in English. Lang files get both `en_us.json` and `ja_jp.json` entries (matches the existing `common/26.2/src/main/resources/assets/beginnersdelight/lang/` files).
- Compile commands always pass `-Ptarget_mc_version=26.2` explicitly, regardless of the configured default, so they work independent of `gradle.properties`.

---

### Task 1: Extract shared config validation ranges

**Files:**
- Create: `common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigRanges.java`
- Modify: `common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigLoader.java`

**Interfaces:**
- Produces: `VillageConfigRanges.MIN_PLOT_SIZE` / `MAX_PLOT_SIZE` / `MIN_HEIGHT_DIFFERENCE` / `MAX_HEIGHT_DIFFERENCE` (all `public static final int`), consumed by Task 3's screen and by `VillageConfigLoader`.

- [ ] **Step 1: Create `VillageConfigRanges`**

```java
package com.beginnersdelight.village;

/**
 * Validation ranges for {@link VillageConfig} values, shared by {@link VillageConfigLoader}
 * and the in-game config screen so both enforce identical rules.
 */
public final class VillageConfigRanges {

    public static final int MIN_PLOT_SIZE = 5;
    public static final int MAX_PLOT_SIZE = 256;
    public static final int MIN_HEIGHT_DIFFERENCE = 0;
    public static final int MAX_HEIGHT_DIFFERENCE = 256;

    private VillageConfigRanges() {
    }
}
```

- [ ] **Step 2: Point `VillageConfigLoader` at the shared constants**

In `common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigLoader.java`, remove these four private constants:

```java
    private static final int MIN_PLOT_SIZE = 5;
    private static final int MAX_PLOT_SIZE = 256;
    private static final int MIN_HEIGHT_DIFFERENCE = 0;
    private static final int MAX_HEIGHT_DIFFERENCE = 256;
```

Then replace their two call sites inside `parseOrDefaults`:

```java
        int plotSize = readInt(parsed, K_VILLAGE + "." + K_PLOT_SIZE,
                VillageConfigDefaults.PLOT_SIZE, MIN_PLOT_SIZE, MAX_PLOT_SIZE);
        int maxHeightDifference = readInt(parsed, K_VILLAGE + "." + K_MAX_HEIGHT_DIFFERENCE,
                VillageConfigDefaults.MAX_HEIGHT_DIFFERENCE, MIN_HEIGHT_DIFFERENCE, MAX_HEIGHT_DIFFERENCE);
```

with:

```java
        int plotSize = readInt(parsed, K_VILLAGE + "." + K_PLOT_SIZE,
                VillageConfigDefaults.PLOT_SIZE, VillageConfigRanges.MIN_PLOT_SIZE, VillageConfigRanges.MAX_PLOT_SIZE);
        int maxHeightDifference = readInt(parsed, K_VILLAGE + "." + K_MAX_HEIGHT_DIFFERENCE,
                VillageConfigDefaults.MAX_HEIGHT_DIFFERENCE, VillageConfigRanges.MIN_HEIGHT_DIFFERENCE, VillageConfigRanges.MAX_HEIGHT_DIFFERENCE);
```

(Same package, so no new import is needed.)

- [ ] **Step 3: Compile**

Run: `./gradlew :common-26.2:compileJava -Ptarget_mc_version=26.2`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Stage and present the diff**

```bash
git add common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigRanges.java \
        common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigLoader.java
git status
git diff --cached
```

Show the diff to the user and wait for explicit approval before running `git commit` (do not commit unattended).

---

### Task 2: Add config write-back and the in-JVM/client-dir hooks on `VillageManager`

**Files:**
- Create: `common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigWriter.java`
- Modify: `common/26.2/src/main/java/com/beginnersdelight/village/VillageManager.java`

**Interfaces:**
- Consumes: `VillageConfig` (existing, `village/VillageConfig.java`), `VillageConfigDefaults.CURRENT_SCHEMA_VERSION` (existing `int` constant).
- Produces: `VillageConfigWriter.save(Path configDir, VillageConfig config)` returning `boolean`; `VillageManager.setConfig(VillageConfig)`, `VillageManager.setClientConfigDir(Path)`, `VillageManager.getClientConfigDir()` returning `Path` (all consumed by Task 3's screen and Tasks 4/5's client entrypoints).

- [ ] **Step 1: Create `VillageConfigWriter`**

```java
package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;

import java.nio.file.Path;

/**
 * Writes {@link VillageConfig} values back to {@code beginnersdelight.toml}. Loads the
 * existing file first and only replaces values, so comments already in the file survive
 * (the file is not rewritten from scratch).
 */
public final class VillageConfigWriter {

    private static final String CONFIG_FILE_NAME = "beginnersdelight.toml";

    private VillageConfigWriter() {
    }

    public static boolean save(Path configDir, VillageConfig config) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);
        try (CommentedFileConfig fileConfig = CommentedFileConfig.builder(configFile, TomlFormat.instance()).build()) {
            fileConfig.load();
            fileConfig.set("schema_version", VillageConfigDefaults.CURRENT_SCHEMA_VERSION);
            fileConfig.set("village.plot_size", config.getPlotSize());
            fileConfig.set("village.max_height_difference", config.getMaxHeightDifference());
            fileConfig.set("village.generate_paths", config.isGeneratePaths());
            fileConfig.set("village.respawn_at_house", config.isRespawnAtHouse());
            fileConfig.set("starter_house.auto_generate", config.isAutoGenerateStarterHouse());
            fileConfig.save();
            return true;
        } catch (RuntimeException e) {
            BeginnersDelight.LOGGER.error("Failed to save {}", configFile, e);
            return false;
        }
    }
}
```

- [ ] **Step 2: Add the setter/holder methods to `VillageManager`**

In `common/26.2/src/main/java/com/beginnersdelight/village/VillageManager.java`, add a new field next to the existing `config` field:

```java
    private static VillageConfig config = VillageConfigDefaults.defaults();
    private static Path clientConfigDir;
```

Then add these three public methods right after the existing `getConfig()` method:

```java
    public static VillageConfig getConfig() {
        return config;
    }

    /**
     * Replaces the in-memory config without touching disk. Called by the config screen's
     * Done button: in singleplayer/LAN the client and the integrated server share one JVM,
     * so this takes effect immediately. On a dedicated server it only affects the calling
     * client's own process (which has no server), matching the screen's non-host warning.
     */
    public static void setConfig(VillageConfig newConfig) {
        config = newConfig;
    }

    /**
     * Records the loader-provided config directory for client-side use (the config screen
     * and its writer need this without a {@link MinecraftServer} reference, unlike
     * {@link #resolveConfigDir}). Set once by each loader's client entrypoint.
     */
    public static void setClientConfigDir(Path configDir) {
        clientConfigDir = configDir;
    }

    public static Path getClientConfigDir() {
        return clientConfigDir;
    }
```

(`Path` is already imported in this file.)

- [ ] **Step 3: Compile**

Run: `./gradlew :common-26.2:compileJava -Ptarget_mc_version=26.2`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Stage and present the diff**

```bash
git add common/26.2/src/main/java/com/beginnersdelight/village/VillageConfigWriter.java \
        common/26.2/src/main/java/com/beginnersdelight/village/VillageManager.java
git status
git diff --cached
```

Show the diff to the user and wait for explicit approval before committing.

---

### Task 3: Build the `VillageConfigScreen`

**Files:**
- Create: `common/26.2/src/main/java/com/beginnersdelight/village/client/VillageConfigScreen.java`
- Modify: `common/26.2/src/main/resources/assets/beginnersdelight/lang/en_us.json`
- Modify: `common/26.2/src/main/resources/assets/beginnersdelight/lang/ja_jp.json`

**Interfaces:**
- Consumes: `VillageManager.getConfig()/setConfig()/getClientConfigDir()` (Task 2), `VillageConfigWriter.save(Path, VillageConfig)` (Task 2), `VillageConfigRanges.*` (Task 1), `VillageConfigDefaults.defaults()` (existing), `VillageConfig` 5-arg constructor (existing: `int plotSize, int maxHeightDifference, boolean generatePaths, boolean respawnAtHouse, boolean autoGenerateStarterHouse`).
- Produces: `public class VillageConfigScreen extends Screen` with a `public VillageConfigScreen(Screen parent)` constructor, consumed by Tasks 4 and 5's entrypoints and ModMenu integration.

Reference implementation (proven on MC 26.2, do not copy verbatim — field set differs): `~/src/github.com/ksoichiro/MinersMarket/common/26.2/src/main/java/com/minersmarket/config/client/ConfigScreen.java`.

- [ ] **Step 1: Create the screen**

```java
package com.beginnersdelight.village.client;

import com.beginnersdelight.village.VillageConfig;
import com.beginnersdelight.village.VillageConfigDefaults;
import com.beginnersdelight.village.VillageConfigRanges;
import com.beginnersdelight.village.VillageConfigWriter;
import com.beginnersdelight.village.VillageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.List;

public class VillageConfigScreen extends Screen {
    private static final int LIST_TOP = 32;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_WIDTH = 310;
    private static final int ITEM_HEIGHT = 25;
    private static final int WIDGET_WIDTH = 100;
    private static final int WIDGET_HEIGHT = 20;
    private static final int VALID_TEXT_COLOR = 0xFFE0E0E0;
    private static final int INVALID_TEXT_COLOR = 0xFFFF5555;
    private static final int HEADER_TEXT_COLOR = 0xFFFFFF55;
    private static final int NOTE_TEXT_COLOR = 0xFFAAAAAA;
    private static final int NOTE_WARN_COLOR = 0xFFFFAA00;

    private final Screen parent;
    private SettingsList list;
    private Button doneButton;

    private NumberEntry plotSize;
    private NumberEntry maxHeightDifference;
    private CycleEntry generatePaths;
    private CycleEntry respawnAtHouse;
    private CycleEntry autoGenerateStarterHouse;

    public VillageConfigScreen(Screen parent) {
        super(Component.translatable("config.beginnersdelight.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        VillageConfig config = VillageManager.getConfig();
        this.list = new SettingsList(this.minecraft, this.width,
                this.height - LIST_TOP - FOOTER_HEIGHT, LIST_TOP, ITEM_HEIGHT);

        boolean joinedRemoteWorld = isJoinedRemoteWorld();
        this.list.addEntry(new HeaderEntry(this.font,
                Component.translatable(joinedRemoteWorld
                        ? "config.beginnersdelight.note_not_host"
                        : "config.beginnersdelight.note_local"),
                joinedRemoteWorld ? NOTE_WARN_COLOR : NOTE_TEXT_COLOR));

        this.list.addEntry(new HeaderEntry(this.font, Component.translatable("config.beginnersdelight.category.village")));
        this.plotSize = addNumberRow("plot_size", String.valueOf(config.getPlotSize()),
                VillageConfigRanges.MIN_PLOT_SIZE, VillageConfigRanges.MAX_PLOT_SIZE);
        this.maxHeightDifference = addNumberRow("max_height_difference", String.valueOf(config.getMaxHeightDifference()),
                VillageConfigRanges.MIN_HEIGHT_DIFFERENCE, VillageConfigRanges.MAX_HEIGHT_DIFFERENCE);
        this.generatePaths = addToggleRow("generate_paths", config.isGeneratePaths());
        this.respawnAtHouse = addToggleRow("respawn_at_house", config.isRespawnAtHouse());

        this.list.addEntry(new HeaderEntry(this.font, Component.translatable("config.beginnersdelight.category.starter_house")));
        this.autoGenerateStarterHouse = addToggleRow("auto_generate_starter_house", config.isAutoGenerateStarterHouse());

        this.addRenderableWidget(this.list);

        int buttonY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.translatable("config.beginnersdelight.reset"), b -> resetToDefaults())
                .bounds(this.width / 2 - 155, buttonY, 100, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 - 50, buttonY, 100, 20).build());
        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(this.width / 2 + 55, buttonY, 100, 20).build());
        updateDoneButton();
    }

    private NumberEntry addNumberRow(String key, String initialValue, long min, long max) {
        NumberEntry entry = new NumberEntry(this.font,
                Component.translatable("config.beginnersdelight.option." + key),
                rangeTooltip("config.beginnersdelight.option." + key + ".tooltip", min, max),
                initialValue, min, max, this::updateDoneButton);
        this.list.addEntry(entry);
        return entry;
    }

    private CycleEntry addToggleRow(String key, boolean initialValue) {
        CycleEntry entry = new CycleEntry(this.font,
                Component.translatable("config.beginnersdelight.option." + key),
                Component.translatable("config.beginnersdelight.option." + key + ".tooltip"),
                initialValue);
        this.list.addEntry(entry);
        return entry;
    }

    private static Component rangeTooltip(String tooltipKey, long min, long max) {
        MutableComponent range = Component.translatable("config.beginnersdelight.valid_range", min, max);
        return Component.translatable(tooltipKey).append("\n").append(range);
    }

    // The config is read only by server-side logic from the local file, and nothing syncs
    // it over the network. On a world hosted by someone else these values are the player's
    // own and do not affect that session, so the notice must say which case the player is
    // in. Singleplayer and LAN hosting both keep an integrated server.
    private boolean isJoinedRemoteWorld() {
        return this.minecraft != null && this.minecraft.level != null
                && !this.minecraft.hasSingleplayerServer();
    }

    private void updateDoneButton() {
        if (this.doneButton != null) {
            this.doneButton.active = validate();
        }
    }

    private boolean validate() {
        return this.plotSize.isValid() && this.maxHeightDifference.isValid();
    }

    private void saveAndClose() {
        if (!validate()) {
            return;
        }
        VillageConfig newConfig = new VillageConfig(
                this.plotSize.intValue(),
                this.maxHeightDifference.intValue(),
                this.generatePaths.getValue(),
                this.respawnAtHouse.getValue(),
                this.autoGenerateStarterHouse.getValue());
        VillageManager.setConfig(newConfig);
        Path configDir = VillageManager.getClientConfigDir();
        if (configDir != null) {
            VillageConfigWriter.save(configDir, newConfig);
        }
        onClose();
    }

    private void resetToDefaults() {
        VillageConfig defaults = VillageConfigDefaults.defaults();
        this.plotSize.setValue(String.valueOf(defaults.getPlotSize()));
        this.maxHeightDifference.setValue(String.valueOf(defaults.getMaxHeightDifference()));
        this.generatePaths.setValue(defaults.isGeneratePaths());
        this.respawnAtHouse.setValue(defaults.isRespawnAtHouse());
        this.autoGenerateStarterHouse.setValue(defaults.isAutoGenerateStarterHouse());
    }

    @Override
    public void resize(int width, int height) {
        // init() rebuilds every widget; snapshot unsaved edits so a window resize does
        // not silently discard them.
        String plotSizeValue = this.plotSize.getValue();
        String maxHeightDifferenceValue = this.maxHeightDifference.getValue();
        boolean generatePathsValue = this.generatePaths.getValue();
        boolean respawnAtHouseValue = this.respawnAtHouse.getValue();
        boolean autoGenerateValue = this.autoGenerateStarterHouse.getValue();
        super.resize(width, height);
        this.plotSize.setValue(plotSizeValue);
        this.maxHeightDifference.setValue(maxHeightDifferenceValue);
        this.generatePaths.setValue(generatePathsValue);
        this.respawnAtHouse.setValue(respawnAtHouseValue);
        this.autoGenerateStarterHouse.setValue(autoGenerateValue);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    static class SettingsList extends ContainerObjectSelectionList<Entry> {
        SettingsList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        @Override
        public int addEntry(VillageConfigScreen.Entry entry) {
            return super.addEntry(entry);
        }
    }

    abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    }

    static class HeaderEntry extends Entry {
        private final Font font;
        private final Component label;
        private final int color;

        HeaderEntry(Font font, Component label) {
            this(font, label, HEADER_TEXT_COLOR);
        }

        HeaderEntry(Font font, Component label, int color) {
            this.font = font;
            this.label = label;
            this.color = color;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            guiGraphics.centeredText(this.font, this.label, getX() + getWidth() / 2, getY() + 7, this.color);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    static class NumberEntry extends Entry {
        private final Font font;
        private final Component label;
        private final EditBox editBox;
        private final long min;
        private final long max;
        private boolean valid = true;

        NumberEntry(Font font, Component label, Component tooltip, String initialValue,
                    long min, long max, Runnable onChanged) {
            this.font = font;
            this.label = label;
            this.min = min;
            this.max = max;
            this.editBox = new EditBox(font, 0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label);
            this.editBox.setMaxLength(10);
            this.editBox.setValue(initialValue);
            this.editBox.setTooltip(Tooltip.create(tooltip));
            this.editBox.setResponder(value -> {
                this.valid = parse(value) != null;
                updateColor();
                onChanged.run();
            });
        }

        private Long parse(String value) {
            try {
                long parsed = Long.parseLong(value.trim());
                return parsed >= this.min && parsed <= this.max ? parsed : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        boolean isValid() {
            return this.valid;
        }

        long longValue() {
            Long parsed = parse(this.editBox.getValue());
            return parsed != null ? parsed : this.min;
        }

        int intValue() {
            return (int) longValue();
        }

        String getValue() {
            return this.editBox.getValue();
        }

        void setValue(String value) {
            this.editBox.setValue(value);
        }

        private void updateColor() {
            this.editBox.setTextColor(this.valid ? VALID_TEXT_COLOR : INVALID_TEXT_COLOR);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            guiGraphics.text(this.font, this.label, getX(), getY() + 6, 0xFFFFFFFF);
            this.editBox.setPosition(getX() + getWidth() - WIDGET_WIDTH, getY());
            this.editBox.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.editBox);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.editBox);
        }
    }

    static class CycleEntry extends Entry {
        private final Font font;
        private final Component label;
        private final CycleButton<Boolean> button;

        CycleEntry(Font font, Component label, Component tooltip, boolean initialValue) {
            this.font = font;
            this.label = label;
            this.button = CycleButton.onOffBuilder(initialValue).displayOnlyValue()
                    .create(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label, (btn, value) -> {
                    });
            this.button.setTooltip(Tooltip.create(tooltip));
        }

        boolean getValue() {
            return this.button.getValue();
        }

        void setValue(boolean value) {
            this.button.setValue(value);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
            guiGraphics.text(this.font, this.label, getX(), getY() + 6, 0xFFFFFFFF);
            this.button.setPosition(getX() + getWidth() - WIDGET_WIDTH, getY());
            this.button.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.button);
        }
    }
}
```

- [ ] **Step 2: Add lang keys (English)**

Replace the full contents of `common/26.2/src/main/resources/assets/beginnersdelight/lang/en_us.json` with:

```json
{
  "gamerule.beginnersdelight.generate_starter_house": "Beginner's Delight: Generate starter house",
  "gamerule.beginnersdelight.generate_starter_house.description": "Whether newly created worlds generate a starter house at spawn. Best set when creating a world; changing it later only affects worlds that have not generated one yet.",
  "config.beginnersdelight.title": "Beginner's Delight Settings",
  "config.beginnersdelight.category.village": "Village",
  "config.beginnersdelight.category.starter_house": "Starter House",
  "config.beginnersdelight.option.plot_size": "Plot Size",
  "config.beginnersdelight.option.plot_size.tooltip": "Size of each village plot in blocks (grid spacing). Changing this only affects newly placed houses.",
  "config.beginnersdelight.option.max_height_difference": "Max Height Difference",
  "config.beginnersdelight.option.max_height_difference.tooltip": "Maximum terrain height difference (in blocks) allowed within a plot for it to be considered buildable.",
  "config.beginnersdelight.option.generate_paths": "Generate Paths",
  "config.beginnersdelight.option.generate_paths.tooltip": "If ON, dirt paths are generated to connect houses and decorations.",
  "config.beginnersdelight.option.respawn_at_house": "Respawn At House",
  "config.beginnersdelight.option.respawn_at_house.tooltip": "If ON, players who have no bed respawn at their village house instead of the world spawn.",
  "config.beginnersdelight.option.auto_generate_starter_house": "Generate Starter House By Default",
  "config.beginnersdelight.option.auto_generate_starter_house.tooltip": "Default value of the starter house game rule for newly created worlds. Does not affect worlds that already exist.",
  "config.beginnersdelight.valid_range": "Valid range: %s - %s",
  "config.beginnersdelight.reset": "Reset to Defaults",
  "config.beginnersdelight.note_local": "Local settings: applied to worlds you host.",
  "config.beginnersdelight.note_not_host": "Not the host: changes won't affect this world.",
  "key.beginnersdelight.open_village_config": "Open Village Config Screen",
  "key.categories.beginnersdelight": "Beginner's Delight",
  "key.categories.beginnersdelight.main": "Beginner's Delight"
}
```

- [ ] **Step 3: Add lang keys (Japanese)**

Replace the full contents of `common/26.2/src/main/resources/assets/beginnersdelight/lang/ja_jp.json` with:

```json
{
  "gamerule.beginnersdelight.generate_starter_house": "Beginner's Delight: スターターハウスを生成",
  "gamerule.beginnersdelight.generate_starter_house.description": "新しく作成するワールドでスポーン地点にスターターハウスを生成するかどうか。ワールド作成時に設定するのが最適です。後から変更しても、まだ生成されていないワールドにのみ影響します。",
  "config.beginnersdelight.title": "Beginner's Delight 設定",
  "config.beginnersdelight.category.village": "村",
  "config.beginnersdelight.category.starter_house": "スターターハウス",
  "config.beginnersdelight.option.plot_size": "区画サイズ",
  "config.beginnersdelight.option.plot_size.tooltip": "各村区画のサイズ(ブロック数、グリッド間隔)。変更は新しく配置される家にのみ影響します。",
  "config.beginnersdelight.option.max_height_difference": "許容する高低差",
  "config.beginnersdelight.option.max_height_difference.tooltip": "区画が建築可能とみなされる、区画内の最大地形高低差(ブロック数)。",
  "config.beginnersdelight.option.generate_paths": "道を生成",
  "config.beginnersdelight.option.generate_paths.tooltip": "ONの場合、家や装飾物を結ぶ土の道が生成されます。",
  "config.beginnersdelight.option.respawn_at_house": "自宅でリスポーン",
  "config.beginnersdelight.option.respawn_at_house.tooltip": "ONの場合、ベッドを持たないプレイヤーはワールドスポーンではなく自分の村の家でリスポーンします。",
  "config.beginnersdelight.option.auto_generate_starter_house": "スターターハウスを既定で生成",
  "config.beginnersdelight.option.auto_generate_starter_house.tooltip": "新しく作成するワールドにおけるスターターハウスのゲームルールの既定値。既に存在するワールドには影響しません。",
  "config.beginnersdelight.valid_range": "有効範囲: %s ~ %s",
  "config.beginnersdelight.reset": "初期値に戻す",
  "config.beginnersdelight.note_local": "ローカル設定です。あなたがホストするワールドに適用されます。",
  "config.beginnersdelight.note_not_host": "ホストではありません。変更はこのワールドには反映されません。",
  "key.beginnersdelight.open_village_config": "村の設定画面を開く",
  "key.categories.beginnersdelight": "Beginner's Delight",
  "key.categories.beginnersdelight.main": "Beginner's Delight"
}
```

- [ ] **Step 4: Compile**

Run: `./gradlew :common-26.2:compileJava -Ptarget_mc_version=26.2`
Expected: `BUILD SUCCESSFUL`. (This does not exercise the GUI itself — a `Screen` subclass compiles without a running game. Visual/behavioral verification happens in Task 8.)

- [ ] **Step 5: Stage and present the diff**

```bash
git add common/26.2/src/main/java/com/beginnersdelight/village/client/VillageConfigScreen.java \
        common/26.2/src/main/resources/assets/beginnersdelight/lang/en_us.json \
        common/26.2/src/main/resources/assets/beginnersdelight/lang/ja_jp.json
git status
git diff --cached
```

Show the diff to the user and wait for explicit approval before committing.

---

### Task 4: Wire the Fabric entry points (keybinding, ModMenu, mod list)

**Files:**
- Create: `fabric/26.2/src/main/java/com/beginnersdelight/fabric/client/BeginnersDelightFabricClient.java`
- Create: `fabric/26.2/src/main/java/com/beginnersdelight/fabric/client/ModMenuIntegration.java`
- Modify: `fabric/26.2/src/main/resources/fabric.mod.json`
- Modify: `fabric/26.2/build.gradle`
- Modify: `props/26.2.properties`

**Interfaces:**
- Consumes: `VillageManager.setClientConfigDir(Path)` (Task 2), `VillageConfigScreen(Screen parent)` (Task 3).

These two new classes are version-local to `fabric/26.2` (not `fabric/base`), since ModMenu support is only being added for this one version right now; promote them to a shared `fabric/base-modmenu`-style location if/when this is rolled out to more Fabric versions (see MinersMarket's `fabric/base-modmenu` for that pattern).

- [ ] **Step 1: Create the Fabric client entrypoint (keybinding)**

```java
package com.beginnersdelight.fabric.client;

import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class BeginnersDelightFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VillageManager.setClientConfigDir(FabricLoader.getInstance().getConfigDir());

        KeyMapping.Category category = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath("beginnersdelight", "main"));
        KeyMapping openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.beginnersdelight.open_village_config",
                InputConstants.UNKNOWN.getValue(),
                category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.setScreenAndShow(new VillageConfigScreen(client.gui.screen()));
            }
        });
    }
}
```

- [ ] **Step 2: Create the optional ModMenu integration**

```java
package com.beginnersdelight.fabric.client;

import com.beginnersdelight.village.client.VillageConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Optional ModMenu integration. ModMenu is a compileOnly dependency: this class is only
 * instantiated by ModMenu itself (via the "modmenu" entrypoint in fabric.mod.json), so it
 * is inert when ModMenu is not installed.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return VillageConfigScreen::new;
    }
}
```

- [ ] **Step 3: Declare the new entrypoints in `fabric.mod.json`**

In `fabric/26.2/src/main/resources/fabric.mod.json`, replace:

```json
  "entrypoints": {
    "main": [
      "com.beginnersdelight.fabric.BeginnersDelightFabric"
    ]
  },
```

with:

```json
  "entrypoints": {
    "main": [
      "com.beginnersdelight.fabric.BeginnersDelightFabric"
    ],
    "client": [
      "com.beginnersdelight.fabric.client.BeginnersDelightFabricClient"
    ],
    "modmenu": [
      "com.beginnersdelight.fabric.client.ModMenuIntegration"
    ]
  },
```

- [ ] **Step 4: Add the optional ModMenu dependency to `fabric/26.2/build.gradle`**

Add this block immediately after the existing `dependencies { ... }` block (i.e. after its closing `}`, before the `sourceSets { ... }` block):

```groovy
// Optional ModMenu integration: compile-time only, and only when
// props/26.2.properties declares modmenu_version.
if (project.hasProperty('modmenu_version')) {
    repositories {
        maven {
            name = 'Modrinth'
            url = 'https://api.modrinth.com/maven'
            content { includeGroup 'maven.modrinth' }
        }
    }
    dependencies {
        compileOnly "maven.modrinth:modmenu:${modmenu_version}"
    }
}
```

- [ ] **Step 5: Declare the ModMenu version for 26.2**

In `props/26.2.properties`, add a new line under the `# API versions` section:

```properties
modmenu_version=20.0.1
```

- [ ] **Step 6: Compile**

Run: `./gradlew :fabric:compileJava -Ptarget_mc_version=26.2`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Stage and present the diff**

```bash
git add fabric/26.2/src/main/java/com/beginnersdelight/fabric/client/BeginnersDelightFabricClient.java \
        fabric/26.2/src/main/java/com/beginnersdelight/fabric/client/ModMenuIntegration.java \
        fabric/26.2/src/main/resources/fabric.mod.json \
        fabric/26.2/build.gradle \
        props/26.2.properties
git status
git diff --cached
```

Show the diff to the user and wait for explicit approval before committing.

---

### Task 5: Fork the NeoForge entrypoint and wire its config screen hooks

**Files:**
- Create: `neoforge/26.2/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java` (forked copy of `neoforge/base/.../BeginnersDelightNeoForge.java`, see Global Constraints)
- Create: `neoforge/26.2/src/main/java/com/beginnersdelight/neoforge/client/BeginnersDelightNeoForgeClient.java`
- Modify: `neoforge/26.2/build.gradle`

**Interfaces:**
- Consumes: `VillageManager.setClientConfigDir(Path)` (Task 2), `VillageConfigScreen(Screen parent)` (Task 3).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Create the client-only hook class**

```java
package com.beginnersdelight.neoforge.client;

import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// Client-only members isolated in their own class: KeyMapping must not be class-loaded
// on a dedicated server.
public final class BeginnersDelightNeoForgeClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath("beginnersdelight", "main"));
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.beginnersdelight.open_village_config",
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    private BeginnersDelightNeoForgeClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        VillageManager.setClientConfigDir(FMLPaths.CONFIGDIR.get());

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (ignored, parent) -> new VillageConfigScreen(parent));

        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_CONFIG_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_CONFIG_KEY.consumeClick()) {
                minecraft.setScreenAndShow(new VillageConfigScreen(minecraft.gui.screen()));
            }
        });
    }
}
```

- [ ] **Step 2: Fork the NeoForge main entrypoint into `neoforge/26.2`**

Create `neoforge/26.2/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java`:

```java
package com.beginnersdelight.neoforge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.neoforge.client.BeginnersDelightNeoForgeClient;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Forked from {@code neoforge/base} (used by every other NeoForge version) because this
 * version needs a client-side hook (the village config screen) that only exists in
 * common/26.2 so far. Reunify with neoforge/base once the screen is ported to the other
 * NeoForge-supported versions.
 */
@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightNeoForge {
    public BeginnersDelightNeoForge(IEventBus modEventBus, ModContainer container) {
        BeginnersDelight.init();

        // The game-rule API was restructured in MC 1.21.11, so registration lives in a
        // per-era NeoForgeGameRules picked by each subproject's source set.
        NeoForgeGameRules.register(modEventBus);

        // VillageManager must see a player's join before StarterHouseGenerator marks them as
        // teleported, or a brand-new player looks indistinguishable from a returning starter
        // house resident and steals the shared plot from whoever actually lived there.
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerStartedEvent event) ->
                VillageManager.onServerStarted(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                VillageManager.onPlayerJoin(serverPlayer);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                VillageManager.onPlayerRespawn(serverPlayer);
        });
        bus.addListener((ServerTickEvent.Post event) ->
                VillageManager.onServerTick(event.getServer()));
        bus.addListener((RegisterCommandsEvent event) ->
                VillageCommand.register(event.getDispatcher()));

        bus.addListener((ServerStartedEvent event) ->
                StarterHouseGenerator.tryGenerate(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                StarterHouseGenerator.onPlayerJoin(serverPlayer);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                StarterHouseGenerator.onPlayerRespawn(serverPlayer, event.isEndConquered());
        });

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            BeginnersDelightNeoForgeClient.init(modEventBus, container);
        }

        BeginnersDelight.LOGGER.info("Beginner's Delight (NeoForge) initialized");
    }
}
```

- [ ] **Step 3: Stop `neoforge/26.2` from also pulling in `neoforge/base`**

In `neoforge/26.2/build.gradle`, in the `sourceSets { main { java { ... } } }` block, remove this line (the class it provided now has a local copy from Step 2, and keeping both would be a duplicate-class compile error):

```groovy
            srcDir '../base/src/main/java'
```

So the `java` block becomes:

```groovy
        java {
            srcDir 'src/main/java'
            srcDir '../gamerule-modern/src/main/java'
        }
```

- [ ] **Step 4: Compile**

Run: `./gradlew :neoforge:compileJava -Ptarget_mc_version=26.2`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Confirm the other NeoForge versions are unaffected**

Run: `./gradlew :neoforge:compileJava -Ptarget_mc_version=1.21.11`
Expected: `BUILD SUCCESSFUL` (still uses the untouched `neoforge/base` copy).

- [ ] **Step 6: Stage and present the diff**

```bash
git add neoforge/26.2/src/main/java/com/beginnersdelight/neoforge/BeginnersDelightNeoForge.java \
        neoforge/26.2/src/main/java/com/beginnersdelight/neoforge/client/BeginnersDelightNeoForgeClient.java \
        neoforge/26.2/build.gradle
git status
git diff --cached
```

Show the diff to the user and wait for explicit approval before committing.

---

### Task 6: Update README and CHANGELOG

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Update the README's Village Mode bullet**

In `README.md`, replace this line (around line 18):

```markdown
- **Village Mode** (optional): Grow a village around the world spawn as players join — each new player gets their own house connected by dirt paths, with decoration buildings (well, shed, storehouse, farm) appearing as the village grows. Enable it in-game with `/beginnersdelight village enable`; players can respawn at their own house. Configurable via a `config/beginnersdelight.toml` file, reloadable in-game with `/beginnersdelight config reload`
```

with:

```markdown
- **Village Mode** (optional): Grow a village around the world spawn as players join — each new player gets their own house connected by dirt paths, with decoration buildings (well, shed, storehouse, farm) appearing as the village grows. Enable it in-game with `/beginnersdelight village enable`; players can respawn at their own house. Configurable via a `config/beginnersdelight.toml` file, reloadable in-game with `/beginnersdelight config reload`, or via an in-game settings screen (mod list "Config" button, ModMenu if installed, or an unbound keybinding) — MC 26.2 only for now, the host player's edits apply immediately for singleplayer/LAN
```

- [ ] **Step 2: Add a CHANGELOG entry**

In `CHANGELOG.md`, under `## [Unreleased]`, add an `### Added` section before the existing `### Fixed` section:

```markdown
## [Unreleased]

### Added

- In-game settings screen for Village Mode (MC 26.2 only for now), reachable from the mod list, ModMenu (if installed), or an unbound keybinding — no external config-mod dependency required

### Fixed
```

- [ ] **Step 3: Stage and present the diff**

```bash
git add README.md CHANGELOG.md
git status
git diff --cached
```

Show the diff to the user and wait for explicit approval before committing.

---

### Task 7: Manual verification (`runClient`)

This task has no code changes — it is the GUI/behavioral check the earlier compile-only steps could not cover. Perform it before asking the user to do a final commit/squash of Tasks 1–6.

**Fabric:**

- [ ] **Step 1: Launch the Fabric client**

Run: `./gradlew :fabric:runClient -Ptarget_mc_version=26.2`

- [ ] **Step 2: Open the screen from the mod list**

In-game: Mods → Beginner's Delight → Config. Confirm the screen opens with the title "Beginner's Delight Settings", a "Local settings: applied to worlds you host." note (singleplayer), the Village and Starter House sections, and all 5 fields showing the current `beginnersdelight.toml` values.

- [ ] **Step 3: Validate field behavior**

Type a value outside `[5, 256]` into Plot Size. Confirm the field turns red and the Done button becomes disabled. Fix it back to a valid value and confirm Done re-enables. Toggle each `CycleButton` field and confirm it flips ON/OFF.

- [ ] **Step 4: Reset / Cancel / Done**

Click Reset, confirm all fields return to `VillageConfigDefaults` values (plot_size=20, max_height_difference=10, generate_paths=ON, respawn_at_house=ON, auto_generate_starter_house=ON). Change a value, click Cancel, reopen the screen, and confirm the change was discarded. Change a value, click Done, reopen the screen, and confirm the new value persisted. Check `run/config/beginnersdelight.toml` on disk to confirm the write-back kept the file's existing comments.

- [ ] **Step 5: Keybinding**

Options → Controls → Key Binds → find "Open Village Config Screen" under the "Beginner's Delight" category, bind it to a key, press it in-game, and confirm the screen opens.

- [ ] **Step 6 (only if ModMenu is installed): ModMenu entry**

Drop a ModMenu jar matching `modmenu_version` (20.0.1) into `fabric/26.2/run/mods/`, relaunch, and confirm Beginner's Delight shows a gear/Config icon in ModMenu that opens the same screen. This step is optional — the mod list "Config" button in Step 2 already proves the core screen works without ModMenu.

**NeoForge:**

- [ ] **Step 7: Launch the NeoForge client**

Run: `./gradlew :neoforge:runClient -Ptarget_mc_version=26.2`

- [ ] **Step 8: Repeat Steps 2-5 on NeoForge**

Open via Mods → Beginner's Delight → Config (NeoForge's own config-screen entry point), repeat the field/Reset/Cancel/Done checks from Steps 3-4, and confirm the keybinding (Step 5) also works here.

- [ ] **Step 9: Non-host warning**

Host a LAN or dedicated-server-joined-as-non-host session (or use the "Open to LAN" flow from a second client) and confirm the screen shows "Not the host: changes won't affect this world." instead of the local-settings note.

- [ ] **Step 10: Report results**

Summarize pass/fail for each step above to the user before proceeding to any final commit.

---

## Self-Review Notes

- **Spec coverage:** every section of `docs/superpowers/specs/2026-09-06-village-config-screen-design.md` maps to a task — storage layer (Tasks 1-2), screen (Task 3), client config dir (Task 2 + Tasks 4-5), entry points (Tasks 4-5), testing (Task 7), out-of-scope items are not touched by any task.
- **Type consistency checked:** `VillageConfig`'s constructor signature (`int, int, boolean, boolean, boolean`) and getters (`getPlotSize`, `getMaxHeightDifference`, `isGeneratePaths`, `isRespawnAtHouse`, `isAutoGenerateStarterHouse`) match between Task 3's screen and the existing `VillageConfig.java`. `VillageManager.setConfig`/`setClientConfigDir`/`getClientConfigDir` are defined once in Task 2 and consumed identically in Tasks 3-5.
- **No placeholders:** all steps contain full file contents or exact diffs; no "TBD" or "add appropriate X" language.
