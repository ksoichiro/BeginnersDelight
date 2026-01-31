package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.Optional;

/**
 * Generates a starter house structure at the world spawn point.
 * Randomly selects one of the available structure variants and places it
 * on the terrain surface.
 */
public class StarterHouseGenerator {

    private static final ResourceKey<LootTable> STARTER_HOUSE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/starter_house"));

    private static final String[] STRUCTURE_VARIANTS = {
            "starter_house1",
            "starter_house2",
            "starter_house3",
            "starter_house4",
            "starter_house5",
            "starter_house6"
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
            // Restore world spawn from SavedData on every server start
            BlockPos savedSpawn = data.getSpawnPos();
            if (savedSpawn != null) {
                overworld.setDefaultSpawnPos(savedSpawn, 0.0f);
                BeginnersDelight.LOGGER.debug("Restored world spawn to: {}", savedSpawn);
            }
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

        // Assign loot table to any chests placed by the structure template
        assignLootTables(level, placePos, template.getSize(), random);

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
     * Samples surface Y across the footprint (skipping vegetation) and uses
     * the minimum so the structure sits flush with the lowest terrain point.
     * This prevents the structure from being placed on cliff edges with a
     * large dirt foundation below.
     */
    private static BlockPos findSurfacePosition(ServerLevel level, BlockPos center,
                                                 net.minecraft.core.Vec3i structureSize) {
        int halfX = structureSize.getX() / 2;
        int halfZ = structureSize.getZ() / 2;

        int startX = center.getX() - halfX;
        int startZ = center.getZ() - halfZ;

        // Sample surface Y at the four corners and center of the footprint
        int endX = startX + structureSize.getX() - 1;
        int endZ = startZ + structureSize.getZ() - 1;
        int centerX = center.getX();
        int centerZ = center.getZ();

        int[][] samplePoints = {
                {centerX, centerZ},
                {startX, startZ},
                {endX, startZ},
                {startX, endZ},
                {endX, endZ}
        };

        int resultY = Integer.MAX_VALUE;
        for (int[] point : samplePoints) {
            int y = findGroundY(level, point[0], point[1]);
            if (y == -1) {
                return null;
            }
            if (y < resultY) {
                resultY = y;
            }
        }

        if (resultY == Integer.MAX_VALUE) {
            return null;
        }

        return new BlockPos(startX, resultY, startZ);
    }

    /**
     * Scans downward from the max build height at the given XZ coordinate
     * to find the Y of the ground surface, skipping vegetation and fluids.
     *
     * @return the Y coordinate to place on (top of the ground block), or -1 if not found
     */
    private static int findGroundY(ServerLevel level, int x, int z) {
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight();

        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
                continue;
            }
            return y + 1;
        }
        return -1;
    }

    /**
     * Teleports a newly joined player into the starter house if they have
     * no respawn point (i.e. have never slept in a bed).
     * This bypasses Minecraft's safe-spawn search that places players on the roof.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        StarterHouseData data = StarterHouseData.get(overworld);
        BlockPos spawnPos = data.getSpawnPos();

        if (!data.isGenerated() || spawnPos == null) {
            return;
        }

        // Only teleport players who have never been teleported to the starter house
        if (data.hasBeenTeleported(player.getUUID())) {
            return;
        }

        data.markTeleported(player.getUUID());
        player.teleportTo(overworld,
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        BeginnersDelight.LOGGER.debug("Teleported player {} to starter house", player.getName().getString());
    }

    /**
     * Teleports a player back into the starter house when they respawn after
     * death without a bed respawn point set.
     */
    public static void onPlayerRespawn(ServerPlayer newPlayer, boolean conqueredEnd,
                                        Entity.RemovalReason removalReason) {
        // Only handle death respawns, not end portal returns
        if (conqueredEnd) {
            return;
        }

        // If the player has a bed/anchor respawn point, let Minecraft handle it
        if (newPlayer.getRespawnPosition() != null) {
            return;
        }

        ServerLevel overworld = newPlayer.server.overworld();
        StarterHouseData data = StarterHouseData.get(overworld);
        BlockPos spawnPos = data.getSpawnPos();

        if (!data.isGenerated() || spawnPos == null) {
            return;
        }

        newPlayer.teleportTo(overworld,
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                newPlayer.getYRot(), newPlayer.getXRot());
        BeginnersDelight.LOGGER.debug("Respawned player {} at starter house", newPlayer.getName().getString());
    }

    /**
     * Scans the placed structure for container block entities (chests, barrels, etc.)
     * and assigns the starter house loot table to them.
     * This allows NBT structure files to contain plain chests without
     * pre-configured LootTable tags.
     */
    private static void assignLootTables(ServerLevel level, BlockPos placePos,
                                          net.minecraft.core.Vec3i structureSize,
                                          RandomSource random) {
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                        container.setLootTable(STARTER_HOUSE_LOOT, random.nextLong());
                        BeginnersDelight.LOGGER.debug("Assigned loot table to container at {}", pos);
                    }
                }
            }
        }
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
