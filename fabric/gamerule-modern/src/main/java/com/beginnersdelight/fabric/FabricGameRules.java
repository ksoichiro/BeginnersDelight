package com.beginnersdelight.fabric;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.worldgen.ModGameRules;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Registers the mod's game rules against the restructured game-rule API of MC 1.21.11+,
 * where a rule is an entry of the {@code minecraft:game_rule} registry keyed by an
 * {@link Identifier}. The pre-1.21.11 counterpart of this class lives in
 * {@code fabric/gamerule-legacy}; both are compiled into the same package, and each
 * Fabric subproject picks exactly one of the two source directories.
 */
final class FabricGameRules {

    private FabricGameRules() {
    }

    static void register() {
        // Vanilla GameRules.registerBoolean(...) is private in the raw (non access-transformed)
        // Fabric dev jar, so it cannot be called directly here as on NeoForge. Fabric API's
        // restructured game-rule module (GameRuleBuilder, replacing the pre-1.21.11
        // GameRuleRegistry/GameRuleFactory) builds the GameRule via public APIs and registers it
        // into the vanilla GAME_RULE registry, so use that instead.
        boolean starterHouseDefault = VillageConfigLoader
                .load(FabricLoader.getInstance().getConfigDir())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = GameRuleBuilder
                .forBoolean(starterHouseDefault)
                .category(GameRuleCategory.MISC)
                .buildAndRegister(Identifier.parse(ModGameRules.RULE_NAME));
    }
}
