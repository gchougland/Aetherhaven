package com.hexvane.aetherhaven.construction;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Protect authored furnishings and the playable areas when origins or rotations change. */
@Tag("town")
class ImportedSpecialistPrefabsTest {
    private static final Path ROOT=Path.of("src/main/resources/Server");
    private static final List<String> KINDS=List.of("alchemist","doctor","druid","monster_hunter","warehouse","simple_workplace");
    private record Cell(int x,int y,int z) {
        Cell rotate(int turns) {var p=this;for(int i=0;i<turns;i++)p=new Cell(-p.z,p.y,p.x);return p;}
        Cell add(int dx,int dy,int dz){return new Cell(x+dx,y+dy,z+dz);}
    }
    private static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    private static JsonObject definition(String kind)throws Exception{return read(ROOT.resolve("Aetherhaven/Buildings/plot_"+kind+".json"));}
    private static JsonObject prefab(String kind)throws Exception{return read(ROOT.resolve("Prefabs/"+definition(kind).get("prefabPath").getAsString()));}
    private static Cell cell(JsonObject b){return new Cell(b.get("x").getAsInt(),b.get("y").getAsInt(),b.get("z").getAsInt());}
    private static Cell point(JsonArray a){return new Cell(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static Map<Cell,JsonObject> cells(JsonObject prefab){
        Map<Cell,JsonObject> map=new HashMap<>();
        for(var e:prefab.getAsJsonArray("blocks")){var b=e.getAsJsonObject();assertNull(map.put(cell(b),b),"Duplicate cell "+cell(b));}
        return map;
    }
    private static String name(Map<Cell,JsonObject> cells,Cell p){return cells.containsKey(p)?cells.get(p).get("name").getAsString():"Empty";}
    private static int signed(int n){return(n&16)!=0?n-32:n;}

    @Test void furnishingsOriginsAndWorkAreasRemainValidInEveryRotation()throws Exception {
        for(String kind:KINDS){
            var def=definition(kind);var source=cells(prefab(kind));
            assertNotNull(def.getAsJsonArray("plotAnchorOffset"),kind);
            Cell min=point(def.getAsJsonObject("boundsLocal").getAsJsonArray("min"));
            Cell max=point(def.getAsJsonObject("boundsLocal").getAsJsonArray("max"));
            for(int turns=0;turns<4;turns++){
                Map<Cell,JsonObject> rotated=new HashMap<>();for(var entry:source.entrySet())rotated.put(entry.getKey().rotate(turns),entry.getValue());
                Cell rmin=min.rotate(turns),rmax=max.rotate(turns);
                for(var entry:source.entrySet()){
                    Cell p=entry.getKey().rotate(turns);var block=entry.getValue();
                    assertTrue(p.x>=Math.min(rmin.x,rmax.x)&&p.x<=Math.max(rmin.x,rmax.x)&&p.y>=min.y&&p.y<=max.y
                        &&p.z>=Math.min(rmin.z,rmax.z)&&p.z<=Math.max(rmin.z,rmax.z),kind+" bounds "+p);
                    if(block.has("filler")&&block.get("filler").getAsInt()!=0){
                        int f=block.get("filler").getAsInt();
                        Cell anchor=entry.getKey().add(-signed(f&31),-signed((f>>10)&31),-signed((f>>5)&31)).rotate(turns);
                        assertNotNull(rotated.get(anchor),kind+" missing filler anchor "+anchor);
                        assertEquals(block.get("name"),rotated.get(anchor).get("name"),kind+" filler name");
                        assertFalse(rotated.get(anchor).has("filler")&&rotated.get(anchor).get("filler").getAsInt()!=0,kind+" nested filler");
                    }
                }
                if(!kind.equals("simple_workplace")){
                    assertEquals("Aetherhaven_Management_Block",name(rotated,point(def.getAsJsonArray("managementBlockLocalPos")).rotate(turns)),kind);
                    for(var e:def.getAsJsonArray("pois")){
                        var poi=e.getAsJsonObject();Cell p=new Cell(poi.get("localX").getAsInt(),poi.get("localY").getAsInt(),poi.get("localZ").getAsInt()).rotate(turns);
                        assertEquals("Empty",name(rotated,p),kind+" work feet");
                        assertEquals("Empty",name(rotated,p.add(0,1,0)),kind+" work head");
                        assertNotEquals("Empty",name(rotated,p.add(0,-1,0)),kind+" work floor");
                    }
                }
            }
        }
    }

    @Test void druidPlantsAreHarvestableBlocksAndRetainTheStorage()throws Exception {
        var prefab=prefab("druid");var blocks=cells(prefab);
        for(String family:List.of("Health","Mana","Stamina"))for(int tier=1;tier<=3;tier++){
            String expected="Plant_Crop_"+family+tier;
            var plant=blocks.entrySet().stream().filter(e->expected.equals(e.getValue().get("name").getAsString())
                &&(!e.getValue().has("filler")||e.getValue().get("filler").getAsInt()==0)).findFirst().orElseThrow();
            assertEquals("Soil_Dirt_Crystal",name(blocks,plant.getKey().add(0,-1,0)));
            if(tier==3){
                var upper=blocks.get(plant.getKey().add(0,1,0));
                assertNotNull(upper,expected+" needs room for its upper half");
                assertEquals(expected,upper.get("name").getAsString());
                assertEquals(1024,upper.get("filler").getAsInt());
            }
        }
        assertEquals("Aetherhaven_Production_Storage",name(blocks,new Cell(0,0,3)));
        for(var e:prefab.has("entities")?prefab.getAsJsonArray("entities"):new JsonArray())assertFalse(e.getAsJsonObject().getAsJsonObject("Components").has("NPC"),"Editor NPC must not become a resident");
    }

    @Test void arenaHasClearFloorAndHeadroomAroundTheBeaconForAllRotations()throws Exception {
        var original=cells(prefab("monster_hunter"));
        for(int turn=0;turn<4;turn++){
            Map<Cell,JsonObject> rotated=new HashMap<>();for(var e:original.entrySet())rotated.put(e.getKey().rotate(turn),e.getValue());
            Cell beacon=original.entrySet().stream().filter(e->"Aetherhaven_Hunt_Beacon".equals(e.getValue().get("name").getAsString())).findFirst().orElseThrow().getKey().rotate(turn);
            assertEquals("Aetherhaven_Hunt_Beacon",name(rotated,beacon));
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                assertNotEquals("Empty",name(rotated,beacon.add(dx,0,dz)),"Arena floor");
                assertEquals("Empty",name(rotated,beacon.add(dx,1,dz)),"Arena feet");
                assertEquals("Empty",name(rotated,beacon.add(dx,2,dz)),"Arena head");
            }
        }
    }

    @Test void authoredCauldronAndFunctionalLedgerArePresentAndOldVariantsAreRemoved()throws Exception {
        assertEquals("Alchemy_Cauldron_Big",name(cells(prefab("alchemist")),new Cell(0,0,0)));
        assertEquals("Aetherhaven_Warehouse_Ledger_Desk",name(cells(prefab("warehouse")),new Cell(1,0,0)));
        assertTrue(definition("simple_workplace").get("decorationPlot").getAsBoolean());
        for(String kind:List.of("alchemist","doctor","druid","monster_hunter","warehouse","crafter","leatherworker"))
            assertFalse(Files.exists(ROOT.resolve("Aetherhaven/Buildings/Stormwind/plot_stormwind_"+kind+".json")),kind);
    }
}
