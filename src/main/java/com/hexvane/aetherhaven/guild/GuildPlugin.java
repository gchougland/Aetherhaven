package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GuildPlugin extends AetherhavenSubplugin {
    @Nullable
    private static GuildPlugin instance;

    @Nullable
    private GameTimeTickListener guildAdventurerPoolListener;

    public GuildPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static GuildPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        GuildBootstrap.register(core(), this);
        guildAdventurerPoolListener = GuildBootstrap.createGuildAdventurerPoolListener(core());
        core().getGameTimeTickListenerRegistry().register(guildAdventurerPoolListener);
    }

    @Override
    protected void shutdownFeature() {
        if (guildAdventurerPoolListener != null) {
            core().getGameTimeTickListenerRegistry().unregister(guildAdventurerPoolListener);
        }
    }
}
