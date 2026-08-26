package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.town.PlotFootprintChunkUtil;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownMemberBlockAccess;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.PlotConstructionPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Opens plot sign construction UI from journal plot rows (no ray-hit block required). */
public final class PlotConstructionOpenHelper {
    private static final int Y_RADIUS = 10;

    public enum OpenResult {
        OPENED,
        NOT_MEMBER,
        NOT_FOUND,
        CHUNK_NOT_LOADED,
        ASSEMBLING,
        COMPLETE
    }

    private PlotConstructionOpenHelper() {}

    @Nonnull
    public static OpenResult tryOpenFromJournal(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID playerUuid = uc != null ? uc.getUuid() : null;
        if (TownMemberBlockAccess.denyIfNotMember(playerRef, town, playerUuid)) {
            return OpenResult.NOT_MEMBER;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            return OpenResult.NOT_FOUND;
        }
        PlotInstanceState state = plot.getState();
        if (state == PlotInstanceState.COMPLETE) {
            return OpenResult.COMPLETE;
        }
        if (state == PlotInstanceState.ASSEMBLING) {
            return OpenResult.ASSEMBLING;
        }
        World world = store.getExternalData().getWorld();
        if (!PlotFootprintChunkUtil.isPlotSignChunkLoaded(world, plot)) {
            return OpenResult.CHUNK_NOT_LOADED;
        }
        PlotConstructionBlockResolver.PlotConstructionTarget target = resolvePlotSign(world, plot);
        if (target == null) {
            PlotPlacementCommit.LinkRepairResult repair = PlotPlacementCommit.repairPlotSignLink(world, plot);
            if (repair == PlotPlacementCommit.LinkRepairResult.SKIPPED_CHUNK_UNLOADED) {
                return OpenResult.CHUNK_NOT_LOADED;
            }
            target = resolvePlotSign(world, plot);
        }
        if (target == null) {
            return OpenResult.CHUNK_NOT_LOADED;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return OpenResult.NOT_FOUND;
        }
        player.getPageManager()
            .openCustomPage(
                ref,
                store,
                new PlotConstructionPage(playerRef, target.blockRef(), target.blockWorldPos(), false, true)
            );
        return OpenResult.OPENED;
    }

    @Nullable
    private static PlotConstructionBlockResolver.PlotConstructionTarget resolvePlotSign(
        @Nonnull World world, @Nonnull PlotInstance plot
    ) {
        int x = plot.getSignX();
        int y = plot.getSignY();
        int z = plot.getSignZ();
        String constructionId = plot.getConstructionId() != null ? plot.getConstructionId() : "";
        String plotIdStr = plot.getPlotId().toString();
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }
        int yMin = Math.max(0, y - Y_RADIUS);
        int yMax = Math.min(319, y + Y_RADIUS);
        Ref<ChunkStore> bestRef = null;
        int bestY = y;
        int bestDist = Integer.MAX_VALUE;
        for (int yy = yMin; yy <= yMax; yy++) {
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, yy, z);
            if (!isLinkedPlotSign(blockRef, constructionId, plotIdStr)) {
                continue;
            }
            int dist = Math.abs(yy - y);
            if (dist < bestDist) {
                bestDist = dist;
                bestRef = blockRef;
                bestY = yy;
            }
        }
        return bestRef == null ? null : new PlotConstructionBlockResolver.PlotConstructionTarget(bestRef, new Vector3i(x, bestY, z));
    }

    private static boolean isLinkedPlotSign(
        @Nullable Ref<ChunkStore> signRef, @Nonnull String constructionId, @Nonnull String plotIdStr
    ) {
        if (signRef == null || !signRef.isValid()) {
            return false;
        }
        PlotSignBlock sign = signRef.getStore().getComponent(signRef, PlotSignBlock.getComponentType());
        return sign != null
            && constructionId.equals(sign.getConstructionId())
            && plotIdStr.equals(sign.getPlotId());
    }
}
