package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.prop.PropCatalog;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropPlacementSession;
import com.hexvane.aetherhaven.prop.PropPlacementValidator;
import com.hexvane.aetherhaven.prop.PropPlacementWireframeOverlay;
import com.hexvane.aetherhaven.prop.PropPrefabOps;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Refreshes placement previews after the Point tool gizmo moves a hologram. */
public final class PlacementGizmoPreviewRefresh {
    private PlacementGizmoPreviewRefresh() {}

    public static void refreshPlot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotPlacementSession session
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            PlotPlacementClientPrefabPreview.clearWorldPreview(store, session);
            PlotPlacementClientPrefabPreview.hide(playerRef);
            PlotPlacementWireframeOverlay.clearFor(playerRef);
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town =
            TownPlayerResolution.resolveTownForPlotPlacement(tm, world.getName(), uc.getUuid(), session.getAnchor());
        String placementErr;
        if (town == null) {
            placementErr = "You need a town (place a charter) first.";
        } else if (!town.playerCanPlacePlots(uc.getUuid())) {
            placementErr = "You do not have permission to place buildings for this town.";
        } else {
            placementErr =
                PlotPlacementValidator.validate(
                    world,
                    tm,
                    town,
                    uc.getUuid(),
                    session.getAnchor(),
                    session.getPrefabYaw(),
                    def,
                    plugin,
                    session.getMovePlotId()
                );
        }
        boolean placementValid = placementErr == null;
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(session.getAnchor(), session.getPrefabYaw());
        IPrefabBuffer buf = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buf == null) {
            PlotPlacementClientPrefabPreview.clearWorldPreview(store, session);
            PlotPlacementClientPrefabPreview.hide(playerRef);
            PlotPlacementWireframeOverlay.clearFor(playerRef);
            return;
        }
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf, def);
        boolean ok =
            PlotPlacementClientPrefabPreview.sendMoveOrFull(
                playerRef,
                def.getPrefabPath(),
                session.getRotationSteps(),
                prefabOrigin,
                session.getPrefabYaw(),
                session
            );
        if (!ok) {
            PlotPlacementClientPrefabPreview.clearWorldPreview(store, session);
            PlotPlacementClientPrefabPreview.hide(playerRef);
        }
        boolean placerNeedFull =
            !session.hasSpectatorPreviewActive()
                || !session.getConstructionId().equals(session.getLastSpectatorPreviewConstructionId())
                || session.getRotationSteps() != session.getLastSpectatorPreviewRotationSteps();
        PlotPlacementPreviewSync.syncSpectators(world, uc.getUuid(), session, def, prefabOrigin, placerNeedFull);
        PlotPlacementWireframeOverlay.send(playerRef, fp, placementValid, town);
        if (session.isGizmoMoveActive()) {
            PlacementGizmoService.syncPointGizmo(playerRef, fp, (float) session.getPrefabYaw().getRadians());
        }
    }

    public static void refreshProp(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull PropPlacementSession session
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PropCatalog catalog = plugin.getPropCatalog();
        World world = store.getExternalData().getWorld();
        IPrefabBuffer buffer =
            PropPlacementValidator.resolveValidatedBuffer(
                world, catalog, session.getPropId(), session.getAnchor(), session.getYaw()
            );
        if (buffer == null) {
            PropPlacementWireframeOverlay.clearFor(playerRef);
            PlotPlacementClientPrefabPreview.clearWorldPreview(store, session.getPreviewEntityRefs());
            PlotPlacementClientPrefabPreview.hide(playerRef);
            return;
        }
        String err = PropPlacementValidator.validate(world, catalog, session.getPropId(), session.getAnchor(), session.getYaw());
        PlotFootprintRecord fp = PropPrefabOps.placementOutlineFootprint(session.getAnchor(), session.getYaw(), buffer);
        PropPlacementWireframeOverlay.send(playerRef, fp, err == null);
        PropDefinition def = catalog.get(session.getPropId());
        if (def != null) {
            boolean ghostOk =
                PlotPlacementClientPrefabPreview.sendMoveOrFullStandalone(
                    playerRef,
                    store,
                    session.getPreviewEntityRefs(),
                    def.getPrefabPath(),
                    session.getRotationSteps(),
                    session.getAnchor(),
                    session.getYaw()
                );
            if (!ghostOk) {
                PlotPlacementClientPrefabPreview.clearWorldPreview(store, session.getPreviewEntityRefs());
                PlotPlacementClientPrefabPreview.hide(playerRef);
            }
        }
        if (session.isGizmoMoveActive()) {
            PlacementGizmoService.syncPointGizmo(playerRef, fp, (float) session.getYaw().getRadians());
        }
    }

    /** Moves the white footprint outline with the hologram while the Point gizmo is dragged. */
    public static void refreshPlotWireframeDuringDrag(
        @Nonnull PlayerRef playerRef,
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
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(session.getAnchor(), session.getPrefabYaw());
        IPrefabBuffer buf = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buf == null) {
            return;
        }
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf, def);
        int dx = (int) Math.round(offset.x);
        int dy = (int) Math.round(offset.y);
        int dz = (int) Math.round(offset.z);
        if (!PlacementGizmoPivot.takeWireframeShiftIfChanged(playerRef.getUuid(), dx, dy, dz)) {
            return;
        }
        PlotPlacementWireframeOverlay.send(playerRef, shiftFootprint(fp, offset), true, null);
    }

    public static void refreshCharterWireframeDuringDrag(
        @Nonnull PlayerRef playerRef,
        @Nonnull CharterRelocationSession session,
        @Nonnull Vector3d offset
    ) {
        Vector3i anchor = session.getAnchor();
        PlotFootprintRecord fp = new PlotFootprintRecord(anchor.x, anchor.y, anchor.z, anchor.x, anchor.y, anchor.z);
        int dx = (int) Math.round(offset.x);
        int dy = (int) Math.round(offset.y);
        int dz = (int) Math.round(offset.z);
        if (!PlacementGizmoPivot.takeWireframeShiftIfChanged(playerRef.getUuid(), dx, dy, dz)) {
            return;
        }
        PlotPlacementWireframeOverlay.send(playerRef, shiftFootprint(fp, offset), true, null);
    }

    public static void refreshPropWireframeDuringDrag(
        @Nonnull PlayerRef playerRef,
        @Nonnull PropPlacementSession session,
        @Nonnull Vector3d offset
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PropCatalog catalog = plugin.getPropCatalog();
        IPrefabBuffer buffer =
            PropPlacementValidator.resolveValidatedBuffer(
                session.getWorld(), catalog, session.getPropId(), session.getAnchor(), session.getYaw()
            );
        if (buffer == null) {
            return;
        }
        PlotFootprintRecord fp = PropPrefabOps.placementOutlineFootprint(session.getAnchor(), session.getYaw(), buffer);
        int dx = (int) Math.round(offset.x);
        int dy = (int) Math.round(offset.y);
        int dz = (int) Math.round(offset.z);
        if (!PlacementGizmoPivot.takeWireframeShiftIfChanged(playerRef.getUuid(), dx, dy, dz)) {
            return;
        }
        PropPlacementWireframeOverlay.send(playerRef, shiftFootprint(fp, offset), true);
    }

    @Nonnull
    private static PlotFootprintRecord shiftFootprint(@Nonnull PlotFootprintRecord fp, @Nonnull Vector3d offset) {
        int dx = (int) Math.round(offset.x);
        int dy = (int) Math.round(offset.y);
        int dz = (int) Math.round(offset.z);
        return new PlotFootprintRecord(
            fp.getMinX() + dx,
            fp.getMinY() + dy,
            fp.getMinZ() + dz,
            fp.getMaxX() + dx,
            fp.getMaxY() + dy,
            fp.getMaxZ() + dz
        );
    }

    public static void refreshCharter(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull CharterRelocationSession session
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || uc == null) {
            PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
            PlotPlacementWireframeOverlay.clearFor(playerRef);
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(session.getTownId());
        Vector3i anchor = session.getAnchor();
        PlotFootprintRecord fp = new PlotFootprintRecord(anchor.x, anchor.y, anchor.z, anchor.x, anchor.y, anchor.z);
        String placementErr = null;
        if (town == null) {
            placementErr = "Town not found.";
        } else if (!town.isOwner(uc.getUuid())) {
            placementErr = "Only the town owner can move the charter.";
        } else if (!tm.allPlotFootprintsFitAfterClaimShift(
            town,
            ChunkUtil.chunkCoordinate(anchor.x) - ChunkUtil.chunkCoordinate(town.getCharterX()),
            ChunkUtil.chunkCoordinate(anchor.z) - ChunkUtil.chunkCoordinate(town.getCharterZ())
        )) {
            placementErr = "Territory from this spot would not cover all your buildings. Move closer to your town.";
        } else if (!isReplaceable(world, anchor.x, anchor.y, anchor.z)) {
            placementErr = "That cell is not clear for the charter.";
        } else {
            int ox = town.getCharterX();
            int oy = town.getCharterY();
            int oz = town.getCharterZ();
            if (anchor.x == ox && anchor.y == oy && anchor.z == oz) {
                placementErr = "Choose a different block than the current charter.";
            }
        }
        PlotPreviewSpawner.rebuildCharterBlockPreview(
            store,
            anchor.x,
            anchor.y,
            anchor.z,
            session.getBlockHorizontalRotation(),
            session.getPreviewEntityRefs()
        );
        PlotPlacementWireframeOverlay.send(playerRef, fp, placementErr == null, town);
        if (session.isGizmoMoveActive()) {
            PlacementGizmoService.syncPointGizmo(
                playerRef,
                fp,
                (float) session.getBlockHorizontalRotation().getRadians()
            );
        }
    }

    private static boolean isReplaceable(@Nonnull World world, int x, int y, int z) {
        BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
    }

    /**
     * Derives session anchor from a moved preview hologram corner and optional body yaw.
     */
    @Nonnull
    public static Vector3i anchorFromPreviewCorner(
        @Nonnull Vector3i previewCorner,
        @Nonnull PlotPlacementClientPrefabPreview.Payload payload,
        @Nonnull Rotation placementYaw
    ) {
        Vector3i anchorOffset =
            PrefabLocalOffset.rotate(placementYaw, payload.anchorX(), payload.anchorY(), payload.anchorZ());
        return new Vector3i(
            previewCorner.x - anchorOffset.x,
            previewCorner.y - anchorOffset.y,
            previewCorner.z - anchorOffset.z
        );
    }

    @Nonnull
    public static Vector3i flooredBlockCorner(double x, double y, double z) {
        return new Vector3i(MathUtil.floor(x), MathUtil.floor(y), MathUtil.floor(z));
    }

    public static int rotationStepsFromBodyYawRadians(float yawRadians) {
        double steps = Math.round(yawRadians / (Math.PI * 0.5));
        return ((int) steps % 4 + 4) % 4;
    }
}
