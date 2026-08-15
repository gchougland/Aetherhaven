package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.WallSegmentRecord;
import com.hexvane.aetherhaven.ui.WallPlacementPage;
import com.hexvane.aetherhaven.ui.WallPlacementUiRegistry;
import com.hexvane.aetherhaven.wall.WallPieceGeometry;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.placement.PlotSignGrounding;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WallPlacementEditHelper {
    private WallPlacementEditHelper() {}

    @Nullable
    public static CustomUIPage tryOpenEdit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        BlockPosition tb = context.getTargetBlock();
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return null;
        }
        World world = store.getExternalData().getWorld();
        if (tb == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_wall_placement.aetherhaven.ui.wallplacement.errorLookAtWall"));
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
        if (town == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_wall_placement.aetherhaven.ui.wallplacement.errorNeedTown"));
            return null;
        }
        WallPlacementOpenHelper.cancelOtherPlacementPreviews(ref, store, playerRef, uc.getUuid());
        WallPlacementSession existing = WallPlacementSessions.get(uc.getUuid());
        if (existing != null && existing.getWorld().getName().equals(world.getName())) {
            if (applyEditTargetFromBlock(existing, world, tb, plugin, town)) {
                WallPlacementSessions.put(uc.getUuid(), existing);
            }
            WallPlacementPage activePage = WallPlacementUiRegistry.get(uc.getUuid());
            if (activePage != null) {
                activePage.scheduleUiRebuild(ref, store);
                return activePage;
            }
            return new WallPlacementPage(playerRef, existing);
        }

        PlotConstructionBlockResolver.PlotConstructionTarget signTarget =
            PlotConstructionBlockResolver.resolveForPlotUi(world, tb, PlotSignBlock.getComponentType());
        if (signTarget != null) {
            PlotSignBlock sign = signTarget.blockRef().getStore().getComponent(signTarget.blockRef(), PlotSignBlock.getComponentType());
            if (sign != null) {
                ConstructionDefinition def = plugin.getConstructionCatalog().get(sign.getConstructionId());
                if (WallPlacementOpenHelper.isWallConstruction(def)) {
                    UUID plotId = parseUuid(sign.getPlotId());
                    if (plotId != null) {
                        PlotInstance plot = town.findPlotById(plotId);
                        if (plot != null) {
                            WallPlacementSession session =
                                new WallPlacementSession(
                                    world,
                                    new Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ())
                                );
                            session.setCurrentRotationSteps(PlotPlacementSession.rotationStepsFromPrefabYaw(plot.resolvePrefabYaw()));
                            session.adoptPieceForEdit(plot.getConstructionId());
                            session.setEditTargetPlotId(plotId);
                            WallPlacementSessions.put(uc.getUuid(), session);
                            return new WallPlacementPage(playerRef, session);
                        }
                    }
                }
            }
        }

        WallSegmentRecord seg = town.findWallSegmentAtBlock(tb.x, tb.y, tb.z);
        if (seg == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_wall_placement.aetherhaven.ui.wallplacement.errorNoWallHere"));
            return null;
        }
        PlotFootprintRecord fp = seg.toFootprint();
        int cx = (fp.getMinX() + fp.getMaxX()) / 2;
        int cz = (fp.getMinZ() + fp.getMaxZ()) / 2;
        Vector3i anchor = groundedSignAnchorForSegment(world, plugin, seg, cx, cz, fp.getMaxY());
        WallPlacementSession session = new WallPlacementSession(world, anchor);
        session.setCurrentRotationSteps(PlotPlacementSession.rotationStepsFromPrefabYaw(seg.resolvePrefabYaw()));
        session.adoptPieceForEdit(seg.getConstructionId());
        session.setEditTargetSegmentId(seg.getSegmentId());
        WallPlacementSessions.put(uc.getUuid(), session);
        return new WallPlacementPage(playerRef, session);
    }

    /**
     * When the wall wand UI is already open, primary-use on a wall piece switches into continue/remove mode for that
     * target instead of ignoring the click.
     */
    private static boolean applyEditTargetFromBlock(
        @Nonnull WallPlacementSession session,
        @Nonnull World world,
        @Nonnull BlockPosition tb,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        PlotConstructionBlockResolver.PlotConstructionTarget signTarget =
            PlotConstructionBlockResolver.resolveForPlotUi(world, tb, PlotSignBlock.getComponentType());
        if (signTarget != null) {
            PlotSignBlock sign = signTarget.blockRef().getStore().getComponent(signTarget.blockRef(), PlotSignBlock.getComponentType());
            if (sign != null) {
                ConstructionDefinition def = plugin.getConstructionCatalog().get(sign.getConstructionId());
                if (WallPlacementOpenHelper.isWallConstruction(def)) {
                    UUID plotId = parseUuid(sign.getPlotId());
                    if (plotId != null && town.findPlotById(plotId) != null) {
                        session.setEditTargetPlotId(plotId);
                        session.setEditTargetSegmentId(null);
                        session.setRemoveConfirmOpen(false);
                        PlotInstance plot = town.findPlotById(plotId);
                        if (plot != null) {
                            session.setCurrentAnchor(new Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ()));
                            session.setCurrentRotationSteps(
                                PlotPlacementSession.rotationStepsFromPrefabYaw(plot.resolvePrefabYaw())
                            );
                            session.adoptPieceForEdit(plot.getConstructionId());
                        }
                        return true;
                    }
                }
            }
        }
        WallSegmentRecord seg = town.findWallSegmentAtBlock(tb.x, tb.y, tb.z);
        if (seg == null) {
            return false;
        }
        session.setEditTargetSegmentId(seg.getSegmentId());
        session.setEditTargetPlotId(null);
        session.setRemoveConfirmOpen(false);
        PlotFootprintRecord fp = seg.toFootprint();
        int cx = (fp.getMinX() + fp.getMaxX()) / 2;
        int cz = (fp.getMinZ() + fp.getMaxZ()) / 2;
        session.setCurrentAnchor(groundedSignAnchorForSegment(world, plugin, seg, cx, cz, fp.getMaxY()));
        session.setCurrentRotationSteps(PlotPlacementSession.rotationStepsFromPrefabYaw(seg.resolvePrefabYaw()));
        session.adoptPieceForEdit(seg.getConstructionId());
        return true;
    }

    @Nonnull
    private static Vector3i groundedSignAnchorForSegment(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WallSegmentRecord seg,
        int signX,
        int signZ,
        int previewY
    ) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(seg.getConstructionId());
        if (def == null) {
            PlotFootprintRecord fp = seg.toFootprint();
            return new Vector3i(
                signX,
                fp.getMinY() + AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
                signZ
            );
        }
        Path path = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (path == null) {
            PlotFootprintRecord fp = seg.toFootprint();
            return new Vector3i(
                signX,
                fp.getMinY() + AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
                signZ
            );
        }
        IPrefabBuffer buf = PrefabBufferUtil.getCached(path);
        Vector3i preview = new Vector3i(signX, previewY, signZ);
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(preview, seg.resolvePrefabYaw());
        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, seg.resolvePrefabYaw(), buf);
        int startY = Math.max(previewY, fp.getMaxY());
        int signY = PlotSignGrounding.resolveSignYAtColumn(world, signX, signZ, startY, previewY);
        return new Vector3i(signX, signY, signZ);
    }

    @Nullable
    private static UUID parseUuid(@Nonnull String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
