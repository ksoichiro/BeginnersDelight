package com.beginnersdelight.worldgen;

import com.beginnersdelight.BeginnersDelight;
import net.minecraft.world.level.GameRules;

/**
 * Holds the mod's custom game rule keys. The {@link net.minecraft.world.level.GameRules.Key}
 * is created by each loader's initializer (Fabric/NeoForge/Forge) and stored here so the
 * version-agnostic generation code can read it. Kept as a mutable static because game rule
 * registration is loader-specific and happens once at mod init before any world loads.
 */
public final class ModGameRules {

    // Up to MC 1.21.10 a game rule name is an opaque String (vanilla uses camelCase, e.g.
    // "keepInventory") rather than a registry Identifier, so any character is accepted here.
    // The namespaced form below is what MC 26.x requires, and reusing it verbatim keeps the
    // /gamerule name identical across every supported version. Note the derived translation
    // key differs by era: here it is "gamerule." + RULE_NAME (so it keeps the colon), whereas
    // 26.x builds it from the Identifier as "gamerule.<namespace>.<path>".
    public static final String RULE_NAME = BeginnersDelight.MOD_ID + ":generate_starter_house";

    /** Assigned by the loader initializer after registering the rule. */
    public static GameRules.Key<GameRules.BooleanValue> GENERATE_STARTER_HOUSE;

    private ModGameRules() {
    }
}
