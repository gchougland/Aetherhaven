package com.hexvane.aetherhaven.plugin;

import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Extensible dialogue condition dispatch keyed by JSON {@code "type"} field. */
public final class DialogueConditionRegistry {
    @FunctionalInterface
    public interface Handler {
        boolean test(
            @Nonnull JsonObject condition,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Store<EntityStore> store,
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

    /**
     * @return null if no handler is registered for {@code type}; otherwise the handler result
     */
    @Nullable
    public Boolean tryEvaluate(
        @Nonnull String type,
        @Nonnull JsonObject condition,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        Handler handler = handlers.get(type);
        if (handler == null) {
            return null;
        }
        return handler.test(condition, playerRef, store, npcRef);
    }

    public void registerAll(@Nonnull Map<String, Handler> batch) {
        handlers.putAll(batch);
    }
}
