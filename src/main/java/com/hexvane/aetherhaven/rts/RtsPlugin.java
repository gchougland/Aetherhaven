package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RtsPlugin extends AetherhavenSubplugin {
    @Nullable
    private static RtsPlugin instance;

    public RtsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static RtsPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        RtsBootstrap.register(core(), this);
    }
}
