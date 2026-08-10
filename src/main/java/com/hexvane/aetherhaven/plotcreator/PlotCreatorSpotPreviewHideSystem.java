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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hides plot-creator spot preview NPCs from everyone except the session owner. */
public final class PlotCreatorSpotPreviewHideSystem extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> query =
        Query.and(EntityTrackerSystems.EntityViewer.getComponentType(), Player.getComponentType());
    private final Set<Dependency<EntityStore>> dependencies =
        Collections.singleton(
            new SystemDependency<>(Order.AFTER, EntityTrackerSystems.CollectVisible.class)
        );

    @Nullable
    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
    }

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
        EntityTrackerSystems.EntityViewer viewer =
            archetypeChunk.getComponent(index, EntityTrackerSystems.EntityViewer.getComponentType());
        if (viewer == null) {
            return;
        }
        Ref<EntityStore> viewerRef = archetypeChunk.getReferenceTo(index);
        UUIDComponent viewerUuid = store.getComponent(viewerRef, UUIDComponent.getComponentType());
        UUID viewerId = viewerUuid != null ? viewerUuid.getUuid() : null;
        for (var iterator = viewer.visible.iterator(); iterator.hasNext(); ) {
            Ref<EntityStore> ref = iterator.next();
            if (!commandBuffer.getArchetype(ref).contains(PlotCreatorSpotPreview.getComponentType())) {
                continue;
            }
            PlotCreatorSpotPreview preview =
                commandBuffer.getComponent(ref, PlotCreatorSpotPreview.getComponentType());
            if (preview == null) {
                preview = store.getComponent(ref, PlotCreatorSpotPreview.getComponentType());
            }
            if (preview == null || preview.getOwnerPlayerUuid() == null) {
                continue;
            }
            if (viewerId == null || !preview.getOwnerPlayerUuid().equals(viewerId)) {
                viewer.hiddenCount++;
                iterator.remove();
            }
        }
    }
}
