package com.beginnersdelight.fabric;

import com.beginnersdelight.BeginnersDelight;
import net.fabricmc.api.ModInitializer;

public class BeginnersDelightFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BeginnersDelight.init();
        BeginnersDelight.LOGGER.info("Beginner's Delight (Fabric) initialized");
    }
}
