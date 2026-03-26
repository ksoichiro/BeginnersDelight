package com.beginnersdelight.forge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightForge {
    public BeginnersDelightForge() {
        BeginnersDelight.init();

        MinecraftForge.EVENT_BUS.addListener((FMLServerStartedEvent event) ->
                StarterHouseGenerator.tryGenerate(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                StarterHouseGenerator.onPlayerJoin((ServerPlayer) event.getEntity());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                StarterHouseGenerator.onPlayerRespawn((ServerPlayer) event.getEntity(), event.isEndConquered());
            }
        });

        MinecraftForge.EVENT_BUS.addListener((FMLServerStartedEvent event) ->
                VillageManager.onServerStarted(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                VillageManager.onPlayerJoin((ServerPlayer) event.getEntity());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer) {
                VillageManager.onPlayerRespawn((ServerPlayer) event.getEntity());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                VillageCommand.register(event.getDispatcher()));

        BeginnersDelight.LOGGER.info("Beginner's Delight (Forge) initialized");
    }
}
