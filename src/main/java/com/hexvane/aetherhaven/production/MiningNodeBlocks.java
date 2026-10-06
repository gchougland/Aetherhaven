package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

/** Exact-cell ownership checks precede every node mutation. Never loads chunks. */
public final class MiningNodeBlocks {
    public static final String PREFIX = "Aetherhaven_Mining_Node_";
    public static final String DEPLETED = PREFIX + "Depleted";
    private MiningNodeBlocks() {}

    public static Vector3i point(PlotInstance plot, int node) {
        if (!plot.hasStoredPrefabWorldAnchor() || node < 0 || node >= plot.getProductionLayout().nodes().size()) return null;
        var cell = plot.getProductionLayout().nodes().get(node);
        return plot.getStoredPrefabWorldAnchor().add(PrefabLocalOffset.rotate(plot.resolvePrefabYaw(),cell.x(),cell.y(),cell.z()));
    }
    public static boolean loaded(World world, Vector3i p) {
        return p != null && ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunkFromBlock(p.x,p.z)) != null;
    }
    public static String current(World world, Vector3i p) {
        if (!loaded(world,p)) return null;
        var type = ChunkSectionBlockUtil.blockType(world,p.x,p.y,p.z);
        return type == null ? null : type.getId();
    }
    public static boolean supported(String output) {
        return output != null && BlockType.getAssetMap().getAsset(PREFIX + output) != null;
    }
    public static boolean safeOriginal(BlockType type) {
        if (type == null) return false;
        String id=type.getId(), draw=String.valueOf(type.getDrawType());
        return (id.startsWith("Rock_") || id.startsWith("Ore_") || "Soil_Dirt".equals(id) || "Soil_Gravel".equals(id))
            && ("Cube".equals(draw) || "CubeWithModel".equals(draw))
            && (type.getInteractions() == null || type.getInteractions().isEmpty());
    }
    public static boolean intact(World world, PlotInstance plot, MiningProductionState state, int node) {
        var p=point(plot,node);
        if (!loaded(world,p) || !plot.containsWorldBlock(p.x,p.y,p.z)
            || ChunkSectionBlockUtil.filler(world,p.x,p.y,p.z)!=0
            || ChunkSectionBlockUtil.blockEntityHolderAt(world,p.x,p.y,p.z)!=null) return false;
        String owned=state.ownedBlock(node), actual=current(world,p);
        if (owned != null) return owned.equals(actual) && owned.startsWith(PREFIX);
        String expected=plot.getProductionLayout().nodes().get(node).expectedBlockId();
        return expected.equals(actual) && safeOriginal(BlockType.getAssetMap().getAsset(expected));
    }
    /** Invoke outside entity iteration using its command buffer. */
    public static boolean replace(World world, PlotInstance plot, MiningProductionState state, int node, String replacement) {
        if (!replacement.startsWith(PREFIX) || BlockType.getAssetMap().getAsset(replacement)==null
            || !intact(world,plot,state,node)) return false;
        var p=point(plot,node);
        if (!replacement.equals(current(world,p))
            && !ChunkSectionBlockUtil.setBlockByKey(world,p.x,p.y,p.z,replacement,0)) return false;
        state.own(node,replacement);
        return true;
    }
}
