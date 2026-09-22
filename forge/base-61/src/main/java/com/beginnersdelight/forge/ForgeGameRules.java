package com.beginnersdelight.forge;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.worldgen.ModGameRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;

final class ForgeGameRules {
    private ForgeGameRules() {
    }

    static void register(BusGroup modBusGroup) {
        boolean starterHouseDefault = VillageConfigLoader
                .load(FMLPaths.CONFIGDIR.get())
                .isAutoGenerateStarterHouse();
        RegisterEvent.getBus(modBusGroup).addListener(event -> {
            if (event.getRegistryKey().equals(Registries.GAME_RULE)) {
                ModGameRules.GENERATE_STARTER_HOUSE = GameRules.registerBoolean(
                        ModGameRules.RULE_NAME, GameRuleCategory.MISC, starterHouseDefault);
            }
        });
    }
}
