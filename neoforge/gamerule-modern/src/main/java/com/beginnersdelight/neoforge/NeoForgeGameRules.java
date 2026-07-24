package com.beginnersdelight.neoforge;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.worldgen.ModGameRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the mod's game rules against the restructured game-rule API of MC 1.21.11+,
 * where a rule is an entry of the {@code minecraft:game_rule} registry. The pre-1.21.11
 * counterpart of this class lives in {@code neoforge/gamerule-legacy}; both are compiled
 * into the same package, and each NeoForge subproject picks exactly one of the two source
 * directories.
 */
final class NeoForgeGameRules {

    private NeoForgeGameRules() {
    }

    static void register(IEventBus modEventBus) {
        boolean starterHouseDefault = VillageConfigLoader
                .load(FMLPaths.CONFIGDIR.get())
                .isAutoGenerateStarterHouse();

        // MC 1.21.11+ game rules live in the frozen minecraft:game_rule registry, which vanilla's
        // Bootstrap.bootStrap() already froze before the mod constructor runs. NeoForge unfreezes
        // every built-in registry and fires a RegisterEvent per registry key on the mod bus
        // (including game_rule) before re-freezing, so registration must happen inside that event.
        modEventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.GAME_RULE)) {
                ModGameRules.GENERATE_STARTER_HOUSE = GameRules.registerBoolean(
                        ModGameRules.RULE_NAME,
                        GameRuleCategory.MISC,
                        starterHouseDefault);
            }
        });
    }
}
