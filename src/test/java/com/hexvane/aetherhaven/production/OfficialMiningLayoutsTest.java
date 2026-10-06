package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Prevent prefab decorations or later edits from silently obstructing the official veins. */
@Tag("construction")
class OfficialMiningLayoutsTest {
    private static final Path SERVER=Path.of("src/main/resources/Server");
    private static final Gson GSON=new Gson();
    private record Cell(int x,int y,int z) {
        Cell offset(int x,int y,int z) { return new Cell(this.x+x,this.y+y,this.z+z); }
    }

    @ParameterizedTest
    @ValueSource(strings={"plot_miners_hut.json","Stormwind/plot_stormwind_mine.json"})
    void officialVeinsMatchSafeRockAndHaveConnectedClearWorkingSpace(String file) throws Exception {
        var definition=GSON.fromJson(Files.readString(SERVER.resolve("Aetherhaven/Buildings").resolve(file)),ConstructionDefinition.class);
        var source=GSON.fromJson(Files.readString(SERVER.resolve("Prefabs").resolve(definition.getPrefabPath())),JsonObject.class);
        var blocks=new HashMap<Cell,JsonObject>();
        for(var element:source.getAsJsonArray("blocks")) {
            var b=element.getAsJsonObject();
            blocks.put(new Cell(b.get("x").getAsInt(),b.get("y").getAsInt(),b.get("z").getAsInt()),b);
        }
        var layout=definition.getProductionLayout();
        assertFalse(layout.legacy()); assertNull(layout.validationError());
        var safeRock=Set.of("Rock_Stone","Rock_Stone_Cobble","Rock_Stone_Cobble_Mossy");
        var workingSides=new ArrayList<Set<Cell>>();
        for(var node:layout.nodes()) {
            var cell=new Cell(node.x(),node.y(),node.z());
            assertTrue(safeRock.contains(node.expectedBlockId()),"Only reviewed plain rock may become a vein");
            assertEquals(node.expectedBlockId(),name(blocks,cell));
            assertFalse(blocks.get(cell).has("components"),"Do not replace block entities");
            assertEquals("Empty",name(blocks,cell.offset(0,1,0)),"Ore needs room above the original cell");
            var sides=new HashSet<Cell>();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                var side=cell.offset(d[0],0,d[1]);
                if(standable(blocks,side,safeRock)) sides.add(side);
            }
            assertFalse(sides.isEmpty(),"Vein needs an adjacent supported two-block-clear stand cell: "+cell);
            workingSides.add(sides);
        }
        // All working sides must connect through the authored chamber, without standing atop a vein.
        var seen=new HashSet<Cell>(); var pending=new ArrayDeque<Cell>();
        pending.add(workingSides.getFirst().iterator().next());
        while(!pending.isEmpty()) {
            var p=pending.remove(); if(!seen.add(p))continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                var next=p.offset(d[0],0,d[1]);
                if(!seen.contains(next) && standable(blocks,next,safeRock)) pending.add(next);
            }
        }
        for(var sides:workingSides) assertTrue(sides.stream().anyMatch(seen::contains),"Working spaces must share a clear chamber route");
        var newPlot=new PlotInstance(); newPlot.captureProductionLayout(definition);
        assertEquals(layout,newPlot.getProductionLayout());
        var oldPlot=GSON.fromJson("{\"constructionId\":\""+definition.getId()+"\"}",PlotInstance.class);
        assertTrue(oldPlot.getProductionLayout().legacy(),"Updating official definitions must not migrate old placed copies");
    }

    private static boolean standable(Map<Cell,JsonObject> blocks,Cell p,Set<String> floors) {
        return floors.contains(name(blocks,p.offset(0,-1,0)))
            && "Empty".equals(name(blocks,p)) && "Empty".equals(name(blocks,p.offset(0,1,0)));
    }
    private static String name(Map<Cell,JsonObject> blocks,Cell p) {
        var b=blocks.get(p); return b==null?"Empty":b.get("name").getAsString();
    }
}
