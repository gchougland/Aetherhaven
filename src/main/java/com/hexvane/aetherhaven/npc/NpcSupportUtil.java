package com.hexvane.aetherhaven.npc;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.DebugSupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.PositionCache;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Reads NPC ECS support components (Update 6: no longer on {@link com.hypixel.hytale.server.npc.role.Role}). */
public final class NpcSupportUtil {

    private NpcSupportUtil() {}

    @Nullable
    public static StateSupport stateSupport(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        return store.getComponent(npcRef, StateSupport.getComponentType());
    }

    @Nullable
    public static StateSupport stateSupport(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return accessor.getComponent(npcRef, StateSupport.getComponentType());
    }

    @Nonnull
    public static MarkedEntitySupport markedEntitySupport(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return MarkedEntitySupport.get(npcRef, accessor);
    }

    @Nullable
    public static WorldSupport worldSupport(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return accessor.getComponent(npcRef, WorldSupport.getComponentType());
    }

    @Nonnull
    public static PositionCache positionCache(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return PositionCache.get(npcRef, accessor);
    }

    @Nonnull
    public static String stateName(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        StateSupport support = stateSupport(store, npcRef);
        return support != null ? support.getStateName() : "";
    }

    @Nullable
    public static DebugSupport debugSupport(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        return store.getComponent(npcRef, DebugSupport.getComponentType());
    }

    public static void setState(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull String state,
        @Nullable String subState,
        @Nonnull Store<EntityStore> store
    ) {
        StateSupport support = stateSupport(store, npcRef);
        if (support != null) {
            support.setState(npcRef, state, subState, store);
        }
    }

    public static void setState(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull String state,
        @Nullable String subState,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        StateSupport support = commandBuffer.getComponent(npcRef, StateSupport.getComponentType());
        if (support != null) {
            support.setState(npcRef, state, subState, commandBuffer);
        }
    }
}
