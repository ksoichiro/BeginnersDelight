package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Places village house structures with terrain handling.
 * Adapted from StarterHouseGenerator's placement logic.
 */
public class VillageHouseGenerator {

    private static final ResourceLocation STARTER_HOUSE_LOOT =
            new ResourceLocation(BeginnersDelight.MOD_ID, "chests/starter_house");
    private static final ResourceLocation VILLAGE_STOREHOUSE_LOOT =
            new ResourceLocation(BeginnersDelight.MOD_ID, "chests/village_storehouse");
    private static final ResourceLocation VILLAGE_FARM_LOOT =
            new ResourceLocation(BeginnersDelight.MOD_ID, "chests/village_farm");
    private static final Map<String, ResourceLocation> DECORATION_LOOT_TABLES = Map.of(
            "village_storehouse", VILLAGE_STOREHOUSE_LOOT, "village_farm", VILLAGE_FARM_LOOT);
    private static final String[] DECORATION_VARIANTS = {"village_shed", "village_storehouse", "village_farm"};

    // starter_house6 is excluded because it uses cherry wood blocks added in 1.20
    private static final String[] STRUCTURE_VARIANTS = {
            "starter_house1", "starter_house2", "starter_house3",
            "starter_house4", "starter_house5"
    };

    public record PlacementResult(BlockPos interiorPos, BlockPos doorFrontPos) {}

    public static boolean isSuitable(ServerLevel level, BlockPos plotCenter, int maxHeightDiff) {
        int centerX = plotCenter.getX();
        int centerZ = plotCenter.getZ();
        int seaLevel = level.getSeaLevel();
        int centerY = findGroundY(level, centerX, centerZ);
        if (centerY == -1) return false;

        if (centerY <= seaLevel) {
            for (int y = centerY; y <= seaLevel; y++) {
                BlockState state = level.getBlockState(new BlockPos(centerX, y, centerZ));
                if (!state.getFluidState().isEmpty()) return false;
            }
        }

        // Check if center is on ice
        BlockState groundState = level.getBlockState(new BlockPos(centerX, centerY - 1, centerZ));
        if (groundState.is(Blocks.ICE) || groundState.is(Blocks.PACKED_ICE)
                || groundState.is(Blocks.BLUE_ICE) || groundState.is(Blocks.FROSTED_ICE)) {
            return false;
        }

        // Reject locations sitting over a large void (e.g. dripstone caves or other
        // caverns), where the detected ground is only a thin ceiling/spike above empty
        // space. Terrain blending cannot produce sane results there.
        if (hasVoidBelow(level, centerX, centerZ, centerY)) return false;

        int halfSize = 7;
        int[][] corners = {
                {centerX - halfSize, centerZ - halfSize},
                {centerX + halfSize, centerZ - halfSize},
                {centerX - halfSize, centerZ + halfSize},
                {centerX + halfSize, centerZ + halfSize}
        };
        int minY = centerY, maxY = centerY;
        for (int[] corner : corners) {
            int y = findGroundY(level, corner[0], corner[1]);
            if (y == -1) return false;
            if (hasVoidBelow(level, corner[0], corner[1], y)) return false;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        return (maxY - minY) <= maxHeightDiff;
    }

    /**
     * Detects whether there is a substantial void (cave/cavern) directly below the
     * detected ground surface. Scans a fixed window beneath the surface block and
     * returns true when most of it is empty, indicating the "ground" is only a thin
     * ceiling or dripstone spike over a cave rather than solid terrain.
     */
    private static boolean hasVoidBelow(ServerLevel level, int x, int z, int groundY) {
        int depth = 12;
        int airCount = 0;
        for (int y = groundY - 2; y >= groundY - 1 - depth; y--) {
            if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                airCount++;
            }
        }
        return airCount >= 6;
    }

    /**
     * Returns true if the column at the given XZ is submerged: any fluid exists
     * between the ground and sea level. Used to decide whether placement should be
     * raised to sea level (to avoid building underwater) versus kept on dry ground
     * that merely sits below sea level (deep valleys/caves), which must not float up.
     */
    private static boolean isSubmerged(ServerLevel level, int x, int z, int groundY, int seaLevel) {
        for (int y = groundY; y <= seaLevel; y++) {
            if (!level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static Optional<PlacementResult> place(ServerLevel level, BlockPos plotCenter) {
        StructureManager templateManager = level.getStructureManager();
        Random random = level.getRandom();

        String variant = STRUCTURE_VARIANTS[random.nextInt(STRUCTURE_VARIANTS.length)];
        ResourceLocation structureId = new ResourceLocation(BeginnersDelight.MOD_ID, variant);

        Optional<StructureTemplate> templateOpt = templateManager.get(structureId);
        if (templateOpt.isEmpty()) {
            BeginnersDelight.LOGGER.error("Structure template not found: {}", structureId);
            return Optional.empty();
        }

        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);

        BlockPos placePos = findSurfacePosition(level, plotCenter, template.getSize());
        if (placePos == null) {
            BeginnersDelight.LOGGER.warn("Could not find suitable surface position for village house");
            return Optional.empty();
        }

        BeginnersDelight.LOGGER.info("Placing village house '{}' at {}", variant, placePos);

        Vec3i size = template.getSize();
        removeMobs(level, placePos, size);
        clearVegetation(level, placePos, size);
        template.placeInWorld(level, placePos, placePos, settings, random, 2 | 16);
        removeDroppedItems(level, placePos, size);
        assignLootTables(level, placePos, size, random);
        // Blend surrounding terrain first so the terrain around the foundation
        // is flat before filling. This prevents corner pillars from being too high.
        blendSurroundingTerrain(level, placePos, size);

        // Fill gaps below the structure floor to prevent floating on slopes
        fillFoundation(level, placePos, size);

        // Blend corner pillars that were skipped by isOutsideChamfer in fillFoundation
        blendCornerPillars(level, placePos, size);

        // Replace surface dirt next to grass with grass for a natural blend.
        naturalizeDirtSurface(level, placePos, size);
        removeDroppedItems(level, placePos, size);

        BlockPos interiorPos = placePos.offset(size.getX() / 2, 1, size.getZ() / 2);
        BlockPos doorFrontPos = new BlockPos(
                placePos.getX() + size.getX() / 2,
                placePos.getY(),
                placePos.getZ() + size.getZ());

        return Optional.of(new PlacementResult(interiorPos, doorFrontPos));
    }

    public static Optional<PlacementResult> placeDecoration(ServerLevel level, BlockPos plotCenter, String structureName) {
        StructureManager templateManager = level.getStructureManager();
        Random random = level.getRandom();
        ResourceLocation structureId = new ResourceLocation(BeginnersDelight.MOD_ID, structureName);
        Optional<StructureTemplate> templateOpt = templateManager.get(structureId);
        if (templateOpt.isEmpty()) { BeginnersDelight.LOGGER.error("Structure template not found: {}", structureId); return Optional.empty(); }
        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE).setIgnoreEntities(false);
        BlockPos placePos = findSurfacePosition(level, plotCenter, template.getSize());
        if (placePos == null) { BeginnersDelight.LOGGER.warn("Could not find suitable surface position for {}", structureName); return Optional.empty(); }
        BlockPos surfacePos = placePos; placePos = placePos.below();
        BeginnersDelight.LOGGER.info("Placing decoration '{}' at {}", structureName, placePos);
        Vec3i size = template.getSize();
        removeMobs(level, placePos, size); clearVegetation(level, placePos, size);
        template.placeInWorld(level, placePos, placePos, settings, random, 2 | 16);
        removeDroppedItems(level, placePos, size);
        ResourceLocation lootTable = DECORATION_LOOT_TABLES.get(structureName);
        if (lootTable != null) { assignLootTablesWithKey(level, placePos, size, random, lootTable); }
        // Blend surrounding terrain first so the terrain around the foundation
        // is flat before filling. This prevents corner pillars from being too high.
        blendSurroundingTerrain(level, surfacePos, size);

        // Fill gaps below the structure floor to prevent floating on slopes
        fillFoundation(level, surfacePos, size);

        // Blend corner pillars that were skipped by isOutsideChamfer in fillFoundation
        blendCornerPillars(level, surfacePos, size);

        // Replace surface dirt next to grass with grass for a natural blend.
        naturalizeDirtSurface(level, surfacePos, size);
        removeDroppedItems(level, surfacePos, size);
        BlockPos interiorPos = surfacePos.offset(size.getX() / 2, 1, size.getZ() / 2);
        BlockPos doorFrontPos = new BlockPos(surfacePos.getX() + size.getX() / 2, surfacePos.getY(), surfacePos.getZ() + size.getZ());
        return Optional.of(new PlacementResult(interiorPos, doorFrontPos));
    }
    public static String selectRandomDecoration(Random random) { return DECORATION_VARIANTS[random.nextInt(DECORATION_VARIANTS.length)]; }

    private static BlockPos findSurfacePosition(ServerLevel level, BlockPos center, Vec3i structureSize) {
        int halfX = structureSize.getX() / 2;
        int halfZ = structureSize.getZ() / 2;
        int startX = center.getX() - halfX;
        int startZ = center.getZ() - halfZ;
        int endX = startX + structureSize.getX() - 1;
        int endZ = startZ + structureSize.getZ() - 1;

        int[][] samplePoints = {
                {center.getX(), center.getZ()},
                {startX, startZ}, {endX, startZ},
                {startX, endZ}, {endX, endZ}
        };

        int resultY = Integer.MAX_VALUE;
        for (int[] point : samplePoints) {
            int y = findGroundY(level, point[0], point[1]);
            if (y == -1) return null;
            if (y < resultY) resultY = y;
        }
        if (resultY == Integer.MAX_VALUE) return null;
        // Only raise to sea level when the ground is actually submerged (ocean/lake),
        // to avoid building underwater. Dry ground below sea level (deep valleys, cave
        // areas) keeps its real height so the house sits on the ground, not floating.
        int seaLevel = level.getSeaLevel();
        if (resultY < seaLevel && isSubmerged(level, center.getX(), center.getZ(), resultY, seaLevel)) {
            resultY = seaLevel;
        }
        return new BlockPos(startX, resultY, startZ);
    }

    private static int findGroundY(ServerLevel level, int x, int z) {
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS)
                    || isThinGroundCover(state)) {
                continue;
            }
            return y + 1;
        }
        return -1;
    }

    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET);
    }

    // 1.17.1: No BlockTags.REPLACEABLE_BY_TREES — use explicit block/tag checks instead
    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS)
                || isThinGroundCover(state);
    }

    private static void clearVegetation(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int extend = 6;
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;
        int minY = placePos.getY();
        int maxY = placePos.getY() + structureSize.getY() + 10;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && isVegetation(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    private static void removeMobs(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int extend = 6;
        AABB area = new AABB(
                placePos.getX() - extend, placePos.getY() - 10, placePos.getZ() - extend,
                placePos.getX() + structureSize.getX() + extend,
                placePos.getY() + structureSize.getY() + 10,
                placePos.getZ() + structureSize.getZ() + extend);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            mob.discard();
        }
    }

    private static void removeDroppedItems(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int extend = 6;
        AABB area = new AABB(
                placePos.getX() - extend, placePos.getY() - 10, placePos.getZ() - extend,
                placePos.getX() + structureSize.getX() + extend,
                placePos.getY() + structureSize.getY() + 10,
                placePos.getZ() + structureSize.getZ() + extend);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            item.discard();
        }
    }

    private static void assignLootTables(ServerLevel level, BlockPos placePos, Vec3i structureSize,
                                          Random random) {
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                    if (be instanceof RandomizableContainerBlockEntity container) {
                        container.setLootTable(STARTER_HOUSE_LOOT, random.nextLong());
                    }
                }
            }
        }
    }
    private static void assignLootTablesWithKey(ServerLevel level, BlockPos placePos, Vec3i structureSize,
                                                 Random random, ResourceLocation lootKey) {
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++)
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++)
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                    if (be instanceof RandomizableContainerBlockEntity container) container.setLootTable(lootKey, random.nextLong());
                }
    }

    private static void fillFoundation(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;
        int strMinX = placePos.getX();
        int strMaxX = placePos.getX() + structureSize.getX();
        int strMinZ = placePos.getZ();
        int strMaxZ = placePos.getZ() + structureSize.getZ();

        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) continue;
                boolean inMargin = x < strMinX || x >= strMaxX || z < strMinZ || z >= strMaxZ;
                int clearFrom = inMargin ? floorY : floorY + structureSize.getY();
                for (int y = clearFrom; y < floorY + structureSize.getY() + 10; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir()) {
                        if (inMargin && isThinGroundCover(existing)) continue;
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

        BlockState surfaceBlock = mapToSurfaceBlock(detectDominantSurfaceBlock(level, placePos, structureSize, margin));
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) continue;
                // Skip columns over a void (no solid ground within reach): filling here would
                // leave floating dirt above a cave.
                boolean solidWithinReach = false;
                for (int sy = floorY - 1; sy >= floorY - 10; sy--) {
                    BlockState below = level.getBlockState(new BlockPos(x, sy, z));
                    if (!below.isAir() && below.getFluidState().isEmpty()) { solidWithinReach = true; break; }
                }
                if (!solidWithinReach) continue;
                for (int y = floorY - 1; y >= floorY - 10; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && existing.getFluidState().isEmpty()) break;
                    level.setBlock(pos, (y == floorY - 1) ? surfaceBlock : subsurfaceBlock, 2);
                }
            }
        }
    }

    private static void blendSurroundingTerrain(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;
        int blendRadius = 3;
        BlockState surfaceBlock = mapToSurfaceBlock(detectDominantSurfaceBlock(level, placePos, structureSize, margin));
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        int innerMinX = placePos.getX() - margin;
        int innerMaxX = placePos.getX() + structureSize.getX() + margin - 1;
        int innerMinZ = placePos.getZ() - margin;
        int innerMaxZ = placePos.getZ() + structureSize.getZ() + margin - 1;

        for (int x = innerMinX - blendRadius; x <= innerMaxX + blendRadius; x++) {
            for (int z = innerMinZ - blendRadius; z <= innerMaxZ + blendRadius; z++) {
                if (x >= innerMinX && x <= innerMaxX && z >= innerMinZ && z <= innerMaxZ) continue;
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
                        // Don't leave a lone surface block floating over a void: only cap
                        // when there is solid support directly beneath it.
                        BlockState capSupport = level.getBlockState(new BlockPos(x, targetY - 2, z));
                        if (!capSupport.isAir() && capSupport.getFluidState().isEmpty()) {
                            level.setBlock(new BlockPos(x, targetY - 1, z), surfaceBlock, 2);
                        }
                    }
                } else if (naturalY < targetY) {
                    // Don't bridge cliffs/voids (e.g. cave edges): a large drop means the natural
                    // ground plunges away, so filling would build an unnatural dirt pillar into the
                    // void. Leave the natural cliff intact.
                    if (floorY - naturalY > 6) continue;
                    for (int y = naturalY; y < targetY; y++) {
                        level.setBlock(new BlockPos(x, y, z), (y == targetY - 1) ? surfaceBlock : subsurfaceBlock, 2);
                    }
                }
            }
        }
    }

    /**
     * Replaces dirt blocks on the leveled/blended top surface with grass so the
     * result blends with naturally generated grass (which only ever shows grass on
     * top). Runs as a post-process after all surface placement.
     *
     * Takes a snapshot of the surface, then flood-fills grass through 8-connected
     * dirt tops: every dirt top reachable from a grass top -- directly or through
     * other dirt tops -- becomes grass, so a whole dirt patch that touches grass is
     * naturalized, not just its outer ring. Dirt with no grass anywhere in the
     * scanned region (e.g. a dirt/sand biome) is left untouched.
     */
    private static void naturalizeDirtSurface(ServerLevel level, BlockPos placePos,
                                               Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius;

        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        boolean[][] grass = new boolean[width][depth];
        // Integer.MIN_VALUE marks "no dirt top here"; otherwise the Y of the dirt surface block.
        int[][] dirtTopY = new int[width][depth];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                dirtTopY[i][j] = Integer.MIN_VALUE;
            }
        }

        // Snapshot pass: record grass tops and dirt tops from the current surface.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY == -1) {
                    continue;
                }
                int topY = groundY - 1;
                BlockState top = level.getBlockState(new BlockPos(x, topY, z));
                int i = x - minX;
                int j = z - minZ;
                if (top.is(Blocks.GRASS_BLOCK)) {
                    grass[i][j] = true;
                } else if (top.is(Blocks.DIRT)) {
                    dirtTopY[i][j] = topY;
                }
            }
        }

        // Convert pass: flood-fill grass through 8-connected dirt tops. Seed the
        // queue with every grass top, then spread into adjacent dirt tops so an
        // entire dirt patch that touches grass is naturalized, not just its outer
        // ring. Dirt not connected to any grass in the region is left untouched.
        boolean[][] convert = new boolean[width][depth];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                if (grass[i][j]) {
                    queue.add(new int[]{i, j});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int ci = cell[0];
            int cj = cell[1];
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    if (di == 0 && dj == 0) {
                        continue;
                    }
                    int ni = ci + di;
                    int nj = cj + dj;
                    if (ni < 0 || ni >= width || nj < 0 || nj >= depth) {
                        continue;
                    }
                    if (dirtTopY[ni][nj] != Integer.MIN_VALUE && !convert[ni][nj]) {
                        convert[ni][nj] = true;
                        queue.add(new int[]{ni, nj});
                    }
                }
            }
        }
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                if (convert[i][j]) {
                    level.setBlock(new BlockPos(minX + i, dirtTopY[i][j], minZ + j),
                            Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * Blends corner pillars that were skipped by isOutsideChamfer in fillFoundation.
     * After fillFoundation, the corners may be tall pillars. This method carves them
     * down to match the surrounding terrain height.
     */
    private static void blendCornerPillars(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;

        int strMinX = placePos.getX();
        int strMaxX = placePos.getX() + structureSize.getX();
        int strMinZ = placePos.getZ();
        int strMaxZ = placePos.getZ() + structureSize.getZ();

        BlockState dominantBlock = detectDominantSurfaceBlock(level, placePos, structureSize, margin);
        BlockState surfaceBlock = mapToSurfaceBlock(dominantBlock);

        // Only process corner points that were skipped by isOutsideChamfer
        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) {
                    // Find the top of the pillar (scan upward from floorY)
                    int pillarTop = floorY;
                    for (int y = floorY; y < floorY + 50; y++) {
                        if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                            pillarTop = y + 1;
                        } else {
                            break;
                        }
                    }

                    // Find target Y by sampling adjacent non-corner points
                    // that were already processed by blendSurroundingTerrain
                    int targetY = floorY;
                    int sampleCount = 0;
                    int totalY = 0;

                    // Sample adjacent points in cardinal directions
                    int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] offset : offsets) {
                        int sx = x + offset[0];
                        int sz = z + offset[1];
                        // Skip if this adjacent point is also a corner
                        if (isOutsideChamfer(sx, sz, strMinX, strMaxX, strMinZ, strMaxZ, margin)) {
                            continue;
                        }
                        // Sample the ground level at this adjacent point
                        int sampledY = findGroundY(level, sx, sz);
                        if (sampledY != -1) {
                            totalY += sampledY;
                            sampleCount++;
                        }
                    }

                    if (sampleCount > 0) {
                        targetY = totalY / sampleCount;
                    } else {
                        // Fallback: use floorY if no samples available
                        targetY = floorY;
                    }

                    // Carve down to target level
                    for (int y = targetY; y < pillarTop; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }

                    // Place surface block at target level (1.17.1: use 0 instead of getMinY())
                    if (targetY > 0) {
                        // Don't leave a lone surface block floating over a void: only cap
                        // when there is solid support directly beneath it.
                        BlockState capSupport = level.getBlockState(new BlockPos(x, targetY - 2, z));
                        if (!capSupport.isAir() && capSupport.getFluidState().isEmpty()) {
                            level.setBlock(new BlockPos(x, targetY - 1, z), surfaceBlock, 2);
                        }
                    }
                }
            }
        }
    }

    private static BlockState detectDominantSurfaceBlock(ServerLevel level, BlockPos placePos,
                                                          Vec3i structureSize, int margin) {
        Map<net.minecraft.world.level.block.Block, Integer> counts = new HashMap<>();
        int sampleY = placePos.getY();
        int minX = placePos.getX() - margin - 1;
        int maxX = placePos.getX() + structureSize.getX() + margin;
        int minZ = placePos.getZ() - margin - 1;
        int maxZ = placePos.getZ() + structureSize.getZ() + margin;
        for (int x = minX; x <= maxX; x++) {
            sampleColumn(level, x, minZ, sampleY, counts);
            sampleColumn(level, x, maxZ, sampleY, counts);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            sampleColumn(level, minX, z, sampleY, counts);
            sampleColumn(level, maxX, z, sampleY, counts);
        }
        net.minecraft.world.level.block.Block dominant = null;
        int maxCount = 0;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant != null ? dominant.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
    }

    private static void sampleColumn(ServerLevel level, int x, int z, int startY,
                                      Map<net.minecraft.world.level.block.Block, Integer> counts) {
        for (int y = startY; y >= startY - 5; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS)
                    || isThinGroundCover(state)) continue;
            counts.merge(state.getBlock(), 1, Integer::sum);
            return;
        }
    }

    private static boolean isOutsideChamfer(int x, int z, int strMinX, int strMaxX,
                                             int strMinZ, int strMaxZ, int margin) {
        int distX = 0;
        if (x < strMinX) distX = strMinX - x;
        else if (x >= strMaxX) distX = x - strMaxX + 1;
        int distZ = 0;
        if (z < strMinZ) distZ = strMinZ - z;
        else if (z >= strMaxZ) distZ = z - strMaxZ + 1;
        return distX + distZ > 2 * margin - 1;
    }

    private static BlockState mapToSurfaceBlock(BlockState detected) {
        var block = detected.getBlock();
        if (block == Blocks.SAND) return Blocks.SANDSTONE.defaultBlockState();
        if (block == Blocks.RED_SAND) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (block == Blocks.GRAVEL) return Blocks.STONE.defaultBlockState();
        return detected;
    }

    private static BlockState mapToSubsurfaceBlock(BlockState surfaceBlock) {
        if (surfaceBlock.is(Blocks.GRASS_BLOCK)) return Blocks.DIRT.defaultBlockState();
        return surfaceBlock;
    }
}
