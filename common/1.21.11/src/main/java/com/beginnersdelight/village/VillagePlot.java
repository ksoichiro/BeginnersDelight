package com.beginnersdelight.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * Represents a placed building (house or decoration) along a road.
 */
public class VillagePlot {

    public static final Codec<VillagePlot> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("id").forGetter(p -> p.id),
                    BlockPos.CODEC.fieldOf("position").forGetter(p -> p.position),
                    BlockPos.CODEC.fieldOf("door_position").forGetter(p -> p.doorPosition),
                    Codec.STRING.fieldOf("type").forGetter(p -> p.type.name()),
                    Codec.INT.fieldOf("road_segment_id").forGetter(p -> p.roadSegmentId)
            ).apply(instance, (id, position, doorPosition, type, roadSegmentId) ->
                    new VillagePlot(id, position, doorPosition, PlotType.valueOf(type), roadSegmentId))
    );

    private final int id;
    private final BlockPos position;
    private final BlockPos doorPosition;
    private final PlotType type;
    private final int roadSegmentId;

    public VillagePlot(int id, BlockPos position, BlockPos doorPosition, PlotType type, int roadSegmentId) {
        this.id = id;
        this.position = position;
        this.doorPosition = doorPosition;
        this.type = type;
        this.roadSegmentId = roadSegmentId;
    }

    public int getId() { return id; }
    public BlockPos getPosition() { return position; }
    public BlockPos getDoorPosition() { return doorPosition; }
    public PlotType getType() { return type; }
    public int getRoadSegmentId() { return roadSegmentId; }
}
