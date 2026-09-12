package com.beginnersdelight.fabric.client;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public class BeginnersDelightFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        VillageManager.setClientConfigDir(configDir);
        VillageManager.setConfig(VillageConfigLoader.load(configDir));

        KeyMapping.Category category = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath("beginnersdelight", "main"));
        KeyMapping openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.beginnersdelight.open_village_config",
                InputConstants.UNKNOWN.getValue(),
                category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                // MC 26.1.x keeps the current screen as a field directly on Minecraft;
                // 26.2 moved it onto Gui as a screen() accessor instead.
                client.setScreenAndShow(new VillageConfigScreen(client.screen));
            }
        });
    }
}
