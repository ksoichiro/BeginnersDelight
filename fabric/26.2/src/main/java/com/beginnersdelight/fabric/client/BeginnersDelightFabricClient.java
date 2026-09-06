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
