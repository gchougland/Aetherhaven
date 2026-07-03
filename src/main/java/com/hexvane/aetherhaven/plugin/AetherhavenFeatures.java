package com.hexvane.aetherhaven.plugin;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Checks whether an Aetherhaven feature pack is enabled in server mod config.
 * Feature packs are registered by the parent via {@link AetherhavenFeatureBootstrap} (not manifest SubPlugins).
 */
public final class AetherhavenFeatures {
    private AetherhavenFeatures() {}

    /**
     * Whether a feature pack is active. Feature packs are registered by the parent plugin (not manifest
     * {@code SubPlugins}), so this is config-gated only — see {@link AetherhavenFeatureBootstrap}.
     */
    public static boolean isLoaded(@Nonnull PluginIdentifier id) {
        return isEnabledInServerConfig(id);
    }

    public static boolean isEnabledInServerConfig(@Nonnull PluginIdentifier id) {
        var serverConfig = HytaleServer.get();
        if (serverConfig == null) {
            return true;
        }
        var modConfig = serverConfig.getConfig().getModConfig().get(id);
        if (modConfig == null || modConfig.getEnabled() == null) {
            return true;
        }
        return modConfig.getEnabled();
    }

    public static boolean shouldSetup(@Nonnull JavaPlugin plugin) {
        PluginIdentifier id = new PluginIdentifier(plugin.getManifest());
        if (id.equals(AetherhavenPluginIds.CORE)) {
            return true;
        }
        if (!isEnabledInServerConfig(id)) {
            return false;
        }
        return true;
    }

    @Nullable
    public static <T extends PluginBase> T getPlugin(@Nonnull PluginIdentifier id, @Nonnull Class<T> type) {
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            return null;
        }
        PluginBase plugin = manager.getPlugin(id);
        if (plugin == null || !plugin.isEnabled() || !type.isInstance(plugin)) {
            return null;
        }
        return type.cast(plugin);
    }
}
