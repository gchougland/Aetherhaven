package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Node;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeConnections;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class BuildingUpgradeTreeTest {
    @Test void everyUpgradeHasAnInspectableNodeWithoutOverlapsOrMissingPrerequisites() {
        var trees=new ArrayList<List<Node>>();
        for(String kind:List.of("blacksmith","farmer","rancher","miner","chef","alchemist","merchant"))trees.add(BuildingUpgradeTree.workplaces(kind));
        trees.add(BuildingUpgradeTree.production());trees.add(BuildingUpgradeTree.restaurant());trees.add(BuildingUpgradeTree.warehouse());trees.add(BuildingUpgradeTree.alchemy());
        for(var tree:trees) {
            assertEquals(tree.size(),tree.stream().map(Node::id).distinct().count());
            for(var n:tree) {
                assertTrue(n.y()>=BuildingUpgradeTree.bandTop(n.level(),tree.stream().anyMatch(o -> o.kind()==BuildingUpgradeTree.Kind.PRODUCTION||o.kind()==BuildingUpgradeTree.Kind.RESTAURANT||o.kind()==BuildingUpgradeTree.Kind.WAREHOUSE||o.kind()==BuildingUpgradeTree.Kind.ALCHEMY))+30);
                for(String parent:n.parents()) {
                    var p=tree.stream().filter(o -> o.id().equals(parent)).findFirst().orElseThrow();
                    assertTrue(p.level()<=n.level());assertFalse(p.parents().contains(n.id()));
                }
                for(var other:tree)if(other!=n)assertFalse(n.x()<other.x()+174&&other.x()<n.x()+174&&n.y()<other.y()+112&&other.y()<n.y()+112,n.id()+" overlaps "+other.id());
            }
        }
        assertEquals(35,BuildingUpgradeTree.workplaces("blacksmith").stream().filter(n -> n.kind()==BuildingUpgradeTree.Kind.SLOT).count());
        assertEquals(8,BuildingUpgradeTree.workplaces("blacksmith").stream().filter(n -> n.kind()==BuildingUpgradeTree.Kind.SMELTING).count());
    }
    @Test void productionConnectionsPreserveBothPrerequisitesForEveryStorageTier() {
        var tree=BuildingUpgradeTree.production();
        for(int tier=1;tier<=3;tier++) {
            final int t=tier;
            var storage=tree.stream().filter(n -> n.id().equals("ADAMANTITE:"+t)).findFirst().orElseThrow();
            assertTrue(storage.parents().containsAll(List.of("THORIUM:"+tier,"COBALT:"+tier)));
            if(tier>1)assertTrue(storage.parents().contains("ADAMANTITE:"+(tier-1)));
        }
        assertEquals(10,tree.size());
    }
    @Test void prerequisitePathsStayInsideMarginsAndNeverCrossNodeInteriorsOrRankLabels() {
        for(var tree:List.of(BuildingUpgradeTree.workplaces("blacksmith"),BuildingUpgradeTree.workplaces("farmer"),BuildingUpgradeTree.production(),BuildingUpgradeTree.restaurant(),BuildingUpgradeTree.warehouse(),BuildingUpgradeTree.alchemy())) {
            var paths=BuildingUpgradeConnections.build(tree,n->0);
            for(var path:paths) {
                assertTrue(path.x()>=32 && path.x()+path.width()<=608);
                for(var node:tree)assertFalse(path.x()<node.x()+174 && path.x()+path.width()>node.x()
                    &&path.y()<node.y()+112 && path.y()+path.height()>node.y(),"Path cuts through "+node.id()+": "+path);
                assertTrue(path.x()>=112,"Path cuts through the town level label rail");
            }
            for(int i=0;i<paths.size();i++)for(int j=i+1;j<paths.size();j++) {
                var a=paths.get(i);var b=paths.get(j);
                if(a.height()==2&&b.height()==2&&a.y()==b.y())assertFalse(a.x()<b.x()+b.width()&&b.x()<a.x()+a.width(),"Overlapping horizontal paths");
                if(a.width()==2&&b.width()==2&&a.x()==b.x())assertFalse(a.y()<b.y()+b.height()&&b.y()<a.y()+a.height(),"Overlapping vertical paths");
                // Shared branch junctions meet at segment endpoints, not four-way crossings.
                if(a.height()==2&&b.width()==2)assertFalse(b.x()>a.x()&&b.x()<a.x()+a.width()&&a.y()>b.y()&&a.y()<b.y()+b.height(),"Crossed prerequisite paths");
                if(b.height()==2&&a.width()==2)assertFalse(a.x()>b.x()&&a.x()<b.x()+b.width()&&b.y()>a.y()&&b.y()<a.y()+a.height(),"Crossed prerequisite paths");
            }
        }
    }
}
