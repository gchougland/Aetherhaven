package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
        long sig = wireframeSignature(session.getDraft());
        Long prevSig = LAST_WIREFRAME_SIG.get(uuid);
        if (prevSig == null || prevSig != sig) {
            LAST_WIREFRAME_SIG.put(uuid, sig);
            PlotCreatorService.refreshWireframe(session, pr);
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
        if (prevSpotSig != null && prevSpotSig == spotSig) {
            return;
        }
        LAST_SPOT_MARKER_SIG.put(ownerUuid, spotSig);

        if (step != PlotCreatorStep.SUBSTEP && step != PlotCreatorStep.REVIEW) {
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
        Vector3i a = draft.getCornerFirst();
        Vector3i b = draft.getCornerSecond();
        if (a == null || b == null) {
            return 0L;
        }
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        long h = 17L;
        h = 31 * h + min.x;
        h = 31 * h + min.y;
        h = 31 * h + min.z;
        h = 31 * h + max.x;
        h = 31 * h + max.y;
        h = 31 * h + max.z;
        return h;
    }
}
