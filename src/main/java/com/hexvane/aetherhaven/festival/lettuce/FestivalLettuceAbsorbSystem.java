package com.hexvane.aetherhaven.festival.lettuce;

import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Drinks life essence that players throw close to the lettuce, pulling it into the middle first. */
public final class FestivalLettuceAbsorbSystem extends EntityTickingSystem<EntityStore> {
    /** How close essence must land before the lettuce starts pulling it in. */
    private static final double MIN_PULL_RADIUS = 5.0;
    /** Grown lettuce reaches a little further as its model swells. */
    private static final double PULL_RADIUS_PER_SCALE = 0.5;
    /** Finish the drink once the essence reaches the lettuce core. */
    private static final double CONSUME_RADIUS = 0.55;
    /** How fast essence flies into the middle once caught. */
    private static final double PULL_SPEED_BLOCKS_PER_SEC = 11.0;
    /** Aim point up from the lettuce feet so the suck ends in the middle of the model. */
    private static final double MOUTH_HEIGHT_PER_SCALE = 0.1;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(FestivalLettuceComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> lettuceRef = chunk.getReferenceTo(index);
        FestivalLettuceComponent lettuce = chunk.getComponent(index, FestivalLettuceComponent.getComponentType());
        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        if (lettuceRef == null
            || !lettuceRef.isValid()
            || lettuce == null
            || tc == null
            || !lettuce.isGrowing()
            || lettuce.getEssence() >= lettuce.maxEssenceCapacity()) {
            return;
        }
        float scale = resolveScale(chunk, index, lettuce);
        Vector3d mouth = mouthPosition(tc.getPosition(), scale);
        int absorbed = pullAndAbsorbNearbyEssence(store, commandBuffer, mouth, absorbRadius(lettuce), dt);
        if (absorbed <= 0) {
            return;
        }
        lettuce.addEssence(absorbed);
        lettuce.setPulseStartEpochMs(System.currentTimeMillis());
        FestivalLettuceEffects.playAbsorb(store, mouth);
        if (lettuce.isMaxOvercharge()) {
            FestivalLettuceBurstSystem.tryBeginBurst(store, lettuce, new Vector3d(tc.getPosition()));
        }
    }

    static double absorbRadius(@Nonnull FestivalLettuceComponent lettuce) {
        return Math.max(
            MIN_PULL_RADIUS,
            FestivalLettuceGrowthSystem.targetScale(lettuce) * PULL_RADIUS_PER_SCALE
        );
    }

    @Nonnull
    static Vector3d mouthPosition(@Nonnull Vector3d lettuceFeet, float scale) {
        return new Vector3d(lettuceFeet.x, lettuceFeet.y + Math.max(0.75, scale * MOUTH_HEIGHT_PER_SCALE), lettuceFeet.z);
    }

    private static float resolveScale(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull FestivalLettuceComponent lettuce
    ) {
        return lettuce.getAppliedModelScale();
    }

    private static int pullAndAbsorbNearbyEssence(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d mouth,
        double pullRadius,
        float dt
    ) {
        // Dropped and thrown items are intangible, so they only live in the item spatial index.
        SpatialResource<Ref<EntityStore>, EntityStore> items =
            store.getResource(EntityModule.get().getItemSpatialResourceType());
        if (items == null) {
            return 0;
        }
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        items.getSpatialStructure().collect(mouth, pullRadius + 2.0, nearby);
        double step = PULL_SPEED_BLOCKS_PER_SEC * Math.min(Math.max(dt, 1.0f / 240.0f), 0.1f);
        int absorbed = 0;
        for (Ref<EntityStore> r : nearby) {
            if (r == null || !r.isValid()) {
                continue;
            }
            // The prefab decorates the square with life essence props; only loose items count.
            if (store.getComponent(r, PropComponent.getComponentType()) != null) {
                continue;
            }
            ItemComponent item = store.getComponent(r, ItemComponent.getComponentType());
            TransformComponent itemTc = store.getComponent(r, TransformComponent.getComponentType());
            if (item == null || itemTc == null) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (stack == null || !FestivalLettuceComponent.isEssenceItem(stack.getItemId())) {
                continue;
            }
            Vector3d pos = itemTc.getPosition();
            double distSq = pos.distanceSquared(mouth);
            if (distSq > pullRadius * pullRadius) {
                continue;
            }
            if (distSq <= CONSUME_RADIUS * CONSUME_RADIUS) {
                int per = FestivalLettuceComponent.essenceValue(stack.getItemId());
                absorbed += Math.max(1, stack.getQuantity()) * per;
                commandBuffer.removeEntity(r, RemoveReason.REMOVE);
                continue;
            }
            pullEssenceTowardMouth(store, commandBuffer, r, itemTc, mouth, step);
        }
        return absorbed;
    }

    private static void pullEssenceTowardMouth(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> itemRef,
        @Nonnull TransformComponent itemTc,
        @Nonnull Vector3d mouth,
        double step
    ) {
        Vector3d pos = itemTc.getPosition();
        double dx = mouth.x - pos.x;
        double dy = mouth.y - pos.y;
        double dz = mouth.z - pos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0e-5) {
            return;
        }
        double travel = Math.min(dist, step);
        double nx = dx / dist;
        double ny = dy / dist;
        double nz = dz / dist;
        Vector3d moved = new Vector3d(pos.x + nx * travel, pos.y + ny * travel, pos.z + nz * travel);
        TransformComponentUtil.replacePreservingChunk(itemRef, store, commandBuffer, moved, itemTc.getRotation());
        Velocity velocity = store.getComponent(itemRef, Velocity.getComponentType());
        if (velocity != null) {
            // Keep physics from fighting the suck-in so the essence flies cleanly into the lettuce.
            velocity.set(nx * PULL_SPEED_BLOCKS_PER_SEC, ny * PULL_SPEED_BLOCKS_PER_SEC, nz * PULL_SPEED_BLOCKS_PER_SEC);
            commandBuffer.putComponent(itemRef, Velocity.getComponentType(), velocity);
        }
    }
}
