package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Broadcasts plot placement building previews to nearby players using cached client prefab packets. */
public final class PlotPlacementPreviewSync {
    private PlotPlacementPreviewSync() {}

    /**
     * Sends or updates the building ghost for players who have the preview chunk loaded (excluding the placer).
     *
     * @param placerNeedFull {@code true} when the placer just received a full resend (construction/rotation change)
     */
    public static void syncSpectators(
        @Nonnull World world,
        @Nonnull UUID placerUuid,
        @Nonnull PlotPlacementSession session,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i prefabOriginWorld,
        boolean placerNeedFull
    ) {
        if (!world.getName().equals(session.getWorld().getName())) {
            return;
        }
        PlotPlacementClientPrefabPreview.Payload payload = session.getClientPrefabPreviewPayload();
        if (payload == null) {
            payload =
                PlotPlacementClientPrefabPreview.resolvePayload(
                    def.getPrefabPath(),
                    session.getRotationSteps(),
                    session
                );
        }
        if (payload == null) {
            hideSpectators(world, placerUuid, session);
            return;
        }

        Vector3i floored =
            PlotPlacementClientPrefabPreview.flooredClientPreviewOrigin(
                prefabOriginWorld,
                session,
                session.getPrefabYaw()
            );
        boolean groupNeedFull =
            placerNeedFull
                || !session.hasSpectatorPreviewActive()
                || !session.getConstructionId().equals(session.getLastSpectatorPreviewConstructionId())
                || session.getRotationSteps() != session.getLastSpectatorPreviewRotationSteps();
        Vector3i lastOrigin = session.getLastSpectatorPreviewOriginFloored();
        boolean originChanged = lastOrigin == null || !lastOrigin.equals(floored);

        long previewChunkIndex = ChunkUtil.indexChunkFromBlock(floored.x, floored.z);
        Set<UUID> active = session.getSpectatorPreviewActive();
        Set<UUID> stillVisible = new HashSet<>();

        for (PlayerRef viewer : world.getPlayerRefs()) {
            UUID viewerUuid = viewer.getUuid();
            if (viewerUuid.equals(placerUuid)) {
                continue;
            }
            if (!viewerHasPreviewChunkLoaded(viewer, previewChunkIndex)) {
                if (active.contains(viewerUuid)) {
                    PlotPlacementClientPrefabPreview.hide(viewer);
                    active.remove(viewerUuid);
                }
                continue;
            }
            stillVisible.add(viewerUuid);
            boolean viewerNeedFull = groupNeedFull || !active.contains(viewerUuid);
            if (viewerNeedFull) {
                PlotPlacementClientPrefabPreview.sendFullToViewer(
                    viewer,
                    world,
                    prefabOriginWorld,
                    payload,
                    session.getPrefabYaw()
                );
            } else if (originChanged) {
                PlotPlacementClientPrefabPreview.sendPositionOnlyToViewer(
                    viewer,
                    world,
                    prefabOriginWorld,
                    payload,
                    session.getPrefabYaw()
                );
            } else {
                continue;
            }
            active.add(viewerUuid);
        }

        active.retainAll(stillVisible);
        if (!active.isEmpty()) {
            session.setLastSpectatorPreviewState(session.getConstructionId(), session.getRotationSteps(), floored);
        } else {
            session.clearSpectatorPreviewState();
        }
    }

    public static void hideSpectators(
        @Nonnull World world,
        @Nonnull UUID placerUuid,
        @Nonnull PlotPlacementSession session
    ) {
        Set<UUID> active = session.getSpectatorPreviewActive();
        if (active.isEmpty()) {
            session.clearSpectatorPreviewState();
            return;
        }
        for (UUID spectatorUuid : new ArrayList<>(active)) {
            if (spectatorUuid.equals(placerUuid)) {
                continue;
            }
            PlayerRef viewer = findPlayerInWorld(world, spectatorUuid);
            if (viewer != null) {
                PlotPlacementClientPrefabPreview.hide(viewer);
            }
        }
        session.clearSpectatorPreviewState();
    }

    private static boolean viewerHasPreviewChunkLoaded(@Nonnull PlayerRef viewer, long previewChunkIndex) {
        ChunkTracker tracker = viewer.getChunkTracker();
        return tracker != null && tracker.isLoaded(previewChunkIndex);
    }

    @Nullable
    private static PlayerRef findPlayerInWorld(@Nonnull World world, @Nonnull UUID playerUuid) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr.getUuid().equals(playerUuid)) {
                return pr;
            }
        }
        return null;
    }
}
