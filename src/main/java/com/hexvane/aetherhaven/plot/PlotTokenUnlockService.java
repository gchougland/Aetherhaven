package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotTokenUnlockService {
    private PlotTokenUnlockService() {}

    public static boolean requiresUnlock(@Nullable ConstructionDefinition def) {
        return def != null && def.isPlotTokenLockedByDefault();
    }

    public static boolean requiresUnlock(@Nullable String constructionId) {
        if (constructionId == null || constructionId.isBlank()) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        return requiresUnlock(plugin.getConstructionCatalog().get(constructionId.trim()));
    }

    public static boolean isUnlocked(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String constructionId) {
        if (!requiresUnlock(constructionId)) {
            return true;
        }
        PlayerPlotTokenUnlockState state = store.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        return state != null && state.isUnlocked(constructionId);
    }

    /** @return true when a new unlock was added */
    public static boolean unlock(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull String constructionId
    ) {
        PlayerPlotTokenUnlockState state = commandBuffer.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        if (state == null) {
            state = new PlayerPlotTokenUnlockState();
            commandBuffer.addComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        }
        return state.unlock(constructionId);
    }

    /** @return true when a new unlock was added */
    public static boolean unlock(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String constructionId
    ) {
        PlayerPlotTokenUnlockState state = store.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        boolean wasNew = state == null;
        if (state == null) {
            state = new PlayerPlotTokenUnlockState();
        }
        boolean added = state.unlock(constructionId);
        if (wasNew) {
            store.addComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        } else if (added) {
            store.putComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        }
        return added;
    }

    /** @return how many new unlocks were added */
    public static int unlockAllLockable(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull ConstructionCatalog catalog
    ) {
        PlayerPlotTokenUnlockState state = store.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        boolean wasNew = state == null;
        if (state == null) {
            state = new PlayerPlotTokenUnlockState();
        }
        int added = 0;
        for (String id : catalog.ids()) {
            ConstructionDefinition def = catalog.get(id);
            if (requiresUnlock(def) && state.unlock(id)) {
                added++;
            }
        }
        if (wasNew) {
            store.addComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        } else if (added > 0) {
            store.putComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        }
        return added;
    }

    public static int getUnlockPoints(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerPlotTokenUnlockState state = store.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        return state != null ? state.getUnlockPoints() : 0;
    }

    public static void addUnlockPoint(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        addUnlockPoints(ref, commandBuffer, 1);
    }

    public static void addUnlockPoints(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        int amount
    ) {
        if (amount <= 0) {
            return;
        }
        PlayerPlotTokenUnlockState state = ensureState(ref, commandBuffer);
        state.addUnlockPoints(amount);
    }

    public static void addUnlockPoint(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        addUnlockPoints(ref, store, 1);
    }

    public static void addUnlockPoints(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        int amount
    ) {
        if (amount <= 0) {
            return;
        }
        PlayerPlotTokenUnlockState state = store.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        boolean wasNew = state == null;
        if (state == null) {
            state = new PlayerPlotTokenUnlockState();
        }
        state.addUnlockPoints(amount);
        if (wasNew) {
            store.addComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        } else {
            store.putComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        }
    }

    /** @return true when a point was spent */
    public static boolean trySpendUnlockPoint(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerPlotTokenUnlockState state = store.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        if (state == null || !state.trySpendUnlockPoint()) {
            return false;
        }
        store.putComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        return true;
    }

    @Nonnull
    private static PlayerPlotTokenUnlockState ensureState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerPlotTokenUnlockState state = commandBuffer.getComponent(ref, PlayerPlotTokenUnlockState.getComponentType());
        if (state == null) {
            state = new PlayerPlotTokenUnlockState();
            commandBuffer.addComponent(ref, PlayerPlotTokenUnlockState.getComponentType(), state);
        }
        return state;
    }

    @Nullable
    public static String displayNameFor(@Nonnull String constructionId) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return constructionId;
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        ConstructionDefinition def = catalog.get(constructionId.trim());
        if (def != null && def.getDisplayName() != null && !def.getDisplayName().isBlank()) {
            return def.getDisplayName().trim();
        }
        return constructionId.trim();
    }
}
