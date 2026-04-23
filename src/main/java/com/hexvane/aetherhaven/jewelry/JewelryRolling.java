package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Supplies {@link AetherhavenPluginConfig} to jewelry roll logic.
 */
public final class JewelryRolling {
    @Nonnull
    private static volatile Supplier<AetherhavenPluginConfig> CONFIG = AetherhavenPluginConfig::defaults;

    private JewelryRolling() {}

    public static void bind(@Nonnull Supplier<AetherhavenPluginConfig> config) {
        CONFIG = config;
    }

    @Nonnull
    public static AetherhavenPluginConfig config() {
        return CONFIG.get();
    }
}
