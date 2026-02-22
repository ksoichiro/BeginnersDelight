package com.beginnersdelight.forge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
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

        BeginnersDelight.LOGGER.info("Beginner's Delight (Forge) initialized");
    }
}
