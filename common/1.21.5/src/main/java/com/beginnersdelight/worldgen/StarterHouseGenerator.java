package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import com.beginnersdelight.util.StructureDoorUtil;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generates a starter house structure at the world spawn point.
 * Randomly selects one of the available structure variants and places it
 * on the terrain surface.
 */
public class StarterHouseGenerator {

    private static final ResourceKey<LootTable> STARTER_HOUSE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/starter_house"));

    // Additional containers (e.g. the second half of a double chest) receive this
    // supplies table instead of a duplicate starter kit, so players get useful
    // early-game consumables rather than redundant wooden tools.
    private static final ResourceKey<LootTable> STARTER_HOUSE_SUPPLIES_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(BeginnersDelight.MOD_ID, "chests/starter_house_supplies"));

    // Footprint relief above which a candidate site is rejected as too uneven
    // (cliff/ravine/cave edge) for the terrain fill/blend to handle naturally.
    private static final int MAX_FOOTPRINT_RELIEF = 10;

    // How far fillFoundation reaches below the floor to fill the gap with ground
    // blocks. Must cover the worst case: footprint relief (MAX_FOOTPRINT_RELIEF)
    // plus the floor being raised further to clear adjacent water (up to 9, see
    // findSurfacePosition), with a small buffer.
    private static final int FOUNDATION_FILL_DEPTH = 20;

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

        if (ModGameRules.GENERATE_STARTER_HOUSE == null
                || !overworld.getGameRules().getBoolean(ModGameRules.GENERATE_STARTER_HOUSE)) {
            BeginnersDelight.LOGGER.debug("Starter house generation disabled by game rule; skipping");
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
        BlockPos center = findSuitableCenter(level, spawnPos, template.getSize());
        BlockPos placePos = findSurfacePosition(level, center, template.getSize());
        if (placePos == null) {
            BeginnersDelight.LOGGER.warn("Could not find suitable surface position for starter house");
            return false;
        }

        BeginnersDelight.LOGGER.info("Placing structure '{}' at {}", variant, placePos);

        // Remove mobs from the area to prevent them from being trapped
        // inside blocks during structure placement
        removeMobs(level, placePos, template.getSize());

        // Remember the thin ground cover (snow, moss carpet, ...) before it is
        // cleared so it can be laid back over the finished terrain.
        Map<Long, BlockState> groundCover = captureGroundCover(level, placePos, template.getSize());

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

        // Blend surrounding terrain first so the terrain around the foundation
        // is flat before filling. This prevents corner pillars from being too high.
        blendSurroundingTerrain(level, placePos, template.getSize());

        // Fill gaps below the structure floor to prevent floating on slopes
        fillFoundation(level, placePos, template.getSize());

        // Blend corner pillars that were skipped by isOutsideChamfer in fillFoundation
        blendCornerPillars(level, placePos, template.getSize());

        // Replace surface dirt next to grass with grass so the leveled/blended
        // ring blends naturally with the surrounding grassland.
        naturalizeDirtSurface(level, placePos, template.getSize());

        // Put the snow/carpet cover back on the reshaped surroundings so the house
        // does not sit in a sharply outlined bare patch.
        restoreGroundCover(level, placePos, template.getSize(), groundCover);

        // Remove item entities (seeds, sticks, etc.) dropped by destroyed vegetation
        removeDroppedItems(level, placePos, template.getSize());

        // Calculate the interior spawn position (center, one block above floor)
        net.minecraft.core.Vec3i size = template.getSize();
        BlockPos insidePos = placePos.offset(size.getX() / 2, 1, size.getZ() / 2);

        // Locate the structure's actual door and target the exterior side it
        // opens onto; falls back to the south wall if no door block is found.
        BlockPos doorFrontPos = StructureDoorUtil.findDoorFrontPos(level, placePos, size);

        // Store spawn position in SavedData for player join teleport
        data.setSpawnPos(insidePos);
        data.setDoorPos(doorFrontPos);

        // Set world spawn and radius (used as fallback for death respawn without bed)
        level.setDefaultSpawnPos(insidePos, 0.0f);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_RADIUS)
                .set(0, level.getServer());
        BeginnersDelight.LOGGER.info("World spawn set to: {}", insidePos);

        return true;
    }

    /**
     * Finds a suitable surface position for placing the structure.
     * Scans surface Y across every column of the footprint (skipping vegetation)
     * and uses the highest point so the structure sits flush with the tallest
     * terrain under it. The gap under lower columns is filled in by
     * {@link #fillFoundation} instead, so slopes keep their natural shape rather
     * than being carved flat down to the lowest point.
     */
    private static BlockPos findSurfacePosition(ServerLevel level, BlockPos center,
                                                 net.minecraft.core.Vec3i structureSize) {
        int halfX = structureSize.getX() / 2;
        int halfZ = structureSize.getZ() / 2;

        int startX = center.getX() - halfX;
        int startZ = center.getZ() - halfZ;

        int[] range = scanFootprintHeights(level, startX, startZ,
                structureSize.getX(), structureSize.getZ());
        if (range == null) {
            return null;
        }
        int resultY = range[1];

        // Keep the floor above the water surface of any ocean/lake that reaches the
        // area being reshaped. Even the footprint's highest point can still sit
        // below the waterline of adjacent water (e.g. a valley bottom next to the
        // sea), and since the surroundings get flattened up to the floor, that
        // water would otherwise flood the house. Dry ground below sea level (deep
        // valleys with no adjacent water) is left at its real height so the house
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
        // of the house standing on nothing. A spot needing one (water perched on a
        // cliff right beside the footprint) cannot be drained by raising at all, so
        // keep the house on the real ground rather than float it above a gap.
        if (floorY - resultY <= 9) {
            resultY = floorY;
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
        int maxY = level.getHeight() - 1;
        int minY = level.getMinY();

        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (isNonGroundCover(state)) {
                continue;
            }
            return y + 1;
        }
        return -1;
    }

    /**
     * Returns true for anything growing on or lying over the ground that must not
     * be mistaken for the ground itself: trees, plants, mushrooms, and thin cover
     * like snow layers or flowers. Used by {@link #findGroundY} to skip past growth
     * while scanning down for the real surface. For the narrower "safe to fill
     * through" notion used by {@link #fillFoundation}, see
     * {@link #isFillableShortCover}.
     */
    private static boolean isNonGroundCover(BlockState state) {
        return isNonGroundPlant(state)
                    || isMushroom(state)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || state.is(BlockTags.REPLACEABLE_BY_TREES)
                    || isThinGroundCover(state);
    }

    /**
     * Returns true for short growth that always sits directly on the solid block
     * beneath it: thin ground cover plus grass and flowers. Used by
     * {@link #fillFoundation} to decide what to fill straight through. Unlike the
     * full {@link #isNonGroundCover} set, this deliberately excludes leaves, logs,
     * mushrooms and other tall/elevated growth -- those are not guaranteed to have
     * solid ground directly beneath them (a branch overhanging a ravine, a mushroom
     * on a cave wall), so treating them as fillable could tunnel the fill loop
     * through a void instead of stopping at them as it used to.
     */
    private static boolean isFillableShortCover(BlockState state) {
        return isThinGroundCover(state)
                || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS);
    }

    /**
     * Returns true if the block is a plant that grows on top of the ground and
     * therefore must not be mistaken for the ground itself. These are not covered
     * by the vegetation tags checked above. Bamboo matters most: a stalk reaches
     * about 16 blocks, so counting it as ground puts the detected surface far above
     * the real terrain and leaves blocks floating in mid-air after blending.
     */
    private static boolean isNonGroundPlant(BlockState state) {
        return state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    /**
     * Returns true for mushrooms: the small ones plus the cap/stem blocks of huge
     * mushrooms. Mushrooms belong to none of the vegetation tags checked above
     * (vanilla keeps them in their own {@code replaceable_by_mushrooms} tag), so
     * without this they survive {@link #clearVegetation} and are instead destroyed
     * later by the shape updates that terrain reshaping sends out. That drops
     * mushroom items which {@link #removeDroppedItems} cannot reliably sweep up:
     * during server start the surrounding chunks are not tracked yet, and item
     * entities in untracked chunks are invisible to the entity query, so they are
     * left floating next to the finished house.
     */
    private static boolean isMushroom(BlockState state) {
        return state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM);
    }

    /**
     * Searches outward from the spawn point for a build center that is not sitting
     * over a large void (e.g. a dripstone cave), where terrain blending would
     * produce a broken, spike-covered foundation. Returns the spawn point itself
     * when it is already suitable, or falls back to it when no better spot is found.
     * The starter house defines the world spawn afterward, so relocating it stays
     * transparent to the player.
     */
    private static BlockPos findSuitableCenter(ServerLevel level, BlockPos spawnPos,
                                                net.minecraft.core.Vec3i size) {
        if (isCenterSuitable(level, spawnPos, size)) {
            return spawnPos;
        }
        int step = 8;
        int maxRadius = 96;
        for (int r = step; r <= maxRadius; r += step) {
            int[][] offsets = {
                    {r, 0}, {-r, 0}, {0, r}, {0, -r},
                    {r, r}, {r, -r}, {-r, r}, {-r, -r}
            };
            for (int[] o : offsets) {
                BlockPos candidate = spawnPos.offset(o[0], 0, o[1]);
                if (isCenterSuitable(level, candidate, size)) {
                    BeginnersDelight.LOGGER.info(
                            "Relocated starter house from {} to {} to avoid a void/cave below spawn",
                            spawnPos, candidate);
                    return candidate;
                }
            }
        }
        BeginnersDelight.LOGGER.warn(
                "No void-free location found near spawn {}; building starter house there anyway",
                spawnPos);
        return spawnPos;
    }

    /**
     * Checks whether a build center has solid ground (no large void below) across
     * the whole footprint, and is not so uneven that filling up to its highest
     * point would need to bridge a cliff, ravine, or cave mouth.
     */
    private static boolean isCenterSuitable(ServerLevel level, BlockPos center,
                                             net.minecraft.core.Vec3i size) {
        // Same margin fillFoundation reshapes beyond the footprint: a site whose
        // footprint alone is flat but whose immediate surroundings drop away
        // sharply (the flat top of a narrow spire) must be rejected too, or
        // fillFoundation turns that unconstrained margin into an unnaturally
        // tall vertical pillar instead of bridging a natural slope.
        int margin = 2;
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;
        int startX = center.getX() - halfX - margin;
        int startZ = center.getZ() - halfZ - margin;
        return scanFootprintHeights(level, startX, startZ,
                size.getX() + margin * 2, size.getZ() + margin * 2) != null;
    }

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
                                               net.minecraft.core.Vec3i size, int floorY) {
        // Same reach as fillFoundation's margin plus blendSurroundingTerrain's radius.
        int extend = 2 + 3;
        int minX = startX - extend;
        int maxX = startX + size.getX() + extend;
        int minZ = startZ - extend;
        int maxZ = startZ + size.getZ() + extend;

        // Water sitting well above the floor rests on terrain the blending never cuts
        // into, so it cannot reach the house; a few blocks of headroom is enough.
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
                Set.of(), player.getYRot(), player.getXRot(), false);
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
        if (newPlayer.getRespawnConfig() != null) {
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
                Set.of(), newPlayer.getYRot(), newPlayer.getXRot(), false);
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
                    if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                        ResourceKey<LootTable> loot = pos.equals(primaryPos)
                                ? STARTER_HOUSE_LOOT : STARTER_HOUSE_SUPPLIES_LOOT;
                        container.setLootTable(loot, random.nextLong());
                        BeginnersDelight.LOGGER.debug("Assigned loot table to container at {}", pos);
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
                                                  net.minecraft.core.Vec3i structureSize) {
        BlockPos firstContainer = null;
        for (int x = placePos.getX(); x < placePos.getX() + structureSize.getX(); x++) {
            for (int y = placePos.getY(); y < placePos.getY() + structureSize.getY(); y++) {
                for (int z = placePos.getZ(); z < placePos.getZ() + structureSize.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (!(blockEntity instanceof RandomizableContainerBlockEntity)) {
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

    /**
     * Pre-clears vegetation and ground-cover blocks (grass, flowers, leaf litter, etc.)
     * in the area that will be modified by structure placement and terrain blending.
     * Uses UPDATE_KNOWN_SHAPE (flag 16) to suppress shape update propagation so that
     * removing one soft block does not cascade-break adjacent soft blocks into items.
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

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (isVegetation(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }

        clearTallPlants(level, placePos, structureSize);
    }

    /**
     * Removes tall plants (bamboo, sugar cane, ...) from the area the foundation
     * flattens. They are not covered by the loop above: they keep growing past the
     * scanned band, and the foundation fill later removes their bottom block with a
     * regular block update, which destroys the rest of the stalk with break sounds
     * and scattered drops. Clearing whole columns up front with UPDATE_KNOWN_SHAPE
     * avoids both. Plants outside the foundation area are left standing.
     */
    private static void clearTallPlants(ServerLevel level, BlockPos placePos,
                                         net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int minX = placePos.getX() - margin;
        int maxX = placePos.getX() + structureSize.getX() + margin;
        int minZ = placePos.getZ() - margin;
        int maxZ = placePos.getZ() + structureSize.getZ() + margin;

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY != -1) {
                    clearTallPlantColumn(level, x, z, groundY);
                }
            }
        }
    }

    /**
     * Removes the tall plant standing on the given ground level as a whole column.
     * Clearing runs bottom-up with UPDATE_KNOWN_SHAPE so no shape update reaches the
     * blocks above: they keep standing until the loop removes them. Taking only the
     * bottom block out instead would leave the rest unsupported, and it would collapse
     * a tick later with break sounds and drops that the cleanup pass no longer covers.
     */
    private static void clearTallPlantColumn(ServerLevel level, int x, int z, int groundY) {
        // Tallest plant handled here is bamboo at 16 blocks; leave headroom.
        int maxPlantHeight = 32;
        for (int y = groundY; y < groundY + maxPlantHeight; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!isNonGroundPlant(level.getBlockState(pos))) {
                break;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    private static boolean isVegetation(BlockState state) {
        return isVegetationExcludingThinCover(state) || isThinGroundCover(state);
    }

    private static boolean isVegetationExcludingThinCover(BlockState state) {
        return state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || isMushroom(state);
    }

    /**
     * Records the thin ground cover standing on every column that is about to be
     * reshaped, so {@link #restoreGroundCover} can lay it back down afterwards.
     * {@link #clearVegetation} strips this cover and nothing used to put it back,
     * which left the house inside a sharply outlined bare rectangle -- glaring in
     * snowy biomes, where the surroundings are snow-covered right up to that edge.
     *
     * {@link #findGroundY} skips thin cover, so the block it points at is the cover
     * itself whenever a column has one.
     */
    private static Map<Long, BlockState> captureGroundCover(ServerLevel level, BlockPos placePos,
                                                             net.minecraft.core.Vec3i structureSize) {
        Map<Long, BlockState> cover = new HashMap<>();
        // Same reach as clearVegetation, which is what removes the cover.
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius + 1;

        int minX = placePos.getX() - extend;
        int maxX = placePos.getX() + structureSize.getX() + extend;
        int minZ = placePos.getZ() - extend;
        int maxZ = placePos.getZ() + structureSize.getZ() + extend;

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                int groundY = findGroundY(level, x, z);
                if (groundY == -1) {
                    continue;
                }
                BlockState state = level.getBlockState(new BlockPos(x, groundY, z));
                if (isThinGroundCover(state)) {
                    cover.put(columnKey(x, z), state);
                }
            }
        }
        return cover;
    }

    /**
     * Lays the recorded ground cover back onto the reshaped surface, one column at a
     * time. Columns that had no cover to begin with (bare ground under a tree, ...)
     * stay bare, so the result keeps the natural patchiness instead of turning into a
     * uniform slab of snow. Columns the building itself stands on get no cover back:
     * a freshly built house has nothing lying on it.
     */
    private static void restoreGroundCover(ServerLevel level, BlockPos placePos,
                                            net.minecraft.core.Vec3i structureSize,
                                            Map<Long, BlockState> cover) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius + 1;

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
                        placePos.getY(), structureSize.getY())) {
                    clearStaleSnowy(level, x, z, placePos.getY());
                    continue;
                }
                int groundY = findGroundY(level, x, z);
                if (groundY == -1) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, groundY, z);
                BlockState recorded = cover.get(columnKey(x, z));
                boolean covered = recorded != null
                        && level.getBlockState(pos).isAir()
                        && recorded.canSurvive(level, pos);
                if (covered) {
                    level.setBlock(pos, recorded, 2);
                }

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

    /**
     * Drops the leftover SNOWY flag from the ground of a column the building stands
     * on. The snow that made it white is gone for good there, and on the sheltered
     * ground under a roof overhang -- the one part of such a column that stays
     * visible -- it would otherwise read as a white patch with nothing lying on it.
     * Vanilla leaves that ground green too: a village generates before the top layer
     * is frozen, so no snow ever reaches under the eaves.
     */
    private static void clearStaleSnowy(ServerLevel level, int x, int z, int floorY) {
        // The reshaped ground inside the footprint sits at floorY - 1; scan a little
        // deeper only to skip over any air the template left below the floor.
        for (int y = floorY - 1; y >= floorY - 3; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.hasProperty(BlockStateProperties.SNOWY)
                    && state.getValue(BlockStateProperties.SNOWY)
                    && !isSnowCover(level.getBlockState(pos.above()))) {
                level.setBlock(pos, state.setValue(BlockStateProperties.SNOWY, false), 2);
            }
            return;
        }
    }

    /**
     * Returns true when the building itself stands on this column, i.e. the template
     * put a block somewhere in the column's structure band. Open ground inside the
     * template's bounding box -- the yard beside an L-shaped house, for instance --
     * is not part of the building and keeps its ground cover like the rest of the
     * surroundings. Ground under a roof overhang counts as built on and stays bare,
     * which is also what a sheltered patch looks like naturally.
     */
    private static boolean isStructureColumn(ServerLevel level, int x, int z,
                                              int floorY, int height) {
        for (int y = floorY; y < floorY + height; y++) {
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true for the blocks that make the ground below them render snowed over,
     * matching vanilla's SnowyDirtBlock.
     */
    private static boolean isSnowCover(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    /**
     * Packs an XZ column coordinate into a single map key.
     */
    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /**
     * Returns true if the block is a thin ground cover that should be ignored
     * when determining the ground surface (snow layers, moss carpet, etc.).
     */
    private static boolean isThinGroundCover(BlockState state) {
        return state.is(Blocks.SNOW)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.PINK_PETALS)
                || state.is(Blocks.PALE_MOSS_CARPET);
    }

    /**
     * Removes mobs (animals, monsters, etc.) from the structure area before
     * placement to prevent them from being trapped inside blocks.
     * Uses discard() to remove silently without drops or death effects.
     */
    private static void removeMobs(ServerLevel level, BlockPos placePos,
                                    net.minecraft.core.Vec3i structureSize) {
        int margin = 2;
        int blendRadius = 3;
        int extend = margin + blendRadius + 1;
        AABB area = new AABB(
                placePos.getX() - extend, placePos.getY() - 10, placePos.getZ() - extend,
                placePos.getX() + structureSize.getX() + extend,
                placePos.getY() + structureSize.getY() + 10,
                placePos.getZ() + structureSize.getZ() + extend);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area);
        for (Mob mob : mobs) {
            mob.discard();
        }
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
                    // Ground cover (snow layers, grass, flowers, ...) never gets cleared
                    // above: clearVegetation and Phase 1 only scan from floorY up, so cover
                    // sitting below floorY survives untouched. Treat it as fillable instead
                    // of solid, or it stops the loop one block short and leaves a
                    // sub-block-height layer standing in as the floor, with the house wall
                    // starting a whole block above it.
                    if (!existing.isAir() && existing.getFluidState().isEmpty()
                            && !isFillableShortCover(existing)) {
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
            if (isNonGroundPlant(state)
                    || isMushroom(state)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || isThinGroundCover(state)) {
                continue;
            }
            counts.merge(state.getBlock(), 1, Integer::sum);
            return;
        }
    }

    /**
     * Smooths the terrain around the structure so the transition between the
     * flat foundation and the natural terrain is gradual rather than abrupt.
     * For each block in a band outside the foundation, the target height is
     * linearly interpolated between the floor level and the natural ground
     * level based on distance from the foundation edge.
     */
    private static void blendSurroundingTerrain(ServerLevel level, BlockPos placePos,
                                                  net.minecraft.core.Vec3i structureSize) {
        int floorY = placePos.getY();
        int margin = 2;
        int blendRadius = 3;

        BlockState dominantBlock = detectDominantSurfaceBlock(level, placePos, structureSize, margin);
        BlockState surfaceBlock = mapToSurfaceBlock(dominantBlock);
        BlockState subsurfaceBlock = mapToSubsurfaceBlock(surfaceBlock);

        // Inner edge = boundary of the foundation fill area
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
                // Skip blocks inside the foundation area
                if (x >= innerMinX && x <= innerMaxX && z >= innerMinZ && z <= innerMaxZ) {
                    continue;
                }

                // Distance from the foundation edge (Chebyshev distance)
                int distX = 0;
                if (x < innerMinX) distX = innerMinX - x;
                else if (x > innerMaxX) distX = x - innerMaxX;

                int distZ = 0;
                if (z < innerMinZ) distZ = innerMinZ - z;
                else if (z > innerMaxZ) distZ = z - innerMaxZ;

                // Use Euclidean distance at corners, but normalize to maxDist
                // to ensure consistent circular blend radius at corners and edges
                double dist;
                if (distX > 0 && distZ > 0) {
                    int maxDist = Math.max(distX, distZ);
                    double euclideanDist = Math.sqrt((double) distX * distX + (double) distZ * distZ);
                    // Normalize to maxDist, then scale by maxDist to get consistent blending
                    dist = (euclideanDist / maxDist) * maxDist;
                } else {
                    dist = Math.max(distX, distZ);
                }
                if (dist <= 0 || dist > blendRadius) continue;

                int naturalY = findGroundY(level, x, z);
                if (naturalY == -1) continue;

                // Linear interpolation: dist=1 is close to floorY, dist=blendRadius is naturalY
                double ratio = (double) dist / blendRadius;
                int targetY = floorY + (int) Math.round((naturalY - floorY) * ratio);

                if (naturalY > targetY) {
                    // Take down any tall plant standing here first: carving the
                    // ground from under it would leave the stalk hanging in the air.
                    clearTallPlantColumn(level, x, z, naturalY);
                    // Terrain higher than target: carve down to create a slope
                    for (int y = targetY; y < naturalY; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    // Place surface block at new ground level
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
                    // Terrain lower than target: fill up to create a slope
                    for (int y = naturalY; y < targetY; y++) {
                        BlockState fill = (y == targetY - 1) ? surfaceBlock : subsurfaceBlock;
                        level.setBlock(new BlockPos(x, y, z), fill, 2);
                    }
                }
            }
        }
    }

    /**
     * Blends corner pillars that were skipped by isOutsideChamfer in fillFoundation.
     * After fillFoundation, the corners may still be at their natural height --
     * above the target (a pillar to carve down) or below it (a gap to fill in),
     * since the floor no longer sits at the lowest point of the footprint.
     */
    private static void blendCornerPillars(ServerLevel level, BlockPos placePos,
                                           net.minecraft.core.Vec3i structureSize) {
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
                        if (targetY > level.getMinY()) {
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
                                               net.minecraft.core.Vec3i structureSize) {
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
