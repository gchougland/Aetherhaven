package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ProductionPlugin extends AetherhavenSubplugin {
    @Nullable
    private static ProductionPlugin instance;

    public ProductionPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static ProductionPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        ProductionBootstrap.register(core(), this);
    }

    @Override
    protected void shutdownFeature() {
        if (instance == this) {
            instance = null;
        }
    }
}
