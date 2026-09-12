package com.beginnersdelight.neoforge.client;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;

// Client-only members isolated in their own class: KeyMapping must not be class-loaded
// on a dedicated server.
public final class BeginnersDelightNeoForgeClient {
    // Pre-1.21.11 KeyMapping categories are plain Strings (no KeyMapping.Category type),
    // by convention translated via a "key.categories.<name>" lang key (matching vanilla's
    // CATEGORY_* constants, e.g. "key.categories.movement").
    private static final String CATEGORY = "key.categories.beginnersdelight";
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.beginnersdelight.open_village_config",
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    private BeginnersDelightNeoForgeClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        Path configDir = FMLPaths.CONFIGDIR.get();
        VillageManager.setClientConfigDir(configDir);
        VillageManager.setConfig(VillageConfigLoader.load(configDir));

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (ignored, parent) -> new VillageConfigScreen(parent));

        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_CONFIG_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_CONFIG_KEY.consumeClick()) {
                minecraft.setScreen(new VillageConfigScreen(minecraft.screen));
            }
        });
    }
}
