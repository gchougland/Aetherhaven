package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.purification.PurificationPowderPlayerComponent;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ReputationUnlocksPlugin extends AetherhavenSubplugin {
    @Nullable
    private static ReputationUnlocksPlugin instance;

    @Nullable
    private GameTimeTickListener sprinklerGameTimeListener;

    public ReputationUnlocksPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static ReputationUnlocksPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.REPUTATION)) {
            getLogger()
                .atWarning()
                .log("ReputationUnlocks requires %s; skipping feature registration", AetherhavenPluginIds.REPUTATION);
            return;
        }
        instance = this;
        ReputationUnlocksBootstrap.register(core(), this);
        sprinklerGameTimeListener = ReputationUnlocksBootstrap.createSprinklerGameTimeListener(core());
        core().getGameTimeTickListenerRegistry().register(sprinklerGameTimeListener);
    }

    @Override
    protected void shutdownFeature() {
        PurificationPowderPlayerComponent.detachAllOnlinePlayers();
        if (sprinklerGameTimeListener != null) {
            core().getGameTimeTickListenerRegistry().unregister(sprinklerGameTimeListener);
            sprinklerGameTimeListener = null;
        }
        if (instance == this) {
            instance = null;
        }
    }
}
