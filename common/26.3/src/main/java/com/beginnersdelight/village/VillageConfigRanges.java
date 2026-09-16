package com.beginnersdelight.village;

/**
 * Validation ranges for {@link VillageConfig} values, shared by {@link VillageConfigLoader}
 * and the in-game config screen so both enforce identical rules.
 */
public final class VillageConfigRanges {

    public static final int MIN_PLOT_SIZE = 5;
    public static final int MAX_PLOT_SIZE = 256;
    public static final int MIN_HEIGHT_DIFFERENCE = 0;
    public static final int MAX_HEIGHT_DIFFERENCE = 256;

    private VillageConfigRanges() {
    }
}
