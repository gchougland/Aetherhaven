package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CommercePlugin extends AetherhavenSubplugin {
    @Nullable
    private static CommercePlugin instance;

    @Nullable
    private GameTimeTickListener commerceGameTimeListener;

    public CommercePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static CommercePlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        CommerceBootstrap.register(core(), this);
        commerceGameTimeListener = CommerceBootstrap.createCommerceGameTimeListener(core());
        core().getGameTimeTickListenerRegistry().register(commerceGameTimeListener);
    }

    @Override
    protected void shutdownFeature() {
        if (commerceGameTimeListener != null) {
            core().getGameTimeTickListenerRegistry().unregister(commerceGameTimeListener);
        }
    }
}
