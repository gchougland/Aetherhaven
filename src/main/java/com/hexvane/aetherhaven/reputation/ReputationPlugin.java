package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ReputationPlugin extends AetherhavenSubplugin {
    @Nullable
    private static ReputationPlugin instance;

    public ReputationPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static ReputationPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        ReputationBootstrap.register(core(), this);
    }
}
