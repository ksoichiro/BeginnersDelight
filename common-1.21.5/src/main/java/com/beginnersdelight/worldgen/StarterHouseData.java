package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persists whether the starter house has already been generated,
 * preventing regeneration on subsequent world loads.
 * Also stores the interior spawn position for teleporting new players.
 */
public class StarterHouseData extends SavedData {

    private static final String DATA_NAME = BeginnersDelight.MOD_ID + "_starter_house";

    public static final Codec<StarterHouseData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("generated").forGetter(d -> d.generated),
                    BlockPos.CODEC.optionalFieldOf("spawn_pos").forGetter(d -> Optional.ofNullable(d.spawnPos)),
                    UUIDUtil.CODEC.listOf().fieldOf("teleported_players").forGetter(d -> new ArrayList<>(d.teleportedPlayers))
            ).apply(instance, (generated, spawnPos, teleportedPlayers) -> {
                StarterHouseData data = new StarterHouseData();
                data.generated = generated;
                data.spawnPos = spawnPos.orElse(null);
                data.teleportedPlayers.addAll(teleportedPlayers);
                return data;
            })
    );

    public static final SavedDataType<StarterHouseData> TYPE = new SavedDataType<>(
            DATA_NAME,
            StarterHouseData::new,
            CODEC,
            null
    );

    private boolean generated;
    private BlockPos spawnPos;
    private final Set<UUID> teleportedPlayers = new HashSet<>();

    public StarterHouseData() {
        this.generated = false;
        this.spawnPos = null;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
        setDirty();
    }

    public BlockPos getSpawnPos() {
        return spawnPos;
    }

    public void setSpawnPos(BlockPos spawnPos) {
        this.spawnPos = spawnPos;
        setDirty();
    }

    public boolean hasBeenTeleported(UUID playerUuid) {
        return teleportedPlayers.contains(playerUuid);
    }

    public void markTeleported(UUID playerUuid) {
        teleportedPlayers.add(playerUuid);
        setDirty();
    }

    public static StarterHouseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
