package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.prefab.AetherhavenWorldPrefabPreview;
import com.hexvane.aetherhaven.prop.PropCatalog;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropPlacementSession;
import com.hexvane.aetherhaven.prop.PropPlacementSessions;
import com.hexvane.aetherhaven.prop.PropPlacementValidator;
import com.hexvane.aetherhaven.prop.PropPrefabOps;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

/** Moves placement holograms smoothly while the Point tool gizmo is dragged. */
final class PlacementGizmoLivePreview {
    private PlacementGizmoLivePreview() {}

    @Nullable
    static Vector3d committedCenter(@Nonnull UUID playerUuid) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            return centerForPlot(plot);
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            return centerForCharter(charter);
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            return centerForProp(prop);
        }
        return null;
    }

    static void applyDragOffset(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d offset
    ) {
        UUID playerUuid = playerRef.getUuid();
        Vector3d hologramBase = PlacementGizmoPivot.dragHologramBase(playerUuid);
        if (hologramBase != null) {
            Vector3d visualPos = new Vector3d(hologramBase).add(offset);
            movePreviewTo(store, playerUuid, visualPos);
            PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
            if (plot != null && plot.isGizmoMoveActive()) {
                PlotPlacementClientPrefabPreview.Payload payload = plot.getClientPrefabPreviewPayload();
                if (payload != null && PlotPlacementClientPrefabPreview.hasEntityOverlay(payload)) {
                    PlotPlacementClientPrefabPreview.sendEntityOverlayAt(
                        playerRef,
                        new Vector3f((float) visualPos.x, (float) visualPos.y, (float) visualPos.z)
                    );
                }
            }
            return;
        }
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            applyPlotOffset(playerRef, store, plot, offset);
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            applyCharterOffset(store, charter, offset);
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            applyPropOffset(store, prop, offset);
        }
    }

    @Nullable
    static Vector3d currentHologramPosition(@Nonnull Store<EntityStore> store, @Nonnull UUID playerUuid) {
        Ref<EntityStore> previewRef = firstPreviewRef(playerUuid);
        if (previewRef == null || !previewRef.isValid()) {
            return null;
        }
        TransformComponent transform = store.getComponent(previewRef, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        var pos = transform.getPosition();
        return new Vector3d(pos.x, pos.y, pos.z);
    }

    private static void movePreviewTo(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull Vector3d visualPos
    ) {
        Ref<EntityStore> previewRef = firstPreviewRef(playerUuid);
        if (previewRef == null) {
            return;
        }
        float yaw = 0f;
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            yaw = (float) plot.getPrefabYaw().getRadians();
        } else {
            PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
            if (prop != null && prop.isGizmoMoveActive()) {
                yaw = (float) prop.getYaw().getRadians();
            } else {
                CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
                if (charter != null && charter.isGizmoMoveActive()) {
                    yaw = (float) charter.getBlockHorizontalRotation().getRadians();
                }
            }
        }
        AetherhavenWorldPrefabPreview.updatePosition(
            store,
            previewRef,
            visualPos,
            new Rotation3f(0f, yaw, 0f)
        );
    }

    @Nullable
    private static Ref<EntityStore> firstPreviewRef(@Nonnull UUID playerUuid) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            return firstValid(plot.getPreviewEntityRefs());
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            return firstValid(charter.getPreviewEntityRefs());
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            return firstValid(prop.getPreviewEntityRefs());
        }
        return null;
    }

    static void applyDragRotation(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        float yawRadians
    ) {
        Rotation3f rotation = new Rotation3f(0f, yawRadians, 0f);
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            applyRotation(store, plot.getPreviewEntityRefs(), rotation);
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            applyRotation(store, charter.getPreviewEntityRefs(), rotation);
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            applyRotation(store, prop.getPreviewEntityRefs(), rotation);
        }
    }

    @Nullable
    private static Vector3d centerForPlot(@Nonnull PlotPlacementSession session) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            return null;
        }
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(session.getAnchor(), session.getPrefabYaw());
        IPrefabBuffer buf = com.hexvane.aetherhaven.prefab.PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buf == null) {
            return null;
        }
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf, def);
        return PlacementGizmoPivot.centerOf(fp);
    }

    @Nullable
    private static Vector3d centerForCharter(@Nonnull CharterRelocationSession session) {
        Vector3i anchor = session.getAnchor();
        return new Vector3d(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
    }

    @Nullable
    private static Vector3d centerForProp(@Nonnull PropPlacementSession session) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        PropCatalog catalog = plugin.getPropCatalog();
        IPrefabBuffer buffer =
            PropPlacementValidator.resolveValidatedBuffer(
                session.getWorld(), catalog, session.getPropId(), session.getAnchor(), session.getYaw()
            );
        if (buffer == null) {
            return null;
        }
        PlotFootprintRecord fp = PropPrefabOps.placementOutlineFootprint(session.getAnchor(), session.getYaw(), buffer);
        return PlacementGizmoPivot.centerOf(fp);
    }

    private static void applyPlotOffset(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotPlacementSession session,
        @Nonnull Vector3d offset
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            return;
        }
        PlotPlacementClientPrefabPreview.Payload payload = session.getClientPrefabPreviewPayload();
        if (payload == null) {
            return;
        }
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(session.getAnchor(), session.getPrefabYaw());
        Vector3i spawnCorner =
            PlotPlacementClientPrefabPreview.flooredOrigin(
                PlotPlacementClientPrefabPreview.resolveClientPreviewPosition(
                    prefabOrigin,
                    payload,
                    session.getPrefabYaw()
                )
            );
        Vector3d visualPos = new Vector3d(spawnCorner).add(offset);
        Ref<EntityStore> previewRef = firstValid(session.getPreviewEntityRefs());
        if (previewRef == null) {
            return;
        }
        AetherhavenWorldPrefabPreview.updatePosition(
            store,
            previewRef,
            visualPos,
            AetherhavenWorldPrefabPreview.rotationFromYaw(session.getPrefabYaw())
        );
        if (PlotPlacementClientPrefabPreview.hasEntityOverlay(payload)) {
            Vector3f overlayPos = new Vector3f((float) visualPos.x, (float) visualPos.y, (float) visualPos.z);
            PlotPlacementClientPrefabPreview.sendEntityOverlayAt(playerRef, overlayPos);
        }
    }

    private static void applyPropOffset(
        @Nonnull Store<EntityStore> store,
        @Nonnull PropPlacementSession session,
        @Nonnull Vector3d offset
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PropDefinition def = plugin.getPropCatalog().get(session.getPropId());
        if (def == null) {
            return;
        }
        PlotPlacementClientPrefabPreview.Payload payload =
            PlotPlacementClientPrefabPreview.loadPayload(def.getPrefabPath(), session.getRotationSteps());
        if (payload == null) {
            return;
        }
        Vector3i spawnCorner =
            PlotPlacementClientPrefabPreview.flooredOrigin(
                PlotPlacementClientPrefabPreview.resolveClientPreviewPosition(
                    session.getAnchor(),
                    payload,
                    session.getYaw()
                )
            );
        Vector3d visualPos = new Vector3d(spawnCorner).add(offset);
        Ref<EntityStore> previewRef = firstValid(session.getPreviewEntityRefs());
        if (previewRef == null) {
            return;
        }
        AetherhavenWorldPrefabPreview.updatePosition(
            store,
            previewRef,
            visualPos,
            AetherhavenWorldPrefabPreview.rotationFromYaw(session.getYaw())
        );
    }

    private static void applyCharterOffset(
        @Nonnull Store<EntityStore> store,
        @Nonnull CharterRelocationSession session,
        @Nonnull Vector3d offset
    ) {
        Vector3i anchor = session.getAnchor();
        Vector3d visualPos = new Vector3d(anchor.x + offset.x, anchor.y + offset.y, anchor.z + offset.z);
        for (Ref<EntityStore> previewRef : session.getPreviewEntityRefs()) {
            if (previewRef == null || !previewRef.isValid()) {
                continue;
            }
            TransformComponent transform = store.getComponent(previewRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            transform.teleportPosition(visualPos);
            store.putComponent(previewRef, TransformComponent.getComponentType(), transform);
        }
    }

    private static void applyRotation(
        @Nonnull Store<EntityStore> store,
        @Nonnull List<Ref<EntityStore>> previewRefs,
        @Nonnull Rotation3f rotation
    ) {
        for (Ref<EntityStore> previewRef : previewRefs) {
            if (previewRef == null || !previewRef.isValid()) {
                continue;
            }
            TransformComponent transform = store.getComponent(previewRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            transform.teleportRotation(rotation);
            store.putComponent(previewRef, TransformComponent.getComponentType(), transform);
        }
    }

    @Nullable
    private static Ref<EntityStore> firstValid(@Nonnull List<Ref<EntityStore>> previewRefs) {
        for (Ref<EntityStore> previewRef : previewRefs) {
            if (previewRef != null && previewRef.isValid()) {
                return previewRef;
            }
        }
        return null;
    }
}
