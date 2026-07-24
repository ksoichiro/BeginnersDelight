package com.beginnersdelight.fabric;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.worldgen.ModGameRules;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.GameRules;

/**
 * Registers the mod's game rules against the String-keyed game-rule API used up to
 * MC 1.21.10. The 1.21.11+ counterpart of this class lives in {@code fabric/gamerule-modern};
 * both are compiled into the same package, and each Fabric subproject picks exactly one of
 * the two source directories.
 */
final class FabricGameRules {

    private FabricGameRules() {
    }

    static void register() {
        // Both vanilla GameRules.register(...) and GameRules.BooleanValue.create(...) are
        // inaccessible from outside net.minecraft.world.level on these versions, so registration
        // goes through Fabric API's game-rule module, which reaches them via an access widener.
        boolean starterHouseDefault = VillageConfigLoader
                .load(FabricLoader.getInstance().getConfigDir())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = GameRuleRegistry.register(
                ModGameRules.RULE_NAME,
                GameRules.Category.MISC,
                GameRuleFactory.createBooleanRule(starterHouseDefault));
    }
}
