package com.beginnersdelight.village;

import com.beginnersdelight.BeginnersDelight;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages village mode configuration stored in a properties file.
 * Loaded at server startup; changes via command are written immediately.
 */
public class VillageConfig {

    private static final String FILE_NAME = "beginnersdelight-village.properties";

    private int plotSize = 20;
    private int maxHeightDifference = 10;
    private boolean generatePaths = true;
    private boolean respawnAtHouse = true;

    private Path configPath;

    public void load(Path configDir) {
        this.configPath = configDir.resolve(FILE_NAME);
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                Properties props = new Properties();
                props.load(in);
                plotSize = Integer.parseInt(props.getProperty("plotSize", "20"));
                maxHeightDifference = Integer.parseInt(props.getProperty("maxHeightDifference", "10"));
                generatePaths = Boolean.parseBoolean(props.getProperty("generatePaths", "true"));
                respawnAtHouse = Boolean.parseBoolean(props.getProperty("respawnAtHouse", "true"));
            } catch (IOException e) {
                BeginnersDelight.LOGGER.warn("Failed to load village config, using defaults", e);
            }
        } else {
            save();
        }
    }

    public void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            Properties props = new Properties();
            props.setProperty("plotSize", String.valueOf(plotSize));
            props.setProperty("maxHeightDifference", String.valueOf(maxHeightDifference));
            props.setProperty("generatePaths", String.valueOf(generatePaths));
            props.setProperty("respawnAtHouse", String.valueOf(respawnAtHouse));
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "Beginner's Delight - Village Mode Configuration");
            }
        } catch (IOException e) {
            BeginnersDelight.LOGGER.error("Failed to save village config", e);
        }
    }

    public boolean set(String key, String value) {
        switch (key) {
            case "plotSize" -> plotSize = Integer.parseInt(value);
            case "maxHeightDifference" -> maxHeightDifference = Integer.parseInt(value);
            case "generatePaths" -> generatePaths = Boolean.parseBoolean(value);
            case "respawnAtHouse" -> respawnAtHouse = Boolean.parseBoolean(value);
            default -> { return false; }
        }
        save();
        return true;
    }

    public int getPlotSize() { return plotSize; }
    public int getMaxHeightDifference() { return maxHeightDifference; }
    public boolean isGeneratePaths() { return generatePaths; }
    public boolean isRespawnAtHouse() { return respawnAtHouse; }
}
