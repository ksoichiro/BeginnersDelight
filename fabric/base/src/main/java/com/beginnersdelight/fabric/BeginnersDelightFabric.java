package com.beginnersdelight.fabric;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.fabricmc.api.ModInitializer;
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

        BeginnersDelight.LOGGER.info("Beginner's Delight (Fabric) initialized");
    }
}
