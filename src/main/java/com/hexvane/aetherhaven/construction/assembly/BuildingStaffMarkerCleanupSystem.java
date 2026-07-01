package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Removes orphaned building-staff preview markers (e.g. after abrupt disconnect, deferred spawns, or markers left
 * behind when assembly finished while the staff was held).
 */
public final class BuildingStaffMarkerCleanupSystem extends TickingSystem<EntityStore> {
    private static final float INTERVAL_SEC = 2.0f;

    private float timer;

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        timer += dt;
        if (timer < INTERVAL_SEC) {
            return;
        }
        timer = 0.0f;
        World world = store.getExternalData().getWorld();
        if (!world.isAlive()) {
            return;
        }
        removeOrphanedMarkers(store, world);
    }

    static void removeOrphanedMarkers(@Nonnull Store<EntityStore> store, @Nonnull World world) {
        ArrayList<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            Query.and(BuildingStaffMarkerEntity.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    BuildingStaffMarkerEntity marker = chunk.getComponent(i, BuildingStaffMarkerEntity.getComponentType());
                    if (marker == null || shouldKeepMarker(world, store, marker.getOwnerPlayerUuid())) {
                        continue;
                    }
                    Ref<EntityStore> markerRef = chunk.getReferenceTo(i);
                    if (markerRef.isValid()) {
                        toRemove.add(markerRef);
                    }
                }
            }
        );
        for (Ref<EntityStore> markerRef : toRemove) {
            if (markerRef.isValid()) {
                store.removeEntity(markerRef, RemoveReason.REMOVE);
            }
        }
    }

    private static boolean shouldKeepMarker(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nullable UUID ownerPlayerEntityUuid
    ) {
        if (ownerPlayerEntityUuid == null) {
            return false;
        }
        Ref<EntityStore> ownerRef = world.getEntityRef(ownerPlayerEntityUuid);
        if (ownerRef == null || !ownerRef.isValid()) {
            return false;
        }
        ItemStack hand = InventoryComponent.getItemInHand(store, ownerRef);
        return hand != null && !hand.isEmpty() && BuildingStaffTiers.isBuildingStaff(hand.getItemId());
    }
}
