package com.beginnersdelight;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeginnersDelight {
    public static final String MOD_ID = "beginnersdelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Beginner's Delight initializing");
    }
}
