package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagersPlugin extends AetherhavenSubplugin {
    @Nullable
    private static VillagersPlugin instance;

    @Nullable
    private GameTimeTickListener villagerScheduleGameTimeListener;

    public VillagersPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static VillagersPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        VillagersBootstrap.register(core(), this);
        villagerScheduleGameTimeListener = VillagersBootstrap.createVillagerScheduleGameTimeListener(core());
        core().getGameTimeTickListenerRegistry().register(villagerScheduleGameTimeListener);
    }

    @Override
    protected void shutdownFeature() {
        if (villagerScheduleGameTimeListener != null) {
            core().getGameTimeTickListenerRegistry().unregister(villagerScheduleGameTimeListener);
        }
    }
}
