package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.joml.Vector3i;

/** Author one physical cell with one click. Preview entities never alter the selected block. */
final class PlotCreatorMiningNodes {
    private PlotCreatorMiningNodes() {}

    /** The editor exports the pasted world orientation with its sign as the new prefab anchor. */
    static void fromPrefabSpace(PlotCreatorDraft draft, Vector3i prefabAnchor, Rotation yaw) {
        Vector3i sign = draft.getPlotAnchor();
        if (sign == null) throw new IllegalStateException("Mining nodes need an editor sign anchor");
        draft.getMiningNodes().replaceAll(node -> {
            var offset = PrefabLocalOffset.rotate(yaw, node.x(), node.y(), node.z());
            return new BuildingProductionLayout.Node(
                prefabAnchor.x + offset.x - sign.x,
                prefabAnchor.y + offset.y - sign.y,
                prefabAnchor.z + offset.z - sign.z,
                node.expectedBlockId());
        });
    }

    static void shift(PlotCreatorDraft draft, int dx, int dy, int dz) {
        draft.getMiningNodes().replaceAll(node -> new BuildingProductionLayout.Node(
            node.x() + dx, node.y() + dy, node.z() + dz, node.expectedBlockId()));
    }

    static String validateWorld(PlotCreatorSession session) {
        var draft=session.getDraft(); var world=session.getWorld();
        for (var node : draft.getMiningNodes()) {
            var p=PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{node.x(),node.y(),node.z()});
            if (!MiningNodeBlocks.loaded(world,p)) return "miningNodeChanged";
            var block=ChunkSectionBlockUtil.blockType(world,p.x,p.y,p.z);
            if (!MiningNodeBlocks.safeOriginal(block) || !node.expectedBlockId().equals(block.getId())
                || ChunkSectionBlockUtil.filler(world,p.x,p.y,p.z)!=0
                || ChunkSectionBlockUtil.blockEntityHolderAt(world,p.x,p.y,p.z)!=null) return "miningNodeChanged";
        }
        return null;
    }
    static boolean add(PlotCreatorSession session,Vector3i target,PlayerRef player) {
        var draft=session.getDraft(); var world=session.getWorld();
        if(!MiningNodeBlocks.loaded(world,target)) return true;
        var block=ChunkSectionBlockUtil.blockType(world,target.x,target.y,target.z);
        if(!MiningNodeBlocks.safeOriginal(block) || ChunkSectionBlockUtil.filler(world,target.x,target.y,target.z)!=0
            || ChunkSectionBlockUtil.blockEntityHolderAt(world,target.x,target.y,target.z)!=null) {
            player.sendMessage(Message.raw("Select plain rock, ore or soil for the mining node.")); return true;
        }
        int[] local=PlotCreatorLocalCoords.toLocal(draft,target);
        if(draft.getMiningNodes().stream().anyMatch(n->n.x()==local[0]&&n.y()==local[1]&&n.z()==local[2])) {
            player.sendMessage(Message.raw("This mining cell is already selected.")); return true;
        }
        if(draft.getMiningNodes().size()>=BuildingProductionLayout.MAX_AUTHORED_NODES) {
            player.sendMessage(Message.raw("A mine can have at most 64 node locations.")); return true;
        }
        draft.getMiningNodes().add(new BuildingProductionLayout.Node(local[0],local[1],local[2],block.getId()));
        player.sendMessage(Message.raw("Mining cell selected. The Miner will find a clear space beside it."));
        return true;
    }
    static boolean remove(PlotCreatorDraft draft,Vector3i target,PlayerRef player) {
        int[] local=PlotCreatorLocalCoords.toLocal(draft,target);
        boolean removed=draft.getMiningNodes().removeIf(n->n.x()==local[0]&&n.y()==local[1]&&n.z()==local[2]);
        if(removed) player.sendMessage(Message.raw("Mining cell removed."));
        return removed;
    }
}
