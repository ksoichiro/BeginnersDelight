package com.beginnersdelight.village.client;

import com.beginnersdelight.village.VillageConfig;
import com.beginnersdelight.village.VillageConfigDefaults;
import com.beginnersdelight.village.VillageConfigRanges;
import com.beginnersdelight.village.VillageConfigWriter;
import com.beginnersdelight.village.VillageManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

import java.nio.file.Path;
import java.util.Collections;
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
    private ToggleEntry generatePaths;
    private ToggleEntry respawnAtHouse;
    private ToggleEntry autoGenerateStarterHouse;

    public VillageConfigScreen(Screen parent) {
        super(new TranslatableComponent("config.beginnersdelight.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        VillageConfig config = VillageManager.getConfig();
        this.list = new SettingsList(this.minecraft, this.width, this.height,
                LIST_TOP, this.height - FOOTER_HEIGHT, ITEM_HEIGHT);

        boolean joinedRemoteWorld = isJoinedRemoteWorld();
        this.list.addEntry(new HeaderEntry(this.font,
                new TranslatableComponent(joinedRemoteWorld
                        ? "config.beginnersdelight.note_not_host"
                        : "config.beginnersdelight.note_local"),
                joinedRemoteWorld ? NOTE_WARN_COLOR : NOTE_TEXT_COLOR));

        this.list.addEntry(new HeaderEntry(this.font, new TranslatableComponent("config.beginnersdelight.category.village")));
        this.plotSize = addNumberRow("plot_size", String.valueOf(config.getPlotSize()),
                VillageConfigRanges.MIN_PLOT_SIZE, VillageConfigRanges.MAX_PLOT_SIZE);
        this.maxHeightDifference = addNumberRow("max_height_difference", String.valueOf(config.getMaxHeightDifference()),
                VillageConfigRanges.MIN_HEIGHT_DIFFERENCE, VillageConfigRanges.MAX_HEIGHT_DIFFERENCE);
        this.generatePaths = addToggleRow("generate_paths", config.isGeneratePaths());
        this.respawnAtHouse = addToggleRow("respawn_at_house", config.isRespawnAtHouse());

        this.list.addEntry(new HeaderEntry(this.font, new TranslatableComponent("config.beginnersdelight.category.starter_house")));
        this.autoGenerateStarterHouse = addToggleRow("auto_generate_starter_house", config.isAutoGenerateStarterHouse());

        this.addWidget(this.list);

        int buttonY = this.height - 28;
        this.addButton(new Button(this.width / 2 - 155, buttonY, 100, 20,
                new TranslatableComponent("config.beginnersdelight.reset"), b -> resetToDefaults()));
        this.addButton(new Button(this.width / 2 - 50, buttonY, 100, 20,
                CommonComponents.GUI_CANCEL, b -> onClose()));
        this.doneButton = this.addButton(new Button(this.width / 2 + 55, buttonY, 100, 20,
                CommonComponents.GUI_DONE, b -> saveAndClose()));
        updateDoneButton();
    }

    private NumberEntry addNumberRow(String key, String initialValue, long min, long max) {
        NumberEntry entry = new NumberEntry(this.font,
                new TranslatableComponent("config.beginnersdelight.option." + key),
                initialValue, min, max, this::updateDoneButton);
        this.list.addEntry(entry);
        return entry;
    }

    private ToggleEntry addToggleRow(String key, boolean initialValue) {
        ToggleEntry entry = new ToggleEntry(this.font,
                new TranslatableComponent("config.beginnersdelight.option." + key),
                initialValue);
        this.list.addEntry(entry);
        return entry;
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
    public void resize(Minecraft minecraft, int width, int height) {
        // init() rebuilds every widget; snapshot unsaved edits so a window resize does
        // not silently discard them.
        String plotSizeValue = this.plotSize.getValue();
        String maxHeightDifferenceValue = this.maxHeightDifference.getValue();
        boolean generatePathsValue = this.generatePaths.getValue();
        boolean respawnAtHouseValue = this.respawnAtHouse.getValue();
        boolean autoGenerateValue = this.autoGenerateStarterHouse.getValue();
        super.resize(minecraft, width, height);
        this.plotSize.setValue(plotSizeValue);
        this.maxHeightDifference.setValue(maxHeightDifferenceValue);
        this.generatePaths.setValue(generatePathsValue);
        this.respawnAtHouse.setValue(respawnAtHouseValue);
        this.autoGenerateStarterHouse.setValue(autoGenerateValue);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        this.list.render(poseStack, mouseX, mouseY, partialTick);
        super.render(poseStack, mouseX, mouseY, partialTick);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    static class SettingsList extends ContainerObjectSelectionList<Entry> {
        SettingsList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
            super(minecraft, width, height, top, bottom, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        @Override
        protected int getScrollbarPosition() {
            // Default falls inside the wider ROW_WIDTH row and overlaps the right-aligned
            // widgets; push the scrollbar past the row edge.
            return this.width / 2 + ROW_WIDTH / 2 + 10;
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
        public void render(PoseStack poseStack, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawCenteredString(poseStack, this.font, this.label, left + width / 2, top + 7, this.color);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }
    }

    static class NumberEntry extends Entry {
        private final Font font;
        private final Component label;
        private final EditBox editBox;
        private final long min;
        private final long max;
        private boolean valid = true;

        NumberEntry(Font font, Component label, String initialValue, long min, long max, Runnable onChanged) {
            this.font = font;
            this.label = label;
            this.min = min;
            this.max = max;
            this.editBox = new EditBox(font, 0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label);
            this.editBox.setMaxLength(10);
            this.editBox.setValue(initialValue);
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
        public void render(PoseStack poseStack, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawString(poseStack, this.font, this.label, left, top + 6, 0xFFFFFFFF);
            this.editBox.setX(left + width - WIDGET_WIDTH);
            this.editBox.y = top;
            this.editBox.render(poseStack, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(this.editBox);
        }
    }

    // MC 1.16.5 has no CycleButton; a plain Button toggling its own state and label
    // stands in for it.
    static class ToggleEntry extends Entry {
        private final Font font;
        private final Component label;
        private final Button button;
        private boolean value;

        ToggleEntry(Font font, Component label, boolean initialValue) {
            this.font = font;
            this.label = label;
            this.value = initialValue;
            this.button = new Button(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, displayText(), b -> {
                this.value = !this.value;
                b.setMessage(displayText());
            });
        }

        private Component displayText() {
            return this.value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        }

        boolean getValue() {
            return this.value;
        }

        void setValue(boolean value) {
            this.value = value;
            this.button.setMessage(displayText());
        }

        @Override
        public void render(PoseStack poseStack, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawString(poseStack, this.font, this.label, left, top + 6, 0xFFFFFFFF);
            this.button.x = left + width - WIDGET_WIDTH;
            this.button.y = top;
            this.button.render(poseStack, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(this.button);
        }
    }
}
