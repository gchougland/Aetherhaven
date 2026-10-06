package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceRules;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("town")
class ArtisanRecruitmentContentTest {
    private static final Path ROOT=Path.of("src/main/resources");
    private static JsonObject read(Path path) throws Exception {return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    private static JsonObject read(String path) throws Exception {return read(ROOT.resolve(path));}
    private static String at(int x,int y,int z){return x+","+y+","+z;}

    @Test void artisanSupervisorsHaveTheirOwnRecruitmentAndReachableWorkshopPlans() throws Exception {
        for(var entry:Map.of(TownsfolkWorkType.CRAFTING,"Crafter",TownsfolkWorkType.LEATHERWORKING,"Leatherworker").entrySet()) {
            String kind=WorkplaceRules.supervisor(entry.getKey()),role="Aetherhaven_"+entry.getValue();
            var villager=read("Server/Aetherhaven/Villagers/"+role+".json");
            assertEquals(kind,villager.get("dialogueVillagerKind").getAsString());
            assertTrue(villager.get("innPoolEligible").getAsBoolean());
            assertEquals(kind.equals("crafter")?3:4,villager.get("minimumTownLevel").getAsInt());
            assertEquals("plot_"+kind,villager.get("workConstructionId").getAsString());
            var dialogue=read("Server/Aetherhaven/Dialogue/aetherhaven_"+kind+".json");
            assertFalse(dialogue.toString().contains("warehouse"),"No borrowed recruitment actions may remain");
            var quest=read(Path.of("subplugin-assets/Quests/Server/Aetherhaven/Quests/Town/q_"+kind+".json"));
            assertEquals("plot_"+kind,quest.get("grantPlotTokenConstructionId").getAsString());
            assertEquals("plot_"+kind,quest.getAsJsonArray("objectives").get(1).getAsJsonObject().get("constructionId").getAsString());
            var item=read("Server/Item/Items/Aetherhaven/Aetherhaven_Plot_Token_"+entry.getValue()+".json");
            assertTrue(Files.isRegularFile(ROOT.resolve("Common/"+item.get("Icon").getAsString())));
            assertTrue(Files.isRegularFile(ROOT.resolve("Common/Icons/ModelsGenerated/"+villager.get("portraitIcon").getAsString())));
        }
    }

    @Test void coreWorkshopsHaveClearWorkCellsAndValidRecordsShelves() throws Exception {
        for(String kind:List.of("crafter","leatherworker")) {
            assertFalse(Files.exists(ROOT.resolve("Server/Aetherhaven/Buildings/Stormwind/plot_stormwind_"+kind+".json")));
            String prefix="plot_";
            var building=read("Server/Aetherhaven/Buildings/"+prefix+kind+".json");
            var prefab=read("Server/Prefabs/"+building.get("prefabPath").getAsString());
            Map<String,JsonObject> cells=new HashMap<>();
            for(var value:prefab.getAsJsonArray("blocks")) {
                var b=value.getAsJsonObject();cells.put(at(b.get("x").getAsInt(),b.get("y").getAsInt(),b.get("z").getAsInt()),b);
            }
            var pos=building.getAsJsonArray("managementBlockLocalPos");
            assertEquals("Aetherhaven_Management_Block",cells.get(at(pos.get(0).getAsInt(),pos.get(1).getAsInt(),pos.get(2).getAsInt())).get("name").getAsString());
            var poi=building.getAsJsonArray("pois").get(0).getAsJsonObject();
            int x=poi.get("localX").getAsInt(),y=poi.get("localY").getAsInt(),z=poi.get("localZ").getAsInt();
            assertEquals(kind,poi.get("workResidentKind").getAsString());
            for(int dy=0;dy<2;dy++)assertEquals("Empty",cells.get(at(x,y+dy,z)).get("name").getAsString(),kind+" standing space");
            assertFalse(cells.get(at(x,y-1,z)).get("name").getAsString().equals("Empty"));
            String requiredBench=kind.equals("crafter")?"Bench_WorkBench":"Bench_Tannery";
            assertTrue(cells.values().stream().anyMatch(b -> requiredBench.equals(b.get("name").getAsString())),"Workshop needs its profession bench");
            assertTrue(cells.values().stream().anyMatch(b -> (requiredBench.equals(b.get("name").getAsString())||"Bench_WorkBench".equals(b.get("name").getAsString()))
                && Math.abs(b.get("x").getAsInt()-x)+Math.abs(b.get("z").getAsInt()-z)<=3),"A work spot needs a nearby bench");
            
            for(var value:prefab.has("entities")?prefab.getAsJsonArray("entities"):new com.google.gson.JsonArray()) {
                var components=value.getAsJsonObject().getAsJsonObject("Components");
                String itemId=components.getAsJsonObject("Item").getAsJsonObject("Item").get("Id").getAsString();
                // Cloth, hide, fibre and repair kits have item models, not block types.
                if(itemId.startsWith("Ingredient_")||itemId.contains("Repair_Kit"))
                    assertFalse(components.has("BlockEntity"),"Loose material prop must not request a block renderer: "+itemId);
                assertTrue(components.has("Prop"));
                assertTrue(components.has("PreventPickup"));
            }
        }
    }
}
