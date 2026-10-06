package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.production.BuildingProductionLayout;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.*;
import org.joml.Vector3i;

/** Disposable selection in the placed mine's original prefab coordinates. No world writes. */
final class PlacedMineNodeDraft {
    final UUID plotId;
    private final Vector3i anchor;
    private final Rotation yaw;
    private final List<BuildingProductionLayout.Node> nodes=new ArrayList<>();
    PlacedMineNodeDraft(PlotInstance plot,BuildingProductionLayout seed) {
        plotId=plot.getPlotId();anchor=plot.getStoredPrefabWorldAnchor();yaw=plot.resolvePrefabYaw();
        if(seed!=null&&!seed.legacy()&&seed.validationError()==null)nodes.addAll(seed.nodes());
    }
    boolean matches(PlotInstance plot) {
        return plot!=null&&plotId.equals(plot.getPlotId())&&plot.getState()==PlotInstanceState.COMPLETE
            &&plot.getProductionLayout().legacy()&&plot.hasStoredPrefabWorldAnchor()
            &&anchor.equals(plot.getStoredPrefabWorldAnchor())&&yaw==plot.resolvePrefabYaw();
    }
    BuildingProductionLayout snapshot() {return new BuildingProductionLayout("4.0",BuildingProductionLayout.MINING_NODES,nodes);}
    Vector3i point(BuildingProductionLayout.Node n) {return new Vector3i(anchor).add(PrefabLocalOffset.rotate(yaw,n.x(),n.y(),n.z()));}
    private Vector3i local(Vector3i point) {
        var d=new Vector3i(point).sub(anchor);return PrefabLocalOffset.inverseRotateWorldDelta(yaw,d.x,d.y,d.z);
    }
    String add(PlotInstance plot,Vector3i point,String block) {
        if(!matches(plot))return "The mine changed. Start a new review.";
        if(!plot.containsWorldBlock(point.x,point.y,point.z))return "Choose a cell inside this mine.";
        if(block==null||block.isBlank())return "Select plain rock, ore or soil.";
        var p=local(point);
        if(nodes.stream().anyMatch(n->n.x()==p.x&&n.y()==p.y&&n.z()==p.z))return "This mining cell is already selected.";
        if(nodes.size()>=BuildingProductionLayout.MAX_AUTHORED_NODES)return "A mine can have at most 64 node locations.";
        nodes.add(new BuildingProductionLayout.Node(p.x,p.y,p.z,block));return null;
    }
    boolean remove(Vector3i point) {
        var p=local(point);return nodes.removeIf(n->n.x()==p.x&&n.y()==p.y&&n.z()==p.z);
    }
}
