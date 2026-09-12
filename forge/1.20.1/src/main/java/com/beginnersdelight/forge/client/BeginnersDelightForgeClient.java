package com.beginnersdelight.forge.client;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

public class BeginnersDelightForgeClient {
    public static void init(IEventBus modBus) {
        VillageManager.setClientConfigDir(FMLPaths.CONFIGDIR.get());
        VillageManager.setConfig(VillageConfigLoader.load(FMLPaths.CONFIGDIR.get()));

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new VillageConfigScreen(parent)));
        ClientConfigHooks.register(modBus);
    }

    // Client-only members isolated in a holder class: KeyMapping must not be
    // class-loaded on a dedicated server.
    private static final class ClientConfigHooks {
        private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
                "key.beginnersdelight.open_village_config",
                InputConstants.UNKNOWN.getValue(),
                "key.categories.beginnersdelight");

        static void register(IEventBus modBus) {
            modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_CONFIG_KEY));
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
