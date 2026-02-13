package com.beginnersdelight.forge;

import com.beginnersdelight.BeginnersDelight;
import net.minecraftforge.fml.common.Mod;

@Mod(BeginnersDelight.MOD_ID)
public class BeginnersDelightForge {
    public BeginnersDelightForge() {
        BeginnersDelight.init();
        BeginnersDelight.LOGGER.info("Beginner's Delight (Forge) initialized");
    }
}
