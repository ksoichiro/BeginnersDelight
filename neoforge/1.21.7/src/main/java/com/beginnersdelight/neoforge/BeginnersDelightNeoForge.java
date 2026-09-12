package com.beginnersdelight.neoforge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.neoforge.client.BeginnersDelightNeoForgeClient;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Forked from {@code neoforge/base} (used by every other NeoForge version) because this
 * version needs a client-side hook (the village config screen) that only exists in
 * common/26.2 and common/1.21.11 so far. Reunify with neoforge/base once the screen is
 * ported to the other NeoForge-supported versions.
 */
@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightNeoForge {
    public BeginnersDelightNeoForge(IEventBus modEventBus, ModContainer container) {
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

        // MC 1.21.7's FancyModLoader (9.0.2) exposes the distribution as the public
        // static final field FMLEnvironment.dist, not a getDist() accessor (added later).
        if (FMLEnvironment.dist == Dist.CLIENT) {
            BeginnersDelightNeoForgeClient.init(modEventBus, container);
        }

        BeginnersDelight.LOGGER.info("Beginner's Delight (NeoForge) initialized");
    }
}
