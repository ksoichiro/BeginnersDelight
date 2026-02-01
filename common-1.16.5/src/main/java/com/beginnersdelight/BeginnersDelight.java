package com.beginnersdelight;

import com.beginnersdelight.worldgen.StarterHouseGenerator;
import me.shedaniel.architectury.event.events.LifecycleEvent;
import me.shedaniel.architectury.event.events.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BeginnersDelight {
    public static final String MOD_ID = "beginnersdelight";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

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
