package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotCreatorPlugin extends AetherhavenSubplugin {
    @Nullable
    private static PlotCreatorPlugin instance;

    public PlotCreatorPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static PlotCreatorPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        PlotCreatorBootstrap.register(core(), this);
    }
}
