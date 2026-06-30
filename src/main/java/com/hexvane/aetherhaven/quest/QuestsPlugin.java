package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hexvane.aetherhaven.plugin.GameTimeTickListener;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestsPlugin extends AetherhavenSubplugin {
    @Nullable
    private static QuestsPlugin instance;

    @Nullable
    private GameTimeTickListener questBoardDawnListener;

    public QuestsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static QuestsPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        QuestsBootstrap.register(core(), this);
        questBoardDawnListener = QuestsBootstrap.createQuestBoardDawnListener(core());
        core().getGameTimeTickListenerRegistry().register(questBoardDawnListener);
    }

    @Override
    protected void shutdownFeature() {
        if (questBoardDawnListener != null) {
            core().getGameTimeTickListenerRegistry().unregister(questBoardDawnListener);
        }
    }
}
