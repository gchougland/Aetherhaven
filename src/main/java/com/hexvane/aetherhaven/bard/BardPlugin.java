package com.hexvane.aetherhaven.bard;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class BardPlugin extends AetherhavenSubplugin {
    @Nullable
    private static BardPlugin instance;

    public BardPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static BardPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        BardBootstrap.register(core(), this);
    }
}
