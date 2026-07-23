package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.world.level.gamerules.GameRule;

/**
 * Holds the mod's custom game rule keys. The {@link net.minecraft.world.level.gamerules.GameRule}
 * is created by each loader's initializer (Fabric/NeoForge/Forge) and stored here so the
 * version-agnostic generation code can read it. Kept as a mutable static because game rule
 * registration is loader-specific and happens once at mod init before any world loads.
 */
public final class ModGameRules {

    // In MC 26.x game rules are registry entries keyed by an Identifier whose path must match
    // [a-z0-9/._-], so the name uses the mod's own namespace and a snake_case path (camelCase,
    // which older MC versions accepted, is rejected as an Identifier path and crashes at
    // registration). The same string doubles as a plain flat key on the older String-based
    // game-rule versions, keeping the /gamerule name identical across all versions.
    public static final String RULE_NAME = BeginnersDelight.MOD_ID + ":generate_starter_house";

    /** Assigned by the loader initializer after registering the rule. */
    public static GameRule<Boolean> GENERATE_STARTER_HOUSE;

    private ModGameRules() {
    }
}
