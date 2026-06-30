package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PatrolRoutesPlugin extends AetherhavenSubplugin {
    @Nullable
    private static PatrolRoutesPlugin instance;

    public PatrolRoutesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static PatrolRoutesPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        PatrolRoutesBootstrap.register(core(), this);
    }
}
