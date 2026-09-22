package com.beginnersdelight.forge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.forge.client.BeginnersDelightForgeClient;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.ModGameRules;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightForge {
    public BeginnersDelightForge() {
        BeginnersDelight.init();

        boolean starterHouseDefault = VillageConfigLoader
                .load(FMLPaths.CONFIGDIR.get())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = GameRules.register(
                ModGameRules.RULE_NAME,
                GameRules.Category.MISC,
                GameRules.BooleanValue.create(starterHouseDefault));

        // VillageManager must handle the join before the starter-house handler marks a player
        // as teleported, otherwise a new player can be mistaken for a returning resident.
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                VillageManager.onServerStarted(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                VillageManager.onPlayerJoin(serverPlayer);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                VillageManager.onPlayerRespawn(serverPlayer);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) ->
                VillageManager.onServerTick(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                VillageCommand.register(event.getDispatcher()));

        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                StarterHouseGenerator.tryGenerate(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                StarterHouseGenerator.onPlayerJoin(serverPlayer);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                StarterHouseGenerator.onPlayerRespawn(serverPlayer, event.isEndConquered());
            }
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            BeginnersDelightForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }

        BeginnersDelight.LOGGER.info("Beginner's Delight (Forge) initialized");
    }
}
