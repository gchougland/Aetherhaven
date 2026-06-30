package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PathDesignerPlugin extends AetherhavenSubplugin {
    @Nullable
    private static PathDesignerPlugin instance;

    public PathDesignerPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static PathDesignerPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        PathDesignerBootstrap.register(core(), this);
    }
}
