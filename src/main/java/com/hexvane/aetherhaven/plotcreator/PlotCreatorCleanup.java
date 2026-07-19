package com.hexvane.aetherhaven.plotcreator;

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
            PlotCreatorService.clearPlotCreatorWireframe(playerRef, session.getWorld());
            returnDepositChestIfOpen(session, playerRef);
        }
        if (!removeWorldArtifacts) {
            return;
        }
        World world = session.getWorld();
        PlotCreatorDraft draft = session.getDraft();
        if (draft.isBuildingEditorMode()) {
            clearBuildingEditorPaste(world, draft);
        } else {
            for (Vector3i pos : draft.getPlacedSpecialBlocks()) {
                breakBlock(world, pos);
            }
        }
        draft.getPlacedSpecialBlocks().clear();
        session.setMaterialsContainer(null);
    }

    /** Clears the temporary building-editor paste (full footprint) on the world thread. */
    private static void clearBuildingEditorPaste(@Nonnull World world, @Nonnull PlotCreatorDraft draft) {
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        if (min == null || max == null) {
            for (Vector3i pos : draft.getPlacedSpecialBlocks()) {
                breakBlock(world, pos);
            }
            return;
        }
        PlotFootprintRecord fp = new PlotFootprintRecord(min.x, min.y, min.z, max.x, max.y, max.z);
        world.execute(
            () -> {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                PrefabFootprintClearUtil.removeEntitiesInFootprint(entityStore, fp);
                PrefabFootprintClearUtil.clearFootprint(world, fp);
            }
        );
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
        WorldChunk ch = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (ch == null) {
            return;
        }
        ch.setBlock(pos.x, pos.y, pos.z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, 10);
    }
}
