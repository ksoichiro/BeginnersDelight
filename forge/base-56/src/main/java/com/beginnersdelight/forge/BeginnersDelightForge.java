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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

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

        if (FMLEnvironment.dist == Dist.CLIENT) {
            BeginnersDelightForgeClient.init();
        }
    }

    @Mod.EventBusSubscriber(modid = BeginnersDelight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            VillageManager.onServerStarted(event.getServer());
            StarterHouseGenerator.tryGenerate(event.getServer());
        }

        @SubscribeEvent
        public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                VillageManager.onPlayerJoin(serverPlayer);
                StarterHouseGenerator.onPlayerJoin(serverPlayer);
            }
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                VillageManager.onPlayerRespawn(serverPlayer);
                StarterHouseGenerator.onPlayerRespawn(serverPlayer, event.isEndConquered());
            }
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
            VillageManager.onServerTick(ServerLifecycleHooks.getCurrentServer());
        }

        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            VillageCommand.register(event.getDispatcher());
        }
    }

    @Mod.EventBusSubscriber(modid = BeginnersDelight.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(BeginnersDelightForgeClient.openConfigKey());
        }
    }

    @Mod.EventBusSubscriber(modid = BeginnersDelight.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
            BeginnersDelightForgeClient.handleTick();
        }
    }
}
