package com.hexvane.aetherhaven.rts;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Frees guards still commanded after RTS ended, crashed, or the commander logged out. */
public final class RtsOrphanedGuardRecoverySystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(GuardRtsCommandState.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        GuardRtsCommandState cmd = chunk.getComponent(index, GuardRtsCommandState.getComponentType());
        if (cmd == null) {
            return;
        }
        UUID commanderUuid = cmd.getCommanderPlayerUuid();
        if (commanderUuid != null && commanderStillCommands(store, commanderUuid, cmd.isFieldOrder())) {
            return;
        }
        Ref<EntityStore> guardRef = chunk.getReferenceTo(index);
        RtsGuardRelease.release(guardRef, store, commandBuffer);
    }

    private static boolean commanderStillCommands(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID commanderUuid,
        boolean fieldOrder
    ) {
        Ref<EntityStore> commanderRef = store.getExternalData().getRefFromUUID(commanderUuid);
        boolean inWorld = commanderRef != null && commanderRef.isValid();
        RtsCommandPlayerComponent session = inWorld
            ? store.getComponent(commanderRef, RtsCommandPlayerComponent.getComponentType())
            : null;
        return keepsOrder(fieldOrder, inWorld, session != null && session.isActive());
    }

    /**
     * Whether a commanded guard keeps its order this tick.
     *
     * <p>An order from the command post lasts while that session is open. An order given with the Command Flag in hand
     * has no session, so it lasts while its commander is in this world, until they release the guards. Before the flag
     * worked outside the command post every such order was dropped here on the next tick.
     *
     * @param fieldOrder the order was given with the Command Flag outside the command post
     * @param commanderInWorld the commander is in this world
     * @param commanderAtPost the commander has a command post session open
     */
    static boolean keepsOrder(boolean fieldOrder, boolean commanderInWorld, boolean commanderAtPost) {
        return commanderInWorld && (fieldOrder || commanderAtPost);
    }
}
