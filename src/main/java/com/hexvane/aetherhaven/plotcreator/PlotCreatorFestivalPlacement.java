package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.PrefabYaw;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Placement helpers for festival merchants, visitor stands, centerpiece, and race lanes. */
public final class PlotCreatorFestivalPlacement {
    private PlotCreatorFestivalPlacement() {}

    public static boolean placeNpc(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nullable String npcRoleId,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (npcRoleId == null || npcRoleId.isBlank()) {
            return false;
        }
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        float yaw = playerYawDegrees(draft, playerEntityRef, store);
        removeNpcRole(draft, npcRoleId);
        draft.getFestivalNpcs().add(
            FestivalDefinition.NpcRow.of(npcRoleId, local[0], local[1], local[2], yaw)
        );
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalNpcRecorded"));
        return true;
    }

    public static boolean placeTouristSpot(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        if (hasTouristAt(draft, local)) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTouristAlreadyRecorded")
            );
            return true;
        }
        float yaw = playerYawDegrees(draft, playerEntityRef, store);
        draft.getFestivalTouristSpots().add(
            FestivalDefinition.TouristSpotRow.of(local[0], local[1], local[2], yaw)
        );
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTouristRecorded")
        );
        return true;
    }

    public static boolean placeCenterpiece(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        int[] local = PlotCreatorLocalCoords.toLocal(draft, targetBlock);
        draft.setFestivalCenterpieceLocal(local);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalCenterpieceRecorded")
        );
        return true;
    }

    public static boolean placeBalloonSpawn(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        // Clicking a floor block should store the air cell above it, not the solid ground.
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        if (hasBalloonAt(draft, local)) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalBalloonAlreadyRecorded")
            );
            return true;
        }
        draft.getFestivalBalloonSpawns().add(FestivalDefinition.BalloonSpawnRow.of(local[0], local[1], local[2]));
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalBalloonRecorded")
        );
        return true;
    }

    public static boolean placeWhackSpawn(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        if (hasWhackAt(draft, local)) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalWhackAlreadyRecorded")
            );
            return true;
        }
        draft.getFestivalWhackSpawns().add(FestivalDefinition.WhackSpawnRow.of(local[0], local[1], local[2]));
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalWhackRecorded")
        );
        return true;
    }

    public static boolean placeWheel(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        int[] local = PlotCreatorLocalCoords.toLocal(draft, targetBlock);
        float yaw = playerYawDegrees(draft, playerEntityRef, store);
        draft.setFestivalWheelLocal(FestivalDefinition.WheelLocalRow.of(local[0], local[1], local[2], yaw));
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalWheelRecorded")
        );
        return true;
    }

    public static boolean placeTreeClimbStart(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        if (hasRaceStartAt(draft, local)) {
            playerRef.sendMessage(
                Message.translation(
                    "aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTreeClimbStartAlreadyRecorded"
                )
            );
            return true;
        }
        float yaw = playerYawDegrees(draft, playerEntityRef, store);
        draft.getFestivalRaceStartSpots().add(
            FestivalDefinition.RaceStartSpotRow.of(local[0], local[1], local[2], yaw)
        );
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTreeClimbStartRecorded")
        );
        return true;
    }

    public static boolean placeTreeClimbFinish(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        draft.setFestivalRaceFinishLocal(
            FestivalDefinition.RaceFinishLocalRow.of(local[0], local[1], local[2])
        );
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTreeClimbFinishRecorded")
        );
        return true;
    }

    public static boolean placeMazeStart(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        float yaw = playerYawDegrees(draft, playerEntityRef, store);
        draft.setFestivalMazeStartLocal(
            FestivalDefinition.MazeStartLocalRow.of(local[0], local[1], local[2], yaw)
        );
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMazeStartRecorded")
        );
        return true;
    }

    public static boolean placeOrbSpawn(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        if (hasOrbAt(draft, local)) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMazeOrbAlreadyRecorded")
            );
            return true;
        }
        draft.getFestivalOrbSpawns().add(FestivalDefinition.OrbSpawnRow.of(local[0], local[1], local[2]));
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMazeOrbRecorded")
        );
        return true;
    }

    public static boolean placeMarketStand(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        if (hasMarketStandAt(draft, local)) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketStandAlreadyRecorded")
            );
            return true;
        }
        if (com.hexvane.aetherhaven.festival.market.MarketIds.MECHANIC_ID.equals(draft.getFestivalMechanicId())
            && draft.getFestivalMarketStands().size() >= com.hexvane.aetherhaven.festival.market.MarketIds.STAND_COUNT) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketStandFull")
            );
            return true;
        }
        float yaw = playerYawDegrees(draft, playerEntityRef, store);
        draft.getFestivalMarketStands().add(FestivalDefinition.RaceStartSpotRow.of(local[0], local[1], local[2], yaw));
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketStandRecorded")
        );
        return true;
    }

    public static boolean placeMarketDisplay(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSpotPlacement.ResolvedSpot spot =
            PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        if (hasMarketDisplayAt(draft, local)) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketDisplayAlreadyRecorded")
            );
            return true;
        }
        if (com.hexvane.aetherhaven.festival.market.MarketIds.MECHANIC_ID.equals(draft.getFestivalMechanicId())
            && draft.getFestivalMarketDisplaySlots().size()
                >= com.hexvane.aetherhaven.festival.market.MarketIds.SLOT_COUNT) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketDisplayFull")
            );
            return true;
        }
        draft.getFestivalMarketDisplaySlots().add(FestivalDefinition.OrbSpawnRow.of(local[0], local[1], local[2]));
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketDisplayRecorded")
        );
        return true;
    }

    public static boolean placeRaceLaneClick(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        int[] local = PlotCreatorLocalCoords.toLocal(draft, targetBlock);
        int[] pending = session.getPendingRaceLaneStartLocal();
        if (pending == null) {
            session.setPendingRaceLaneStartLocal(local);
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalRaceLaneStart")
            );
            return true;
        }
        if (pending[0] == local[0] && pending[1] == local[1] && pending[2] == local[2]) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalRaceLaneSameCell")
            );
            return true;
        }
        List<String> pigRoles = PlotCreatorFestivalNpcRoles.defaultRacePigRoleIds();
        int index = draft.getFestivalRaceLanes().size();
        String role = index < pigRoles.size() ? pigRoles.get(index) : pigRoles.get(pigRoles.size() - 1);
        draft.getFestivalRaceLanes().add(
            FestivalDefinition.RaceLaneRow.of(
                role,
                pending[0],
                pending[1],
                pending[2],
                local[0],
                local[1],
                local[2]
            )
        );
        session.clearPendingRaceLaneStartLocal();
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalRaceLaneRecorded")
        );
        return true;
    }

    public static boolean tryRemoveNpcNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nullable String npcRoleId,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.NpcRow> it = draft.getFestivalNpcs().iterator();
        while (it.hasNext()) {
            FestivalDefinition.NpcRow npc = it.next();
            if (npcRoleId != null && !npcRoleId.isBlank() && !npcRoleId.equals(npc.getNpcRoleId())) {
                continue;
            }
            Vector3i world =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {npc.getLocalX(), npc.getLocalY(), npc.getLocalZ()}
                );
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalNpcRemoved")
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveTouristNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.TouristSpotRow> it = draft.getFestivalTouristSpots().iterator();
        while (it.hasNext()) {
            FestivalDefinition.TouristSpotRow spot = it.next();
            Vector3i world =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {spot.getLocalX(), spot.getLocalY(), spot.getLocalZ()}
                );
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTouristRemoved")
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveCenterpieceNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        int[] local = draft.getFestivalCenterpieceLocal();
        if (local == null) {
            return false;
        }
        Vector3i world = PlotCreatorLocalCoords.toWorldBlock(draft, local);
        if (!near(world, targetBlock)) {
            return false;
        }
        draft.setFestivalCenterpieceLocal(null);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalCenterpieceRemoved")
        );
        return true;
    }

    public static boolean tryRemoveBalloonNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.BalloonSpawnRow> it = draft.getFestivalBalloonSpawns().iterator();
        while (it.hasNext()) {
            FestivalDefinition.BalloonSpawnRow spot = it.next();
            Vector3i world =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {spot.getLocalX(), spot.getLocalY(), spot.getLocalZ()}
                );
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalBalloonRemoved")
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveWhackNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.WhackSpawnRow> it = draft.getFestivalWhackSpawns().iterator();
        while (it.hasNext()) {
            FestivalDefinition.WhackSpawnRow spot = it.next();
            Vector3i world =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {spot.getLocalX(), spot.getLocalY(), spot.getLocalZ()}
                );
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalWhackRemoved")
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveWheelNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        FestivalDefinition.WheelLocalRow wheel = draft.getFestivalWheelLocal();
        if (wheel == null) {
            return false;
        }
        Vector3i world =
            PlotCreatorLocalCoords.toWorldBlock(
                draft,
                new int[] {wheel.getLocalX(), wheel.getLocalY(), wheel.getLocalZ()}
            );
        if (!near(world, targetBlock)) {
            return false;
        }
        draft.setFestivalWheelLocal(null);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalWheelRemoved")
        );
        return true;
    }

    public static boolean tryRemoveTreeClimbStartNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.RaceStartSpotRow> it = draft.getFestivalRaceStartSpots().iterator();
        while (it.hasNext()) {
            FestivalDefinition.RaceStartSpotRow spot = it.next();
            Vector3i world =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {spot.getLocalX(), spot.getLocalY(), spot.getLocalZ()}
                );
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation(
                        "aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTreeClimbStartRemoved"
                    )
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveTreeClimbFinishNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        FestivalDefinition.RaceFinishLocalRow finish = draft.getFestivalRaceFinishLocal();
        if (finish == null) {
            return false;
        }
        Vector3i world =
            PlotCreatorLocalCoords.toWorldBlock(
                draft,
                new int[] {finish.getLocalX(), finish.getLocalY(), finish.getLocalZ()}
            );
        if (!near(world, targetBlock)) {
            return false;
        }
        draft.setFestivalRaceFinishLocal(null);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalTreeClimbFinishRemoved")
        );
        return true;
    }

    public static boolean tryRemoveMazeStartNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        FestivalDefinition.MazeStartLocalRow start = draft.getFestivalMazeStartLocal();
        if (start == null) {
            return false;
        }
        Vector3i world =
            PlotCreatorLocalCoords.toWorldBlock(
                draft,
                new int[] {start.getLocalX(), start.getLocalY(), start.getLocalZ()}
            );
        if (!near(world, targetBlock)) {
            return false;
        }
        draft.setFestivalMazeStartLocal(null);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMazeStartRemoved")
        );
        return true;
    }

    public static boolean tryRemoveOrbNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.OrbSpawnRow> it = draft.getFestivalOrbSpawns().iterator();
        while (it.hasNext()) {
            FestivalDefinition.OrbSpawnRow spot = it.next();
            Vector3i world = PlotCreatorLocalCoords.toWorldBlock(draft, orbLocalBlock(spot));
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation(
                        "aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMazeOrbRemoved"
                    )
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveMarketStandNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.RaceStartSpotRow> it = draft.getFestivalMarketStands().iterator();
        while (it.hasNext()) {
            FestivalDefinition.RaceStartSpotRow spot = it.next();
            Vector3i world =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {spot.getLocalX(), spot.getLocalY(), spot.getLocalZ()}
                );
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketStandRemoved")
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveMarketDisplayNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        Iterator<FestivalDefinition.OrbSpawnRow> it = draft.getFestivalMarketDisplaySlots().iterator();
        while (it.hasNext()) {
            FestivalDefinition.OrbSpawnRow spot = it.next();
            Vector3i world = PlotCreatorLocalCoords.toWorldBlock(draft, orbLocalBlock(spot));
            if (near(world, targetBlock)) {
                it.remove();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalMarketDisplayRemoved")
                );
                return true;
            }
        }
        return false;
    }

    public static boolean tryRemoveRaceLaneNear(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        Iterator<FestivalDefinition.RaceLaneRow> it = draft.getFestivalRaceLanes().iterator();
        while (it.hasNext()) {
            FestivalDefinition.RaceLaneRow lane = it.next();
            Vector3i start =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {lane.getStartLocalX(), lane.getStartLocalY(), lane.getStartLocalZ()}
                );
            Vector3i finish =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {lane.getFinishLocalX(), lane.getFinishLocalY(), lane.getFinishLocalZ()}
                );
            if (near(start, targetBlock) || near(finish, targetBlock)) {
                it.remove();
                session.clearPendingRaceLaneStartLocal();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalRaceLaneRemoved")
                );
                return true;
            }
        }
        int[] pending = session.getPendingRaceLaneStartLocal();
        if (pending != null) {
            Vector3i world = PlotCreatorLocalCoords.toWorldBlock(draft, pending);
            if (near(world, targetBlock)) {
                session.clearPendingRaceLaneStartLocal();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.festivalRaceLaneStartCleared")
                );
                return true;
            }
        }
        return false;
    }

    private static void removeNpcRole(@Nonnull PlotCreatorDraft draft, @Nonnull String npcRoleId) {
        draft.getFestivalNpcs().removeIf(npc -> npcRoleId.equals(npc.getNpcRoleId()));
    }

    private static boolean hasTouristAt(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (FestivalDefinition.TouristSpotRow spot : draft.getFestivalTouristSpots()) {
            if (spot.getLocalX() == local[0] && spot.getLocalY() == local[1] && spot.getLocalZ() == local[2]) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBalloonAt(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (FestivalDefinition.BalloonSpawnRow spot : draft.getFestivalBalloonSpawns()) {
            if (spot.getLocalX() == local[0] && spot.getLocalY() == local[1] && spot.getLocalZ() == local[2]) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWhackAt(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (FestivalDefinition.WhackSpawnRow spot : draft.getFestivalWhackSpawns()) {
            if (spot.getLocalX() == local[0] && spot.getLocalY() == local[1] && spot.getLocalZ() == local[2]) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRaceStartAt(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (FestivalDefinition.RaceStartSpotRow spot : draft.getFestivalRaceStartSpots()) {
            if (spot.getLocalX() == local[0] && spot.getLocalY() == local[1] && spot.getLocalZ() == local[2]) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOrbAt(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (FestivalDefinition.OrbSpawnRow spot : draft.getFestivalOrbSpawns()) {
            int[] block = orbLocalBlock(spot);
            if (block[0] == local[0] && block[1] == local[1] && block[2] == local[2]) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMarketStandAt(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (FestivalDefinition.RaceStartSpotRow spot : draft.getFestivalMarketStands()) {
            if (spot.getLocalX() == local[0] && spot.getLocalY() == local[1] && spot.getLocalZ() == local[2]) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMarketDisplayAt(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (FestivalDefinition.OrbSpawnRow spot : draft.getFestivalMarketDisplaySlots()) {
            int[] block = orbLocalBlock(spot);
            if (block[0] == local[0] && block[1] == local[1] && block[2] == local[2]) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static int[] orbLocalBlock(@Nonnull FestivalDefinition.OrbSpawnRow spot) {
        return new int[] {
            (int) Math.round(spot.getLocalX()),
            (int) Math.round(spot.getLocalY()),
            (int) Math.round(spot.getLocalZ())
        };
    }

    private static float playerYawDegrees(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        TransformComponent tc = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (tc == null) {
            return 0.0f;
        }
        float prefabYaw = PrefabYaw.prefabFromWorld(PlotCreatorPrefabCoords.placementYaw(draft), tc.getRotation().yaw());
        float deg = (float) Math.toDegrees(prefabYaw);
        while (deg < 0.0f) {
            deg += 360.0f;
        }
        while (deg >= 360.0f) {
            deg -= 360.0f;
        }
        return deg;
    }

    private static boolean near(@Nonnull Vector3i a, @Nonnull Vector3i b) {
        return Math.abs(a.x - b.x) <= 1 && Math.abs(a.y - b.y) <= 1 && Math.abs(a.z - b.z) <= 1;
    }
}
