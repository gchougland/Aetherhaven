package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.NPCPreTickSystem;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Keeps plot-creator spot preview NPCs out of ambient despawn before {@link NPCPreTickSystem} can remove them.
 * Also clears walk/run so Frozen previews do not look like they are running in place.
 */
public final class PlotCreatorSpotPreviewSanitizeSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(new SystemDependency<>(Order.BEFORE, NPCPreTickSystem.class));
    @Nonnull
    private final Query<EntityStore> query =
        Query.and(PlotCreatorSpotPreview.getComponentType(), NPCEntity.getComponentType());

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlotCreatorSpotPreviewSanitize.applyEachTick(ref, store, commandBuffer);
        PlotCreatorSpotPreviewSanitize.clearMovementAnim(ref, store, commandBuffer);
    }
}
