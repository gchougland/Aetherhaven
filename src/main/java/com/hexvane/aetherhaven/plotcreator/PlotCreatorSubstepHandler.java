package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.shopspot.ShopSpotBlock;
import com.hexvane.aetherhaven.shopspot.ShopSpotBlockUtil;
import com.hexvane.aetherhaven.tourist.TouristPortalBlock;
import com.hexvane.aetherhaven.tourist.TouristPortalBlockUtil;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PlotCreatorSubstepHandler {
    private PlotCreatorSubstepHandler() {}

    public static boolean tryRemoveAdventurerSpawnAt(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!PlotCreatorSpawnLocations.tryRemoveAdventurerNear(session.getDraft(), session.getWorld(), targetBlock, 2.0)) {
            return false;
        }
        PlotCreatorAdventurerMarkers.removeMarkerNear(commandBuffer, targetBlock);
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.adventurerSpawnRemoved"));
        return true;
    }

    public static boolean tryRemoveVisitorSpawnAt(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        if (!PlotCreatorSpawnLocations.tryRemoveVisitorNear(session.getDraft(), session.getWorld(), targetBlock, 2.0)) {
            return false;
        }
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.visitorSpawnRemoved"));
        return true;
    }

    /** Right-click: remove the placement for the current important-spot substep near {@code targetBlock}. */
    public static boolean tryRemoveCurrentSubstepAt(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlotCreatorDraft draft = session.getDraft();
        PlotBuildingKindRequirements.SubstepRequirement req = PlotCreatorService.currentSubstep(draft);
        if (req == null) {
            return false;
        }
        return switch (req.type()) {
            case VISITOR_SPAWN -> tryRemoveVisitorSpawnAt(session, targetBlock, playerRef);
            case ADVENTURER_SPAWN -> tryRemoveAdventurerSpawnAt(session, targetBlock, playerRef, commandBuffer);
            case MANAGEMENT_BLOCK -> clearLocalIfNear(
                draft,
                draft.getManagementBlockLocalPos(),
                targetBlock,
                () -> draft.setManagementBlockLocalPos(null),
                playerRef
            );
            case PRODUCTION_STORAGE -> clearLocalIfNear(
                draft,
                draft.getProductionStorageLocalPos(),
                targetBlock,
                () -> draft.setProductionStorageLocalPos(null),
                playerRef
            );
            case TREASURY_BLOCK -> clearLocalIfNear(
                draft,
                draft.getTreasuryLocalPos(),
                targetBlock,
                () -> draft.setTreasuryLocalPos(null),
                playerRef
            );
            case SHOP_SAFE_BLOCK -> clearLocalIfNear(
                draft,
                draft.getShopSafeLocalPos(),
                targetBlock,
                () -> draft.setShopSafeLocalPos(null),
                playerRef
            );
            case INN_BELL_BLOCK -> clearLocalIfNear(
                draft,
                draft.getInnBellLocalPos(),
                targetBlock,
                () -> draft.setInnBellLocalPos(null),
                playerRef
            );
            case GAIA_STATUE_BLOCK -> clearLocalIfNear(
                draft,
                draft.getGaiaStatueLocalPos(),
                targetBlock,
                () -> PlotCreatorGaiaStatueSupport.clear(draft),
                playerRef
            );
            case INNKEEPER_SPAWN -> clearLocalIfNear(
                draft,
                draft.getInnkeeperSpawnLocal(),
                targetBlock,
                () -> draft.setInnkeeperSpawnLocal(null),
                playerRef
            );
            case GUILD_MASTER_SPAWN -> clearLocalIfNear(
                draft,
                draft.getGuildMasterSpawnLocal(),
                targetBlock,
                () -> draft.setGuildMasterSpawnLocal(null),
                playerRef
            );
            case SHOP_SPOT, TOURIST_PORTAL_BLOCK -> tryRemoveSpecialBlockNear(draft, targetBlock, playerRef);
            case WORK_POI, SLEEP_POI, EAT_POI, FUN_POI, SHOP_POI, TOURIST_VISIT_POI, PLANNING_DESK_POI, BARD_WORK_POI,
                QUEST_BOARD_POI -> tryRemoveMatchingPoiNear(draft, targetBlock, req, playerRef);
            case FESTIVAL_NPC ->
                PlotCreatorFestivalPlacement.tryRemoveNpcNear(draft, targetBlock, req.workResidentKind(), playerRef);
            case FESTIVAL_TOURIST_SPOT ->
                PlotCreatorFestivalPlacement.tryRemoveTouristNear(draft, targetBlock, playerRef);
            case FESTIVAL_CENTERPIECE ->
                PlotCreatorFestivalPlacement.tryRemoveCenterpieceNear(draft, targetBlock, playerRef);
            case FESTIVAL_RACE_LANE ->
                PlotCreatorFestivalPlacement.tryRemoveRaceLaneNear(session, targetBlock, playerRef);
            case FESTIVAL_BALLOON_SPAWN ->
                PlotCreatorFestivalPlacement.tryRemoveBalloonNear(draft, targetBlock, playerRef);
            case FESTIVAL_WHACK_SPAWN ->
                PlotCreatorFestivalPlacement.tryRemoveWhackNear(draft, targetBlock, playerRef);
            case FESTIVAL_WHEEL -> PlotCreatorFestivalPlacement.tryRemoveWheelNear(draft, targetBlock, playerRef);
            case FESTIVAL_TREE_CLIMB_START ->
                PlotCreatorFestivalPlacement.tryRemoveTreeClimbStartNear(draft, targetBlock, playerRef);
            case FESTIVAL_TREE_CLIMB_FINISH ->
                PlotCreatorFestivalPlacement.tryRemoveTreeClimbFinishNear(draft, targetBlock, playerRef);
            case FESTIVAL_MAZE_START ->
                PlotCreatorFestivalPlacement.tryRemoveMazeStartNear(draft, targetBlock, playerRef);
            case FESTIVAL_MAZE_ORB_SPAWN ->
                PlotCreatorFestivalPlacement.tryRemoveOrbNear(draft, targetBlock, playerRef);
            case FESTIVAL_MARKET_STAND ->
                PlotCreatorFestivalPlacement.tryRemoveMarketStandNear(draft, targetBlock, playerRef);
            case FESTIVAL_MARKET_DISPLAY ->
                PlotCreatorFestivalPlacement.tryRemoveMarketDisplayNear(draft, targetBlock, playerRef);
            case FESTIVAL_SNOWBALL_PILE ->
                PlotCreatorFestivalPlacement.tryRemoveSnowballPileNear(draft, targetBlock, playerRef);
            case FESTIVAL_SNOWBALL_TEAM_A ->
                PlotCreatorFestivalPlacement.tryRemoveSnowballTeamANear(draft, targetBlock, playerRef);
            case FESTIVAL_SNOWBALL_TEAM_B ->
                PlotCreatorFestivalPlacement.tryRemoveSnowballTeamBNear(draft, targetBlock, playerRef);
            case FESTIVAL_SNOWBALL_OUT ->
                PlotCreatorFestivalPlacement.tryRemoveSnowballOutNear(draft, targetBlock, playerRef);
        };
    }

    private static boolean clearLocalIfNear(
        @Nonnull PlotCreatorDraft draft,
        @Nullable int[] local,
        @Nonnull Vector3i targetBlock,
        @Nonnull Runnable clear,
        @Nonnull PlayerRef playerRef
    ) {
        if (local == null || local.length < 3) {
            return false;
        }
        Vector3i world = PlotCreatorLocalCoords.toWorldBlock(draft, local);
        if (!sameOrAdjacent(world, targetBlock)) {
            return false;
        }
        clear.run();
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spotRemoved"));
        return true;
    }

    private static boolean tryRemoveSpecialBlockNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        List<Vector3i> list = draft.getPlacedSpecialBlocks();
        for (int i = 0; i < list.size(); i++) {
            Vector3i pos = list.get(i);
            if (sameOrAdjacent(pos, targetBlock)) {
                list.remove(i);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spotRemoved"));
                return true;
            }
        }
        return false;
    }

    private static boolean tryRemoveMatchingPoiNear(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement req,
        @Nonnull PlayerRef playerRef
    ) {
        List<PlotCreatorPoiDraft> pois = draft.getPois();
        for (int i = 0; i < pois.size(); i++) {
            PlotCreatorPoiDraft poi = pois.get(i);
            if (!PlotCreatorValidator.matchesPoiRequirement(poi, req)) {
                continue;
            }
            int[] local = new int[] {poi.getLocalX(), poi.getLocalY(), poi.getLocalZ()};
            Vector3i world = PlotCreatorLocalCoords.toWorldBlock(draft, local);
            if (sameOrAdjacent(world, targetBlock)) {
                pois.remove(i);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spotRemoved"));
                return true;
            }
        }
        return false;
    }

    private static boolean sameOrAdjacent(@Nonnull Vector3i a, @Nonnull Vector3i b) {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        int dz = Math.abs(a.z - b.z);
        return dx <= 1 && dy <= 1 && dz <= 1;
    }

    public static boolean handleBlockClick(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (draft.getStep() == PlotCreatorStep.WALL_PIECES) {
            return handleWallConnectionClick(draft, targetBlock, playerRef);
        }
        if (draft.getStep() != PlotCreatorStep.SUBSTEP) {
            return false;
        }
        return handleSubstepClick(session, targetBlock, playerRef, ref, commandBuffer);
    }

    private static boolean handleWallConnectionClick(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef
    ) {
        // Nothing to click while the build cost of a piece is being set.
        if (!PlotCreatorWallPieceAuthoring.isConnectionSubstep(draft)) {
            return true;
        }
        String prefix = "aetherhaven_plot_creator.aetherhaven.plotcreator.";
        com.hexvane.aetherhaven.wall.WallCardinal face = PlotCreatorWallPieceAuthoring.expectedFace(draft);
        Message side =
            face == null
                ? Message.raw("")
                : Message.translation(prefix + "wallSide." + face.name().toLowerCase(java.util.Locale.ROOT));
        String err = PlotCreatorWallPieceAuthoring.recordConnectionClick(draft, targetBlock);
        if (err != null) {
            playerRef.sendMessage(Message.translation(prefix + "error." + err).param("side", side));
            return true;
        }
        playerRef.sendMessage(
            Message.translation(prefix + "hint.wallConnectionRecorded").param("side", side)
        );
        return true;
    }

    private static boolean handleSubstepClick(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Store<EntityStore> store = commandBuffer.getStore();
        PlotCreatorDraft draft = session.getDraft();
        if (session.getPendingPoiPlacement() != null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.poiActivityPending")
            );
            return true;
        }
        if (!draft.isInsideBounds(targetBlock)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.outsideBounds"));
            return true;
        }
        PlotBuildingKindRequirements.SubstepRequirement req = PlotCreatorService.currentSubstep(draft);
        if (req == null) {
            return false;
        }
        String blockId = PlotCreatorLocalCoords.blockTypeAt(session.getWorld(), targetBlock);
        int[] local = PlotCreatorLocalCoords.toLocal(draft, targetBlock);
        return switch (req.type()) {
            case MANAGEMENT_BLOCK -> {
                if (!AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID.equals(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                draft.setManagementBlockLocalPos(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.blockRecorded"));
                yield true;
            }
            case PRODUCTION_STORAGE -> {
                if (!AetherhavenConstants.BLOCK_PRODUCTION_STORAGE.equals(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                draft.setProductionStorageLocalPos(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.blockRecorded"));
                yield true;
            }
            case TREASURY_BLOCK -> {
                if (!AetherhavenConstants.TREASURY_BLOCK_TYPE_ID.equals(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                draft.setTreasuryLocalPos(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.blockRecorded"));
                yield true;
            }
            case SHOP_SAFE_BLOCK -> {
                if (!AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID.equals(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                draft.setShopSafeLocalPos(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.blockRecorded"));
                yield true;
            }
            case INN_BELL_BLOCK -> {
                if (!AetherhavenConstants.INN_BELL_BLOCK_TYPE_ID.equals(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                draft.setInnBellLocalPos(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.blockRecorded"));
                yield true;
            }
            case GAIA_STATUE_BLOCK -> {
                if (!PlotCreatorGaiaStatueSupport.isGaiaStatueBlockTypeId(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                draft.setGaiaStatueLocalPos(local);
                PlotCreatorGaiaStatueSupport.syncPoiFromLocalPos(draft);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.blockRecorded"));
                yield true;
            }
            case SHOP_SPOT -> {
                if (!AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID.equals(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                ShopSpotBlock blockComp = ShopSpotBlockUtil.getBlockComponent(session.getWorld(), targetBlock);
                if (blockComp == null || !blockComp.isConfigured()) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.shopSpotNotConfigured"));
                    yield true;
                }
                for (Vector3i recorded : draft.getPlacedSpecialBlocks()) {
                    if (recorded.x == targetBlock.x && recorded.y == targetBlock.y && recorded.z == targetBlock.z) {
                        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.shopSpotAlreadyRecorded"));
                        yield true;
                    }
                }
                draft.getPlacedSpecialBlocks().add(new Vector3i(targetBlock));
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.shopSpotRecorded"));
                yield true;
            }
            case TOURIST_PORTAL_BLOCK -> {
                if (!TownPortalTravelColor.isTouristPortalBlockTypeId(blockId)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                Vector3i portalBase = TouristPortalBlockUtil.resolvePortalBaseBlock(session.getWorld(), targetBlock);
                TouristPortalBlock blockComp = TouristPortalBlockUtil.getBlockComponent(session.getWorld(), portalBase);
                if (blockComp == null) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
                    yield true;
                }
                for (Vector3i recorded : draft.getPlacedSpecialBlocks()) {
                    if (recorded.x == portalBase.x && recorded.y == portalBase.y && recorded.z == portalBase.z) {
                        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.touristPortalAlreadyRecorded"));
                        yield true;
                    }
                }
                TouristPortalBlock confirmed =
                    new TouristPortalBlock(blockComp.getPortalId(), blockComp.getTownId(), blockComp.getPlotId(), true);
                TouristPortalBlockUtil.writeBlockComponent(session.getWorld(), portalBase, confirmed);
                draft.getPlacedSpecialBlocks().add(portalBase);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.touristPortalRecorded"));
                yield true;
            }
            case INNKEEPER_SPAWN -> {
                PlotCreatorSpotPlacement.ResolvedSpot spot =
                    PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
                draft.setInnkeeperSpawnLocal(PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock()));
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spawnRecorded"));
                yield true;
            }
            case VISITOR_SPAWN -> {
                if (draft.getVisitorSpawnLocals().size() >= 2) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.tooManyVisitors"));
                    yield true;
                }
                PlotCreatorSpotPlacement.ResolvedSpot spot =
                    PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
                int[] standLocal = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
                if (hasVisitorLocal(draft, standLocal)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.visitorSpawnAlreadyRecorded"));
                    yield true;
                }
                draft.getVisitorSpawnLocals().add(standLocal);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spawnRecorded"));
                yield true;
            }
            case GUILD_MASTER_SPAWN -> {
                PlotCreatorSpotPlacement.ResolvedSpot spot =
                    PlotCreatorSpotPlacement.resolveStandSpawn(session.getWorld(), targetBlock);
                draft.setGuildMasterSpawnLocal(PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock()));
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spawnRecorded"));
                yield true;
            }
            case ADVENTURER_SPAWN -> {
                PlotCreatorSpotPlacement.ResolvedSpot spot =
                    PlotCreatorSpotPlacement.resolveAdventurerSpawn(
                        session.getWorld(),
                        draft,
                        targetBlock,
                        playerEntityRef,
                        store
                    );
                int[] prefabLocal = PlotCreatorPrefabCoords.standPrefabLocal(draft, spot.worldBlock());
                if (!hasAdventurerLocal(draft, prefabLocal)) {
                    float yaw =
                        spot.worldYawRadians() != null
                            ? PlotCreatorSpotPlacement.prefabYawFromWorld(draft, spot.worldYawRadians())
                            : PlotCreatorPrefabCoords.standPrefabYawFacingPlayer(
                                draft,
                                playerEntityRef,
                                store,
                                prefabLocal[0],
                                prefabLocal[1],
                                prefabLocal[2]
                            );
                    PlotCreatorAdventurerSpawnEntry entry =
                        new PlotCreatorAdventurerSpawnEntry(prefabLocal[0], prefabLocal[1], prefabLocal[2], yaw);
                    draft.getAdventurerSpawns().add(entry);
                    PlotCreatorAdventurerMarkers.spawnForEntry(session.getWorld(), commandBuffer, draft, entry);
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spawnRecorded"));
                }
                yield true;
            }
            case WORK_POI, SLEEP_POI, EAT_POI, FUN_POI, SHOP_POI, TOURIST_VISIT_POI, PLANNING_DESK_POI, BARD_WORK_POI,
                QUEST_BOARD_POI -> addPoiForSubstep(
                session,
                targetBlock,
                blockId,
                local,
                req,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_NPC -> PlotCreatorFestivalPlacement.placeNpc(
                session,
                targetBlock,
                req.workResidentKind(),
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_TOURIST_SPOT -> PlotCreatorFestivalPlacement.placeTouristSpot(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_CENTERPIECE -> PlotCreatorFestivalPlacement.placeCenterpiece(session, targetBlock, playerRef);
            case FESTIVAL_RACE_LANE -> PlotCreatorFestivalPlacement.placeRaceLaneClick(session, targetBlock, playerRef);
            case FESTIVAL_BALLOON_SPAWN ->
                PlotCreatorFestivalPlacement.placeBalloonSpawn(session, targetBlock, playerRef);
            case FESTIVAL_WHACK_SPAWN ->
                PlotCreatorFestivalPlacement.placeWhackSpawn(session, targetBlock, playerRef);
            case FESTIVAL_WHEEL -> PlotCreatorFestivalPlacement.placeWheel(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_TREE_CLIMB_START -> PlotCreatorFestivalPlacement.placeTreeClimbStart(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_TREE_CLIMB_FINISH ->
                PlotCreatorFestivalPlacement.placeTreeClimbFinish(session, targetBlock, playerRef);
            case FESTIVAL_MAZE_START -> PlotCreatorFestivalPlacement.placeMazeStart(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_MAZE_ORB_SPAWN ->
                PlotCreatorFestivalPlacement.placeOrbSpawn(session, targetBlock, playerRef);
            case FESTIVAL_MARKET_STAND -> PlotCreatorFestivalPlacement.placeMarketStand(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_MARKET_DISPLAY ->
                PlotCreatorFestivalPlacement.placeMarketDisplay(session, targetBlock, playerRef);
            case FESTIVAL_SNOWBALL_PILE ->
                PlotCreatorFestivalPlacement.placeSnowballPile(session, targetBlock, playerRef);
            case FESTIVAL_SNOWBALL_TEAM_A -> PlotCreatorFestivalPlacement.placeSnowballTeamA(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_SNOWBALL_TEAM_B -> PlotCreatorFestivalPlacement.placeSnowballTeamB(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
            case FESTIVAL_SNOWBALL_OUT -> PlotCreatorFestivalPlacement.placeSnowballOut(
                session,
                targetBlock,
                playerRef,
                playerEntityRef,
                store
            );
        };
    }

    public static void finalizePendingPoi(
        @Nonnull PlotCreatorSession session,
        @Nonnull String activityId,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorPendingPoiPlacement pending = session.getPendingPoiPlacement();
        if (pending == null) {
            return;
        }
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        poi.setLocal(pending.poiLocal()[0], pending.poiLocal()[1], pending.poiLocal()[2]);
        poi.setBlockTypeId(pending.resolvedBlockTypeId());
        poi.setCapacity(1);
        applyPoiDefaults(poi, pending.req().type(), draft, pending.req().workResidentKind());
        PlotCreatorWorkActivityOptions.applyToPoi(poi, activityId);
        if (pending.useSeatFacing() && pending.seatYawRadians() != null) {
            PlotCreatorPoiInteractionTarget.applyFromSeatFacing(
                draft,
                pending.seatYawRadians(),
                pending.poiLocal(),
                poi
            );
        } else {
            PlotCreatorPoiInteractionTarget.applyFromPlayerFacing(
                draft,
                playerEntityRef,
                store,
                session.getWorld(),
                pending.spotWorldBlock(),
                pending.poiLocal(),
                poi
            );
        }
        draft.getPois().add(poi);
        session.clearPendingPoiPlacement();
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.poiRecorded"));
    }

    public static void cancelPendingPoi(@Nonnull PlotCreatorSession session) {
        session.clearPendingPoiPlacement();
    }

    private static boolean needsActivityPicker(@Nonnull PlotCreatorSubstepType type) {
        return type == PlotCreatorSubstepType.WORK_POI
            || type == PlotCreatorSubstepType.BARD_WORK_POI
            || type == PlotCreatorSubstepType.FUN_POI;
    }

    private static boolean hasAdventurerLocal(@Nonnull PlotCreatorDraft draft, @Nonnull int[] prefabLocal) {
        for (PlotCreatorAdventurerSpawnEntry existing : draft.getAdventurerSpawns()) {
            if (existing.matchesLocal(prefabLocal[0], prefabLocal[1], prefabLocal[2])) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVisitorLocal(@Nonnull PlotCreatorDraft draft, @Nonnull int[] local) {
        for (int[] existing : draft.getVisitorSpawnLocals()) {
            if (existing.length == 3
                && existing[0] == local[0]
                && existing[1] == local[1]
                && existing[2] == local[2]) {
                return true;
            }
        }
        return false;
    }

    private static boolean addPoiForSubstep(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i targetBlock,
        @Nullable String blockId,
        @Nonnull int[] local,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement req,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        com.hypixel.hytale.server.core.universe.world.World world = session.getWorld();
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorSubstepType type = req.type();
        if (type == PlotCreatorSubstepType.QUEST_BOARD_POI
            && blockId != null
            && !AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(blockId)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
            return true;
        }
        PlotCreatorSpotPlacement.ResolvedSpot spot = PlotCreatorSpotPlacement.resolvePoiAnchor(world, targetBlock);
        int[] poiLocal = PlotCreatorLocalCoords.toLocal(draft, spot.worldBlock());
        for (PlotCreatorPoiDraft existing : draft.getPois()) {
            if (existing.getLocalX() == poiLocal[0]
                && existing.getLocalY() == poiLocal[1]
                && existing.getLocalZ() == poiLocal[2]
                && PlotCreatorValidator.matchesPoiRequirement(existing, req)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.poiAlreadyRecorded")
                );
                return true;
            }
        }
        String resolvedBlockTypeId;
        if (blockId != null && spot.role() == PlotCreatorSpotPlacement.SpotRole.POI_ANCHOR) {
            resolvedBlockTypeId = PlotCreatorLocalCoords.blockTypeAt(world, spot.worldBlock());
        } else {
            resolvedBlockTypeId = blockId;
        }
        if (needsActivityPicker(type)) {
            boolean useSeatFacing = spot.worldYawRadians() != null;
            session.setPendingPoiPlacement(
                new PlotCreatorPendingPoiPlacement(
                    req,
                    poiLocal,
                    resolvedBlockTypeId,
                    spot.worldYawRadians(),
                    new Vector3i(spot.worldBlock()),
                    useSeatFacing
                )
            );
            PlotCreatorInteractions.openPoiActivityPage(playerRef, playerEntityRef, store, session);
            return true;
        }
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        poi.setLocal(poiLocal[0], poiLocal[1], poiLocal[2]);
        poi.setBlockTypeId(resolvedBlockTypeId);
        poi.setCapacity(1);
        applyPoiDefaults(poi, type, draft, req.workResidentKind());
        if (spot.worldYawRadians() != null) {
            PlotCreatorPoiInteractionTarget.applyFromSeatFacing(draft, spot.worldYawRadians(), poiLocal, poi);
        } else {
            PlotCreatorPoiInteractionTarget.applyFromPlayerFacing(
                draft,
                playerEntityRef,
                store,
                world,
                spot.worldBlock(),
                poiLocal,
                poi
            );
        }
        draft.getPois().add(poi);
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.poiRecorded"));
        return true;
    }

    private static void applyPoiDefaults(
        @Nonnull PlotCreatorPoiDraft poi,
        @Nonnull PlotCreatorSubstepType type,
        @Nonnull PlotCreatorDraft draft,
        @Nullable String workResidentKind
    ) {
        switch (type) {
            case WORK_POI -> {
                poi.getTags().add("WORK");
                poi.setInteractionKind("WORK_SURFACE");
                if (workResidentKind != null && !workResidentKind.isBlank()) {
                    poi.setWorkResidentKind(workResidentKind);
                    if (com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_BARD.equals(workResidentKind)) {
                        poi.getTags().add(AetherhavenConstants.POI_TAG_BARD);
                    }
                }
                PlotCreatorWorkActivityTags.applyDefault(poi, type, workResidentKind);
            }
            case BARD_WORK_POI -> {
                poi.getTags().add("WORK");
                poi.getTags().add(AetherhavenConstants.POI_TAG_BARD);
                poi.setInteractionKind("WORK_SURFACE");
                poi.setWorkResidentKind(com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_BARD);
                PlotCreatorWorkActivityTags.applyDefault(poi, type, TownVillagerBinding.KIND_BARD);
            }
            case QUEST_BOARD_POI -> {
                poi.getTags().add(AetherhavenConstants.POI_TAG_QUEST_BOARD);
                poi.setInteractionKind("NONE");
                poi.setBlockTypeId(AetherhavenConstants.QUEST_BOARD_ITEM_ID);
            }
            case SLEEP_POI -> {
                poi.getTags().add("SLEEP");
                poi.getTags().add("ENERGY");
                poi.setInteractionKind("SLEEP");
            }
            case EAT_POI -> {
                poi.getTags().add("EAT");
                if (PlotBuildingKindRequirements.usesRestaurantEatTag(draft, AetherhavenPlugin.get())) {
                    poi.getTags().add(AetherhavenConstants.POI_TAG_RESTAURANT);
                }
                poi.setInteractionKind("USE_BENCH");
            }
            case FUN_POI -> {
                poi.getTags().add("FUN");
                poi.getTags().add("SIT");
                poi.setInteractionKind("SIT");
                PlotCreatorWorkActivityTags.applyDefault(poi, type, null);
            }
            case SHOP_POI -> {
                poi.getTags().add("SHOP");
                poi.setInteractionKind("SIT");
            }
            case TOURIST_VISIT_POI -> {
                poi.getTags().add(AetherhavenConstants.POI_TAG_TOURIST_VISIT);
                poi.setCapacity(1);
                poi.setInteractionKind("SIT");
            }
            case PLANNING_DESK_POI -> {
                poi.getTags().add("WORK");
                poi.setInteractionKind("WORK_SURFACE");
                poi.setBlockTypeId("Aetherhaven_Town_Planning_Desk");
                PlotCreatorWorkActivityTags.applyDefault(poi, type, null);
            }
            default -> {}
        }
    }
}
