package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

public final class JewelryStatSyncSystem extends EntityTickingSystem<EntityStore> {
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
        return PlayerJewelryLoadout.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerJewelryLoadout loadout = archetypeChunk.getComponent(index, PlayerJewelryLoadout.getComponentType());
        if (loadout == null || !loadout.isStatsDirty()) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        EntityStatMap map = store.getComponent(ref, EntityStatMap.getComponentType());
        if (map == null) {
            loadout.clearStatsDirty();
            commandBuffer.putComponent(ref, PlayerJewelryLoadout.getComponentType(), loadout);
            return;
        }
        JewelryStatSync.apply(ref, store, loadout);
        loadout.clearStatsDirty();
        commandBuffer.putComponent(ref, PlayerJewelryLoadout.getComponentType(), loadout);
    }
}
