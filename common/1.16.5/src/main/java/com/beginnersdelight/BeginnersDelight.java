package com.beginnersdelight;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BeginnersDelight {
    public static final String MOD_ID = "beginnersdelight";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Beginner's Delight initializing");
    }
}
