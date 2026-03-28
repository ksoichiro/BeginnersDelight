package com.beginnersdelight.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one straight section of road from start to end point.
 */
public class RoadSegment {

    public static final Codec<RoadSegment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("id").forGetter(s -> s.id),
                    BlockPos.CODEC.fieldOf("start").forGetter(s -> s.start),
                    BlockPos.CODEC.fieldOf("end").forGetter(s -> s.end),
                    Codec.INT.listOf().fieldOf("children").forGetter(s -> s.childSegmentIds)
            ).apply(instance, RoadSegment::new)
    );

    private final int id;
    private final BlockPos start;
    private final BlockPos end;
    private final List<Integer> childSegmentIds;

    /**
     * Constructor always copies the input list to ensure mutability.
     * Codec deserialization may pass an unmodifiable list.
     */
    public RoadSegment(int id, BlockPos start, BlockPos end, List<Integer> childSegmentIds) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.childSegmentIds = new ArrayList<>(childSegmentIds);
    }

    public int getId() { return id; }
    public BlockPos getStart() { return start; }
    public BlockPos getEnd() { return end; }
    public List<Integer> getChildSegmentIds() { return childSegmentIds; }

    public boolean isTip() { return childSegmentIds.isEmpty(); }

    public void addChild(int childId) { childSegmentIds.add(childId); }
}
