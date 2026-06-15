package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class RtsSelectionService {
    private RtsSelectionService() {}

    /** Replace selection with guards inside the world-space box (may be empty). */
    public static void selectGuardsInBox(
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull TownRecord town,
        double minX,
        double maxX,
        double minZ,
        double maxZ
    ) {
        session.clearSelection();
        addGuardsInWorldBox(store, session, town, minX, maxX, minZ, maxZ);
    }

    /** Add guards whose ground footprint projects inside the drawn HUD drag rectangle. */
    public static void addGuardsInScreenRect(
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull TownRecord town,
        float screenX0,
        float screenY0,
        float screenX1,
        float screenY1
    ) {
        for (Ref<EntityStore> ref : RtsGuardDirectory.livingGuardRefs(town, store)) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            org.joml.Vector3d p = tc.getPosition();
            if (!RtsScreenPickUtil.guardInHudSelectionRect(p.x, p.z, session)) {
                continue;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                session.getSelectedGuardUuids().add(uc.getUuid());
            }
        }
    }

    /** Add guards inside the world-space box without clearing existing selection. */
    public static void addGuardsInWorldBox(
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull TownRecord town,
        double minX,
        double maxX,
        double minZ,
        double maxZ
    ) {
        addGuardsInWorldColumn(store, session, town, minX, maxX, minZ, maxZ);
    }

    /** Tall X/Z column selection — ignores entity Y so guards at any height in the column match. */
    public static void addGuardsInWorldColumn(
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull TownRecord town,
        double minX,
        double maxX,
        double minZ,
        double maxZ
    ) {
        for (Ref<EntityStore> ref : RtsGuardDirectory.livingGuardRefs(town, store)) {
            if (!RtsGuardDirectory.inWorldColumn(ref, store, minX, maxX, minZ, maxZ)) {
                continue;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                session.getSelectedGuardUuids().add(uc.getUuid());
            }
        }
    }

    /** Select every living town guard; deselects all others. */
    public static void selectAllGuards(
        @Nonnull Store<EntityStore> store,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull TownRecord town
    ) {
        session.clearSelection();
        for (Ref<EntityStore> ref : RtsGuardDirectory.livingGuardRefs(town, store)) {
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                session.getSelectedGuardUuids().add(uc.getUuid());
            }
        }
    }

    /** Select only guards matching the profile; deselects all others. */
    public static void selectOnlyByProfile(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull RtsCommandPlayerComponent session,
        @Nonnull TownRecord town,
        @Nonnull ProfileFilter filter
    ) {
        session.clearSelection();
        for (Ref<EntityStore> ref : RtsGuardDirectory.livingGuardRefs(town, store)) {
            String profile = RtsGuardDirectory.guardProfileId(ref, store, town, plugin);
            if (profile == null) {
                continue;
            }
            boolean match = switch (filter) {
                case KNIGHT -> RtsGuardDirectory.isKnight(profile) || RtsGuardDirectory.isRogue(profile);
                case ARCHER -> RtsGuardDirectory.isArcher(profile);
                case MAGE -> RtsGuardDirectory.isMage(profile);
            };
            if (!match) {
                continue;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                session.getSelectedGuardUuids().add(uc.getUuid());
            }
        }
    }

    public static void toggleGuard(@Nonnull RtsCommandPlayerComponent session, @Nonnull UUID guardUuid) {
        List<UUID> selected = session.getSelectedGuardUuids();
        if (selected.contains(guardUuid)) {
            selected.remove(guardUuid);
        } else {
            selected.add(guardUuid);
        }
    }

    public static boolean isSelected(@Nonnull RtsCommandPlayerComponent session, @Nonnull UUID guardUuid) {
        return session.getSelectedGuardUuids().contains(guardUuid);
    }

    /** Resolve a ground pick from session focus and optional client block. */
    @Nullable
    public static RtsScreenPickUtil.GroundPick resolveClickPick(
        @Nonnull RtsCommandPlayerComponent session,
        @Nullable Vector3i clientBlock,
        @Nullable org.joml.Vector2fc screenPoint
    ) {
        return RtsScreenPickUtil.resolve(session, clientBlock, screenPoint);
    }

    @Nullable
    public static RtsScreenPickUtil.GroundPick resolveClickPick(
        @Nonnull RtsCommandPlayerComponent session,
        @Nullable Vector3i clientBlock,
        @Nullable org.joml.Vector2dc screenPoint
    ) {
        return RtsScreenPickUtil.resolve(session, clientBlock, screenPoint);
    }

    public static boolean isSprinting(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        var ms = store.getComponent(playerRef, com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
        if (ms == null) {
            return false;
        }
        return ms.getMovementStates().sprinting;
    }

    @Nullable
    public static TownRecord townForSession(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull RtsCommandPlayerComponent session
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        try {
            return tm.getTown(UUID.fromString(session.getTownId()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public enum ProfileFilter {
        KNIGHT,
        ARCHER,
        MAGE
    }
}
