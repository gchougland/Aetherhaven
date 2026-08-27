package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PlotCreatorCleanup {
    private PlotCreatorCleanup() {}

    public static void endSession(
        @Nonnull PlotCreatorSession session,
        @Nullable PlayerRef playerRef,
        boolean removeWorldArtifacts
    ) {
        PlotCreatorSessions.remove(session.getPlayerUuid());
        if (playerRef != null) {
            PlotCreatorSelectionBoundsService.deactivateIfPresent(playerRef);
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                PlotCreatorSelectionBoundsService.restoreNormalStaffInHand(playerRef, ref, ref.getStore());
            }
            PlotCreatorService.clearPlotCreatorWireframe(playerRef, session.getWorld());
            returnDepositChestIfOpen(session, playerRef);
        }
        if (!removeWorldArtifacts) {
            return;
        }
        World world = session.getWorld();
        PlotCreatorDraft draft = session.getDraft();
        if (draft.isBuildingEditorMode() || draft.isFestivalMode()) {
            clearBuildingEditorPaste(world, draft);
        } else {
            for (Vector3i pos : draft.getPlacedSpecialBlocks()) {
                breakBlock(world, pos);
            }
        }
        draft.getPlacedSpecialBlocks().clear();
        session.setMaterialsContainer(null);
    }

    /** Clears the temporary building-editor or festival paste (full footprint) on the world thread. */
    private static void clearBuildingEditorPaste(@Nonnull World world, @Nonnull PlotCreatorDraft draft) {
        List<PlotFootprintRecord> boxes = editorPasteBoxes(draft);
        if (boxes.isEmpty()) {
            for (Vector3i pos : draft.getPlacedSpecialBlocks()) {
                breakBlock(world, pos);
            }
            return;
        }
        world.execute(
            () -> {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                for (PlotFootprintRecord fp : boxes) {
                    PrefabFootprintClearUtil.removeEntitiesInFootprint(entityStore, fp);
                    PrefabFootprintClearUtil.clearFootprint(world, fp, true);
                }
            }
        );
    }

    /**
     * Every box the building editor pasted. A wall style is pasted one piece at a time side by side, so each piece has
     * its own box and they are cleared separately to leave the ground between them alone.
     */
    @Nonnull
    private static List<PlotFootprintRecord> editorPasteBoxes(@Nonnull PlotCreatorDraft draft) {
        List<PlotFootprintRecord> out = new ArrayList<>();
        if (!draft.getWallPieces().isEmpty()) {
            for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
                if (piece.hasBounds()) {
                    out.add(box(piece.boundsMin(), piece.boundsMax()));
                }
            }
            return out;
        }
        if (PlotCreatorAnchorRules.hasBounds(draft)) {
            out.add(box(draft.boundsMin(), draft.boundsMax()));
        }
        return out;
    }

    @Nonnull
    private static PlotFootprintRecord box(@Nonnull Vector3i min, @Nonnull Vector3i max) {
        return new PlotFootprintRecord(min.x, min.y, min.z, max.x, max.y, max.z);
    }

    private static void returnDepositChestIfOpen(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef
    ) {
        if (!session.isMaterialsManualDepositOpen()) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlotCreatorMaterialsHelper.returnDepositChestToPlayer(session, player, ref, store);
    }

    private static void breakBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        WorldChunk ch = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (ch == null) {
            return;
        }
        ChunkSectionBlockUtil.setBlockEmpty(world, pos.x, pos.y, pos.z, 10);
    }
}
