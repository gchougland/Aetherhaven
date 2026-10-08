package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ExcavationFittingsTest {
    @Test void wallTorchesAttachToTheirSupportingWallInEveryDirectionIncludingSavedOrders(){
        var gson=new Gson();
        for(var direction:MineshaftSelection.Direction.values())
            for(var layout:List.of(ExcavationPlan.Layout.STRAIGHT,ExcavationPlan.Layout.DEEP_STAIRS_DOWN,ExcavationPlan.Layout.DEEP_STAIRS_UP,ExcavationPlan.Layout.ROOM)){
                var area=MineshaftSelection.area(10,100,20,direction,12,layout);
                var torches=ExcavationFittings.plan(area,MineshaftSelection.entrance(direction),layout,ExcavationFittings.Mode.LIGHTS);
                assertFalse(torches.isEmpty());
                for(var torch:torches){
                    assertTorchSupport(torch);
                    var saved=gson.toJsonTree(torch).getAsJsonObject();
                    // Existing saves can contain the opposite yaw. Use their saved support,
                    // preserving the position, item cost and supporting block.
                    saved.addProperty("rotation",com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple.of(
                        com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple.get(torch.rotation()).yaw().flip(),
                        com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.None,
                        com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.None).index());
                    var restored=gson.fromJson(saved,ExcavationFittings.Fitting.class);
                    assertTorchSupport(restored);
                    assertEquals(torch.point(),restored.point());assertEquals(torch.support(),restored.support());assertEquals(torch.item(),restored.item());
                }
            }
    }
    private void assertTorchSupport(ExcavationFittings.Fitting torch){
        // The base game's Wood_Torch_Wall asset declares Support.North.
        var north=com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple.get(torch.rotation()).yaw().getAxisDirection();
        assertEquals(new Point(torch.point().x()+north.x(),torch.point().y(),torch.point().z()+north.z()),torch.support());
    }
    @Test void nativeRailsFollowEveryTunnelStepAndNeverAppearInRooms(){
        for(var direction:MineshaftSelection.Direction.values())for(var layout:List.of(ExcavationPlan.Layout.STRAIGHT,ExcavationPlan.Layout.DEEP_STAIRS_DOWN,ExcavationPlan.Layout.DEEP_STAIRS_UP,ExcavationPlan.Layout.ROOM)){
            var area=MineshaftSelection.area(10,100,20,direction,12,layout);var entrance=MineshaftSelection.entrance(direction);
            var rails=ExcavationFittings.plan(area,entrance,layout,ExcavationFittings.Mode.RAILS);
            if(layout==ExcavationPlan.Layout.ROOM){assertTrue(rails.isEmpty());continue;}
            assertEquals(12,rails.size());
            for(var rail:rails){assertEquals("Rail",rail.item());assertEquals(ExcavationPlan.floorAt(area,entrance,layout,rail.point()),rail.point().y());assertEquals(rail.point().y()-1,rail.support().y());}
            assertEquals(layout!=ExcavationPlan.Layout.STRAIGHT,rails.stream().anyMatch(f->f.block().equals("*Rail_State_Definitions_Slope")));
            for(var rail:rails)if(rail.block().equals("*Rail_State_Definitions_Slope")){
                var high=com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple.get(rail.rotation()).yaw().getAxisDirection();
                var p=rail.point();var upper=new Point(p.x()+high.x(),p.y(),p.z()+high.z());
                assertEquals(p.y()+1,ExcavationPlan.floorAt(area,entrance,layout,upper),"The native rail's high edge must face uphill for "+direction);
            }
            var saved=new Gson().fromJson(new Gson().toJson(ExcavationFittings.Mode.SUPPORTS_LANTERNS_RAILS),ExcavationFittings.Mode.class);
            assertTrue(saved.rails);assertTrue(saved.supports);assertTrue(saved.lanterns);assertFalse(saved.lights);
            assertFalse(ExcavationFittings.Mode.ALL.rails);
        }
    }
    @Test void FittingsStayInsideClearedCellsWithoutDuplicatesForEveryLayoutAndEntrance() {
        for(var side:ExcavationPlan.Entrance.values())for(var layout:ExcavationPlan.Layout.values())
            for(var mode:ExcavationFittings.Mode.values())for(int size:new int[]{5,6,9,13,21,64}) {
                var area=new WorkplaceArea(-20,100,-30,-20+size,-30+size,10);
                var tunnel=new HashSet<>(ExcavationPlan.tunnel(area,side,layout));
                var fittings=ExcavationFittings.plan(area,side,layout,mode);var points=new HashSet<Point>();
                for(var f:fittings) {
                    assertTrue(tunnel.contains(f.point()),side+" "+layout+" "+mode+" "+size);
                    assertTrue(points.add(f.point()),"A fitting cannot be charged twice for one cell");
                    assertTrue(area.contains(f.point().x(),f.point().y(),f.point().z()));
                    if(ExcavationFittings.TORCH.equals(f.item()))assertEquals(ExcavationPlan.floorAt(area,side,layout,f.point())+1,f.point().y());
                }
                // The central feet/head column of both passages must remain unobstructed.
                int cx=area.minX()+(size-1)/2,cz=area.minZ()+(size-1)/2;
                boolean alongZ=side==ExcavationPlan.Entrance.NORTH||side==ExcavationPlan.Entrance.SOUTH;
                int junction=3+Math.min(16,size-3)/2;
                for(var f:fittings) {
                    var p=f.point();int d=switch(side){case NORTH->p.z()-area.minZ();case SOUTH->area.maxZ()-1-p.z();case WEST->p.x()-area.minX();case EAST->area.maxX()-1-p.x();};
                    boolean center=alongZ?p.x()==cx:p.z()==cz;
                    if(!ExcavationFittings.RAIL.equals(f.item())&&(center||layout==ExcavationPlan.Layout.BRANCHES&&d==junction))
                        assertTrue(p.y()>=ExcavationPlan.floorAt(area,side,layout,p)+2,"Walking route must remain open");
                }
                if(mode==ExcavationFittings.Mode.NONE)assertTrue(fittings.isEmpty());
            }
    }
    @Test void CostsMatchCompleteFramesAndNativeOrientation() {
        var a=new WorkplaceArea(0,80,0,13,13);
        for(var side:ExcavationPlan.Entrance.values()) {
            var fittings=ExcavationFittings.plan(a,side,ExcavationPlan.Layout.STRAIGHT,ExcavationFittings.Mode.BOTH);
            assertEquals(16,fittings.size());assertEquals(14,fittings.stream().filter(f->ExcavationFittings.BEAM.equals(f.item())).count());
            assertEquals(6,fittings.stream().filter(f->!f.block().equals(f.item())&&ExcavationFittings.BEAM.equals(f.item())).count());
        }
    }
    private ExcavationPlan prepared() {
        var a=new WorkplaceArea(0,100,0,13,13);var p=new ExcavationPlan();
        assertNull(p.start(0,UUID.randomUUID(),UUID.randomUUID(),a,ExcavationPlan.Entrance.NORTH,ExcavationPlan.Layout.STRAIGHT,
            List.of(new ExcavationPlan.Cell(new Point(6,100,3),"Rock_Stone",0,1)),ExcavationFittings.Mode.BOTH));
        return p;
    }
    private void work(ExcavationPlan p){for(int i=0;i<12;i++)p.attend(.5,true,1);}
    @Test void FittingsRequireFinishedDeliveredRockAndSuccessfulAttendedPlacement() {
        var p=prepared();var first=p.nextFitting();work(p);
        assertFalse(p.fit(p.id(),first,()->fail("Rock comes first")));
        assertTrue(p.prepare(Map.of("Rock_Stone_Cobble",1)));work(p);
        assertTrue(p.begin(p.id(),6,new Point(6,100,2),()->true));assertTrue(p.settle(p.id(),true));work(p);
        assertFalse(p.fit(p.id(),first,()->fail("Deliver before placing fittings")));
        assertTrue(p.delivered(p.id(),()->true));
        assertFalse(p.fit(p.id(),first,()->fail("Placement requires attended work")));work(p);
        assertFalse(p.fit(UUID.randomUUID(),first,()->fail("Stale plan")));
        assertFalse(p.fit(p.id(),new ExcavationFittings.Fitting(new Point(0,0,0),ExcavationFittings.BEAM,0),()->fail("Stale target")));
        assertFalse(p.fit(p.id(),first,()->false));assertEquals(0,p.fitted());assertEquals(first,p.nextFitting());
        assertTrue(p.pause(p.revision(),true));assertFalse(p.fit(p.id(),first,()->fail("Paused")));
        assertTrue(p.pause(p.revision(),false));assertTrue(p.fit(p.id(),first,()->true));
        assertEquals(1,p.fitted());assertEquals(0,p.work());work(p);
        assertFalse(p.fit(p.id(),first,()->fail("Repeated old placement")));
        var gson=new Gson();var copy=gson.fromJson(gson.toJson(p),ExcavationPlan.class);
        assertEquals(p.nextFitting(),copy.nextFitting());assertEquals(p.fitted(),copy.fitted());
        assertEquals(ExcavationFittings.Mode.BOTH,copy.fittingsMode());
        assertFalse(copy.markEdited(copy.nextFitting().point()),"An empty-only placement can retry after obstruction clears");
        assertTrue(copy.fit(copy.id(),copy.nextFitting(),()->true));assertEquals(2,copy.fitted());
    }
    @Test void OldSavesHaveNoFittings() {
        var gson=new Gson();var saved=gson.toJsonTree(prepared()).getAsJsonObject();
        for(String field:List.of("fittings","fittingsMode","fitted"))saved.remove(field);
        var copy=gson.fromJson(saved,ExcavationPlan.class);
        assertEquals(ExcavationFittings.Mode.NONE,copy.fittingsMode());assertEquals(0,copy.fittingsTotal());assertNull(copy.nextFitting());
    }
}
