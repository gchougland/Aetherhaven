package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Tracks per-player building staff assembly preview marker entities (not serialized). */
public final class BuildingStaffPreviewPlayerComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<BuildingStaffPreviewPlayerComponent> CODEC =
        BuilderCodec.builder(BuildingStaffPreviewPlayerComponent.class, BuildingStaffPreviewPlayerComponent::new)
            .documentation("Transient building staff assembly marker state.")
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, BuildingStaffPreviewPlayerComponent> componentType;

    /** Packed block coord → spawned marker entity uuid. */
    @Nonnull
    private final Map<Long, UUID> cellKeyToMarkerUuid = new HashMap<>();

    /** Last applied model scale per cell (skip redundant model updates). */
    @Nonnull
    private final Map<Long, Float> cellKeyToLastScale = new HashMap<>();

    /** Last texture path for placing markers. */
    @Nonnull
    private final Map<Long, String> cellKeyToLastTexture = new HashMap<>();

    /** Marker kind per cell. */
    @Nonnull
    private final Map<Long, AssemblyMarkerKind> cellKeyToKind = new HashMap<>();

    /** Cell keys with an in-flight spawn on the world queue. */
    @Nonnull
    private final Set<Long> pendingSpawnCellKeys = new HashSet<>();

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                BuildingStaffPreviewPlayerComponent.class,
                "AetherhavenBuildingStaffPreview",
                BuildingStaffPreviewPlayerComponent.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, BuildingStaffPreviewPlayerComponent> getComponentType() {
        ComponentType<EntityStore, BuildingStaffPreviewPlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("BuildingStaffPreviewPlayerComponent not registered");
        }
        return t;
    }

    @Nonnull
    public Map<Long, UUID> getCellKeyToMarkerUuid() {
        return cellKeyToMarkerUuid;
    }

    @Nonnull
    public Map<Long, Float> getCellKeyToLastScale() {
        return cellKeyToLastScale;
    }

    @Nonnull
    public Map<Long, String> getCellKeyToLastTexture() {
        return cellKeyToLastTexture;
    }

    @Nonnull
    public Map<Long, AssemblyMarkerKind> getCellKeyToKind() {
        return cellKeyToKind;
    }

    @Nonnull
    public Set<Long> getPendingSpawnCellKeys() {
        return pendingSpawnCellKeys;
    }

    public void clearAllTracking() {
        cellKeyToMarkerUuid.clear();
        cellKeyToLastScale.clear();
        cellKeyToLastTexture.clear();
        cellKeyToKind.clear();
        pendingSpawnCellKeys.clear();
    }

    public static long packCellKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL)
            | (((long) y & 0xFFFL) << 21)
            | (((long) z & 0x1FFFFFL) << 33);
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new BuildingStaffPreviewPlayerComponent();
    }
}
