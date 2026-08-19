package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.AetherhavenNpcTeleport;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class WorldNpcSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double STATIC_SNAP_DISTANCE_SQ = 0.35 * 0.35;

    private WorldNpcSpawnService() {}

    /**
     * Ensures a placement exists as a live entity. Safe to call from world.execute / commands — never from a tick
     * system that writes Store inline without deferring.
     */
    @Nullable
    public static UUID ensurePlacement(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldNpcPlacementRecord placement
    ) {
        String placementId = placement.placementIdOrEmpty();
        String logicalRoleId = WorldNpcSpawnRoles.toLogicalRoleId(placement.npcRoleIdOrEmpty());
        String spawnRoleId = WorldNpcSpawnRoles.toSpawnRoleId(logicalRoleId);
        if (placementId.isEmpty() || logicalRoleId.isEmpty()) {
            LOGGER.atWarning().log("World NPC ensure skipped: missing placementId or npcRoleId");
            return null;
        }
        if (!logicalRoleId.equals(placement.npcRoleIdOrEmpty())) {
            placement.setNpcRoleId(logicalRoleId);
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return null;
        }
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        UUID existing = placement.entityUuidOrNull();
        if (existing != null) {
            EntityPresenceUtil.EntityPresence presence = EntityPresenceUtil.resolve(store, existing);
            if (EntityPresenceUtil.isLoadedLive(presence)) {
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(existing);
                if (ref != null && ref.isValid()) {
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    String liveRole = npc != null ? npc.getRoleName() : "";
                    if (liveRole != null
                        && !liveRole.isBlank()
                        && !WorldNpcSpawnRoles.matchesSpawnedRole(logicalRoleId, liveRole)) {
                        // Role changed on the placement — must despawn and respawn with the new role.
                        store.removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE);
                        placement.setEntityUuid(null);
                        registry.upsertPlacement(placement);
                    } else {
                        attachComponents(store, ref, placement);
                        return existing;
                    }
                }
            }
            if (EntityPresenceUtil.isUnknownUnloaded(presence)) {
                return existing;
            }
        }
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atWarning().log("World NPC spawn failed: NPCPlugin unavailable");
            return null;
        }
        Vector3d pos = new Vector3d(placement.getX(), placement.getY(), placement.getZ());
        float yawRadians = (float) Math.toRadians(placement.getYawDegrees());
        Rotation3f rotation = new Rotation3f(0f, yawRadians, 0f);
        var pair = npcPlugin.spawnNPC(store, spawnRoleId, null, pos, rotation);
        if (pair == null) {
            // Custom / third-party roles may not have an Aetherhaven_World_* twin — fall back.
            if (!spawnRoleId.equals(logicalRoleId)) {
                pair = npcPlugin.spawnNPC(store, logicalRoleId, null, pos, rotation);
            }
        }
        if (pair == null) {
            LOGGER.atWarning().log(
                "World NPC spawn failed for role %s (spawn %s) placement %s",
                logicalRoleId,
                spawnRoleId,
                placementId
            );
            return null;
        }
        Ref<EntityStore> ref = pair.first();
        attachComponents(store, ref, placement);
        NpcSpawnOriginUtil.attach(store, ref, "WORLD_NPC", "placementId=" + placementId, world, pos);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        UUID entityUuid = uc.getUuid();
        placement.setEntityUuid(entityUuid);
        registry.upsertPlacement(placement);
        WorldNpcPersistence.save(world, plugin, registry);
        LOGGER.atInfo().log("Spawned world NPC %s (%s -> %s) as %s", placementId, logicalRoleId, spawnRoleId, entityUuid);
        return entityUuid;
    }

    /** Despawn then spawn again so role, name, and idle hold refresh. */
    public static void respawnPlacement(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String placementId
    ) {
        world.execute(() -> {
            WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
            WorldNpcPlacementRecord placement = registry.findPlacement(placementId);
            if (placement == null) {
                return;
            }
            despawnPlacement(world, plugin, placementId);
            WorldNpcPlacementRecord fresh = registry.findPlacement(placementId);
            if (fresh != null) {
                ensurePlacement(world, plugin, fresh);
            }
        });
    }

    private static void attachComponents(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull WorldNpcPlacementRecord placement
    ) {
        String placementId = placement.placementIdOrEmpty();
        String logicalRoleId = WorldNpcSpawnRoles.toLogicalRoleId(placement.npcRoleIdOrEmpty());
        store.putComponent(
            ref,
            WorldNpcBinding.getComponentType(),
            new WorldNpcBinding(placementId, logicalRoleId)
        );
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(placementId));
        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        // Frozen skips role ticks (no dialogue). World idle roles stand still; snap only corrects drift.
        store.tryRemoveComponent(ref, Frozen.getComponentType());
        applyDisplayName(store, ref, placement);
        if (placement.scheduleModeOrDefault() == WorldNpcScheduleMode.STATIC) {
            applyStaticIdleHold(store, ref, placement);
        }
    }

    static void applyDisplayName(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull WorldNpcPlacementRecord placement
    ) {
        String name = placement.displayNameOrEmpty();
        if (name.isEmpty()) {
            store.tryRemoveComponent(ref, PersistentDisplayName.getComponentType());
            return;
        }
        store.putComponent(ref, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(name)));
    }

    /**
     * Keep static hub NPCs at their pose in Idle so they can still run dialogue sensors (unlike {@link Frozen}).
     * Skips entirely while the NPC is in {@code $Interaction} so dialogue facing is not yanked back to spawn yaw.
     */
    public static void applyStaticIdleHold(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull WorldNpcPlacementRecord placement
    ) {
        store.tryRemoveComponent(ref, Frozen.getComponentType());
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        // getStateName() is "State.subState", not the bare id — use inState like guild-hall / face visuals.
        if (npc != null && NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d pos = tc.getPosition();
            double dx = pos.x - placement.getX();
            double dy = pos.y - placement.getY();
            double dz = pos.z - placement.getZ();
            float yawRad = (float) Math.toRadians(placement.getYawDegrees());
            if (dx * dx + dy * dy + dz * dz > STATIC_SNAP_DISTANCE_SQ) {
                // Position drifted — teleport home with placement facing.
                AetherhavenNpcTeleport.apply(
                    ref,
                    store,
                    Teleport.createExact(
                        new Vector3d(placement.getX(), placement.getY(), placement.getZ()),
                        new Rotation3f(0f, yawRad, 0f)
                    )
                );
            }
            // Do not re-assert yaw every hold while on-spot; that fights dialogue body/head turn.
        }
        if (npc != null && npc.getRole() != null) {
            try {
                npc.getRole().getStateSupport().setState(ref, "Idle", null, store);
            } catch (RuntimeException e) {
                LOGGER.atFine().withCause(e).log("World NPC idle state set failed for %s", placement.placementIdOrEmpty());
            }
        }
        NpcStandStill.forceIdleMovementStates(store, ref);
    }

    public static void despawnPlacement(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String placementId
    ) {
        WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
        WorldNpcPlacementRecord placement = registry.findPlacement(placementId);
        if (placement == null) {
            return;
        }
        UUID entityUuid = placement.entityUuidOrNull();
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store != null && entityUuid != null) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE);
            }
        }
        placement.setEntityUuid(null);
        registry.upsertPlacement(placement);
        WorldNpcPersistence.save(world, plugin, registry);
    }

    public static void removePlacement(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String placementId
    ) {
        world.execute(() -> {
            despawnPlacement(world, plugin, placementId);
            WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
            registry.removePlacement(placementId);
            WorldNpcPersistence.save(world, plugin, registry);
        });
    }

    /** Schedule ensure for all placements after world load (deferred). */
    public static void reconcileAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> {
            WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
            for (WorldNpcPlacementRecord placement : registry.allPlacements()) {
                ensurePlacement(world, plugin, placement);
            }
        });
    }
}
