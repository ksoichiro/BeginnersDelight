package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

/**
 * Generates a starter house structure at the world spawn point.
 * Randomly selects one of the available structure variants and places it
 * on the terrain surface.
 */
public class StarterHouseGenerator {

    private static final String[] STRUCTURE_VARIANTS = {
            "starter_house1",
            "starter_house2",
            "starter_house3"
    };

    /**
     * Attempts to generate the starter house at the world spawn point.
     * Does nothing if the house has already been generated.
     */
    public static void tryGenerate(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        StarterHouseData data = StarterHouseData.get(overworld);

        if (data.isGenerated()) {
            BeginnersDelight.LOGGER.debug("Starter house already generated, skipping");
            return;
        }

        BlockPos spawnPos = overworld.getSharedSpawnPos();
        BeginnersDelight.LOGGER.info("Generating starter house near spawn point: {}", spawnPos);

        if (placeStructure(overworld, spawnPos, data)) {
            data.setGenerated(true);
            BeginnersDelight.LOGGER.info("Starter house generated successfully");
        } else {
            BeginnersDelight.LOGGER.warn("Failed to generate starter house");
        }
    }

    private static boolean placeStructure(ServerLevel level, BlockPos spawnPos, StarterHouseData data) {
        StructureTemplateManager templateManager = level.getStructureManager();
        RandomSource random = level.getRandom();

        // Randomly select a structure variant
        String variant = STRUCTURE_VARIANTS[random.nextInt(STRUCTURE_VARIANTS.length)];
        ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath(
                BeginnersDelight.MOD_ID, variant);

        Optional<StructureTemplate> templateOpt = templateManager.get(structureId);
        if (templateOpt.isEmpty()) {
            BeginnersDelight.LOGGER.error("Structure template not found: {}", structureId);
            return false;
        }

        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);

        // Find appropriate placement position on the surface
        BlockPos placePos = findSurfacePosition(level, spawnPos, template.getSize());
        if (placePos == null) {
            BeginnersDelight.LOGGER.warn("Could not find suitable surface position for starter house");
            return false;
        }

        BeginnersDelight.LOGGER.info("Placing structure '{}' at {}", variant, placePos);
        template.placeInWorld(level, placePos, placePos, settings, random, 2);

        // Fill gaps below the structure floor to prevent floating on slopes
        fillFoundation(level, placePos, template.getSize());

        // Calculate the interior spawn position (center, one block above floor)
        net.minecraft.core.Vec3i size = template.getSize();
        BlockPos insidePos = placePos.offset(size.getX() / 2, 1, size.getZ() / 2);

        // Store spawn position in SavedData for player join teleport
        data.setSpawnPos(insidePos);

        // Set world spawn and radius (used as fallback for death respawn without bed)
        level.setDefaultSpawnPos(insidePos, 0.0f);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_RADIUS)
                .set(0, level.getServer());
        BeginnersDelight.LOGGER.info("World spawn set to: {}", insidePos);

        return true;
    }

    /**
     * Finds a suitable surface position for placing the structure.
     * Uses the minimum surface height across the footprint so the structure
     * sits flush with the lowest terrain point. Gaps on higher terrain
     * are filled by {@link #fillFoundation}.
     */
    private static BlockPos findSurfacePosition(ServerLevel level, BlockPos center,
                                                 net.minecraft.core.Vec3i structureSize) {
        int halfX = structureSize.getX() / 2;
        int halfZ = structureSize.getZ() / 2;

        int startX = center.getX() - halfX;
        int startZ = center.getZ() - halfZ;

        // Use the minimum surface Y so the structure never floats
        int minY = Integer.MAX_VALUE;
        for (int x = startX; x < startX + structureSize.getX(); x++) {
            for (int z = startZ; z < startZ + structureSize.getZ(); z++) {
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (surfaceY < minY) {
                    minY = surfaceY;
                }
            }
        }

        if (minY == Integer.MAX_VALUE) {
            return null;
        }

        return new BlockPos(startX, minY, startZ);
    }

    /**
     * Teleports a newly joined player into the starter house if they have
     * no respawn point (i.e. have never slept in a bed).
     * This bypasses Minecraft's safe-spawn search that places players on the roof.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        if (player.getRespawnPosition() != null) {
            return;
        }

        ServerLevel overworld = player.server.overworld();
        StarterHouseData data = StarterHouseData.get(overworld);
        BlockPos spawnPos = data.getSpawnPos();

        if (!data.isGenerated() || spawnPos == null) {
            return;
        }

        player.teleportTo(overworld,
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        BeginnersDelight.LOGGER.debug("Teleported player {} to starter house", player.getName().getString());
    }

    /**
     * Fills the gap between the structure floor and the terrain below with
     * dirt blocks, creating a natural-looking foundation on slopes.
     */
    private static void fillFoundation(ServerLevel level, BlockPos placePos,
                                        net.minecraft.core.Vec3i structureSize) {
        int floorY = placePos.getY();
        BlockState dirt = Blocks.DIRT.defaultBlockState();

        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                // Fill downward from just below the floor until we hit existing terrain
                for (int y = floorY - 1; y >= floorY - 10; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        break;
                    }
                    level.setBlock(pos, dirt, 2);
                }
            }
        }
    }
}
