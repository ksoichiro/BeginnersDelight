package com.beginnersdelight.neoforge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.ModGameRules;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightNeoForge {
    public BeginnersDelightNeoForge(IEventBus modEventBus) {
        BeginnersDelight.init();

        boolean starterHouseDefault = VillageConfigLoader
                .load(FMLPaths.CONFIGDIR.get())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = GameRules.registerBoolean(
                ModGameRules.RULE_NAME,
                GameRuleCategory.MISC,
                starterHouseDefault);

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
        bus.addListener((RegisterCommandsEvent event) ->
                VillageCommand.register(event.getDispatcher()));

        BeginnersDelight.LOGGER.info("Beginner's Delight (NeoForge) initialized");
    }
}
