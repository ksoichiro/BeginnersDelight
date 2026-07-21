package com.beginnersdelight.worldgen;

import net.minecraft.world.level.gamerules.GameRule;

/**
 * Holds the mod's custom game rule keys. The {@link net.minecraft.world.level.gamerules.GameRule}
 * is created by each loader's initializer (Fabric/NeoForge/Forge) and stored here so the
 * version-agnostic generation code can read it. Kept as a mutable static because game rule
 * registration is loader-specific and happens once at mod init before any world loads.
 */
public final class ModGameRules {

    /** Vanilla game rules share one global namespace, so this is prefixed with the mod name. */
    public static final String RULE_NAME = "beginnersDelightGenerateStarterHouse";

    /** Assigned by the loader initializer after registering the rule. */
    public static GameRule<Boolean> GENERATE_STARTER_HOUSE;

    private ModGameRules() {
    }
}
