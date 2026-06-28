package com.beginnersdelight.village;

/**
 * Immutable village mode configuration.
 *
 * <p>Built by {@link VillageConfigLoader} from {@code beginnersdelight.toml} and held by
 * {@link VillageManager}. The getter names are kept stable so existing call sites
 * ({@link VillageGrid}, {@link VillageCommand}) need no changes.
 */
public final class VillageConfig {

    private final int plotSize;
    private final int maxHeightDifference;
    private final boolean generatePaths;
    private final boolean respawnAtHouse;

    public VillageConfig(int plotSize, int maxHeightDifference, boolean generatePaths, boolean respawnAtHouse) {
        this.plotSize = plotSize;
        this.maxHeightDifference = maxHeightDifference;
        this.generatePaths = generatePaths;
        this.respawnAtHouse = respawnAtHouse;
    }

    public int getPlotSize() {
        return plotSize;
    }

    public int getMaxHeightDifference() {
        return maxHeightDifference;
    }

    public boolean isGeneratePaths() {
        return generatePaths;
    }

    public boolean isRespawnAtHouse() {
        return respawnAtHouse;
    }
}
