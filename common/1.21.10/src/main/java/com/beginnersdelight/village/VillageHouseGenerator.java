package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places village house structures with terrain handling.
 * Adapted from StarterHouseGenerator's placement logic.
 */
public class VillageHouseGenerator {

    private static final ResourceKey<LootTable> STARTER_HOUSE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/starter_house"));

    // Additional containers (e.g. the second half of a double chest) receive this
    // supplies table instead of a duplicate starter kit, so players get useful
    // early-game consumables rather than redundant wooden tools.
    private static final ResourceKey<LootTable> STARTER_HOUSE_SUPPLIES_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/starter_house_supplies"));

    private static final ResourceKey<LootTable> VILLAGE_STOREHOUSE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/village_storehouse"));

    private static final ResourceKey<LootTable> VILLAGE_FARM_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/village_farm"));

    private static final Map<String, ResourceKey<LootTable>> DECORATION_LOOT_TABLES = Map.of(
            "village_storehouse", VILLAGE_STOREHOUSE_LOOT,
            "village_farm", VILLAGE_FARM_LOOT
    );

    private static final String[] DECORATION_VARIANTS = {
            "village_shed", "village_storehouse", "village_farm"
    };

    private static final String[] STRUCTURE_VARIANTS = {
            "starter_house1", "starter_house2", "starter_house3",
            "starter_house4", "starter_house5", "starter_house6"
    };

    /**
     * Result of a successful house placement.
     */
    public record PlacementResult(BlockPos interiorPos, BlockPos doorFrontPos) {}

    /**
     * Checks whether a plot location is suitable for house placement.
     * Returns false if height difference exceeds the threshold or center is underwater.
     */
    public static boolean isSuitable(ServerLevel level, BlockPos plotCenter, int maxHeightDiff) {
        // Check if center is underwater: scan from sea level upward for water
        int centerX = plotCenter.getX();
        int centerZ = plotCenter.getZ();
        int seaLevel = level.getSeaLevel();
        int centerY = findGroundY(level, centerX, centerZ);
        if (centerY == -1) return false;

        // If ground level is below sea level, check for water above ground
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

        // Sample corners to check height difference
        int halfSize = 7; // approximate half of structure footprint
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
     * Returns the Y of the highest fluid surface found at or above {@code floorY}
     * within the whole area the generator reshapes (structure footprint plus the
     * foundation margin and the terrain blend ring), or {@link Integer#MIN_VALUE}
     * when that area holds no such fluid.
     *
     * The scan covers every column, not just the footprint corners: a pond or the
     * ocean touching one edge is enough to flood the flattened surroundings, and
     * the corner samples alone routinely miss it.
     */
    private static int findHighestFluidSurface(ServerLevel level, int startX, int startZ,
                                               Vec3i size, int floorY) {
        // Same reach as fillFoundation's margin plus blendSurroundingTerrain's radius.
        int extend = 2 + 3;
        int minX = startX - extend;
        int maxX = startX + size.getX() + extend;
        int minZ = startZ - extend;
        int maxZ = startZ + size.getZ() + extend;

        // Water sitting well above the floor rests on terrain the blending never cuts
        // into, so it cannot reach the building; a few blocks of headroom is enough.
        // Sea level is always included so an ocean is still seen when the detected
        // floor happens to be far below it.
        int scanTop = Math.max(level.getSeaLevel(), floorY + 4);

        int highest = Integer.MIN_VALUE;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = scanTop; y >= floorY; y--) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
                        if (y > highest) {
                            highest = y;
                        }
                        break;
                    }
                }
            }
        }
        return highest;
    }

    /**
     * Places a randomly selected house structure at the given plot center.
     * Returns the placement result with interior and door positions, or empty if placement failed.
     */
    public static Optional<PlacementResult> place(ServerLevel level, BlockPos plotCenter) {
        StructureTemplateManager templateManager = level.getStructureManager();
        RandomSource random = level.getRandom();

        String variant = STRUCTURE_VARIANTS[random.nextInt(STRUCTURE_VARIANTS.length)];
        ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, variant);

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

        // Interior position: center of structure, one block above floor
        BlockPos interiorPos = placePos.offset(size.getX() / 2, 1, size.getZ() / 2);

        // Door front position: south side center, one block outside the structure
        BlockPos doorFrontPos = new BlockPos(
                placePos.getX() + size.getX() / 2,
                placePos.getY(),
                placePos.getZ() + size.getZ());

        return Optional.of(new PlacementResult(interiorPos, doorFrontPos));
    }

    /**
     * Places a specific named structure at the given plot center.
     * Used for decoration buildings.
     */
    public static Optional<PlacementResult> placeDecoration(ServerLevel level, BlockPos plotCenter, String structureName) {
        StructureTemplateManager templateManager = level.getStructureManager();
        RandomSource random = level.getRandom();

        ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, structureName);

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
            BeginnersDelight.LOGGER.warn("Could not find suitable surface position for {}", structureName);
            return Optional.empty();
        }

        // Sink structure 1 block so the foundation row is embedded underground.
        // Keep surfacePos for terrain handling (fillFoundation, blendSurroundingTerrain)
        // so they operate at the visible ground level, not the lowered placement level.
        BlockPos surfacePos = placePos;
        placePos = placePos.below();

        BeginnersDelight.LOGGER.info("Placing decoration '{}' at {}", structureName, placePos);

        Vec3i size = template.getSize();
        removeMobs(level, placePos, size);
        clearVegetation(level, placePos, size);
        template.placeInWorld(level, placePos, placePos, settings, random, 2 | 16);
        removeDroppedItems(level, placePos, size);

        ResourceKey<LootTable> lootTable = DECORATION_LOOT_TABLES.get(structureName);
        if (lootTable != null) {
            assignLootTablesWithKey(level, placePos, size, random, lootTable);
        }

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

        // Use surfacePos (visible floor level) for positions
        BlockPos interiorPos = surfacePos.offset(size.getX() / 2, 1, size.getZ() / 2);
        BlockPos doorFrontPos = new BlockPos(
                surfacePos.getX() + size.getX() / 2,
                surfacePos.getY(),
                surfacePos.getZ() + size.getZ());

        return Optional.of(new PlacementResult(interiorPos, doorFrontPos));
    }

    /**
     * Selects a random decoration type (excluding well).
     */
    public static String selectRandomDecoration(RandomSource random) {
        return DECORATION_VARIANTS[random.nextInt(DECORATION_VARIANTS.length)];
    }

    // --- Terrain handling methods (adapted from StarterHouseGenerator) ---

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
        // Keep the floor above the water surface of any ocean/lake that reaches the
        // area being reshaped. The lowest sample is often dry ground that still lies
        // below the waterline of adjacent water (a shoreline slope), and since the
        // surroundings get flattened down to the floor, that water then floods the
        // building. Dry ground below sea level (deep valleys) is left at its real
        // height so the building sits on the ground instead of floating.
        // Raising the floor widens the scanned band, so repeat until it comes out
        // clear; a handful of rounds is plenty for terrain that holds water.
        int floorY = resultY;
        for (int round = 0; round < 4; round++) {
            int waterSurfaceY = findHighestFluidSurface(level, startX, startZ, structureSize, floorY);
            if (waterSurfaceY < floorY) {
                break;
            }
            floorY = waterSurfaceY + 1;
        }
        // fillFoundation reaches 10 blocks below the floor, so a bigger lift than that
        // would leave the building standing on nothing. A spot needing one (water
        // perched on a cliff right beside the footprint) cannot be drained by raising
        // at all, so keep the building on the real ground rather than float it.
        if (floorY - resultY <= 9) {
            resultY = floorY;
        }
        return new BlockPos(startX, resultY, startZ);
    }

    private static int findGroundY(ServerLevel level, int x, int z) {
        int maxY = level.getHeight() - 1;
        int minY = level.getMinY();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (isNonGroundPlant(state)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || state.is(BlockTags.REPLACEABLE_BY_TREES)
                    || isThinGroundCover(state)) {
                continue;
            }
            return y + 1;
        }
        return -1;
    }

    // Plants that grow on top of the ground and must not be mistaken for the ground
    // itself; they are not covered by the vegetation tags checked above. Bamboo matters
    // most: a stalk reaches about 16 blocks, so counting it as ground puts the detected
    // surface far above the real terrain and leaves blocks floating in mid-air.
    private static boolean isNonGroundPlant(BlockState state) {
        return state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.PINK_PETALS) || state.is(Blocks.PALE_MOSS_CARPET);
    }

    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS) || isThinGroundCover(state);
    }

    private static void clearVegetation(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int extend = 6; // margin(2) + blendRadius(3) + 1
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

        clearTallPlants(level, placePos, structureSize);
    }

    // Tall plants (bamboo, sugar cane, ...) keep growing past the band scanned above,
    // and the foundation fill later removes their bottom block with a regular block
    // update, which destroys the rest of the stalk with break sounds and scattered
    // drops. Clear whole columns up front with UPDATE_KNOWN_SHAPE instead, over the
    // area that gets flattened; plants outside it are left standing.
    private static void clearTallPlants(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int margin = 2;
        int minX = placePos.getX() - margin;
        int maxX = placePos.getX() + structureSize.getX() + margin;
        int minZ = placePos.getZ() - margin;
        int maxZ = placePos.getZ() + structureSize.getZ() + margin;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY != -1) clearTallPlantColumn(level, x, z, groundY);
            }
        }
    }

    // Removes the tall plant standing on the given ground level as a whole column,
    // bottom-up with UPDATE_KNOWN_SHAPE so no shape update reaches the blocks above:
    // they keep standing until the loop removes them. Taking only the bottom block out
    // would leave the rest unsupported, and it would collapse a tick later with break
    // sounds and drops that the cleanup pass no longer covers.
    private static void clearTallPlantColumn(ServerLevel level, int x, int z, int groundY) {
        // Tallest plant handled here is bamboo at 16 blocks; leave headroom.
        int maxPlantHeight = 32;
        for (int y = groundY; y < groundY + maxPlantHeight; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!isNonGroundPlant(level.getBlockState(pos))) break;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
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
                                          RandomSource random) {
        // Only the first container gets the starter kit (food + one set of wooden
        // tools); any further containers get supplies instead, so a double chest
        // (two block entities) does not yield duplicate tool sets.
        boolean primaryAssigned = false;
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                    if (be instanceof RandomizableContainerBlockEntity container) {
                        ResourceKey<LootTable> loot = primaryAssigned
                                ? STARTER_HOUSE_SUPPLIES_LOOT : STARTER_HOUSE_LOOT;
                        container.setLootTable(loot, random.nextLong());
                        primaryAssigned = true;
                    }
                }
            }
        }
    }

    private static void assignLootTablesWithKey(ServerLevel level, BlockPos placePos, Vec3i structureSize,
                                                 RandomSource random, ResourceKey<LootTable> lootKey) {
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                    if (be instanceof RandomizableContainerBlockEntity container) {
                        container.setLootTable(lootKey, random.nextLong());
                    }
                }
            }
        }
    }

    private static void fillFoundation(ServerLevel level, BlockPos placePos, Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;
        int strMinX = placePos.getX();
        int strMaxX = placePos.getX() + structureSize.getX();
        int strMinZ = placePos.getZ();
        int strMaxZ = placePos.getZ() + structureSize.getZ();

        // Phase 1: Clear above floor and convert exposed dirt to grass
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

        // Phase 2: Detect dominant surface block
        BlockState surfaceBlock = mapToSurfaceBlock(detectDominantSurfaceBlock(level, placePos, structureSize, margin));
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        // Phase 3: Fill foundation downward
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
                    // Take down any tall plant standing here first: carving the
                    // ground from under it would leave the stalk hanging in the air.
                    clearTallPlantColumn(level, x, z, naturalY);
                    for (int y = targetY; y < naturalY; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    if (targetY > level.getMinY()) {
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
                    // Take down any tall plant standing here first: burying its base
                    // would break the rest of the stalk apart.
                    clearTallPlantColumn(level, x, z, naturalY);
                    for (int y = naturalY; y < targetY; y++) {
                        level.setBlock(new BlockPos(x, y, z), (y == targetY - 1) ? surfaceBlock : subsurfaceBlock, 2);
                    }
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

                    // Place surface block at target level
                    if (targetY > level.getMinY()) {
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
            if (isNonGroundPlant(state)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || isThinGroundCover(state)) continue;
            counts.merge(state.getBlock(), 1, Integer::sum);
            return;
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
