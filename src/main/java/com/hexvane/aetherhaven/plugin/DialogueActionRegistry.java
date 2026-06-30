package com.hexvane.aetherhaven.plugin;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Extensible dialogue action dispatch keyed by JSON {@code "type"} field. */
public final class DialogueActionRegistry {
    @FunctionalInterface
    public interface Handler {
        void run(
            @Nonnull JsonObject action,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull DialogueActionBatchResult out,
            @Nullable Ref<EntityStore> npcRef
        );
    }

    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public void register(@Nonnull String type, @Nonnull Handler handler) {
        handlers.put(type, handler);
    }

    public void unregister(@Nonnull String type) {
        handlers.remove(type);
    }

    public boolean dispatch(
        @Nonnull String type,
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult out,
        @Nullable Ref<EntityStore> npcRef
    ) {
        Handler handler = handlers.get(type);
        if (handler == null) {
            return false;
        }
        handler.run(action, playerRef, store, out, npcRef);
        return true;
    }

    public void registerAll(@Nonnull Map<String, Handler> batch) {
        handlers.putAll(batch);
    }

    @Nonnull
    public BiConsumer<String, Handler> asRegistrar() {
        return this::register;
    }
}
