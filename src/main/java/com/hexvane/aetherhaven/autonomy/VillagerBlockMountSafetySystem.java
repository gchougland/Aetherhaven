package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkUnloadingSystem;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Runs before chunk unload logic each tick: town villagers with {@link MountedComponent} (block seats from
 * {@link PoiAutonomyVisuals}) must clear mount during the entity tick, not when entities are removed mid-chunk-store
 * processing (vanilla mount cleanup can throw).
 */
public final class VillagerBlockMountSafetySystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public VillagerBlockMountSafetySystem(@Nonnull AetherhavenPlugin plugin) {
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
        return Query.and(TownVillagerBinding.getComponentType(), MountedComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TransformComponent tc = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        ObjectArrayList<ChunkTracker> trackers = new ObjectArrayList<>();
        for (PlayerRef pr : world.getPlayerRefs()) {
            ChunkTracker ct = pr.getChunkTracker();
            if (ct != null) {
                trackers.add(ct);
            }
        }
        int bx = (int) Math.floor(tc.getPosition().getX());
        int bz = (int) Math.floor(tc.getPosition().getZ());
        long chunkIndex = ChunkUtil.indexChunkFromBlock(bx, bz);
        if (ChunkUnloadingSystem.getChunkVisibility(trackers, chunkIndex) == ChunkTracker.ChunkVisibility.HOT) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        VillagerAutonomyState autonomy = archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());
        VillagerNeeds needs = archetypeChunk.getComponent(index, VillagerNeeds.getComponentType());
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        long now = resolveNowMs(store);
        VillagerAutonomySystem.onUnloadSafetyDismount(ref, store, commandBuffer, npc, autonomy, needs, reg, now);
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
