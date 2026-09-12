package com.beginnersdelight.forge.client;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

public class BeginnersDelightForgeClient {
    public static void init(IEventBus modBus) {
        VillageManager.setClientConfigDir(FMLPaths.CONFIGDIR.get());
        VillageManager.setConfig(VillageConfigLoader.load(FMLPaths.CONFIGDIR.get()));

        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((minecraft, parent) -> new VillageConfigScreen(parent)));
        modBus.addListener(ClientConfigHooks::onClientSetup);
        ClientConfigHooks.registerTicker();
    }

    // Client-only members isolated in a holder class: KeyMapping must not be
    // class-loaded on a dedicated server.
    private static final class ClientConfigHooks {
        private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
                "key.beginnersdelight.open_village_config",
                InputConstants.UNKNOWN.getValue(),
                "key.categories.beginnersdelight");

        static void onClientSetup(FMLClientSetupEvent event) {
            ClientRegistry.registerKeyBinding(OPEN_CONFIG_KEY);
        }

        static void registerTicker() {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase != TickEvent.Phase.END) {
                    return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                while (OPEN_CONFIG_KEY.consumeClick()) {
                    minecraft.setScreen(new VillageConfigScreen(minecraft.screen));
                }
            });
        }
    }
}
