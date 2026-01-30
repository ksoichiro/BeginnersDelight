package com.beginnersdelight.neoforge;

import com.beginnersdelight.BeginnersDelight;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightNeoForge {
    public BeginnersDelightNeoForge(IEventBus modEventBus) {
        BeginnersDelight.init();
        BeginnersDelight.LOGGER.info("Beginner's Delight (NeoForge) initialized");
    }
}
