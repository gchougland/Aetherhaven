package com.hexvane.aetherhaven.reputation;

import com.google.gson.*;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.workplace.WorkplaceRecipe;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ReputationRecipeMigrationTest {
    @Test void existingKitClaimsLearnOnlyTheirRecipesOnceAndPersist() throws Exception {
        var gson=new Gson();var player=UUID.randomUUID();var town=new TownRecord();
        var recipe=gson.fromJson(Files.readString(Path.of("src/main/resources/Server/Aetherhaven/WorkplaceRecipes/craft_leatherworker_kit.json")),WorkplaceRecipe.class);
        var reward=new ReputationRewardCatalog.ReputationRewardDefinition("rep_leatherworker_25","Aetherhaven_Leatherworker",25,"",0,"rep_reward_25",recipe.output());
        var recipes=List.of(recipe);var rewards=List.of(reward);
        assertFalse(ReputationRecipeMigration.repairWorkplaceRecipes(town,player,recipes,rewards));
        assertFalse(recipe.unlocked(town));
        VillagerReputationService.getOrCreateEntry(town,player,UUID.randomUUID()).getClaimedRewardIds().add(reward.rewardId());
        assertFalse(ReputationRecipeMigration.repairWorkplaceRecipes(town,UUID.randomUUID(),recipes,rewards));
        assertTrue(ReputationRecipeMigration.repairWorkplaceRecipes(town,player,recipes,rewards));assertTrue(recipe.unlocked(town));
        var restored=gson.fromJson(gson.toJson(town),TownRecord.class);
        assertTrue(recipe.unlocked(restored));assertFalse(ReputationRecipeMigration.repairWorkplaceRecipes(restored,player,recipes,rewards));
        assertEquals(0,restored.getWarehouse().used(com.hexvane.aetherhaven.warehouse.TownWarehouse.Page.GENERAL));
    }
    @Test void shippedProfessionRewardsTeachKnowledgeAndNeverGiveFinishedKits() throws Exception {
        var root=Path.of("src/main/resources/Server/Aetherhaven");var gson=new Gson();
        var kits=new HashSet<String>();
        try(var paths=Files.list(root.resolve("ProfessionKits"))) {
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList())
                kits.add(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("itemId").getAsString());
        }
        var recipes=new ArrayList<WorkplaceRecipe>();
        try(var paths=Files.list(root.resolve("WorkplaceRecipes"))) {
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList())recipes.add(gson.fromJson(Files.readString(file),WorkplaceRecipe.class));
        }
        int checked=0;
        try(var paths=Files.list(root.resolve("Villagers"))) {
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var rewards=data.getAsJsonArray("reputationMilestones");if(rewards==null)continue;
                for(var value:rewards) {
                    var reward=value.getAsJsonObject();assertFalse(kits.contains(reward.get("itemId").getAsString()),file.toString());
                    var learned=reward.get("learnRecipeItemId");if(learned==null||learned.isJsonNull()||!kits.contains(learned.getAsString()))continue;
                    var recipe=recipes.stream().filter(r->learned.getAsString().equals(r.output())).findFirst().orElseThrow();
                    assertTrue(recipe.requiresKnowledge(),recipe.id());assertTrue(recipe.townOnly());assertEquals(0,reward.get("itemCount").getAsInt());checked++;
                }
            }
        }
        assertTrue(checked>=9);
    }
    @Test void firstWarehouseRewardUnlocksTheSupplyLinkWithCompactAssetsAndAnInteractionPrompt() throws Exception {
        Path resources=Path.of("src/main/resources");
        var villager=JsonParser.parseString(Files.readString(resources.resolve("Server/Aetherhaven/Villagers/Aetherhaven_Warehouse_Manager.json"))).getAsJsonObject();
        var first=villager.getAsJsonArray("reputationMilestones").get(0).getAsJsonObject();String id="Aetherhaven_Warehouse_Supply_Link";
        assertEquals(id,first.get("learnRecipeItemId").getAsString());assertEquals(25,first.get("minReputation").getAsInt());assertEquals(0,first.get("itemCount").getAsInt());
        var item=JsonParser.parseString(Files.readString(resources.resolve("Server/Item/Items/Aetherhaven/"+id+".json"))).getAsJsonObject();
        assertTrue(item.getAsJsonObject("Recipe").get("KnowledgeRequired").getAsBoolean());
        var block=item.getAsJsonObject("BlockType");assertTrue(block.has("InteractionHint"));assertTrue(block.getAsJsonObject("BlockEntity").getAsJsonObject("Components").has("AetherhavenWarehouseSupplyLink"));
        assertTrue(Files.exists(resources.resolve("Common/"+block.get("CustomModel").getAsString())));
        var image=javax.imageio.ImageIO.read(resources.resolve("Common/"+block.getAsJsonArray("CustomModelTexture").get(0).getAsJsonObject().get("Texture").getAsString()).toFile());
        assertEquals(128,image.getWidth());assertEquals(128,image.getHeight());
        String hints=Files.readString(resources.resolve("Server/Languages/en-US/aetherhaven_warehouse.lang"));
        assertTrue(hints.lines().anyMatch(line->line.startsWith("items.supplyLink.hint=")&&line.contains("{key}")));
        var model=JsonParser.parseString(Files.readString(resources.resolve("Common/"+block.get("CustomModel").getAsString()))).getAsJsonObject();
        var hitboxes=JsonParser.parseString(Files.readString(resources.resolve("Server/Item/Block/Hitboxes/Aetherhaven/"+id+".json"))).getAsJsonObject().getAsJsonArray("Boxes");
        // Prop model coordinates are centered on the block; collision coordinates start at its corner.
        for(var entry:model.getAsJsonArray("nodes")) {
            var node=entry.getAsJsonObject();var shape=node.getAsJsonObject("shape");
            var offset=shape.getAsJsonObject("offset");var size=shape.getAsJsonObject("settings").getAsJsonObject("size");
            var position=node.getAsJsonObject("position");
            for(int corner=0;corner<8;corner++) {
                double[] point=new double[3];
                for(int axis=0;axis<3;axis++) {
                    String key="xyz".substring(axis,axis+1);
                    point[axis]=(position.get(key).getAsDouble()+offset.get(key).getAsDouble()
                        +size.get(key).getAsDouble()*((corner&(1<<axis))==0?-.5:.5))/32+(axis==1?0:.5);
                }
                boolean covered=false;
                for(var box:hitboxes) {
                    boolean inside=true;
                    for(int axis=0;axis<3;axis++) {
                        String key="XYZ".substring(axis,axis+1);
                        inside &= point[axis]>=box.getAsJsonObject().getAsJsonObject("Min").get(key).getAsDouble()-1e-6
                            &&point[axis]<=box.getAsJsonObject().getAsJsonObject("Max").get(key).getAsDouble()+1e-6;
                    }
                    covered |= inside;
                }
                assertTrue(covered,node.get("name").getAsString()+" has geometry outside the hitbox");
            }
        }
        assertTrue(Files.exists(resources.resolve("Common/"+item.get("Icon").getAsString())));
    }
}
