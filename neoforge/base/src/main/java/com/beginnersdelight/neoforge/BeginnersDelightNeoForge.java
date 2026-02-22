package com.beginnersdelight.neoforge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightNeoForge {
    public BeginnersDelightNeoForge(IEventBus modEventBus) {
        BeginnersDelight.init();

        IEventBus bus = NeoForge.EVENT_BUS;
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
