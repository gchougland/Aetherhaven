package com.hexvane.aetherhaven.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AdminToolsPlugin extends AetherhavenSubplugin {
    @Nullable
    private static AdminToolsPlugin instance;

    public AdminToolsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static AdminToolsPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        AdminToolsBootstrap.register(core(), this);
    }
}
