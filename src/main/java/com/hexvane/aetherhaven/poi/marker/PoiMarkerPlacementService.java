package com.hexvane.aetherhaven.poi.marker;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.marker.MarkerEntityProximity;
import com.hexvane.aetherhaven.marker.MarkerFacingYaw;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Spawns POI marker entities and registers matching {@link PoiEntry} rows. */
public final class PoiMarkerPlacementService {
    private PoiMarkerPlacementService() {}

    @Nullable
    public static Ref<EntityStore> placeMarkerFromPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull Vector3i anchorBlock,
        @Nonnull Set<String> tags,
        int capacity,
        @Nonnull PoiInteractionKind interactionKind,
        boolean mountOnUse,
        @Nullable String equipmentProfileId,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3d pos = new Vector3d(anchorBlock.x + 0.5, anchorBlock.y + 0.5, anchorBlock.z + 0.5);
        float yaw = 0f;
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc != null) {
            yaw = MarkerFacingYaw.yawFacingToward(pos, playerTc.getPosition());
        }
        return placeMarker(
            world,
            plugin,
            town,
            plotId,
            anchorBlock,
            tags,
            capacity,
            interactionKind,
            mountOnUse,
            equipmentProfileId,
            yaw,
            store
        );
    }

    @Nullable
    public static Ref<EntityStore> placeMarker(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull Vector3i anchorBlock,
        @Nonnull Set<String> tags,
        int capacity,
        @Nonnull PoiInteractionKind interactionKind,
        boolean mountOnUse,
        @Nullable String equipmentProfileId,
        float yawRadians,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3d pos = new Vector3d(anchorBlock.x + 0.5, anchorBlock.y + 0.5, anchorBlock.z + 0.5);
        if (MarkerEntityProximity.isDuplicatePosition(store, PoiMarkerEntity.getComponentType(), pos)) {
            return null;
        }
        UUID poiId = UUID.randomUUID();
        PoiMarkerDataComponent data =
            new PoiMarkerDataComponent(poiId, tags, capacity, interactionKind, mountOnUse, equipmentProfileId);
        PoiEntry entry =
            PoiMarkerLocator.toRegistryEntry(poiId, town, plotId, anchorBlock.x, anchorBlock.y, anchorBlock.z, data);
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        reg.register(entry);

        Holder<EntityStore> holder = PoiMarkerSpawner.createHolder(world, pos, yawRadians, data);
        if (holder == null) {
            reg.unregister(poiId);
            return null;
        }
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nullable
    public static Ref<EntityStore> placeMarker(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull Vector3i anchorBlock,
        @Nonnull Set<String> tags,
        int capacity,
        @Nonnull PoiInteractionKind interactionKind,
        boolean mountOnUse,
        @Nullable String equipmentProfileId,
        float yawRadians,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        return placeMarker(
            world,
            plugin,
            town,
            plotId,
            anchorBlock,
            tags,
            capacity,
            interactionKind,
            mountOnUse,
            equipmentProfileId,
            yawRadians,
            commandBuffer.getStore()
        );
    }

    public static void unregisterLinkedPoi(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PoiMarkerDataComponent data
    ) {
        UUID poiId = data.getPoiRegistryId();
        if (poiId == null) {
            return;
        }
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        reg.unregister(poiId);
    }

    @Nonnull
    public static Set<String> tagsForPreset(@Nonnull String preset) {
        Set<String> tags = new HashSet<>();
        switch (preset) {
            case "Sleep" -> {
                tags.add("SLEEP");
                tags.add("ENERGY");
            }
            case "Eat" -> tags.add("EAT");
            case "Sit" -> {
                tags.add("SIT");
                tags.add("FUN");
            }
            case "Work" -> tags.add("WORK");
            default -> {}
        }
        return tags;
    }

    @Nonnull
    public static PoiInteractionKind kindForPreset(@Nonnull String preset) {
        return switch (preset) {
            case "Sleep" -> PoiInteractionKind.SLEEP;
            case "Eat" -> PoiInteractionKind.USE_BENCH;
            case "Sit" -> PoiInteractionKind.SIT;
            case "Work" -> PoiInteractionKind.WORK_SURFACE;
            default -> PoiInteractionKind.NONE;
        };
    }

    @Nullable
    public static String equipmentForWorkAction(@Nullable String workAction) {
        if (workAction == null) {
            return null;
        }
        return switch (workAction) {
            case "Mine" -> "work_miner";
            case "Chop" -> "work_logger";
            default -> null;
        };
    }
}
