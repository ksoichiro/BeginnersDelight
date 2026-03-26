package com.beginnersdelight.fabric;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class BeginnersDelightFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BeginnersDelight.init();

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
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                VillageCommand.register(dispatcher));

        BeginnersDelight.LOGGER.info("Beginner's Delight (Fabric) initialized");
    }
}
