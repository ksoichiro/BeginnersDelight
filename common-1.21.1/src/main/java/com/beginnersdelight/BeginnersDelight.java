package com.beginnersdelight;

import com.beginnersdelight.worldgen.StarterHouseGenerator;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeginnersDelight {
    public static final String MOD_ID = "beginnersdelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Beginner's Delight initializing");

        // Generate starter house when server finishes starting
        LifecycleEvent.SERVER_STARTED.register(StarterHouseGenerator::tryGenerate);

        // Teleport new players into the starter house on first join
        PlayerEvent.PLAYER_JOIN.register(StarterHouseGenerator::onPlayerJoin);

        // Teleport players back to the starter house on death respawn (no bed set)
        PlayerEvent.PLAYER_RESPAWN.register(StarterHouseGenerator::onPlayerRespawn);
    }
}

