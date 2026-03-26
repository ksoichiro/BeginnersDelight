package com.beginnersdelight.village;

import java.util.Objects;

/**
 * Represents a position on the village grid (not world coordinates).
 */
public class GridPos {

    private final int x;
    private final int z;

    public GridPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GridPos)) return false;
        GridPos gridPos = (GridPos) o;
        return x == gridPos.x && z == gridPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "GridPos[x=" + x + ", z=" + z + "]";
    }
}
