package com.hexvane.aetherhaven.villager.audit;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Public API for villager disappearance audit logging. */
public final class VillagerAuditService {
    private static final long DEATH_DEDUP_MS = 5_000L;
    private static final Map<UUID, Long> RECENT_DEATH_BY_ENTITY = new ConcurrentHashMap<>();

    private VillagerAuditService() {}

    public static void logDeath(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull String source,
        @Nullable String deathCause
    ) {
        World world = store.getExternalData().getWorld();
        VillagerAuditSnapshot snap = VillagerAuditSnapshot.fromEntity(store, victimRef, plugin);
        RECENT_DEATH_BY_ENTITY.put(snap.getEntityUuid(), System.currentTimeMillis());
        write(plugin, world, snap, VillagerAuditEvent.EventType.DEATH, source, deathCause, "");
    }

    public static void logRemoved(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String source
    ) {
        if (shouldSkipRemovedAfterRecentDeath(ref, store)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        VillagerAuditSnapshot snap = VillagerAuditSnapshot.fromEntity(store, ref, plugin);
        write(plugin, world, snap, VillagerAuditEvent.EventType.REMOVED, source, null, "");
    }

    public static void logDetectedMissing(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nonnull UUID townId,
        @Nullable String roleId,
        @Nullable String bindingKind,
        @Nullable String displayNameHint,
        @Nonnull String source,
        @Nonnull String notes
    ) {
        VillagerAuditSnapshot snap =
            VillagerAuditSnapshot.fromTrackedUuid(
                store,
                world,
                plugin,
                entityUuid,
                townId,
                roleId,
                bindingKind,
                displayNameHint
            );
        write(plugin, world, snap, VillagerAuditEvent.EventType.DETECTED_MISSING, source, null, notes);
    }

    public static boolean shouldSkipRemovedAfterRecentDeath(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        var uc = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        Long loggedAt = RECENT_DEATH_BY_ENTITY.get(uc.getUuid());
        if (loggedAt == null) {
            return false;
        }
        if (System.currentTimeMillis() - loggedAt > DEATH_DEDUP_MS) {
            RECENT_DEATH_BY_ENTITY.remove(uc.getUuid(), loggedAt);
            return false;
        }
        var death = store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType());
        return death != null;
    }

    private static void write(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull VillagerAuditSnapshot snap,
        @Nonnull VillagerAuditEvent.EventType event,
        @Nonnull String source,
        @Nullable String deathCause,
        @Nonnull String notes
    ) {
        VillagerAuditEvent auditEvent =
            new VillagerAuditEvent(
                System.currentTimeMillis(),
                event,
                snap.getEntityUuid().toString(),
                snap.getDisplayName(),
                snap.getRoleId(),
                snap.getBindingKind(),
                snap.getTownId().toString(),
                snap.getTownName(),
                snap.getWorldName(),
                snap.getX(),
                snap.getY(),
                snap.getZ(),
                source,
                deathCause,
                notes
            );
        VillagerAuditLogWriter.append(plugin, world, auditEvent);
    }
}
