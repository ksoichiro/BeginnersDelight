package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core road generation logic for village mode.
 * Handles bootstrap (first segment), growth (extending tips),
 * branching, dead-end handling, and road block placement.
 */
public class VillageRoadGenerator {

    private static final int MIN_SEGMENT_LENGTH = 8;
    private static final int MAX_SEGMENT_LENGTH = 15;
    private static final int PROBE_DISTANCE = 12;
    private static final int MIN_SCORE_THRESHOLD = 20;

    /**
     * 8 directions: N, NE, E, SE, S, SW, W, NW
     */
    private static final int[][] DIRECTIONS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    /**
     * Creates the first road segment from the village center.
     * Called once when village is first enabled and a house is requested.
     */
    public static RoadSegment bootstrap(ServerLevel level, VillageData data, BlockPos center) {
        RandomSource random = level.getRandom();

        // Score all 8 directions and pick the best
        int bestScore = Integer.MIN_VALUE;
        int bestIdx = 0;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int score = scoreDirection(level, center, DIRECTIONS[i][0], DIRECTIONS[i][1], PROBE_DISTANCE, data);
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }

        int dx = DIRECTIONS[bestIdx][0];
        int dz = DIRECTIONS[bestIdx][1];
        int length = MIN_SEGMENT_LENGTH + random.nextInt(MAX_SEGMENT_LENGTH - MIN_SEGMENT_LENGTH + 1);

        int endX = center.getX() + dx * length;
        int endZ = center.getZ() + dz * length;
        int endY = findGroundY(level, endX, endZ);
        if (endY == -1) {
            endY = center.getY();
        }
        BlockPos endPos = new BlockPos(endX, endY, endZ);

        int segmentId = data.allocateSegmentId();
        RoadSegment segment = new RoadSegment(segmentId, center, endPos, List.of());
        data.addRoad(segment);

        placeRoadBlocks(level, segment);

        BeginnersDelight.LOGGER.info("Bootstrap road segment {} from {} to {}", segmentId, center, endPos);
        return segment;
    }

    /**
     * Grows the road network by adding a new segment from a tip.
     * Returns the new segment, or empty if no growth is possible.
     */
    public static Optional<RoadSegment> grow(ServerLevel level, VillageData data) {
        RandomSource random = level.getRandom();
        List<RoadSegment> tips = data.getTipSegments();

        if (tips.isEmpty()) {
            BeginnersDelight.LOGGER.warn("No tip segments available for road growth");
            return Optional.empty();
        }

        // Try all tips (starting from a random one) before giving up
        List<RoadSegment> shuffledTips = new ArrayList<>(tips);
        // Simple Fisher-Yates shuffle
        for (int i = shuffledTips.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            RoadSegment tmp = shuffledTips.get(j);
            shuffledTips.set(j, shuffledTips.get(i));
            shuffledTips.set(i, tmp);
        }

        for (RoadSegment tip : shuffledTips) {
            Optional<RoadSegment> result = tryGrowFromTip(level, data, tip, random);
            if (result.isPresent()) {
                return result;
            }
        }

        // All tips are dead ends — try branching from midpoint of existing segments
        List<RoadSegment> allRoads = data.getAllRoads();
        for (int i = allRoads.size() - 1; i >= 0; i--) {
            RoadSegment segment = allRoads.get(i);
            Optional<RoadSegment> result = tryGrowFromMidpoint(level, data, segment, random);
            if (result.isPresent()) {
                return result;
            }
        }

        BeginnersDelight.LOGGER.warn("No valid growth direction found from any segment");
        return Optional.empty();
    }

    /**
     * Places Dirt Path blocks along a road segment (2-block width).
     */
    public static void placeRoadBlocks(ServerLevel level, RoadSegment segment) {
        BlockPos start = segment.getStart();
        BlockPos end = segment.getEnd();

        int totalDx = end.getX() - start.getX();
        int totalDz = end.getZ() - start.getZ();

        // Normalize to get direction components (-1, 0, or 1)
        int dx = Integer.signum(totalDx);
        int dz = Integer.signum(totalDz);

        int steps = Math.max(Math.abs(totalDx), Math.abs(totalDz));
        if (steps == 0) return;

        for (int i = 0; i <= steps; i++) {
            // Interpolate position along the segment
            int x = start.getX() + (int) Math.round((double) totalDx * i / steps);
            int z = start.getZ() + (int) Math.round((double) totalDz * i / steps);
            VillagePathGenerator.placePathBlockWide(level, x, z, dx, dz);
        }
    }

    // --- Private methods ---

    private static Optional<RoadSegment> tryGrowFromTip(ServerLevel level, VillageData data,
                                                         RoadSegment tip, RandomSource random) {
        BlockPos from = tip.getEnd();
        return tryGrowFrom(level, data, tip, from, random);
    }

    private static Optional<RoadSegment> tryGrowFromMidpoint(ServerLevel level, VillageData data,
                                                              RoadSegment segment, RandomSource random) {
        BlockPos start = segment.getStart();
        BlockPos end = segment.getEnd();
        BlockPos midpoint = new BlockPos(
                (start.getX() + end.getX()) / 2,
                (start.getY() + end.getY()) / 2,
                (start.getZ() + end.getZ()) / 2
        );
        return tryGrowFrom(level, data, segment, midpoint, random);
    }

    private static Optional<RoadSegment> tryGrowFrom(ServerLevel level, VillageData data,
                                                      RoadSegment parent, BlockPos from,
                                                      RandomSource random) {
        // Score all 8 directions
        int[] scores = new int[DIRECTIONS.length];
        int maxScore = Integer.MIN_VALUE;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            scores[i] = scoreDirection(level, from, DIRECTIONS[i][0], DIRECTIONS[i][1], PROBE_DISTANCE, data);
            if (scores[i] > maxScore) {
                maxScore = scores[i];
            }
        }

        // Filter directions with score above threshold
        if (maxScore < MIN_SCORE_THRESHOLD) {
            return Optional.empty();
        }

        // Weighted random selection from directions with positive scores
        int selectedIdx = selectDirectionWeighted(scores, random);
        if (selectedIdx == -1) {
            return Optional.empty();
        }

        // Generate the new segment
        int dx = DIRECTIONS[selectedIdx][0];
        int dz = DIRECTIONS[selectedIdx][1];
        int length = MIN_SEGMENT_LENGTH + random.nextInt(MAX_SEGMENT_LENGTH - MIN_SEGMENT_LENGTH + 1);

        int endX = from.getX() + dx * length;
        int endZ = from.getZ() + dz * length;
        int endY = findGroundY(level, endX, endZ);
        if (endY == -1) {
            endY = from.getY();
        }
        BlockPos endPos = new BlockPos(endX, endY, endZ);

        int segmentId = data.allocateSegmentId();
        RoadSegment newSegment = new RoadSegment(segmentId, from, endPos, List.of());
        data.addRoad(newSegment);
        parent.addChild(segmentId);
        data.setDirty();

        placeRoadBlocks(level, newSegment);

        BeginnersDelight.LOGGER.info("Grew road segment {} from {} to {}", segmentId, from, endPos);

        // Check for branch opportunity: if 2+ directions score similarly high,
        // add a branch with some probability
        tryBranch(level, newSegment, data, scores, selectedIdx, from, random);

        return Optional.of(newSegment);
    }

    /**
     * Selects a direction by weighted random from scored directions.
     * Only considers directions with positive scores.
     * Returns -1 if no direction has a positive score.
     */
    private static int selectDirectionWeighted(int[] scores, RandomSource random) {
        // Shift scores so all positive ones become weights
        int minPositive = Integer.MAX_VALUE;
        boolean hasPositive = false;
        for (int score : scores) {
            if (score > 0) {
                hasPositive = true;
                minPositive = Math.min(minPositive, score);
            }
        }
        if (!hasPositive) return -1;

        // Use scores directly as weights (only positive ones)
        int totalWeight = 0;
        for (int score : scores) {
            if (score > 0) {
                totalWeight += score;
            }
        }
        if (totalWeight <= 0) return -1;

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > 0) {
                cumulative += scores[i];
                if (roll < cumulative) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Checks if a branch should be added when multiple directions score similarly high.
     */
    private static void tryBranch(ServerLevel level, RoadSegment parentSegment, VillageData data,
                                   int[] scores, int selectedIdx, BlockPos from, RandomSource random) {
        int selectedScore = scores[selectedIdx];
        // Threshold: within 80% of selected score
        int branchThreshold = (int) (selectedScore * 0.8);

        int highScoreCount = 0;
        int bestBranchIdx = -1;
        int bestBranchScore = Integer.MIN_VALUE;
        for (int i = 0; i < scores.length; i++) {
            if (i == selectedIdx) continue;
            if (scores[i] >= branchThreshold) {
                highScoreCount++;
                if (scores[i] > bestBranchScore) {
                    bestBranchScore = scores[i];
                    bestBranchIdx = i;
                }
            }
        }

        // Branch if 2+ other directions scored high, with 40% probability
        if (highScoreCount >= 2 && bestBranchIdx != -1 && random.nextFloat() < 0.4f) {
            int dx = DIRECTIONS[bestBranchIdx][0];
            int dz = DIRECTIONS[bestBranchIdx][1];
            int length = MIN_SEGMENT_LENGTH + random.nextInt(MAX_SEGMENT_LENGTH - MIN_SEGMENT_LENGTH + 1);

            int endX = from.getX() + dx * length;
            int endZ = from.getZ() + dz * length;
            int endY = findGroundY(level, endX, endZ);
            if (endY == -1) {
                endY = from.getY();
            }
            BlockPos endPos = new BlockPos(endX, endY, endZ);

            int branchId = data.allocateSegmentId();
            RoadSegment branch = new RoadSegment(branchId, from, endPos, List.of());
            data.addRoad(branch);
            parentSegment.addChild(branchId);
            data.setDirty();

            placeRoadBlocks(level, branch);

            BeginnersDelight.LOGGER.info("Added branch segment {} from {} to {}", branchId, from, endPos);
        }
    }

    /**
     * Scores a direction for road growth.
     * Probes ahead probeDistance blocks and evaluates terrain suitability.
     */
    private static int scoreDirection(ServerLevel level, BlockPos from, int dx, int dz,
                                       int probeDistance, VillageData data) {
        int score = 0;
        int prevY = from.getY();
        BlockPos centerPos = data.getCenterPos();

        for (int step = 1; step <= probeDistance; step++) {
            int probeX = from.getX() + dx * step;
            int probeZ = from.getZ() + dz * step;
            int probeY = findGroundY(level, probeX, probeZ);

            if (probeY == -1) {
                // No ground found — heavily penalize
                score -= 20;
                continue;
            }

            // Check height difference from previous step
            int heightDiff = Math.abs(probeY - prevY);
            if (heightDiff <= 1) {
                score += 10;
            } else if (heightDiff <= 2) {
                score += 3;
            } else {
                // Steep terrain — penalize
                score -= 10;
            }

            // Check for water/lava
            BlockState state = level.getBlockState(new BlockPos(probeX, probeY - 1, probeZ));
            if (!state.getFluidState().isEmpty()) {
                score -= 20;
            }

            // Check for overlap with existing roads or buildings
            if (overlapsExisting(data, probeX, probeZ)) {
                score -= 30;
            }

            // Minor bonus for being within 100 blocks of center
            if (centerPos != null) {
                double dist = Math.sqrt(
                        Math.pow(probeX - centerPos.getX(), 2) +
                        Math.pow(probeZ - centerPos.getZ(), 2));
                if (dist <= 100) {
                    score += 2;
                }
            }

            prevY = probeY;
        }

        return score;
    }

    /**
     * Checks whether a position overlaps with existing road segments or building plots.
     */
    private static boolean overlapsExisting(VillageData data, int x, int z) {
        // Check road segments
        for (RoadSegment road : data.getAllRoads()) {
            if (isNearSegment(road, x, z, 3)) {
                return true;
            }
        }
        // Check building plots
        for (VillagePlot plot : data.getAllPlots()) {
            BlockPos pos = plot.getPosition();
            // Approximate building footprint check (15x15 collision rectangle for houses)
            int margin = 8;
            if (Math.abs(x - pos.getX()) <= margin && Math.abs(z - pos.getZ()) <= margin) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a point is near a road segment (within the given margin).
     */
    private static boolean isNearSegment(RoadSegment segment, int x, int z, int margin) {
        BlockPos start = segment.getStart();
        BlockPos end = segment.getEnd();

        // Project point onto the segment line and check distance
        int sx = start.getX(), sz = start.getZ();
        int ex = end.getX(), ez = end.getZ();
        int dx = ex - sx, dz = ez - sz;
        int lengthSq = dx * dx + dz * dz;

        if (lengthSq == 0) {
            // Zero-length segment
            return Math.abs(x - sx) <= margin && Math.abs(z - sz) <= margin;
        }

        // Clamp t to [0, 1] to stay within segment bounds
        double t = (double) ((x - sx) * dx + (z - sz) * dz) / lengthSq;
        t = Math.max(0, Math.min(1, t));

        double closestX = sx + t * dx;
        double closestZ = sz + t * dz;

        double dist = Math.sqrt(Math.pow(x - closestX, 2) + Math.pow(z - closestZ, 2));
        return dist <= margin;
    }

    /**
     * Finds the ground Y at the given XZ coordinates.
     * Scans downward, skipping vegetation and fluids.
     * Returns the Y position one above the ground block, or -1 if not found.
     */
    private static int findGroundY(ServerLevel level, int x, int z) {
        int maxY = level.getHeight() - 1;
        int minY = level.getMinY();
        for (int y = maxY; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SHORT_GRASS)
                    || state.is(BlockTags.REPLACEABLE_BY_TREES)
                    || state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)
                    || state.is(Blocks.PINK_PETALS) || state.is(Blocks.PALE_MOSS_CARPET)) {
                continue;
            }
            return y + 1;
        }
        return -1;
    }
}
