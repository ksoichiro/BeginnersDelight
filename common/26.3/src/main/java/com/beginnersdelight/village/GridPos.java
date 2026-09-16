package com.beginnersdelight.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Represents a position on the village grid (not world coordinates).
 */
public record GridPos(int x, int z) {

    public static final Codec<GridPos> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("x").forGetter(GridPos::x),
                    Codec.INT.fieldOf("z").forGetter(GridPos::z)
            ).apply(instance, GridPos::new)
    );

    /**
     * Manhattan distance from origin.
     */
    public int manhattanDistance() {
        return Math.abs(x) + Math.abs(z);
    }

    /**
     * Manhattan distance to another grid position.
     */
    public int manhattanDistanceTo(GridPos other) {
        return Math.abs(x - other.x) + Math.abs(z - other.z);
    }
}
