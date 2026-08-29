package com.beginnersdelight.forge;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.village.VillageCommand;
import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.village.VillageManager;
import com.beginnersdelight.worldgen.ModGameRules;
import com.beginnersdelight.worldgen.StarterHouseGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightForge {
    public BeginnersDelightForge() {
        BeginnersDelight.init();

        // The rule table is a plain static map on this version, so registering straight from the
        // mod constructor is enough; it must happen exactly once per JVM, before any world builds
        // its GameRules. BooleanValue.create is opened up by META-INF/accesstransformer.cfg.
        boolean starterHouseDefault = VillageConfigLoader
                .load(FMLPaths.CONFIGDIR.get())
                .isAutoGenerateStarterHouse();
        ModGameRules.GENERATE_STARTER_HOUSE = GameRules.register(
                ModGameRules.RULE_NAME,
                GameRules.Category.MISC,
                GameRules.BooleanValue.create(starterHouseDefault));

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
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                VillageManager.onServerTick(server);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                VillageCommand.register(event.getDispatcher()));

        BeginnersDelight.LOGGER.info("Beginner's Delight (Forge) initialized");
    }
}
