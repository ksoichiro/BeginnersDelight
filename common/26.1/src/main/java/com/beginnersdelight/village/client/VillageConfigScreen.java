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
        boolean saved = configDir == null || VillageConfigWriter.save(configDir, newConfig);
        if (saved) {
            onClose();
        }
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
