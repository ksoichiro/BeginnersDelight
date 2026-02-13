package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Generates a starter house structure at the world spawn point.
 * Randomly selects one of the available structure variants and places it
 * on the terrain surface.
 */
public class StarterHouseGenerator {

    private static final ResourceLocation STARTER_HOUSE_LOOT =
            new ResourceLocation(BeginnersDelight.MOD_ID, "chests/starter_house");

    // starter_house6 is excluded because it uses cherry wood blocks added in 1.20
    private static final String[] STRUCTURE_VARIANTS = {
            "starter_house1",
            "starter_house2",
            "starter_house3",
            "starter_house4",
            "starter_house5"
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
        StructureManager templateManager = level.getStructureManager();
        Random random = level.getRandom();

        // Randomly select a structure variant
        String variant = STRUCTURE_VARIANTS[random.nextInt(STRUCTURE_VARIANTS.length)];
        ResourceLocation structureId = new ResourceLocation(
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

        // Pre-clear vegetation and ground-cover blocks to prevent them from dropping
        // items when their supporting blocks are removed during terrain modification.
        // Uses UPDATE_KNOWN_SHAPE to suppress shape update propagation so adjacent
        // soft blocks don't cascade-break into item entities.
        clearVegetation(level, placePos, template.getSize());

        // Use UPDATE_KNOWN_SHAPE to suppress shape updates during placement.
        // Without this, doors and other multi-block structures can break when the
        // upper half is placed before the lower half and shape updates fire on
        // the not-yet-placed neighbor.
        template.placeInWorld(level, placePos, placePos, settings, random, 2 | 16);

        // Remove any item entities that were dropped during structure placement
        removeDroppedItems(level, placePos, template.getSize());

        // Assign loot table to any chests placed by the structure template
        assignLootTables(level, placePos, template.getSize(), random);

        // Fill gaps below the structure floor to prevent floating on slopes
        fillFoundation(level, placePos, template.getSize());

        // Blend surrounding terrain so the structure doesn't look like it sits
        // in a pit (high terrain) or on a cliff (low terrain)
        blendSurroundingTerrain(level, placePos, template.getSize());

        // Remove item entities (seeds, sticks, etc.) dropped by destroyed vegetation
        removeDroppedItems(level, placePos, template.getSize());

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

        // Ensure placement is at or above sea level to prevent flooding
        resultY = Math.max(resultY, level.getSeaLevel());

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
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS)
                    || state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)) {
                continue;
            }
            return y + 1;
        }
        return -1;
    }

    /**
     * Teleports a newly joined player into the starter house if they have
     * no respawn point (i.e. have never slept in a bed).
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
    public static void onPlayerRespawn(ServerPlayer newPlayer, boolean conqueredEnd) {
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
     */
    private static void assignLootTables(ServerLevel level, BlockPos placePos,
                                          net.minecraft.core.Vec3i structureSize,
                                          Random random) {
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
     * Fills the gap between the structure floor and the terrain below,
     * using blocks that match the surrounding terrain for a natural look.
     * Also clears blocks above the floor level in the margin area so the
     * surroundings are flat at the same height as the structure floor.
     *
     * Processing order:
     * 1. Clear above floorY and convert exposed dirt to grass — this ensures
     *    the perimeter shows grass (not underground dirt) before sampling.
     * 2. Detect dominant surface block from the corrected perimeter.
     * 3. Fill foundation downward with the detected block.
     */
    private static void fillFoundation(ServerLevel level, BlockPos placePos,
                                        net.minecraft.core.Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;

        int strMinX = placePos.getX();
        int strMaxX = placePos.getX() + structureSize.getX();
        int strMinZ = placePos.getZ();
        int strMaxZ = placePos.getZ() + structureSize.getZ();

        // Phase 1: Clear blocks above the floor level and convert exposed dirt
        // to grass. This must happen before surface detection so the sampled
        // perimeter reflects the actual surface block, not underground dirt.
        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) {
                    continue;
                }

                boolean inMargin = x < strMinX || x >= strMaxX || z < strMinZ || z >= strMaxZ;

                int clearFrom = inMargin ? floorY : floorY + structureSize.getY();
                for (int y = clearFrom; y < floorY + structureSize.getY() + 10; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir()) {
                        // Preserve thin ground covers (snow, moss carpet) in margin area
                        if (inMargin && isThinGroundCover(existing)) {
                            continue;
                        }
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                if (inMargin) {
                    BlockPos surfacePos = new BlockPos(x, floorY - 1, z);
                    if (level.getBlockState(surfacePos).is(Blocks.DIRT)) {
                        level.setBlock(surfacePos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    }
                }
            }
        }

        // Phase 2: Detect dominant surface block from the now-corrected perimeter
        BlockState dominantBlock = detectDominantSurfaceBlock(level, placePos, structureSize, margin);
        BlockState surfaceBlock = mapToSurfaceBlock(dominantBlock);
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        // Phase 3: Fill foundation downward
        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) {
                    continue;
                }
                for (int y = floorY - 1; y >= floorY - 10; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && existing.getFluidState().isEmpty()) {
                        break;
                    }
                    BlockState fill = (y == floorY - 1) ? surfaceBlock : subsurfaceBlock;
                    level.setBlock(pos, fill, 2);
                }
            }
        }
    }

    /**
     * Samples blocks around the structure perimeter at ground level to
     * determine the dominant surface block type in the surrounding terrain.
     */
    private static BlockState detectDominantSurfaceBlock(ServerLevel level, BlockPos placePos,
                                                          net.minecraft.core.Vec3i structureSize,
                                                          int margin) {
        Map<net.minecraft.world.level.block.Block, Integer> counts = new HashMap<>();
        int sampleY = placePos.getY();

        int minX = placePos.getX() - margin - 1;
        int maxX = placePos.getX() + structureSize.getX() + margin;
        int minZ = placePos.getZ() - margin - 1;
        int maxZ = placePos.getZ() + structureSize.getZ() + margin;

        // Sample the perimeter just outside the fill area
        for (int x = minX; x <= maxX; x++) {
            sampleColumn(level, x, minZ, sampleY, counts);
            sampleColumn(level, x, maxZ, sampleY, counts);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            sampleColumn(level, minX, z, sampleY, counts);
            sampleColumn(level, maxX, z, sampleY, counts);
        }

        // Find the most common block
        net.minecraft.world.level.block.Block dominant = null;
        int maxCount = 0;
        for (Map.Entry<net.minecraft.world.level.block.Block, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }

        if (dominant == null) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return dominant.defaultBlockState();
    }

    /**
     * Scans downward from the given Y to find the first solid surface block
     * at the given XZ coordinate and adds it to the count map.
     */
    private static void sampleColumn(ServerLevel level, int x, int z, int startY,
                                      Map<net.minecraft.world.level.block.Block, Integer> counts) {
        for (int y = startY; y >= startY - 5; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            // Skip vegetation and thin ground cover
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS)
                    || state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)) {
                continue;
            }
            counts.merge(state.getBlock(), 1, Integer::sum);
            return;
        }
    }

    /**
     * Pre-clears vegetation and ground-cover blocks (grass, flowers, leaf litter, etc.)
     * in the area that will be modified by structure placement and terrain blending.
     * Uses UPDATE_KNOWN_SHAPE (flag 16) to suppress shape update propagation so that
     * removing one soft block does not cascade-break adjacent soft blocks into items.
     *
     * Thin ground covers (snow, moss carpet) are only cleared within the structure
     * footprint to preserve the natural terrain appearance around the structure.
     */
    private static void clearVegetation(ServerLevel level, BlockPos placePos,
                                         net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius + 1;

        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;
        int minY = placePos.getY();
        int maxY = placePos.getY() + structureSize.getY() + 10;

        // Structure footprint boundaries
        int strMinX = placePos.getX();
        int strMaxX = placePos.getX() + structureSize.getX();
        int strMinZ = placePos.getZ();
        int strMaxZ = placePos.getZ() + structureSize.getZ();

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                boolean inStructure = x >= strMinX && x < strMaxX && z >= strMinZ && z < strMaxZ;
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    // Only clear thin ground covers (snow, moss carpet) inside the structure
                    boolean shouldClear = inStructure
                            ? isVegetation(state)
                            : isVegetationExcludingThinCover(state);
                    if (shouldClear) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    // 1.17.1: No BlockTags.REPLACEABLE_BY_TREES — use explicit block/tag checks instead
    private static boolean isVegetation(BlockState state) {
        return isVegetationExcludingThinCover(state)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.MOSS_CARPET);
    }

    private static boolean isVegetationExcludingThinCover(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.GRASS);
    }

    /**
     * Returns true if the block is a thin ground cover that should be ignored
     * when determining the ground surface (snow layers, moss carpet, etc.).
     * These blocks cause structures to appear floating one block above ground.
     */
    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW)
                || state.is(Blocks.MOSS_CARPET);
    }

    /**
     * Removes item entities (seeds, sticks, saplings, etc.) that were dropped
     * when vegetation was destroyed during terrain modification.
     * Covers the structure footprint plus the foundation margin and blend radius.
     */
    private static void removeDroppedItems(ServerLevel level, BlockPos placePos,
                                            net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        // +1 accounts for items dropped by shape updates on blocks adjacent to the
        // outermost modified blocks (e.g. leaf litter losing support at the blend edge)
        int extend = margin + blendRadius + 1;
        AABB area = new AABB(
                placePos.getX() - extend, placePos.getY() - 10, placePos.getZ() - extend,
                placePos.getX() + structureSize.getX() + extend,
                placePos.getY() + structureSize.getY() + 10,
                placePos.getZ() + structureSize.getZ() + extend);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity item : items) {
            item.discard();
        }
    }

    /**
     * Smooths the terrain around the structure so the transition between the
     * flat foundation and the natural terrain is gradual rather than abrupt.
     */
    private static void blendSurroundingTerrain(ServerLevel level, BlockPos placePos,
                                                  net.minecraft.core.Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;
        int blendRadius = 3;

        BlockState dominantBlock = detectDominantSurfaceBlock(level, placePos, structureSize, margin);
        BlockState surfaceBlock = mapToSurfaceBlock(dominantBlock);
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        int innerMinX = placePos.getX() - margin;
        int innerMaxX = placePos.getX() + structureSize.getX() + margin - 1;
        int innerMinZ = placePos.getZ() - margin;
        int innerMaxZ = placePos.getZ() + structureSize.getZ() + margin - 1;

        int outerMinX = innerMinX - blendRadius;
        int outerMaxX = innerMaxX + blendRadius;
        int outerMinZ = innerMinZ - blendRadius;
        int outerMaxZ = innerMaxZ + blendRadius;

        for (int x = outerMinX; x <= outerMaxX; x++) {
            for (int z = outerMinZ; z <= outerMaxZ; z++) {
                if (x >= innerMinX && x <= innerMaxX && z >= innerMinZ && z <= innerMaxZ) {
                    continue;
                }

                int distX = 0;
                if (x < innerMinX) distX = innerMinX - x;
                else if (x > innerMaxX) distX = x - innerMaxX;

                int distZ = 0;
                if (z < innerMinZ) distZ = innerMinZ - z;
                else if (z > innerMaxZ) distZ = z - innerMaxZ;

                int dist = Math.max(distX, distZ);
                if (dist <= 0 || dist > blendRadius) continue;

                int naturalY = findGroundY(level, x, z);
                if (naturalY == -1) continue;

                double ratio = (double) dist / blendRadius;
                int targetY = floorY + (int) Math.round((naturalY - floorY) * ratio);

                if (naturalY > targetY) {
                    for (int y = targetY; y < naturalY; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    if (targetY > level.getMinBuildHeight()) {
                        level.setBlock(new BlockPos(x, targetY - 1, z), surfaceBlock, 2);
                    }
                } else if (naturalY < targetY) {
                    for (int y = naturalY; y < targetY; y++) {
                        BlockState fill = (y == targetY - 1) ? surfaceBlock : subsurfaceBlock;
                        level.setBlock(new BlockPos(x, y, z), fill, 2);
                    }
                }
            }
        }
    }

    /**
     * Returns true if the position is outside the chamfered rectangle,
     * i.e. at the outermost corner blocks that should be skipped.
     */
    private static boolean isOutsideChamfer(int x, int z,
                                             int strMinX, int strMaxX,
                                             int strMinZ, int strMaxZ,
                                             int margin) {
        int distX = 0;
        if (x < strMinX) distX = strMinX - x;
        else if (x >= strMaxX) distX = x - strMaxX + 1;
        int distZ = 0;
        if (z < strMinZ) distZ = strMinZ - z;
        else if (z >= strMaxZ) distZ = z - strMaxZ + 1;
        return distX + distZ > 2 * margin - 1;
    }

    /**
     * Maps a detected terrain block to the appropriate surface fill block.
     * Gravity-affected blocks are replaced with their solid equivalents.
     */
    private static BlockState mapToSurfaceBlock(BlockState detected) {
        net.minecraft.world.level.block.Block block = detected.getBlock();
        if (block == Blocks.SAND) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        if (block == Blocks.RED_SAND) {
            return Blocks.RED_SANDSTONE.defaultBlockState();
        }
        if (block == Blocks.GRAVEL) {
            return Blocks.STONE.defaultBlockState();
        }
        // Grass, stone, dirt, etc. — use as-is
        return detected;
    }

    /**
     * Maps a surface fill block to the appropriate subsurface fill block.
     * Grass blocks become dirt below the surface; others remain the same.
     */
    private static BlockState mapToSubsurfaceBlock(BlockState surfaceBlock) {
        if (surfaceBlock.is(Blocks.GRASS_BLOCK)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return surfaceBlock;
    }
}
