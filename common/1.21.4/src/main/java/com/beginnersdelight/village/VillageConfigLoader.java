package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads {@code beginnersdelight.toml} from the loader-provided config directory.
 *
 * <p>If the file is missing, the bundled commented default is materialised to disk on
 * first run. Invalid values fall back to defaults per-field (the rest of the config still
 * loads). Unknown top-level keys are logged but otherwise ignored. The config is read at
 * server start and can be re-read at runtime via {@code /beginnersdelight config reload}.
 *
 * <p>This class is loader- and version-agnostic: it uses only night-config (bundled into
 * the Fabric jar, provided at runtime by NeoForge/Forge) and {@link BeginnersDelight#LOGGER},
 * so the same source compiles on Java 8 (1.16.5) through Java 21.
 */
public final class VillageConfigLoader {

    private static final String CONFIG_FILE_NAME = "beginnersdelight.toml";
    private static final String BUNDLED_DEFAULT_RESOURCE = "/beginnersdelight-default-config.toml";

    private static final String K_SCHEMA_VERSION = "schema_version";
    private static final String K_VILLAGE = "village";
    private static final String K_PLOT_SIZE = "plot_size";
    private static final String K_MAX_HEIGHT_DIFFERENCE = "max_height_difference";
    private static final String K_GENERATE_PATHS = "generate_paths";
    private static final String K_RESPAWN_AT_HOUSE = "respawn_at_house";

    private static final int MIN_PLOT_SIZE = 5;
    private static final int MAX_PLOT_SIZE = 256;
    private static final int MIN_HEIGHT_DIFFERENCE = 0;
    private static final int MAX_HEIGHT_DIFFERENCE = 256;

    private VillageConfigLoader() {
    }

    /**
     * Read {@code <configDir>/beginnersdelight.toml}, creating it from the bundled default
     * if missing. Returns the parsed (and validated) config, or built-in defaults on failure.
     */
    public static VillageConfig load(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);
        try {
            ensureFileExists(configFile);
        } catch (IOException e) {
            BeginnersDelight.LOGGER.error("Failed to write default village config at {}; using built-in defaults", configFile, e);
            return VillageConfigDefaults.defaults();
        }
        return parseOrDefaults(configFile);
    }

    private static void ensureFileExists(Path configFile) throws IOException {
        if (Files.exists(configFile)) {
            return;
        }
        Files.createDirectories(configFile.getParent());
        try (InputStream in = VillageConfigLoader.class.getResourceAsStream(BUNDLED_DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled default config resource not found: " + BUNDLED_DEFAULT_RESOURCE);
            }
            Files.copy(in, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
        BeginnersDelight.LOGGER.info("Wrote default village config to {}", configFile);
    }

    private static VillageConfig parseOrDefaults(Path configFile) {
        CommentedConfig parsed;
        try (InputStream in = Files.newInputStream(configFile)) {
            parsed = new TomlParser().parse(in);
        } catch (IOException e) {
            BeginnersDelight.LOGGER.error("Failed to read {}; using built-in defaults", configFile, e);
            return VillageConfigDefaults.defaults();
        } catch (RuntimeException e) {
            // night-config throws ParsingException (RuntimeException) on malformed TOML
            BeginnersDelight.LOGGER.error("Failed to parse {} (is the TOML well-formed?); using built-in defaults", configFile, e);
            return VillageConfigDefaults.defaults();
        }

        int schemaVersion = readInt(parsed, K_SCHEMA_VERSION, VillageConfigDefaults.CURRENT_SCHEMA_VERSION,
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (schemaVersion > VillageConfigDefaults.CURRENT_SCHEMA_VERSION) {
            BeginnersDelight.LOGGER.warn("{} declares schema_version={} but this build only knows up to {}; reading what we can",
                    CONFIG_FILE_NAME, schemaVersion, VillageConfigDefaults.CURRENT_SCHEMA_VERSION);
        }

        int plotSize = readInt(parsed, K_VILLAGE + "." + K_PLOT_SIZE,
                VillageConfigDefaults.PLOT_SIZE, MIN_PLOT_SIZE, MAX_PLOT_SIZE);
        int maxHeightDifference = readInt(parsed, K_VILLAGE + "." + K_MAX_HEIGHT_DIFFERENCE,
                VillageConfigDefaults.MAX_HEIGHT_DIFFERENCE, MIN_HEIGHT_DIFFERENCE, MAX_HEIGHT_DIFFERENCE);
        boolean generatePaths = readBoolean(parsed, K_VILLAGE + "." + K_GENERATE_PATHS,
                VillageConfigDefaults.GENERATE_PATHS);
        boolean respawnAtHouse = readBoolean(parsed, K_VILLAGE + "." + K_RESPAWN_AT_HOUSE,
                VillageConfigDefaults.RESPAWN_AT_HOUSE);

        // Surface unknown top-level keys at WARN; the most common mistake is misspelling at top level.
        for (CommentedConfig.Entry entry : parsed.entrySet()) {
            String key = entry.getKey();
            if (!key.equals(K_SCHEMA_VERSION) && !key.equals(K_VILLAGE)) {
                BeginnersDelight.LOGGER.warn("Unknown top-level key in {}: {}", CONFIG_FILE_NAME, key);
            }
        }

        return new VillageConfig(plotSize, maxHeightDifference, generatePaths, respawnAtHouse);
    }

    private static boolean readBoolean(CommentedConfig parsed, String path, boolean defaultValue) {
        Object value = parsed.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        BeginnersDelight.LOGGER.error("Invalid {}.{} = {} (must be true or false); using default {}",
                CONFIG_FILE_NAME, path, value, defaultValue);
        return defaultValue;
    }

    private static int readInt(CommentedConfig parsed, String path, int defaultValue, int min, int max) {
        Object value = parsed.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            int intValue = ((Number) value).intValue();
            if (intValue >= min && intValue <= max) {
                return intValue;
            }
        }
        BeginnersDelight.LOGGER.error("Invalid {}.{} = {} (must be in [{}, {}]); using default {}",
                CONFIG_FILE_NAME, path, value, min, max, defaultValue);
        return defaultValue;
    }
}
