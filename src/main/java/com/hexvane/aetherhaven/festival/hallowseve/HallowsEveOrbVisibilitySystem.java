package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hides maze orbs from everyone except the player currently racing. */
public final class HallowsEveOrbVisibilitySystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Collections.singleton(
        new SystemDependency<>(Order.AFTER, EntityTrackerSystems.HideFromPlayer.class)
    );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            EntityTrackerSystems.EntityViewer.getComponentType(),
            PlayerRef.getComponentType(),
            Player.getComponentType(),
            UUIDComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!HallowsEveOrbComponent.isRegistered()) {
            return;
        }
        UUIDComponent uc = chunk.getComponent(index, UUIDComponent.getComponentType());
        EntityTrackerSystems.EntityViewer viewer =
            chunk.getComponent(index, EntityTrackerSystems.EntityViewer.getComponentType());
        if (uc == null || viewer == null) {
            return;
        }
        UUID playerUuid = uc.getUuid();
        Iterator<Ref<EntityStore>> iterator = viewer.visible.iterator();
        while (iterator.hasNext()) {
            Ref<EntityStore> visibleRef = iterator.next();
            if (visibleRef == null || !visibleRef.isValid()) {
                continue;
            }
            HallowsEveOrbComponent orb = store.getComponent(visibleRef, HallowsEveOrbComponent.getComponentType());
            if (orb == null) {
                continue;
            }
            UUID townId = orb.getTownId();
            HallowsEveSession session = townId != null ? HallowsEveSessionIndex.get(townId) : null;
            boolean racerCanSee =
                session != null
                    && session.getPhase() == HallowsEveSession.Phase.RACING
                    && session.isRacer(playerUuid);
            if (racerCanSee) {
                continue;
            }
            viewer.hiddenCount++;
            iterator.remove();
        }
    }
}
