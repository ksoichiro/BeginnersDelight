package com.beginnersdelight.village;

/**
 * Built-in default values for {@link VillageConfig}.
 *
 * <p>Used when the config file is missing a key, holds an invalid value, or cannot be
 * read at all. Mirrors the values shipped in {@code beginnersdelight-default-config.toml}.
 */
public final class VillageConfigDefaults {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static final int PLOT_SIZE = 20;
    public static final int MAX_HEIGHT_DIFFERENCE = 10;
    public static final boolean GENERATE_PATHS = true;
    public static final boolean RESPAWN_AT_HOUSE = true;

    private VillageConfigDefaults() {
    }

    public static VillageConfig defaults() {
        return new VillageConfig(PLOT_SIZE, MAX_HEIGHT_DIFFERENCE, GENERATE_PATHS, RESPAWN_AT_HOUSE);
    }
}
