package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialoguePlugin extends AetherhavenSubplugin {
    @Nullable
    private static DialoguePlugin instance;

    public DialoguePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static DialoguePlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        DialogueBootstrap.register(core(), this);
    }
}
