package com.beginnersdelight.village;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers /beginnersdelight village commands.
 */
public class VillageCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("beginnersdelight")
                        .then(Commands.literal("village")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("enable")
                                        .executes(ctx -> enable(ctx.getSource())))
                                .then(Commands.literal("disable")
                                        .executes(ctx -> disable(ctx.getSource())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> status(ctx.getSource())))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.word())
                                                        .executes(ctx -> set(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "key"),
                                                                StringArgumentType.getString(ctx, "value"))))))
                                .then(Commands.literal("test")
                                        .executes(ctx -> test(ctx.getSource()))))
        );
    }

    private static int enable(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        VillageData data = VillageData.get(overworld);
        data.setEnabled(true);

        if (data.getCenterPos() == null) {
            VillageGrid grid = new VillageGrid(data, VillageManager.getConfig());
            grid.initialize(overworld.getSharedSpawnPos());
        }

        source.sendSuccess(new TextComponent("Village mode enabled"), true);
        return 1;
    }

    private static int disable(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        VillageData data = VillageData.get(overworld);
        data.setEnabled(false);
        source.sendSuccess(new TextComponent("Village mode disabled (data preserved)"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        VillageData data = VillageData.get(overworld);
        VillageConfig config = VillageManager.getConfig();

        String centerInfo = data.getCenterPos() != null
                ? String.format("(%d, %d, %d)", data.getCenterPos().getX(), data.getCenterPos().getY(), data.getCenterPos().getZ())
                : "not set";
        String statusText = String.format(
                "Village Mode: %s\nCenter: %s\nHouses: %d\nPlayers: %d\nPlot size: %d\nMax height diff: %d\nPaths: %s\nRespawn at house: %s",
                data.isEnabled() ? "enabled" : "disabled",
                centerInfo,
                data.getHouseCount(),
                data.getPlayerCount(),
                config.getPlotSize(),
                config.getMaxHeightDifference(),
                config.isGeneratePaths() ? "on" : "off",
                config.isRespawnAtHouse() ? "on" : "off"
        );
        source.sendSuccess(new TextComponent(statusText), false);
        return 1;
    }

    private static int test(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(new TextComponent("This command must be run by a player"));
            return 0;
        }

        ServerLevel overworld = source.getServer().overworld();
        VillageData data = VillageData.get(overworld);
        if (!data.isEnabled()) {
            source.sendFailure(new TextComponent("Village mode is not enabled"));
            return 0;
        }

        int beforeCount = data.getHouseCount();
        VillageManager.forceAssignHouse(player);
        int afterCount = data.getHouseCount();

        if (afterCount > beforeCount) {
            source.sendSuccess(new TextComponent("Test house placed (total: " + afterCount + ")"), true);
        } else {
            source.sendFailure(new TextComponent("Failed to place test house"));
        }
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String value) {
        VillageConfig config = VillageManager.getConfig();
        try {
            if (config.set(key, value)) {
                source.sendSuccess(new TextComponent("Set " + key + " = " + value), true);
                return 1;
            } else {
                source.sendFailure(new TextComponent("Unknown config key: " + key
                        + ". Valid keys: plotSize, maxHeightDifference, generatePaths, respawnAtHouse"));
                return 0;
            }
        } catch (NumberFormatException e) {
            source.sendFailure(new TextComponent("Invalid value for " + key + ": " + value));
            return 0;
        }
    }
}
