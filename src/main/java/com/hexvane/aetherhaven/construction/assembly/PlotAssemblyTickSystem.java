package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Runs passive assembly pacing once per world per few hundred ms (first matching player in the tick batch triggers).
 */
public final class PlotAssemblyTickSystem extends EntityTickingSystem<EntityStore> {
    private static final long MIN_INTERVAL_MS = 220L;
    private static final ConcurrentHashMap<String, Long> LAST_RUN_MS = new ConcurrentHashMap<>();

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @Nonnull
    private final AetherhavenPlugin plugin;

    public PlotAssemblyTickSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (index != 0) {
            return;
        }
        World world = store.getExternalData().getWorld();
        String name = world.getName();
        long now = System.currentTimeMillis();
        Long prev = LAST_RUN_MS.get(name);
        if (prev != null && now - prev < MIN_INTERVAL_MS) {
            return;
        }
        LAST_RUN_MS.put(name, now);
        PlotAssemblyService.tickPassive(world, plugin, store);
    }
}
