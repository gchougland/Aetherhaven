package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Attaches {@link TownVillagerBinding} to elders from pre-Week-3 saves. */
public final class TownNpcMigration {
    private TownNpcMigration() {}

    public static void ensureElderBindingsOnWorldThread(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> ensureElderBindings(world, plugin));
    }

    private static void ensureElderBindings(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<Ref<EntityStore>> toBind = new ArrayList<>();
        List<TownRecord> towns = new ArrayList<>();
        for (TownRecord t : tm.allTowns()) {
            if (!world.getName().equals(t.getWorldName())) {
                continue;
            }
            UUID elderUuid = t.getElderEntityUuid();
            if (elderUuid == null) {
                continue;
            }
            Ref<EntityStore> r = findRefByUuid(store, elderUuid);
            if (r == null || !r.isValid()) {
                continue;
            }
            if (store.getComponent(r, TownVillagerBinding.getComponentType()) != null) {
                continue;
            }
            toBind.add(r);
            towns.add(t);
        }
        for (int i = 0; i < toBind.size(); i++) {
            Ref<EntityStore> r = toBind.get(i);
            TownRecord t = towns.get(i);
            store.putComponent(
                r,
                TownVillagerBinding.getComponentType(),
                new TownVillagerBinding(t.getTownId(), TownVillagerBinding.KIND_ELDER, null)
            );
        }
    }

    @Nullable
    private static Ref<EntityStore> findRefByUuid(@Nonnull Store<EntityStore> store, @Nonnull UUID target) {
        AtomicReference<Ref<EntityStore>> holder = new AtomicReference<>();
        store.forEachChunk(
            Query.and(UUIDComponent.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                if (holder.get() != null) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    UUIDComponent u = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (u != null && target.equals(u.getUuid())) {
                        holder.set(archetypeChunk.getReferenceTo(i));
                        return;
                    }
                }
            }
        );
        return holder.get();
    }
}
