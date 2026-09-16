package com.beginnersdelight.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tag keys whose Java constants were removed from vanilla.
 */
public final class ModBlockTags {

    private ModBlockTags() {
    }

    /**
     * The BlockTags.SAPLINGS constant was removed in MC 26.2, but the vanilla
     * data tag minecraft:saplings still exists.
     */
    public static final TagKey<Block> SAPLINGS =
            TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("saplings"));
}
