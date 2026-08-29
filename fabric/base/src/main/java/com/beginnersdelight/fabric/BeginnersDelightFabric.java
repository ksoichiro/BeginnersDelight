package com.beginnersdelight.fabric;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Shared Fabric entry point. MC 1.16.5, 1.17.1 and 1.18.2 do not use this file: their
 * subprojects leave {@code ../base/src/main/java} off the source set because they need
 * the v1 CommandRegistrationCallback, and keep their own copy instead. Any listener
 * added here has to be added to those three copies as well, or the feature it drives
 * silently does nothing on them -- the build stays green either way.
 */
public class BeginnersDelightFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BeginnersDelight.init();

        // The game-rule API was restructured in MC 1.21.11, so registration lives in a
        // per-era FabricGameRules picked by each subproject's source set.
        FabricGameRules.register();

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
        ServerTickEvents.END_SERVER_TICK.register(VillageManager::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VillageCommand.register(dispatcher));

        BeginnersDelight.LOGGER.info("Beginner's Delight (Fabric) initialized");
    }
}
