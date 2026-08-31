package com.beginnersdelight.neoforge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightNeoForge {
    public BeginnersDelightNeoForge(IEventBus modEventBus) {
        BeginnersDelight.init();

        // The game-rule API was restructured in MC 1.21.11, so registration lives in a
        // per-era NeoForgeGameRules picked by each subproject's source set.
        NeoForgeGameRules.register(modEventBus);

        // VillageManager must see a player's join before StarterHouseGenerator marks them as
        // teleported, or a brand-new player looks indistinguishable from a returning starter
        // house resident and steals the shared plot from whoever actually lived there.
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerStartedEvent event) ->
                VillageManager.onServerStarted(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                VillageManager.onPlayerJoin(serverPlayer);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                VillageManager.onPlayerRespawn(serverPlayer);
        });
        bus.addListener((ServerTickEvent.Post event) ->
                VillageManager.onServerTick(event.getServer()));
        bus.addListener((RegisterCommandsEvent event) ->
                VillageCommand.register(event.getDispatcher()));

        bus.addListener((ServerStartedEvent event) ->
                StarterHouseGenerator.tryGenerate(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                StarterHouseGenerator.onPlayerJoin(serverPlayer);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer)
                StarterHouseGenerator.onPlayerRespawn(serverPlayer, event.isEndConquered());
        });

        BeginnersDelight.LOGGER.info("Beginner's Delight (NeoForge) initialized");
    }
}
