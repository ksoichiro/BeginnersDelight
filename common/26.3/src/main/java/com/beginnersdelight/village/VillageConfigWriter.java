package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;

import java.nio.file.Path;

/**
 * Writes {@link VillageConfig} values back to {@code beginnersdelight.toml}. Loads the
 * existing file first and only replaces values, so comments already in the file survive
 * (the file is not rewritten from scratch).
 */
public final class VillageConfigWriter {

    private static final String CONFIG_FILE_NAME = "beginnersdelight.toml";

    private VillageConfigWriter() {
    }

    public static boolean save(Path configDir, VillageConfig config) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);
        try (CommentedFileConfig fileConfig = CommentedFileConfig.builder(configFile, TomlFormat.instance()).build()) {
            fileConfig.load();
            fileConfig.set("schema_version", VillageConfigDefaults.CURRENT_SCHEMA_VERSION);
            fileConfig.set("village.plot_size", config.getPlotSize());
            fileConfig.set("village.max_height_difference", config.getMaxHeightDifference());
            fileConfig.set("village.generate_paths", config.isGeneratePaths());
            fileConfig.set("village.respawn_at_house", config.isRespawnAtHouse());
            fileConfig.set("starter_house.auto_generate", config.isAutoGenerateStarterHouse());
            fileConfig.save();
            return true;
        } catch (RuntimeException e) {
            BeginnersDelight.LOGGER.error("Failed to save {}", configFile, e);
            return false;
        }
    }
}
