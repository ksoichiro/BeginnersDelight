package com.beginnersdelight.util;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MC 26.1+ stores SavedData at data/&lt;namespace&gt;/&lt;path&gt;.dat, while this mod on
 * older Minecraft versions (&lt;= 1.21.11) used a flat data/&lt;legacyName&gt;.dat file.
 * When a world is upgraded to 26.1+, the old file would be ignored and the mod
 * would regenerate the starter house/village into the existing world. This helper
 * copies the legacy file to the new location once to preserve that state.
 */
public final class SavedDataMigration {

    private SavedDataMigration() {
    }

    public static void migrateLegacyFile(ServerLevel level, String legacyName, SavedDataType<?> type) {
        Path dataDir = level.getServer().getWorldPath(LevelResource.DATA);
        Path legacyFile = dataDir.resolve(legacyName + ".dat");
        Path modernFile = dataDir.resolve(type.id().getNamespace()).resolve(type.id().getPath() + ".dat");
        if (!Files.exists(legacyFile) || Files.exists(modernFile)) {
            return;
        }
        try {
            Files.createDirectories(modernFile.getParent());
            Files.copy(legacyFile, modernFile);
            BeginnersDelight.LOGGER.info("Migrated legacy saved data {} to {}", legacyFile, modernFile);
        } catch (IOException e) {
            BeginnersDelight.LOGGER.warn("Failed to migrate legacy saved data {}", legacyFile, e);
        }
    }
}
