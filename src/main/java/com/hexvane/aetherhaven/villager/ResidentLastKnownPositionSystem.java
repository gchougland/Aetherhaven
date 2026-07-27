package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentLastKnownPositionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Periodically saves last known positions for town residents while they are loaded. */
public final class ResidentLastKnownPositionSystem extends EntityTickingSystem<EntityStore> {
    private static final int TICK_INTERVAL = 40;

    private final AetherhavenPlugin plugin;

    public ResidentLastKnownPositionSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TownVillagerBinding.getComponentType(),
            TransformComponent.getComponentType(),
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
        World world = store.getExternalData().getWorld();
        if (((world.getTick() + index) % TICK_INTERVAL) != 0) {
            return;
        }
        TownVillagerBinding binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        UUIDComponent uc = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (binding == null || transform == null || uc == null) {
            return;
        }
        if (TownVillagerBinding.isVisitorKind(binding.getKind())) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return;
        }
        var pos = transform.getPosition();
        ResidentLastKnownPositionService.recordPosition(
            town,
            tm,
            uc.getUuid(),
            pos.x,
            pos.y,
            pos.z,
            resolveNowMs(store)
        );
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeModule mod = TimeModule.get();
        if (mod != null) {
            TimeResource tr = store.getResource(mod.getTimeResourceType());
            if (tr != null) {
                return tr.getNow().toEpochMilli();
            }
        }
        return System.currentTimeMillis();
    }
}
