package com.hexvane.aetherhaven.town;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Recruitment, construction and artwork must remain a connected content chain. */
@Tag("town")
class AlchemicalVillagerContentTest {
    private static final Path ROOT=Path.of("src/main/resources");
    private JsonObject read(Path path) throws Exception {return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}

    @Test void specialistFriendshipConnectsGiftsToTownOnlyPlansAndAdvancedFormulas() throws Exception {
        var gson=new com.google.gson.Gson();
        var ids=new HashSet<String>();
        for(String kind:new String[]{"druid","alchemist"}) {
            String role=Character.toUpperCase(kind.charAt(0))+kind.substring(1);
            String kit=kind.equals("druid")?"Botanist":"Alchemist";
            var villager=read(ROOT.resolve("Server/Aetherhaven/Villagers/Aetherhaven_"+role+".json"));
            var dialogue=read(ROOT.resolve("Server/Aetherhaven/Dialogue/aetherhaven_"+kind+".json")).getAsJsonObject("nodes");
            var rewards=villager.getAsJsonArray("reputationMilestones");
            assertEquals(3,rewards.size());
            for(int i=0;i<3;i++) {
                var reward=rewards.get(i).getAsJsonObject();
                String id=reward.get("rewardId").getAsString();assertTrue(ids.add(id));
                assertEquals(25*(i+1),reward.get("minReputation").getAsInt());
                var node=dialogue.getAsJsonObject(reward.get("dialogueNodeId").getAsString());
                assertEquals(id,node.getAsJsonArray("actions").get(0).getAsJsonObject().get("rewardId").getAsString());
                if(i==0) {assertEquals("Aetherhaven_"+kit+"_Kit",reward.get("itemId").getAsString());continue;}
                Path path=i==1?ROOT.resolve("Server/Aetherhaven/WorkplaceRecipes/craft_"+kit.toLowerCase(java.util.Locale.ROOT)+"_kit.json")
                    :Path.of("subplugin-assets/ReputationUnlocks/Server/Aetherhaven/WorkplaceRecipes/"+(kind.equals("druid")?"brew_verdant_catalyst":"brew_gaia_shard")+".json");
                var recipe=gson.fromJson(Files.readString(path),com.hexvane.aetherhaven.workplace.WorkplaceRecipe.class);
                assertTrue(recipe.valid());assertTrue(recipe.townOnly());assertTrue(recipe.requiresKnowledge());
                assertEquals(reward.get("learnRecipeItemId").getAsString(),recipe.output());
                var town=new TownRecord();assertFalse(recipe.unlocked(town));
                assertTrue(com.hexvane.aetherhaven.workplace.WorkplaceRecipeUnlocks.grant(town,java.util.List.of(recipe),recipe.output()));
                assertTrue(recipe.unlocked(town));
                if(i==2) {
                    int tier=kind.equals("druid")?2:3;
                    for(String family:java.util.List.of("Health","Stamina","Mana"))assertTrue(recipe.inputs().stream()
                        .anyMatch(input->("Plant_Crop_"+family+tier).equals(input.itemId())),"Advanced formulas must use all three existing plant families");
                    assertTrue(recipe.skillLevel()>=6);assertTrue(recipe.benchTier()>=3);
                }
            }
        }
    }

    @Test void recruitmentScreenTemplatesExist() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/inn/InnOutreachPage.java"));
        var matcher=java.util.regex.Pattern.compile("\"(Aetherhaven/[^\"]+\\.ui)\"").matcher(source);
        int templates=0;
        while(matcher.find()) {
            assertTrue(Files.isRegularFile(ROOT.resolve("Common/UI/Custom/"+matcher.group(1))),matcher.group(1));
            templates++;
        }
        assertTrue(templates>=3);
    }

    @Test void specialistsHaveReachableRecruitmentAndAuthoredCoreBuildings() throws Exception {
        for(String kind:new String[]{"druid","alchemist"}) {
            String model=Character.toUpperCase(kind.charAt(0))+kind.substring(1);
            var villager=read(ROOT.resolve("Server/Aetherhaven/Villagers/Aetherhaven_"+model+".json"));
            var npc=read(ROOT.resolve("Server/NPC/Roles/Aetherhaven/"+villager.get("npcRoleId").getAsString()+".json"));
            assertTrue(Files.isRegularFile(ROOT.resolve("Server/Models/Villager/"+npc.get("Appearance").getAsString()+".json")));
            assertTrue(Files.isRegularFile(ROOT.resolve("Common/UI/Custom/Pages/Memories/npcs/"+villager.get("portraitIcon").getAsString())));
            assertTrue(villager.get("innPoolEligible").getAsBoolean());
            assertTrue(villager.get("minimumTownLevel").getAsInt()>1,"Do not dilute the founding food visitor pool");
            String construction=villager.get("workConstructionId").getAsString();
            var quest=read(Path.of("subplugin-assets/Quests/Server/Aetherhaven/Quests/Town/q_"+kind+".json"));
            assertEquals(construction,quest.get("grantPlotTokenConstructionId").getAsString());
            assertEquals(construction,quest.getAsJsonArray("objectives").get(1).getAsJsonObject().get("constructionId").getAsString());
            var dialogue=read(ROOT.resolve("Server/Aetherhaven/Dialogue/"+villager.get("residentTreeId").getAsString()+".json"));
            assertEquals(quest.get("id").getAsString(),dialogue.getAsJsonObject("nodes").getAsJsonObject("offer").getAsJsonArray("choices")
                .get(0).getAsJsonObject().getAsJsonArray("actions").get(0).getAsJsonObject().get("id").getAsString());
            assertFalse(Files.exists(ROOT.resolve("Server/Aetherhaven/Buildings/Stormwind/plot_stormwind_"+kind+".json")));
            for(String file:new String[]{construction}) {
                var building=read(ROOT.resolve("Server/Aetherhaven/Buildings/"+file+".json"));
                if(file.startsWith("Stormwind/"))assertEquals(construction,building.get("countsAsConstructionId").getAsString());
                var prefab=read(ROOT.resolve("Server/Prefabs/"+building.get("prefabPath").getAsString()));
                var cells=new java.util.HashMap<String,JsonObject>();
                for(var entry:prefab.getAsJsonArray("blocks")) {
                    var b=entry.getAsJsonObject();
                    cells.put(b.get("x")+","+b.get("y")+","+b.get("z"),b);
                }
                for(var entry:building.getAsJsonArray("pois")) {
                    var poi=entry.getAsJsonObject();
                    int x=poi.get("localX").getAsInt(),y=poi.get("localY").getAsInt(),z=poi.get("localZ").getAsInt();
                    for(int dy=0;dy<2;dy++) {
                        var cell=cells.get(x+","+(y+dy)+","+z);
                        assertTrue(cell==null||"Empty".equals(cell.get("name").getAsString()),file+": a work spot needs clear feet and head space");
                    }
                    var floor=cells.get(x+","+(y-1)+","+z);
                    assertNotNull(floor,file+": work spot needs a floor");
                    assertEquals(poi.get("blockTypeId").getAsString(),floor.get("name").getAsString());
                    String surface=kind.equals("druid")?"Bench_Farming":"Alchemy_Cauldron_Big";
                    assertTrue(cells.values().stream().anyMatch(b -> surface.equals(b.get("name").getAsString())
                        && Math.abs(b.get("x").getAsInt()-x)+Math.abs(b.get("z").getAsInt()-z)<=3),file+": work spot must be near its work surface");
                }
                Set<String> management=new HashSet<>();
                int crystalSoil=0;
                for(var entry:prefab.getAsJsonArray("blocks")) {
                    var b=entry.getAsJsonObject();String name=b.get("name").getAsString();
                    if("Aetherhaven_Management_Block".equals(name))management.add(b.get("x")+","+b.get("y")+","+b.get("z"));
                    if("Soil_Dirt_Crystal".equals(name))crystalSoil++;
                }
                var pos=building.getAsJsonArray("managementBlockLocalPos");
                assertEquals(Set.of(pos.get(0)+","+pos.get(1)+","+pos.get(2)),management,"Construction must point to its actual management block");
                if(kind.equals("druid")) {
                    int paidSoil=0;
                    for(var entry:building.getAsJsonArray("materials")) {
                        var m=entry.getAsJsonObject();
                        if(m.has("itemId")&&"Soil_Dirt_Crystal".equals(m.get("itemId").getAsString()))paidSoil+=m.get("count").getAsInt();
                    }
                    assertTrue(crystalSoil>0);assertEquals(crystalSoil,paidSoil,"Do not create free crystal soil through plot construction");
                }
            }
            var building=read(ROOT.resolve("Server/Aetherhaven/Buildings/"+construction+".json"));
            var token=read(ROOT.resolve("Server/Item/Items/Aetherhaven/"+building.get("plotTokenItemId").getAsString()+".json"));
            assertTrue(Files.isRegularFile(ROOT.resolve("Common/"+token.get("Icon").getAsString())));
        }
    }
}
