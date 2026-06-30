package com.hexvane.aetherhaven.plugin;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import javax.annotation.Nonnull;

/** Fails an interaction when its owning subplugin is config-disabled or unloaded. */
public final class SubpluginInteractionGuard {
    private SubpluginInteractionGuard() {}

    public static boolean failIfDisabled(@Nonnull InteractionContext context, @Nonnull PluginIdentifier subpluginId) {
        if (!AetherhavenFeatures.isLoaded(subpluginId)) {
            context.getState().state = InteractionState.Failed;
            return true;
        }
        return false;
    }
}
