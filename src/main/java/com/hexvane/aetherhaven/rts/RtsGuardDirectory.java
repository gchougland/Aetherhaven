package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class RtsGuardDirectory {
    private RtsGuardDirectory() {}

    public static boolean townHasLivingGuard(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        return !livingGuardRefs(town, store).isEmpty();
    }

    @Nonnull
    public static List<Ref<EntityStore>> livingGuardRefs(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        List<Ref<EntityStore>> out = new ArrayList<>();
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID u = rec.getEntityUuid();
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = findByUuid(store, u);
            if (ref != null && isTownGuard(ref, store, town.getTownId())) {
                out.add(ref);
            }
        }
        return out;
    }

    @Nullable
    public static Ref<EntityStore> findByUuid(@Nonnull Store<EntityStore> store, @Nonnull UUID uuid) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    public static boolean isTownGuard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId
    ) {
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return false;
        }
        try {
            return townId.equals(binding.getTownId());
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Nullable
    public static String guardProfileId(
        @Nonnull Ref<EntityStore> guardRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin
    ) {
        UUIDComponent uc = store.getComponent(guardRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(uc.getUuid())) {
                String id = rec.getEquipmentProfileId();
                return id != null && !id.isBlank() ? id : "guard_knight";
            }
        }
        return GuardHireService.equipmentProfileForNpc(plugin, guardRef, store);
    }

    public static boolean isKnight(@Nonnull String profileId) {
        return profileId.contains("knight");
    }

    public static boolean isArcher(@Nonnull String profileId) {
        return profileId.contains("archer");
    }

    public static boolean isMage(@Nonnull String profileId) {
        return profileId.contains("mage");
    }

    public static boolean isRogue(@Nonnull String profileId) {
        return profileId.contains("rogue");
    }

    public static boolean inBox(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        double minX,
        double maxX,
        double minZ,
        double maxZ
    ) {
        return inWorldColumn(ref, store, minX, maxX, minZ, maxZ);
    }

    /** X/Z column hit test with no Y bounds (full height). */
    public static boolean inWorldColumn(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        double minX,
        double maxX,
        double minZ,
        double maxZ
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return false;
        }
        Vector3d p = tc.getPosition();
        return p.x >= minX && p.x <= maxX && p.z >= minZ && p.z <= maxZ;
    }

    private static final double GROUND_CLICK_RADIUS = 10.0;

    @Nullable
    public static Ref<EntityStore> guardFromTarget(
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId
    ) {
        if (!targetRef.isValid() || !isTownGuard(targetRef, store, townId)) {
            return null;
        }
        if (store.getComponent(targetRef, NPCEntity.getComponentType()) == null) {
            return null;
        }
        return targetRef;
    }

    /** Top-down LookAtPlane clicks only return a ground block — pick the nearest guard at that point. */
    @Nullable
    public static Ref<EntityStore> guardNearGroundPoint(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        double clickX,
        double clickZ
    ) {
        Ref<EntityStore> best = null;
        double bestDistSq = GROUND_CLICK_RADIUS * GROUND_CLICK_RADIUS;
        for (Ref<EntityStore> ref : livingGuardRefs(town, store)) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            double dx = p.x - clickX;
            double dz = p.z - clickZ;
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = ref;
            }
        }
        return best;
    }
}
