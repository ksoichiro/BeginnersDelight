package com.beginnersdelight.fabric.client;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

import java.nio.file.Path;

public class BeginnersDelightFabricClient implements ClientModInitializer {
    // Pre-1.21.11 KeyMapping categories are plain Strings (no KeyMapping.Category type),
    // by convention translated via a "key.categories.<name>" lang key (matching vanilla's
    // CATEGORY_* constants, e.g. "key.categories.movement").
    private static final String CATEGORY = "key.categories.beginnersdelight";

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        VillageManager.setClientConfigDir(configDir);
        VillageManager.setConfig(VillageConfigLoader.load(configDir));

        KeyMapping openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.beginnersdelight.open_village_config",
                InputConstants.UNKNOWN.getValue(),
                CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.setScreen(new VillageConfigScreen(client.screen));
            }
        });
    }
}
