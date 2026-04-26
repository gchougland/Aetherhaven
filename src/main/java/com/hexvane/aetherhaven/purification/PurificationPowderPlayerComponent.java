package com.hexvane.aetherhaven.purification;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks per-player purification preview entities (not serialized; re-created when holding the item).
 */
public final class PurificationPowderPlayerComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PurificationPowderPlayerComponent> CODEC =
        BuilderCodec.builder(PurificationPowderPlayerComponent.class, PurificationPowderPlayerComponent::new)
            .documentation("Transient purification powder visualization state (previews are not serialized).")
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PurificationPowderPlayerComponent> componentType;

    /** Last tick we scanned for legacy beacons (expensive). */
    private long lastLegacyScanTick = -1L;

    @Nonnull
    private final List<PurificationSpawnSupport.Target> cachedLegacy = new ArrayList<>();

    @Nonnull
    private final Map<UUID, UUID> spawnEntityIdToPreviewEntityId = new HashMap<>();

    /** Spawns a preview is being added asynchronously. */
    @Nonnull
    private final Set<UUID> pendingPreviewSpawn = new HashSet<>();

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                PurificationPowderPlayerComponent.class,
                "AetherhavenPurificationPowder",
                PurificationPowderPlayerComponent.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, PurificationPowderPlayerComponent> getComponentType() {
        ComponentType<EntityStore, PurificationPowderPlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PurificationPowderPlayerComponent not registered");
        }
        return t;
    }

    public long getLastLegacyScanTick() {
        return lastLegacyScanTick;
    }

    public void setLastLegacyScanTick(long lastLegacyScanTick) {
        this.lastLegacyScanTick = lastLegacyScanTick;
    }

    @Nonnull
    public List<PurificationSpawnSupport.Target> getCachedLegacy() {
        return cachedLegacy;
    }

    public void clearCachedLegacy() {
        cachedLegacy.clear();
    }

    @Nonnull
    public Map<UUID, UUID> getSpawnEntityIdToPreviewEntityId() {
        return spawnEntityIdToPreviewEntityId;
    }

    @Nonnull
    public Set<UUID> getPendingPreviewSpawn() {
        return pendingPreviewSpawn;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new PurificationPowderPlayerComponent();
    }
}
