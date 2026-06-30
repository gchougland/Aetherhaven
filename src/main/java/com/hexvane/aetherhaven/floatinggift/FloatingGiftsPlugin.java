package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FloatingGiftsPlugin extends AetherhavenSubplugin {
    @Nullable
    private static FloatingGiftsPlugin instance;

    public FloatingGiftsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static FloatingGiftsPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        FloatingGiftsBootstrap.register(core(), this);
    }
}
