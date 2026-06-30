package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.GaiaStatueBlock;
import javax.annotation.Nonnull;

/**
 * Chunk components used by core plot construction (Gaia altar statue, etc.). Registered on the parent plugin so they
 * remain available when optional subplugins are config-disabled.
 */
public final class AetherhavenSharedChunkComponents {
    private AetherhavenSharedChunkComponents() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        GaiaStatueBlock.register(plugin.getChunkStoreRegistry());
    }
}
