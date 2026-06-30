package com.hexvane.aetherhaven.plugin;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Checks whether an Aetherhaven subplugin is loaded and enabled. Subplugins call {@link #shouldSetup(JavaPlugin)}
 * at the start of {@code setup()} because Hytale does not run {@code canLoadOnBoot} per subplugin entry.
 */
public final class AetherhavenFeatures {
    private AetherhavenFeatures() {}

    public static boolean isLoaded(@Nonnull PluginIdentifier id) {
        if (!isEnabledInServerConfig(id)) {
            return false;
        }
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            return false;
        }
        PluginBase plugin = manager.getPlugin(id);
        return plugin != null && plugin.isEnabled();
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
