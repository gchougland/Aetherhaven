package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.tool.PoiDebugLineHelper;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Keeps the plot creator step HUD visible for the whole session while a draft is active. */
public final class PlotCreatorPreviewSystem extends EntityTickingSystem<EntityStore> {
    private static final ConcurrentHashMap<UUID, Long> LAST_GUIDE_SIG = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_WIREFRAME_SIG = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_SPOT_MARKER_SIG = new ConcurrentHashMap<>();

    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public PlotCreatorPreviewSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }
        @Nullable
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        World world = store.getExternalData().getWorld();
        PlotCreatorSession session = PlotCreatorSessions.get(uuid);
        if (session == null) {
            if (PlotCreatorHudSupport.isActive(player)) {
                PlotCreatorHudSupport.removeHud(player, pr);
            }
            LAST_GUIDE_SIG.remove(uuid);
            LAST_WIREFRAME_SIG.remove(uuid);
            PlotCreatorService.clearPlotCreatorWireframe(pr, world);
            clearSpotMarkers(world, store, ref, commandBuffer);
            return;
        }
        PlotCreatorStep step = session.getDraft().getStep();
        if (step == PlotCreatorStep.DONE) {
            PlotCreatorHudSupport.removeHud(player, pr);
            LAST_GUIDE_SIG.remove(uuid);
            LAST_WIREFRAME_SIG.remove(uuid);
            PlotCreatorService.clearPlotCreatorWireframe(pr, world);
            clearSpotMarkers(world, store, ref, commandBuffer);
            return;
        }
        long guideSig = PlotCreatorProgressModel.guideSignature(session.getDraft());
        Long prevGuide = LAST_GUIDE_SIG.get(uuid);
        if (prevGuide == null || prevGuide != guideSig || !PlotCreatorHudSupport.isActive(player)) {
            LAST_GUIDE_SIG.put(uuid, guideSig);
            PlotCreatorHudSupport.refreshAll(player, pr, session);
        }
        if (step == PlotCreatorStep.BOUNDS) {
            PlotCreatorBoundsInput.tickHover(session, ref, store, pr);
            if (session.getDraft().isBoundsPrimaryHeld()) {
                PlotCreatorBoundsInput.onDragTick(session, ref, store, pr);
            }
        }
        long sig = wireframeSignature(session.getDraft());
        Long prevSig = LAST_WIREFRAME_SIG.get(uuid);
        if (prevSig == null || prevSig != sig) {
            LAST_WIREFRAME_SIG.put(uuid, sig);
            PlotCreatorService.refreshBoundsVisuals(session, pr);
        }
        syncImportantSpotMarkers(session, world, store, ref, commandBuffer);
    }

    private static void syncImportantSpotMarkers(
        @Nonnull PlotCreatorSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        UUID ownerUuid = PlotCreatorSpotMarkerSync.requireOwnerEntityUuid(store, playerRef);
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorStep step = draft.getStep();
        long spotSig = PlotCreatorSpotMarkerCollector.signature(draft, world);
        Long prevSpotSig = LAST_SPOT_MARKER_SIG.get(ownerUuid);
        boolean showAllInEditorChooser =
            step == PlotCreatorStep.IMPORTANT_SPOTS && draft.isBuildingEditorMode();
        boolean showingSpots =
            step == PlotCreatorStep.SUBSTEP || step == PlotCreatorStep.REVIEW || showAllInEditorChooser;

        if (prevSpotSig == null || prevSpotSig != spotSig) {
            LAST_SPOT_MARKER_SIG.put(ownerUuid, spotSig);
            if (!showingSpots) {
                PlotCreatorSpotMarkerSync.clearAll(world, ownerUuid, commandBuffer);
                return;
            }
            @Nullable
            PlotBuildingKindRequirements.SubstepRequirement filter =
                step == PlotCreatorStep.SUBSTEP ? PlotCreatorService.currentSubstep(draft) : null;
            List<PlotCreatorSpotMarkerCollector.DesiredSpotMarker> desired =
                filter == null && step == PlotCreatorStep.SUBSTEP
                    ? Collections.emptyList()
                    : PlotCreatorSpotMarkerCollector.collect(draft, world, filter);
            PlotCreatorSpotMarkerSync.sync(world, ownerUuid, desired, commandBuffer);
        } else if (!showingSpots) {
            return;
        }

        PlayerRef playerRefComp = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp != null && showingSpots) {
            drawFacingHintLines(draft, world, playerRefComp, step);
        }
    }

    private static void drawFacingHintLines(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull World world,
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorStep step
    ) {
        @Nullable
        PlotBuildingKindRequirements.SubstepRequirement filter =
            step == PlotCreatorStep.SUBSTEP ? PlotCreatorService.currentSubstep(draft) : null;
        if (filter == null && step == PlotCreatorStep.SUBSTEP) {
            return;
        }
        for (PlotCreatorSpotMarkerCollector.DesiredSpotMarker m :
            PlotCreatorSpotMarkerCollector.collect(draft, world, filter)) {
            if (m.facingYawWorldRadians() == null) {
                continue;
            }
            float yaw = m.facingYawWorldRadians();
            double sx = m.x() + 0.5;
            double sy = m.y() + 1.05;
            double sz = m.z() + 0.5;
            double ex = sx + (-Math.sin(yaw)) * 1.35;
            double ez = sz + (-Math.cos(yaw)) * 1.35;
            PoiDebugLineHelper.addLineToPlayer(
                playerRef,
                sx,
                sy,
                sz,
                ex,
                sy,
                ez,
                DebugUtils.COLOR_YELLOW,
                0.07,
                1.25F,
                0
            );
        }
    }

    private static void clearSpotMarkers(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        try {
            UUID ownerUuid = PlotCreatorSpotMarkerSync.requireOwnerEntityUuid(store, playerRef);
            LAST_SPOT_MARKER_SIG.remove(ownerUuid);
            PlotCreatorSpotMarkerSync.clearAll(world, ownerUuid, commandBuffer);
        } catch (IllegalStateException ignored) {
            // Player missing UUID — nothing to clear.
        }
    }

    private static long wireframeSignature(@Nonnull PlotCreatorDraft draft) {
        PlotCreatorService.BoundsPreview preview = PlotCreatorService.boundsPreview(draft);
        if (preview == null) {
            long h = draft.getStep() == PlotCreatorStep.BOUNDS ? 1L : 0L;
            h = 31 * h + draft.getBoundsPhase().ordinal();
            h = 31 * h + (draft.getHoveredBoundsFace() != null ? draft.getHoveredBoundsFace().ordinal() : -1);
            return h;
        }
        Vector3i min = preview.min();
        Vector3i max = preview.max();
        long h = 17L;
        h = 31 * h + min.x;
        h = 31 * h + min.y;
        h = 31 * h + min.z;
        h = 31 * h + max.x;
        h = 31 * h + max.y;
        h = 31 * h + max.z;
        if (draft.getStep() == PlotCreatorStep.BOUNDS) {
            h = 31 * h + draft.getBoundsPhase().ordinal();
            h = 31 * h + (draft.getHoveredBoundsFace() != null ? draft.getHoveredBoundsFace().ordinal() : -1);
            h = 31 * h + (draft.getActiveBoundsFaceDrag() != null ? draft.getActiveBoundsFaceDrag().ordinal() : -1);
        } else {
            h = 31 * h + draft.getStep().ordinal();
        }
        return h;
    }
}
