package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class EconomyPlugin extends AetherhavenSubplugin {
    @Nullable
    private static EconomyPlugin instance;

    @Nullable
    private GameTimeTickListener economyGameTimeListener;

    public EconomyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static EconomyPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        EconomyBootstrap.register(core(), this);
        economyGameTimeListener = EconomyBootstrap.createEconomyGameTimeListener(core());
        core().getGameTimeTickListenerRegistry().register(economyGameTimeListener);
    }

    @Override
    protected void shutdownFeature() {
        if (economyGameTimeListener != null) {
            core().getGameTimeTickListenerRegistry().unregister(economyGameTimeListener);
        }
    }
}
