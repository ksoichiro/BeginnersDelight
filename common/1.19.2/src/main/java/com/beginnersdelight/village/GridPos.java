package com.beginnersdelight.village;

/**
 * Represents a position on the village grid (not world coordinates).
 */
public record GridPos(int x, int z) {

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
