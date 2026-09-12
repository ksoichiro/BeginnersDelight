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
