package com.beginnersdelight.neoforge;

import com.beginnersdelight.village.VillageConfigLoader;
import com.beginnersdelight.worldgen.ModGameRules;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Registers the mod's game rules against the String-keyed game-rule API used up to
 * MC 1.21.10. The 1.21.11+ counterpart of this class lives in
 * {@code neoforge/gamerule-modern}; both are compiled into the same package, and each
 * NeoForge subproject picks exactly one of the two source directories.
 */
final class NeoForgeGameRules {

    private NeoForgeGameRules() {
    }

    static void register(IEventBus modEventBus) {
        boolean starterHouseDefault = VillageConfigLoader
                .load(FMLPaths.CONFIGDIR.get())
                .isAutoGenerateStarterHouse();

        // On these versions the rule table is a plain static map rather than a frozen registry,
        // and NeoForge's access transformer opens up both GameRules.register and
        // BooleanValue.create, so registering straight from the mod constructor is enough. It
        // must still happen exactly once per JVM, before any world builds its GameRules.
        ModGameRules.GENERATE_STARTER_HOUSE = GameRules.register(
                ModGameRules.RULE_NAME,
                GameRules.Category.MISC,
                GameRules.BooleanValue.create(starterHouseDefault));
    }
}
