package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ConstructionPlugin extends AetherhavenSubplugin {
    @Nullable
    private static ConstructionPlugin instance;

    @Nullable
    private GameTimeTickListener plotAssemblyGameTimeListener;

    public ConstructionPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static ConstructionPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        ConstructionBootstrap.register(core(), this);
        plotAssemblyGameTimeListener = ConstructionBootstrap.createPlotAssemblyGameTimeListener(core());
        core().getGameTimeTickListenerRegistry().register(plotAssemblyGameTimeListener);
    }

    @Override
    protected void shutdownFeature() {
        if (plotAssemblyGameTimeListener != null) {
            core().getGameTimeTickListenerRegistry().unregister(plotAssemblyGameTimeListener);
        }
    }
}
