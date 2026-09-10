package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.util.StructureDoorUtil;
import com.beginnersdelight.worldgen.StarterHousePool;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Random;

/**
 * Places village house structures with terrain handling.
 * Adapted from StarterHouseGenerator's placement logic.
 */
public class VillageHouseGenerator {

    private static final ResourceLocation STARTER_HOUSE_LOOT =
            new ResourceLocation(BeginnersDelight.MOD_ID, "chests/starter_house");

    // Additional containers (e.g. the second half of a double chest) receive this
    // supplies table instead of a duplicate starter kit, so players get useful
    // early-game consumables rather than redundant wooden tools.
    private static final ResourceLocation STARTER_HOUSE_SUPPLIES_LOOT =
            new ResourceLocation(BeginnersDelight.MOD_ID, "chests/starter_house_supplies");
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

    // Footprint relief above which a candidate site is rejected as too uneven
    // (cliff/ravine/cave edge) for the terrain fill/blend to handle naturally.
    private static final int MAX_FOOTPRINT_RELIEF = 10;

    // How far fillFoundation reaches below the floor to fill the gap with ground
    // blocks. Must cover the worst case: footprint relief (MAX_FOOTPRINT_RELIEF)
    // plus the floor being raised further to clear adjacent water (up to 9, see
    // findSurfacePosition), with a small buffer.
    private static final int FOUNDATION_FILL_DEPTH = 20;

    // The foundation extends two blocks beyond the template and the terrain blend
    // extends another three. A cave in this band is just as visible as one below
    // the house, but previously only the sampled corners were checked.
    private static final int TERRAIN_SAFETY_MARGIN = 5;

    // Vanilla leaves can remain attached at a distance of up to seven blocks from
    // a log. Search this far beyond the altered area for trunks whose canopy must
    // be preserved.
    private static final int MAX_LEAF_DISTANCE = 7;

    // Jungle trees are unusually large (megatrees, 2x2 trunks) and grow densely
    // packed, so the fixed 6-block trunk sweep above turns one nearby trunk into
    // disproportionate canopy loss. Shrink it for jungle wood only, to keep
    // clearing proportionate to tree size. The leaf search above stays at the
    // full MAX_LEAF_DISTANCE for every species, jungle included: a removed
    // tree's own canopy must be captured out to vanilla's real leaf-support
    // distance, or leaves beyond a shorter cap are left floating, belonging to
    // neither the removed tree (out of reach) nor a retained one (none nearby).
    private static final int JUNGLE_INSIDE_SHRINK = 3;

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

        // Scan the whole approximate footprint plus the margin fillFoundation
        // reshapes beyond it (not just the 4 corners): a spot flat at the corners but
        // dropping away sharply in between, or hiding a void under an edge midpoint,
        // used to pass this check and then get bridged into an unnaturally tall
        // pillar or a patchwork of holes.
        int halfSize = 7; // approximate half of structure footprint
        int margin = 2;
        int minX = centerX - halfSize - margin;
        int maxX = centerX + halfSize + margin;
        int minZ = centerZ - halfSize - margin;
        int maxZ = centerZ + halfSize + margin;
        int minY = centerY, maxY = centerY;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int y = findGroundY(level, x, z);
                if (y == -1) return false;
                if (hasVoidBelow(level, x, z, y)) return false;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxY - minY > maxHeightDiff) return false;

        // Also check the wider band that blendSurroundingTerrain reaches beyond the
        // margin: fillFoundation leaves unsupported columns alone instead of bridging
        // a cave, so a void there would otherwise survive as a hole next to an
        // approved site.
        return isTerrainBandVoidFree(level,
                centerX - halfSize - TERRAIN_SAFETY_MARGIN, centerZ - halfSize - TERRAIN_SAFETY_MARGIN,
                (halfSize + TERRAIN_SAFETY_MARGIN) * 2 + 1, (halfSize + TERRAIN_SAFETY_MARGIN) * 2 + 1);
    }

    private static boolean isTerrainBandVoidFree(ServerLevel level, int startX, int startZ,
                                                  int sizeX, int sizeZ) {
        for (int x = startX; x < startX + sizeX; x++) {
            for (int z = startZ; z < startZ + sizeZ; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY == -1 || hasVoidBelow(level, x, z, groundY)) {
                    return false;
                }
            }
        }
        return true;
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

    public static Optional<PlacementResult> place(ServerLevel level, BlockPos plotCenter) {
        StructureManager templateManager = level.getStructureManager();
        Random random = level.getRandom();

        Optional<StarterHousePool.Entry> entryOpt = StarterHousePool.select(level.getServer().getResourceManager(), random);
        if (entryOpt.isEmpty()) return Optional.empty();
        StarterHousePool.Entry entry = entryOpt.get();
        ResourceLocation structureId = new ResourceLocation(entry.template().toString());

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

        BeginnersDelight.LOGGER.info("Placing village house '{}' at {}", structureId, placePos);

        Vec3i size = template.getSize();
        removeMobs(level, placePos, size);
        // Remember the thin ground cover (snow, moss carpet, ...) before clearing it
        Map<Long, BlockState> groundCover = captureGroundCover(level, placePos, size);
        Set<BlockPos> protectedTreeParts = clearIntersectingTrees(level, placePos, size);
        clearVegetation(level, placePos, size, protectedTreeParts);
        template.placeInWorld(level, placePos, placePos, settings, random, 2 | 16);
        removeDroppedItems(level, placePos, size);
        if (entry.lootMode() == StarterHousePool.LootMode.STARTER) assignLootTables(level, placePos, size, random);
        // Blend surrounding terrain first so the terrain around the foundation
        // is flat before filling. This prevents corner pillars from being too high.
        blendSurroundingTerrain(level, placePos, size);

        // Fill gaps below the structure floor to prevent floating on slopes
        fillFoundation(level, placePos, size);

        // Blend corner pillars that were skipped by isOutsideChamfer in fillFoundation
        blendCornerPillars(level, placePos, size);

        // Replace surface dirt next to grass with grass for a natural blend.
        naturalizeDirtSurface(level, placePos, size);

        // Put the snow/carpet cover back so the house does not sit in a bare patch
        restoreGroundCover(level, placePos, size, placePos.getY(), groundCover);

        // Terrain shaping can replace the supporting log of a protected canopy.
        // Remove only the leaves that lost their matching outside tree, rather than
        // leaving a curtain of detached leaves down to the ground.
        clearDetachedProtectedLeaves(level, protectedTreeParts, placePos, size);
        removeDroppedItems(level, placePos, size);

        BlockPos interiorPos = placePos.offset(size.getX() / 2, 1, size.getZ() / 2);
        BlockPos doorFrontPos = StructureDoorUtil.findDoorFrontPos(level, placePos, size);

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
        // Remember the thin ground cover (snow, moss carpet, ...) before clearing it.
        // Uses placePos, not the surfacePos the terrain methods take: the cover only
        // needs the same XZ area, and restoring it later reads the template's own Y
        // band to tell which columns the structure occupies.
        Map<Long, BlockState> groundCover = captureGroundCover(level, placePos, size);
        removeMobs(level, placePos, size); Set<BlockPos> protectedTreeParts = clearIntersectingTrees(level, placePos, size);
        clearVegetation(level, placePos, size, protectedTreeParts);
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

        // Put the snow/carpet cover back so the structure does not sit in a bare patch
        restoreGroundCover(level, placePos, size, surfacePos.getY(), groundCover);

        // Terrain shaping can replace the supporting log of a protected canopy.
        clearDetachedProtectedLeaves(level, protectedTreeParts, placePos, size);
        removeDroppedItems(level, surfacePos, size);
        BlockPos interiorPos = surfacePos.offset(size.getX() / 2, 1, size.getZ() / 2);
        BlockPos doorFrontPos = StructureDoorUtil.findDoorFrontPos(level, surfacePos, size);
        return Optional.of(new PlacementResult(interiorPos, doorFrontPos));
    }
    public static String selectRandomDecoration(Random random) { return DECORATION_VARIANTS[random.nextInt(DECORATION_VARIANTS.length)]; }

    /**
     * Scans every column of the footprint (not just corners/center) to find the
     * lowest and highest ground surface, skipping vegetation the same way
     * {@link #findGroundY} does. Returns {@code null} -- rejecting the site -- when
     * any column has no ground, sits over a void (see {@link #hasVoidBelow}), or
     * the footprint's relief exceeds {@link #MAX_FOOTPRINT_RELIEF}, since filling
     * up to the highest point would then have to bridge a cliff, ravine, or cave
     * mouth instead of a natural slope.
     *
     * @return {@code {minY, maxY}} of the footprint's ground surface, or
     *         {@code null} if the footprint is unsuitable
     */
    private static int[] scanFootprintHeights(ServerLevel level, int startX, int startZ,
                                               int sizeX, int sizeZ) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int x = startX; x < startX + sizeX; x++) {
            for (int z = startZ; z < startZ + sizeZ; z++) {
                int y = findGroundY(level, x, z);
                if (y == -1) return null;
                if (hasVoidBelow(level, x, z, y)) return null;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        if (maxY - minY > MAX_FOOTPRINT_RELIEF) return null;
        return new int[]{minY, maxY};
    }

    /**
     * Scans surface Y across every column of the footprint (skipping vegetation)
     * and uses the highest point so the structure sits flush with the tallest
     * terrain under it. The gap under lower columns is filled in by
     * {@link #fillFoundation} instead, so slopes keep their natural shape rather
     * than being carved flat down to the lowest point.
     */
    private static BlockPos findSurfacePosition(ServerLevel level, BlockPos center, Vec3i structureSize) {
        int halfX = structureSize.getX() / 2;
        int halfZ = structureSize.getZ() / 2;
        int startX = center.getX() - halfX;
        int startZ = center.getZ() - halfZ;

        int[] range = scanFootprintHeights(level, startX, startZ,
                structureSize.getX(), structureSize.getZ());
        if (range == null) return null;
        int resultY = range[1];

        // Keep the floor above the water surface of any ocean/lake that reaches the
        // area being reshaped. Even the footprint's highest point can still sit
        // below the waterline of adjacent water (e.g. a valley bottom next to the
        // sea), and since the surroundings get flattened up to the floor, that
        // water would otherwise flood the building. Dry ground below sea level (deep
        // valleys with no adjacent water) is left at its real height so the building
        // sits on the ground instead of floating.
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
        // fillFoundation fills at most FOUNDATION_FILL_DEPTH blocks below the floor,
        // and up to MAX_FOOTPRINT_RELIEF of that is already spent reaching the
        // footprint's lowest column, so a bigger lift than 9 here would leave part
        // of the building standing on nothing. A spot needing one (water perched on
        // a cliff right beside the footprint) cannot be drained by raising at all,
        // so keep the building on the real ground rather than float it above a gap.
        if (floorY - resultY <= 9) {
            resultY = floorY;
        }
        return new BlockPos(startX, resultY, startZ);
    }

    private static int findGroundY(ServerLevel level, int x, int z) {
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (isNonGroundPlant(state)
                    || isMushroom(state)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS)
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

    // Mushrooms: the small ones plus huge-mushroom cap/stem blocks. They belong to none
    // of the vegetation tags checked above (vanilla keeps them in their own
    // replaceable_by_mushrooms tag), so without this they survive clearVegetation and are
    // instead destroyed later by the shape updates terrain reshaping sends out. That drops
    // mushroom items which removeDroppedItems cannot reliably sweep up: item entities in
    // chunks that are not tracked yet are invisible to the entity query, so they are left
    // floating next to the finished building.
    private static boolean isMushroom(BlockState state) {
        return state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM);
    }

    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET);
    }

    /**
     * Returns true for short growth that always sits directly on the solid block
     * beneath it: thin ground cover plus grass and flowers. Used by
     * {@link #fillFoundation} to decide what to fill straight through, since
     * clearVegetation and Phase 1 of fillFoundation only scan from floorY up, so
     * cover sitting below floorY survives untouched and would otherwise stop the
     * fill loop one block short, leaving it standing in as the floor.
     */
    private static boolean isFillableShortCover(BlockState state) {
        return isThinGroundCover(state)
                || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS);
    }

    // Records the thin ground cover standing on every column about to be reshaped so
    // restoreGroundCover can lay it back down. clearVegetation strips this cover and
    // nothing used to put it back, which left the structure inside a sharply outlined
    // bare rectangle -- glaring in snowy biomes. findGroundY skips thin cover, so the
    // block it points at is the cover itself whenever a column has one.
    private static Map<Long, BlockState> captureGroundCover(ServerLevel level, BlockPos placePos,
                                                             Vec3i structureSize) {
        Map<Long, BlockState> cover = new HashMap<>();
        int extend = 6; // same reach as clearVegetation, which removes the cover
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY == -1) continue;
                BlockState state = level.getBlockState(new BlockPos(x, groundY, z));
                if (isThinGroundCover(state)) cover.put(columnKey(x, z), state);
            }
        }
        return cover;
    }

    // Lays the recorded ground cover back onto the reshaped surface, one column at a
    // time. Columns that had no cover to begin with stay bare, so the result keeps the
    // natural patchiness instead of turning into a uniform slab of snow. Columns the
    // building stands on get no cover back: a freshly built house has nothing on it.
    // placePos only marks out the XZ area; the Y work goes off floorY, the visible floor
    // level, which for a decoration sits one above placePos because placeDecoration sinks
    // the template. Measuring the structure band from placePos instead would report every
    // footprint column as built on, since fillFoundation lays the ground at that same row.
    private static void restoreGroundCover(ServerLevel level, BlockPos placePos,
                                            Vec3i structureSize, int floorY,
                                            Map<Long, BlockState> cover) {
        int extend = 6;
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                boolean insideFootprint = x >= placePos.getX()
                        && x < placePos.getX() + structureSize.getX()
                        && z >= placePos.getZ()
                        && z < placePos.getZ() + structureSize.getZ();
                if (insideFootprint && isStructureColumn(level, x, z,
                        floorY, structureSize.getY())) {
                    clearStaleSnowy(level, x, z, floorY);
                    continue;
                }
                int groundY = findGroundY(level, x, z);
                if (groundY == -1) continue;
                BlockPos pos = new BlockPos(x, groundY, z);
                BlockState recorded = cover.get(columnKey(x, z));
                boolean covered = recorded != null && level.getBlockState(pos).isAir()
                        && recorded.canSurvive(level, pos);
                if (covered) level.setBlock(pos, recorded, 2);

                // Grass, podzol and mycelium only look snowed over while SNOWY is set,
                // and the flag no longer matches the surface: clearVegetation took the
                // snow away with UPDATE_KNOWN_SHAPE, which suppresses the shape update
                // that would have cleared it, while the blocks the foundation fill put
                // down carry the default (unset) value. Sync it with what actually lies
                // on top so no white-but-snowless -- or snowed-but-green -- patch shows.
                BlockPos belowPos = pos.below();
                BlockState below = level.getBlockState(belowPos);
                if (below.hasProperty(BlockStateProperties.SNOWY)) {
                    // Read what is really on the ground rather than assuming the
                    // cover is ours: a column outside the reshaped band still carries
                    // the snow clearVegetation never reached, since that pass only
                    // scans from the floor level upward.
                    boolean snowy = isSnowCover(level.getBlockState(pos));
                    if (below.getValue(BlockStateProperties.SNOWY) != snowy) {
                        level.setBlock(belowPos,
                                below.setValue(BlockStateProperties.SNOWY, snowy), 2);
                    }
                }
            }
        }
    }

    // Drops the leftover SNOWY flag from the ground of a column the building stands on.
    // The snow that made it white is gone for good there, and on the sheltered ground
    // under a roof overhang -- the one part of such a column that stays visible -- it
    // would otherwise read as a white patch with nothing lying on it. Vanilla leaves
    // that ground green too: a village generates before the top layer is frozen, so no
    // snow ever reaches under the eaves.
    private static void clearStaleSnowy(ServerLevel level, int x, int z, int floorY) {
        // The reshaped ground inside the footprint sits at floorY - 1; scan a little
        // deeper only to skip over any air the template left below the floor.
        for (int y = floorY - 1; y >= floorY - 3; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.hasProperty(BlockStateProperties.SNOWY)
                    && state.getValue(BlockStateProperties.SNOWY)
                    && !isSnowCover(level.getBlockState(pos.above()))) {
                level.setBlock(pos, state.setValue(BlockStateProperties.SNOWY, false), 2);
            }
            return;
        }
    }

    // True when the building itself stands on this column, i.e. the template put a
    // block somewhere in the column's structure band. Open ground inside the template's
    // bounding box (the yard beside an L-shaped house) is not part of the building and
    // keeps its ground cover. Ground under a roof overhang counts as built on and stays
    // bare, which is also what a sheltered patch looks like naturally.
    private static boolean isStructureColumn(ServerLevel level, int x, int z,
                                              int floorY, int height) {
        for (int y = floorY; y < floorY + height; y++) {
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) return true;
        }
        return false;
    }

    // The blocks that make the ground below them render snowed over, matching vanilla's
    // SnowyDirtBlock.
    private static boolean isSnowCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    // 1.17.1: No BlockTags.REPLACEABLE_BY_TREES — use explicit block/tag checks instead
    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS)
                || isThinGroundCover(state)
                || isMushroom(state);
    }

    private static void clearVegetation(ServerLevel level, BlockPos placePos, Vec3i structureSize,
                                         Set<BlockPos> protectedTreeParts) {
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
                    if (!state.isAir() && !protectedTreeParts.contains(pos) && isVegetation(state)) {
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

    /**
     * Pairs the tree blocks a removal pass must clear with the ones neighboring
     * trees need kept, so both plans are derived from the same trunk analysis.
     */
    private static final class TreeClearPlan {
        private final Set<BlockPos> toRemove;
        private final Set<BlockPos> protectedParts;

        TreeClearPlan(Set<BlockPos> toRemove, Set<BlockPos> protectedParts) {
            this.toRemove = toRemove;
            this.protectedParts = protectedParts;
        }
    }

    private static Set<BlockPos> clearIntersectingTrees(ServerLevel level, BlockPos placePos,
                                                        net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius + 1;
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;

        TreeClearPlan plan = planTreeClearing(level, minX, maxX, minZ, maxZ);
        removeTreeParts(level, plan.toRemove);
        return plan.protectedParts;
    }

    /**
     * Identifies trees by trunk rather than by flood-filling touching canopy, so
     * a removed tree's leaves cannot cascade into an adjacent, untouched tree
     * through touching leaves the way a canopy-wide flood fill would. Trunks
     * inside the altered area contribute their logs and, up to the leaf-support
     * distance, their canopy to the removal set; trunks outside it are protected
     * the same way, with a leaf shared by both assigned to the nearer trunk.
     * A log/leaf that cannot be traced back to a trunk base (e.g. a stray branch
     * from a player-altered or non-vanilla tree shape) is left untouched; vanilla
     * world generation does not produce such trees, so this trades an unreachable
     * edge case for bounding the removal to the trees actually being cleared.
     */
    private static TreeClearPlan planTreeClearing(ServerLevel level, int minX, int maxX,
                                                   int minZ, int maxZ) {
        Set<BlockPos> retainedLogs = new HashSet<>();
        Set<BlockPos> removedLogs = new HashSet<>();
        Set<BlockPos> visitedRetainedLogs = new HashSet<>();
        Set<BlockPos> visitedRemovedLogs = new HashSet<>();

        for (int x = minX - MAX_LEAF_DISTANCE; x < maxX + MAX_LEAF_DISTANCE; x++) {
            for (int z = minZ - MAX_LEAF_DISTANCE; z < maxZ + MAX_LEAF_DISTANCE; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY == -1) {
                    continue;
                }
                BlockPos trunkBase = new BlockPos(x, groundY, z);
                if (!isTrunkBase(level, trunkBase)) {
                    continue;
                }
                boolean inside = isJungleLog(level.getBlockState(trunkBase))
                        ? isInsideClearedArea(x, z, minX + JUNGLE_INSIDE_SHRINK, maxX - JUNGLE_INSIDE_SHRINK,
                                                     minZ + JUNGLE_INSIDE_SHRINK, maxZ - JUNGLE_INSIDE_SHRINK)
                        : isInsideClearedArea(x, z, minX, maxX, minZ, maxZ);
                if (inside) {
                    collectConnectedLogs(level, trunkBase, removedLogs, visitedRemovedLogs);
                } else {
                    collectConnectedLogs(level, trunkBase, retainedLogs, visitedRetainedLogs);
                }
            }
        }

        // Logs that directly connect an inside and outside trunk are ambiguous.
        // Treat them as part of the removed tree so a shared branch cannot preserve
        // an otherwise removable canopy.
        retainedLogs.removeAll(removedLogs);

        Map<BlockPos, Integer> retainedLeafDistances = findLeafDistances(level, retainedLogs);
        Map<BlockPos, Integer> removedLeafDistances = findLeafDistances(level, removedLogs);
        Set<BlockPos> protectedParts = new HashSet<>(retainedLogs);
        for (Map.Entry<BlockPos, Integer> entry : retainedLeafDistances.entrySet()) {
            int removedDistance = removedLeafDistances.getOrDefault(entry.getKey(),
                    MAX_LEAF_DISTANCE + 1);
            // A tie belongs to the removed tree. This trims the shared edge instead
            // of leaving a curtain of leaves from the tree that was cut down.
            if (entry.getValue() < removedDistance) {
                protectedParts.add(entry.getKey());
            }
        }

        Set<BlockPos> toRemove = new HashSet<>(removedLogs);
        for (BlockPos leaf : removedLeafDistances.keySet()) {
            if (!protectedParts.contains(leaf)) {
                toRemove.add(leaf);
            }
        }
        collectAttachedVines(level, toRemove);

        return new TreeClearPlan(toRemove, protectedParts);
    }

    /**
     * Adds every vine block reachable from the removal set by walking only
     * through other vines, so a hanging strand attached to a removed tree comes
     * down with it without the search crossing into an unrelated tree's logs
     * or leaves.
     */
    private static void collectAttachedVines(ServerLevel level, Set<BlockPos> toRemove) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>(toRemove);
        for (BlockPos pos : new HashSet<>(toRemove)) {
            queueAdjacentVines(level, pos, visited, pending);
        }
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            toRemove.add(pos);
            queueAdjacentVines(level, pos, visited, pending);
        }
    }

    private static void queueAdjacentVines(ServerLevel level, BlockPos pos, Set<BlockPos> visited,
                                           ArrayDeque<BlockPos> pending) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos adjacent = pos.offset(dx, dy, dz);
                    if (visited.add(adjacent) && level.getBlockState(adjacent).is(Blocks.VINE)) {
                        pending.add(adjacent);
                    }
                }
            }
        }
    }

    private static void removeTreeParts(ServerLevel level, Set<BlockPos> toRemove) {
        for (BlockPos pos : toRemove) {
            // UPDATE_KNOWN_SHAPE suppresses the usual support check, so snow resting
            // on a removed leaf/log would otherwise remain floating in the air.
            BlockPos above = pos.above();
            if (isSnowCover(level.getBlockState(above))) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    private static boolean isInsideClearedArea(int x, int z, int minX, int maxX,
                                               int minZ, int maxZ) {
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }

    /**
     * A base log is anchored directly above the natural surface with another log
     * above it. This excludes branches, so only trees rooted outside the cleared
     * area contribute a protected canopy.
     */
    private static boolean isTrunkBase(ServerLevel level, BlockPos pos) {
        return isTreeLog(level.getBlockState(pos))
                && !isTreeLog(level.getBlockState(pos.below()))
                && isTreeLog(level.getBlockState(pos.above()));
    }

    private static void collectConnectedLogs(ServerLevel level, BlockPos start,
                                             Set<BlockPos> logs, Set<BlockPos> visited) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || !isTreeLog(level.getBlockState(pos))) {
                continue;
            }
            logs.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            pending.add(pos.offset(dx, dy, dz));
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the shortest leaf-path distance from any supplied log to each leaf
     * of the matching species. Distances are calculated separately for retained
     * and removed trees, which lets the caller assign a shared canopy to the
     * nearest trunk instead of preserving it merely because some other tree is
     * within vanilla's seven-block support distance.
     */
    private static Map<BlockPos, Integer> findLeafDistances(ServerLevel level, Set<BlockPos> logs) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        for (BlockPos log : logs) {
            collectLeafDistances(level, log, distances);
        }
        return distances;
    }

    private static void collectLeafDistances(ServerLevel level, BlockPos log,
                                             Map<BlockPos, Integer> result) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        pending.add(log);
        distances.put(log, 0);

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            int distance = distances.get(pos);
            if (distance == MAX_LEAF_DISTANCE) {
                continue;
            }
            for (int[] direction : new int[][] {
                    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                    {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos adjacent = pos.offset(direction[0], direction[1], direction[2]);
                if (!isLeafForLog(level.getBlockState(log), level.getBlockState(adjacent))
                        || distances.containsKey(adjacent)) {
                    continue;
                }
                distances.put(adjacent, distance + 1);
                result.merge(adjacent, distance + 1, Math::min);
                pending.add(adjacent);
            }
        }
    }

    private static boolean isLeafForLog(BlockState log, BlockState leaf) {
        if (isOakLog(log)) return leaf.is(Blocks.OAK_LEAVES);
        if (isBirchLog(log)) return leaf.is(Blocks.BIRCH_LEAVES);
        if (isSpruceLog(log)) return leaf.is(Blocks.SPRUCE_LEAVES);
        if (isJungleLog(log)) return leaf.is(Blocks.JUNGLE_LEAVES);
        if (isAcaciaLog(log)) return leaf.is(Blocks.ACACIA_LEAVES);
        if (isDarkOakLog(log)) return leaf.is(Blocks.DARK_OAK_LEAVES);
        return false;
    }

    private static boolean isOakLog(BlockState state) {
        return state.is(Blocks.OAK_LOG) || state.is(Blocks.OAK_WOOD)
                || state.is(Blocks.STRIPPED_OAK_LOG) || state.is(Blocks.STRIPPED_OAK_WOOD);
    }

    private static boolean isBirchLog(BlockState state) {
        return state.is(Blocks.BIRCH_LOG) || state.is(Blocks.BIRCH_WOOD)
                || state.is(Blocks.STRIPPED_BIRCH_LOG) || state.is(Blocks.STRIPPED_BIRCH_WOOD);
    }

    private static boolean isSpruceLog(BlockState state) {
        return state.is(Blocks.SPRUCE_LOG) || state.is(Blocks.SPRUCE_WOOD)
                || state.is(Blocks.STRIPPED_SPRUCE_LOG) || state.is(Blocks.STRIPPED_SPRUCE_WOOD);
    }

    private static boolean isJungleLog(BlockState state) {
        return state.is(Blocks.JUNGLE_LOG) || state.is(Blocks.JUNGLE_WOOD)
                || state.is(Blocks.STRIPPED_JUNGLE_LOG) || state.is(Blocks.STRIPPED_JUNGLE_WOOD);
    }

    private static boolean isAcaciaLog(BlockState state) {
        return state.is(Blocks.ACACIA_LOG) || state.is(Blocks.ACACIA_WOOD)
                || state.is(Blocks.STRIPPED_ACACIA_LOG) || state.is(Blocks.STRIPPED_ACACIA_WOOD);
    }

    private static boolean isDarkOakLog(BlockState state) {
        return state.is(Blocks.DARK_OAK_LOG) || state.is(Blocks.DARK_OAK_WOOD)
                || state.is(Blocks.STRIPPED_DARK_OAK_LOG) || state.is(Blocks.STRIPPED_DARK_OAK_WOOD);
    }




    private static void clearDetachedProtectedLeaves(ServerLevel level, Set<BlockPos> protectedParts,
                                                      BlockPos placePos,
                                                      net.minecraft.core.Vec3i structureSize) {
        int extend = 6; // foundation margin (2) + blend radius (3) + shape-update neighbor
        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;
        Set<BlockPos> connectedParts = planTreeClearing(level, minX, maxX, minZ, maxZ).protectedParts;

        for (BlockPos pos : protectedParts) {
            if (!connectedParts.contains(pos) && level.getBlockState(pos).is(BlockTags.LEAVES)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static boolean isTreeLog(BlockState state) {
        return state.is(BlockTags.LOGS);
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
        // A house can hold several containers: a chest plus a few barrels that are
        // there as furnishing, or two chests. Only one of them gets the starter kit
        // (food + one set of wooden tools) and the rest get supplies, so the player
        // is not handed the same tools over and over.
        BlockPos primaryPos = findPrimaryContainer(level, placePos, structureSize);
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof RandomizableContainerBlockEntity container
                            && !hasExistingLootTable(container) && container.isEmpty()) {
                        ResourceLocation loot = pos.equals(primaryPos)
                                ? STARTER_HOUSE_LOOT : STARTER_HOUSE_SUPPLIES_LOOT;
                        container.setLootTable(loot, random.nextLong());
                    }
                }
            }
        }
    }

    /**
     * Picks the container that receives the starter kit: the first chest in scan
     * order, or -- for the house variants furnished with barrels only -- the first
     * container of any kind. A chest is preferred because it is the one a beginner
     * opens first; leaving the kit in a decorative barrel would hide the food and
     * tools the house exists to hand over.
     */
    private static BlockPos findPrimaryContainer(ServerLevel level, BlockPos placePos,
                                                  Vec3i structureSize) {
        BlockPos firstContainer = null;
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                            || hasExistingLootTable(container) || !container.isEmpty()) {
                        continue;
                    }
                    if (blockEntity instanceof ChestBlockEntity) {
                        return pos;
                    }
                    if (firstContainer == null) {
                        firstContainer = pos;
                    }
                }
            }
        }
        return firstContainer;
    }

    private static boolean hasExistingLootTable(RandomizableContainerBlockEntity container) {
        return container.saveWithFullMetadata().contains("LootTable", 8);
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
                for (int sy = floorY - 1; sy >= floorY - FOUNDATION_FILL_DEPTH; sy--) {
                    BlockState below = level.getBlockState(new BlockPos(x, sy, z));
                    if (!below.isAir() && below.getFluidState().isEmpty()) { solidWithinReach = true; break; }
                }
                if (!solidWithinReach) continue;
                for (int y = floorY - 1; y >= floorY - FOUNDATION_FILL_DEPTH; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && existing.getFluidState().isEmpty()
                            && !isFillableShortCover(existing)) break;
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
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        // Only process corner points that were skipped by isOutsideChamfer
        for (int x = strMinX - margin; x < strMaxX + margin; x++) {
            for (int z = strMinZ - margin; z < strMaxZ + margin; z++) {
                if (isOutsideChamfer(x, z, strMinX, strMaxX, strMinZ, strMaxZ, margin)) {
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

                    int naturalY = findGroundY(level, x, z);
                    if (naturalY == -1) continue;

                    if (naturalY > targetY) {
                        // Terrain higher than target: carve down to create a flat corner
                        clearTallPlantColumn(level, x, z, naturalY);
                        for (int y = targetY; y < naturalY; y++) {
                            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        }
                        // Place surface block at target level. Don't leave a lone surface
                        // block floating over a void: only cap when there is solid support
                        // directly beneath it.
                        if (targetY > level.getMinBuildHeight()) {
                            BlockState capSupport = level.getBlockState(new BlockPos(x, targetY - 2, z));
                            if (!capSupport.isAir() && capSupport.getFluidState().isEmpty()) {
                                level.setBlock(new BlockPos(x, targetY - 1, z), surfaceBlock, 2);
                            }
                        }
                    } else if (naturalY < targetY) {
                        // Don't bridge cliffs/voids: same guard as blendSurroundingTerrain.
                        if (targetY - naturalY > 6) continue;
                        // Terrain lower than target: fill up to close the gap left at this
                        // chamfered corner instead of leaving it hollow.
                        clearTallPlantColumn(level, x, z, naturalY);
                        for (int y = naturalY; y < targetY; y++) {
                            BlockState fill = (y == targetY - 1) ? surfaceBlock : subsurfaceBlock;
                            level.setBlock(new BlockPos(x, y, z), fill, 2);
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
                    || isMushroom(state)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
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
