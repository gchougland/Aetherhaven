package com.hexvane.aetherhaven.festival.lettuce;

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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Drinks life essence that players throw at the lettuce. */
public final class FestivalLettuceAbsorbSystem extends EntityTickingSystem<EntityStore> {
    /** Reach around the lettuce even while it is still small. */
    private static final double MIN_ABSORB_RADIUS = 5.0;
    /** The lettuce swells as it fills, so its reach has to follow the model outwards. */
    private static final double ABSORB_RADIUS_PER_SCALE = 0.5;

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
        FestivalLettuceComponent lettuce = chunk.getComponent(index, FestivalLettuceComponent.getComponentType());
        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        if (lettuce == null || tc == null || !lettuce.isGrowing() || lettuce.isFull()) {
            return;
        }
        Vector3d center = new Vector3d(tc.getPosition());
        int absorbed = absorbNearbyEssence(store, commandBuffer, center, absorbRadius(lettuce));
        if (absorbed <= 0) {
            return;
        }
        lettuce.addEssence(absorbed);
        lettuce.setPulseStartEpochMs(System.currentTimeMillis());
        FestivalLettuceEffects.playAbsorb(store, center);
    }

    static double absorbRadius(@Nonnull FestivalLettuceComponent lettuce) {
        return Math.max(
            MIN_ABSORB_RADIUS,
            FestivalLettuceGrowthSystem.targetScale(lettuce) * ABSORB_RADIUS_PER_SCALE
        );
    }

    private static int absorbNearbyEssence(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d center,
        double radius
    ) {
        // Dropped and thrown items are intangible, so they only live in the item spatial index.
        SpatialResource<Ref<EntityStore>, EntityStore> items =
            store.getResource(EntityModule.get().getItemSpatialResourceType());
        if (items == null) {
            return 0;
        }
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        items.getSpatialStructure().collect(center, radius + 2.0, nearby);
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
            TransformComponent tc = store.getComponent(r, TransformComponent.getComponentType());
            if (item == null || tc == null) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (stack == null || !FestivalLettuceComponent.isEssenceItem(stack.getItemId())) {
                continue;
            }
            if (tc.getPosition().distanceSquared(center) > radius * radius) {
                continue;
            }
            absorbed += Math.max(1, stack.getQuantity());
            commandBuffer.removeEntity(r, RemoveReason.REMOVE);
        }
        return absorbed;
    }
}
