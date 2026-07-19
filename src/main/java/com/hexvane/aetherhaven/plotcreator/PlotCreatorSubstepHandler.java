package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.shopspot.ShopSpotBlock;
import com.hexvane.aetherhaven.shopspot.ShopSpotBlockUtil;
import com.hexvane.aetherhaven.tourist.TouristPortalBlock;
import com.hexvane.aetherhaven.tourist.TouristPortalBlockUtil;
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
        if (!PlotCreatorSpawnLocations.tryRemoveAdventurerNear(session.getDraft(), targetBlock, 2.0)) {
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
        if (!PlotCreatorSpawnLocations.tryRemoveVisitorNear(session.getDraft(), targetBlock, 2.0)) {
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
        Store<EntityStore> store = commandBuffer.getStore();
        PlotCreatorDraft draft = session.getDraft();
        if (draft.getStep() != PlotCreatorStep.CORNER_FIRST
            && draft.getStep() != PlotCreatorStep.CORNER_SECOND
            && draft.getStep() != PlotCreatorStep.ANCHOR
            && draft.getStep() != PlotCreatorStep.SUBSTEP) {
            return false;
        }
        return switch (draft.getStep()) {
            case CORNER_FIRST -> {
                draft.setCornerFirst(targetBlock);
                PlotCreatorService.advance(session, ref, store);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.cornerFirstSet"));
                yield true;
            }
            case CORNER_SECOND -> {
                draft.setCornerSecond(targetBlock);
                PlotCreatorService.advance(session, ref, store);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.cornerSecondSet"));
                yield true;
            }
            case ANCHOR -> {
                if (draft.isInsideBounds(targetBlock)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.anchorInside"));
                    yield true;
                }
                if (!PlotCreatorAnchorRules.isOutsideCorner(draft, targetBlock)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.anchorNotCorner"));
                    yield true;
                }
                Vector3i previousAnchor = draft.getPlotAnchor();
                if (draft.isBuildingEditorMode() && previousAnchor != null) {
                    BuildingEditorSessionStarter.rebaseLocalsForNewPlotSign(
                        draft,
                        previousAnchor,
                        targetBlock
                    );
                }
                draft.setPlotAnchor(targetBlock);
                draft.setPrefabOriginMin(new Vector3i(draft.boundsMin()));
                PlotCreatorLocalCoords.recomputeAnchorOffset(draft);
                PlotCreatorService.advance(session, ref, store);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.anchorSet"));
                yield true;
            }
            case SUBSTEP -> handleSubstepClick(session, targetBlock, playerRef, ref, commandBuffer);
            default -> false;
        };
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
                if (!AetherhavenConstants.TOURIST_PORTAL_BLOCK_TYPE_ID.equals(blockId)) {
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
                draft.setInnkeeperSpawnLocal(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spawnRecorded"));
                yield true;
            }
            case VISITOR_SPAWN -> {
                if (draft.getVisitorSpawnLocals().size() >= 2) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.tooManyVisitors"));
                    yield true;
                }
                if (hasVisitorLocal(draft, local)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.visitorSpawnAlreadyRecorded"));
                    yield true;
                }
                draft.getVisitorSpawnLocals().add(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spawnRecorded"));
                yield true;
            }
            case GUILD_MASTER_SPAWN -> {
                draft.setGuildMasterSpawnLocal(local);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spawnRecorded"));
                yield true;
            }
            case ADVENTURER_SPAWN -> {
                int[] prefabLocal = PlotCreatorPrefabCoords.standPrefabLocal(draft, PlotCreatorPrefabCoords.standWorldBlock(draft, targetBlock));
                if (!hasAdventurerLocal(draft, prefabLocal)) {
                    float yaw =
                        PlotCreatorPrefabCoords.standPrefabYawFacingPlayer(
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
                session.getWorld(),
                draft,
                targetBlock,
                blockId,
                local,
                req,
                playerRef,
                playerEntityRef,
                store
            );
        };
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
        @Nonnull com.hypixel.hytale.server.core.universe.world.World world,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Vector3i targetBlock,
        @Nullable String blockId,
        @Nonnull int[] local,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement req,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorSubstepType type = req.type();
        if (type == PlotCreatorSubstepType.QUEST_BOARD_POI
            && blockId != null
            && !AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(blockId)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wrongBlock"));
            return true;
        }
        for (PlotCreatorPoiDraft existing : draft.getPois()) {
            if (existing.getLocalX() == local[0]
                && existing.getLocalY() == local[1]
                && existing.getLocalZ() == local[2]
                && PlotCreatorValidator.matchesPoiRequirement(existing, req)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.poiAlreadyRecorded")
                );
                return true;
            }
        }
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        poi.setLocal(local[0], local[1], local[2]);
        poi.setBlockTypeId(blockId);
        poi.setCapacity(1);
        applyPoiDefaults(poi, type, draft, req.workResidentKind());
        PlotCreatorPoiInteractionTarget.applyFromPlayerFacing(
            draft,
            playerEntityRef,
            store,
            world,
            targetBlock,
            local,
            poi
        );
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
                poi.getTags().add("WORK");
                poi.getTags().add("SHOP");
                poi.setInteractionKind("WORK_SURFACE");
                PlotCreatorWorkActivityTags.applyDefault(poi, type, null);
            }
            case TOURIST_VISIT_POI -> {
                poi.getTags().add(AetherhavenConstants.POI_TAG_TOURIST_VISIT);
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
