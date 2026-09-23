package com.beginnersdelight.forge.client;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.village.client.VillageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-only setup, loaded only after the loader-side distribution check. */
public final class BeginnersDelightForgeClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "main"));
    private static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.beginnersdelight.open_village_config", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    private BeginnersDelightForgeClient() {
    }

    public static void init() {
        VillageManager.setClientConfigDir(FMLPaths.CONFIGDIR.get());
        VillageManager.setConfig(VillageConfigLoader.load(FMLPaths.CONFIGDIR.get()));
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new VillageConfigScreen(parent)));
    }

    public static KeyMapping openConfigKey() { return OPEN_CONFIG_KEY; }

    public static void handleTick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CONFIG_KEY.consumeClick()) {
            minecraft.setScreen(new VillageConfigScreen(minecraft.screen));
        }
    }
}
