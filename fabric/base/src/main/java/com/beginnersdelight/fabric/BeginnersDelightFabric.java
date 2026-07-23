package com.beginnersdelight.fabric;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.ModGameRules;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public class BeginnersDelightFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BeginnersDelight.init();

        // Vanilla GameRules.registerBoolean(...) is private in the raw (non access-transformed)
        // Fabric dev jar, so it cannot be called directly here as on NeoForge. Fabric API's
        // restructured game-rule module (GameRuleBuilder, replacing the pre-26.x
        // GameRuleRegistry/GameRuleFactory) builds the GameRule via public APIs and registers it
        // into the vanilla GAME_RULE registry, so use that instead.
        boolean starterHouseDefault = VillageConfigLoader
                .load(FabricLoader.getInstance().getConfigDir())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = GameRuleBuilder
                .forBoolean(starterHouseDefault)
                .category(GameRuleCategory.MISC)
                .buildAndRegister(Identifier.parse(ModGameRules.RULE_NAME));

        ServerLifecycleEvents.SERVER_STARTED.register(StarterHouseGenerator::tryGenerate);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                StarterHouseGenerator.onPlayerJoin(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                StarterHouseGenerator.onPlayerRespawn(newPlayer, !alive));

        ServerLifecycleEvents.SERVER_STARTED.register(VillageManager::onServerStarted);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                VillageManager.onPlayerJoin(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                VillageManager.onPlayerRespawn(newPlayer));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VillageCommand.register(dispatcher));

        BeginnersDelight.LOGGER.info("Beginner's Delight (Fabric) initialized");
    }
}
