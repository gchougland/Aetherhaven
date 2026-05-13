package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkUnloadingSystem;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Clears {@link MountedComponent} on block seats before chunk unload paths run: vanilla {@code MountSystems} can call
 * {@code ChunkStore.removeComponent} while the chunk store is already processing (e.g. {@code EntityChunkLoadingSystem}
 * during non-ticking), which throws. Town villagers also get {@link VillagerAutonomySystem#onUnloadSafetyDismount} for
 * POI cleanup.
 *
 * <p>Dismount when the entity’s chunk or the <em>seat block’s</em> chunk is not {@link ChunkTracker.ChunkVisibility#HOT},
 * so NPCs sitting on POIs in a neighbor chunk are cleared before that chunk stops ticking.
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
        return MountedComponent.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        MountedComponent mounted = archetypeChunk.getComponent(index, MountedComponent.getComponentType());
        if (mounted == null || mounted.getControllerType() != MountController.BlockMount) {
            return;
        }
        Ref<ChunkStore> blockRef = mounted.getMountedToBlock();
        if (blockRef == null || !blockRef.isValid()) {
            commandBuffer.tryRemoveComponent(archetypeChunk.getReferenceTo(index), MountedComponent.getComponentType());
            return;
        }

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
        long entityChunk = ChunkUtil.indexChunkFromBlock(bx, bz);
        ChunkTracker.ChunkVisibility entityVis = ChunkUnloadingSystem.getChunkVisibility(trackers, entityChunk);

        ChunkTracker.ChunkVisibility seatVis = ChunkTracker.ChunkVisibility.HOT;
        Store<ChunkStore> cs = blockRef.getStore();
        BlockMountComponent seat = cs.getComponent(blockRef, BlockMountComponent.getComponentType());
        if (seat != null) {
            Vector3i bp = seat.getBlockPos();
            long seatChunk = ChunkUtil.indexChunkFromBlock(bp.getX(), bp.getZ());
            seatVis = ChunkUnloadingSystem.getChunkVisibility(trackers, seatChunk);
        }

        if (entityVis == ChunkTracker.ChunkVisibility.HOT && seatVis == ChunkTracker.ChunkVisibility.HOT) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (binding != null) {
            NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
            VillagerAutonomyState autonomy = archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());
            VillagerNeeds needs = archetypeChunk.getComponent(index, VillagerNeeds.getComponentType());
            PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            long now = resolveNowMs(store);
            VillagerAutonomySystem.onUnloadSafetyDismount(ref, store, commandBuffer, npc, autonomy, needs, reg, now);
        } else {
            commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
        }
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
