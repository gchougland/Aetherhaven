package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;
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
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
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
            movePreviewTo(playerRef, ref, store, playerUuid, visualPos);
            PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
            if (plot != null && plot.isGizmoMoveActive()) {
                moveEntityOverlay(playerRef, playerUuid, plot.getClientPrefabPreviewPayload(), visualPos);
                return;
            }
            PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
            if (prop != null && prop.isGizmoMoveActive()) {
                movePropEntityOverlay(playerRef, playerUuid, prop, offset);
            }
            return;
        }
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            applyPlotOffset(playerRef, ref, store, plot, offset);
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            applyCharterOffset(playerRef, ref, store, charter, offset);
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            applyPropOffset(playerRef, ref, store, prop, offset);
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
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull Vector3d visualPos
    ) {
        ensurePreviewExists(playerRef, ref, store, playerUuid);
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
        @Nonnull Ref<EntityStore> ref,
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
        ensurePreviewExists(playerRef, ref, store, playerRef.getUuid());
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
        moveEntityOverlay(playerRef, playerRef.getUuid(), payload, visualPos);
    }

    private static void applyPropOffset(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
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
        ensurePreviewExists(playerRef, ref, store, playerRef.getUuid());
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
        movePropEntityOverlay(playerRef, playerRef.getUuid(), session, offset);
    }

    private static void applyCharterOffset(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CharterRelocationSession session,
        @Nonnull Vector3d offset
    ) {
        ensurePreviewExists(playerRef, ref, store, playerRef.getUuid());
        Vector3i anchor = session.getAnchor();
        Vector3d visualPos = new Vector3d(anchor.x + offset.x, anchor.y + offset.y, anchor.z + offset.z);
        Rotation3f rotation = new Rotation3f(0f, (float) session.getBlockHorizontalRotation().getRadians(), 0f);
        for (Ref<EntityStore> previewRef : session.getPreviewEntityRefs()) {
            if (previewRef == null || !previewRef.isValid()) {
                continue;
            }
            TransformComponentUtil.replacePreservingChunk(previewRef, store, visualPos, rotation);
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
            TransformComponentUtil.replacePreservingChunk(
                previewRef,
                store,
                new Vector3d(transform.getPosition()),
                rotation
            );
        }
    }

    private static void ensurePreviewExists(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid
    ) {
        if (firstPreviewRef(playerUuid) != null) {
            return;
        }
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
            if (def == null) {
                return;
            }
            Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(plot.getAnchor(), plot.getPrefabYaw());
            PlotPlacementClientPrefabPreview.sendMoveOrFull(
                playerRef,
                def.getPrefabPath(),
                plot.getRotationSteps(),
                prefabOrigin,
                plot.getPrefabYaw(),
                plot
            );
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            PlacementGizmoPreviewRefresh.refreshCharter(ref, store, playerRef, charter);
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop == null || !prop.isGizmoMoveActive()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PropDefinition def = plugin.getPropCatalog().get(prop.getPropId());
        if (def == null) {
            return;
        }
        PlotPlacementClientPrefabPreview.sendMoveOrFullStandalone(
            playerRef,
            store,
            prop.getPreviewEntityRefs(),
            def.getPrefabPath(),
            prop.getRotationSteps(),
            prop.getAnchor(),
            prop.getYaw()
        );
    }

    private static void moveEntityOverlay(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID playerUuid,
        @Nullable PlotPlacementClientPrefabPreview.Payload payload,
        @Nonnull Vector3d visualPos
    ) {
        if (payload == null || !PlotPlacementClientPrefabPreview.hasEntityOverlay(payload)) {
            return;
        }
        Vector3i floor = new Vector3i(MathUtil.floor(visualPos.x), MathUtil.floor(visualPos.y), MathUtil.floor(visualPos.z));
        if (PlacementGizmoPivot.takeOverlayFloorIfChanged(playerUuid, floor)) {
            PlotPlacementClientPrefabPreview.sendEntityOverlayMoved(
                playerRef,
                new Vector3f(floor.x, floor.y, floor.z),
                payload
            );
            return;
        }
        PlotPlacementClientPrefabPreview.sendEntityOverlayAt(
            playerRef,
            new Vector3f((float) visualPos.x, (float) visualPos.y, (float) visualPos.z)
        );
    }

    /**
     * Keep the existing entity overlay and move its paste origin the same way the placement page does.
     * Hide-and-resend makes the client treat an entity-only preview as centered on {@code position}.
     */
    private static void movePropEntityOverlay(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID playerUuid,
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
        if (payload == null || !PlotPlacementClientPrefabPreview.hasEntityOverlay(payload)) {
            return;
        }
        Vector3i visualOrigin = new Vector3i(
            session.getAnchor().x + (int) Math.round(offset.x),
            session.getAnchor().y + (int) Math.round(offset.y),
            session.getAnchor().z + (int) Math.round(offset.z)
        );
        Vector3i overlayFloor =
            PlotPlacementClientPrefabPreview.resolveClientPreviewPosition(
                visualOrigin,
                payload,
                session.getYaw()
            );
        if (!PlacementGizmoPivot.takeOverlayFloorIfChanged(playerUuid, overlayFloor)) {
            return;
        }
        PlotPlacementClientPrefabPreview.sendEntityOverlayPositionOnly(
            playerRef,
            visualOrigin,
            payload,
            session.getYaw()
        );
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
