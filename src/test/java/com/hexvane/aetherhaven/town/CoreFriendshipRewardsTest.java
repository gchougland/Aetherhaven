package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.equipment.ProfessionKit;
import com.hexvane.aetherhaven.workplace.WorkplaceRecipe;
import com.hexvane.aetherhaven.workplace.WorkplaceRecipeUnlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the shipped reward, dialogue, crafting and artwork connections as a whole. */
@Tag("town")
class CoreFriendshipRewardsTest {
    private static final Path ROOT=Path.of("src/main/resources");
    private static final Gson GSON=new Gson();
    private JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }
    private WorkplaceRecipe recipe(String kind) throws Exception {
        return GSON.fromJson(Files.readString(ROOT.resolve("Server/Aetherhaven/WorkplaceRecipes/craft_"+kind+"_kit.json")),WorkplaceRecipe.class);
    }

    @Test void existingVillagersHaveThreeClaimableRewardsWithoutReplacingOldMilestones() throws Exception {
        Map<String,Set<String>> legacy=Map.ofEntries(
            Map.entry("Chef",Set.of("rep_chef_50")),Map.entry("Merchant",Set.of("rep_merchant_50","rep_merchant_100")),
            Map.entry("Rancher",Set.of("rep_rancher_50","rep_rancher_100")),Map.entry("Blacksmith",Set.of("rep_blacksmith_25","rep_blacksmith_50")),
            Map.entry("Crafter",Set.of()),Map.entry("Leatherworker",Set.of()),
            Map.entry("Elder_Lyren",Set.of("rep_elder_50","rep_elder_100")),
            Map.entry("Logger",Set.of("rep_logger_50","rep_logger_100")),
            Map.entry("Miner",Set.of("rep_miner_50","rep_miner_100")),
            Map.entry("Guild_Master",Set.of("rep_guild_master_50")),
            Map.entry("Pyrotechnic",Set.of("rep_pyrotechnic_50")),
            Map.entry("Bard",Set.of()),Map.entry("Builder",Set.of()),Map.entry("Clown",Set.of()),
            Map.entry("Crystal_Keeper",Set.of()),Map.entry("Florist",Set.of()),Map.entry("Furniture_Merchant",Set.of()));
        Set<String> ids=new HashSet<>();
        for(var role:legacy.keySet()) {
            var villager=read("Server/Aetherhaven/Villagers/Aetherhaven_"+role+".json");
            var nodes=read("Server/Aetherhaven/Dialogue/"+villager.get("residentTreeId").getAsString()+".json").getAsJsonObject("nodes");
            var rewards=villager.getAsJsonArray("reputationMilestones");
            assertEquals(3,rewards.size(),role);
            Set<String> own=new HashSet<>();
            int previous=0;
            for(var entry:rewards) {
                var reward=entry.getAsJsonObject();String id=reward.get("rewardId").getAsString();
                assertTrue(ids.add(id),"Reward IDs must not collide across villagers");own.add(id);
                int level=reward.get("minReputation").getAsInt();assertTrue(level>previous&&level<=100);previous=level;
                var node=nodes.getAsJsonObject(reward.get("dialogueNodeId").getAsString());assertNotNull(node,id);
                assertTrue(node.getAsJsonArray("actions").asList().stream().anyMatch(a->
                    "reputation_reward_grant".equals(a.getAsJsonObject().get("type").getAsString())
                    &&id.equals(a.getAsJsonObject().get("rewardId").getAsString())),id);
            }
            assertTrue(own.containsAll(legacy.get(role)),role+" must retain existing claim IDs");
        }
    }

    @Test void professionRewardsConnectToTownRecipesAndOriginalItemArtwork() throws Exception {
        String lang=Files.readString(ROOT.resolve("Server/Languages/en-US/aetherhaven_profession_kits.lang"));
        for(String kind:List.of("cook","jeweler","rancher","mastersmith","crafter","leatherworker")) {
            var kit=GSON.fromJson(Files.readString(ROOT.resolve("Server/Aetherhaven/ProfessionKits/"+kind+".json")),ProfessionKit.class);
            assertTrue(kit.valid(),kind);var recipe=recipe(kind);assertTrue(recipe.valid());assertTrue(recipe.townOnly());
            assertEquals(kit.itemId(),recipe.output());assertEquals(1,recipe.outputCount());
            assertTrue(recipe.skillLevel()>=3);assertEquals(0,kit.extraCarryingStacks());
            var item=read("Server/Item/Items/Aetherhaven/"+kit.itemId()+".json");
            assertFalse(item.has("Recipe"),"Players must not craft profession kits directly");
            for(String field:List.of("Model","Texture","Icon"))assertTrue(Files.isRegularFile(ROOT.resolve("Common/"+item.get(field).getAsString())),kind+" "+field);
            var icon=ImageIO.read(ROOT.resolve("Common/"+item.get("Icon").getAsString()).toFile());
            assertEquals(64,icon.getWidth());assertEquals(64,icon.getHeight());
            assertTrue(lang.contains(kind+".name = "));assertTrue(lang.contains(kind+".description = "));
        }
    }

    @Test void newRewardIdsSurviveLegacySaveMigrationBeforeAndAfterClaiming() throws Exception {
        for(String role:List.of("Chef","Merchant","Rancher","Blacksmith","Crafter","Leatherworker",
                "Elder_Lyren","Logger","Miner","Guild_Master","Pyrotechnic",
                "Bard","Builder","Clown","Crystal_Keeper","Florist","Furniture_Merchant")) {
            for(var value:read("Server/Aetherhaven/Villagers/Aetherhaven_"+role+".json").getAsJsonArray("reputationMilestones")) {
                var reward=value.getAsJsonObject();String id=reward.get("rewardId").getAsString();
                var entry=new com.hexvane.aetherhaven.reputation.VillagerReputationEntry();
                entry.setReputation(100);entry.getPendingRewardIds().add(id);
                entry.migrateIfNeeded();assertTrue(entry.getPendingRewardIds().contains(id),id+" must remain claimable");
                entry.getPendingRewardIds().remove(id);entry.getClaimedRewardIds().add(id);
                var restored=GSON.fromJson(GSON.toJson(entry),com.hexvane.aetherhaven.reputation.VillagerReputationEntry.class);
                restored.migrateIfNeeded();assertTrue(restored.getClaimedRewardIds().contains(id),id+" must remain claimed after reload");
            }
        }
    }

    @Test void friendshipPlansRemainSeparateAndPersistWithTown() throws Exception {
        var cook=recipe("cook");var smith=recipe("mastersmith");var all=List.of(cook,smith);
        assertTrue(cook.requiresKnowledge());assertTrue(smith.requiresKnowledge());
        var town=new TownRecord();assertFalse(cook.unlocked(town));assertFalse(smith.unlocked(town));
        assertFalse(WorkplaceRecipeUnlocks.grant(town,all,"Ingredient_Bar_Iron"));
        assertTrue(WorkplaceRecipeUnlocks.grant(town,all,cook.output()));
        assertTrue(cook.unlocked(town));assertFalse(smith.unlocked(town));
        var restored=GSON.fromJson(GSON.toJson(town),TownRecord.class);
        assertTrue(cook.unlocked(restored));assertFalse(smith.unlocked(restored));
        assertTrue(WorkplaceRecipeUnlocks.grant(restored,all,smith.output()));assertTrue(smith.unlocked(restored));
    }

    @Test void glowRingAndFireworkPlansStayIndependentAndKeepTheirExistingArtwork() throws Exception {
        var roots=List.of(Path.of("subplugin-assets/Jewelry"),ROOT);
        var ids=List.of("craft_glow_ring","craft_festival_fireworks");
        var plans=new ArrayList<WorkplaceRecipe>();
        for(int i=0;i<ids.size();i++) {
            var plan=GSON.fromJson(Files.readString(roots.get(i).resolve("Server/Aetherhaven/WorkplaceRecipes/"+ids.get(i)+".json")),WorkplaceRecipe.class);
            assertTrue(plan.valid());assertTrue(plan.townOnly());assertTrue(plan.requiresKnowledge());
            assertEquals(1,plan.benchTier());assertNull(plan.rarity(),"Special gifts must retain their original item behavior");
            var item=JsonParser.parseString(Files.readString(roots.get(i).resolve("Server/Item/Items/Aetherhaven/"+plan.output()+".json"))).getAsJsonObject();
            assertFalse(item.has("Recipe"),"Friendship plans must not enable direct player crafting");
            for(String field:List.of("Model","Texture","Icon")) {
                String path="Common/"+item.get(field).getAsString();
                assertTrue(Files.isRegularFile(roots.get(i).resolve(path))||Files.isRegularFile(ROOT.resolve(path)),plan.output()+" "+field);
            }
            plans.add(plan);
        }
        var ring=plans.get(0);var fireworks=plans.get(1);
        assertEquals("jewelry",ring.workplace());assertEquals("Aetherhaven_Jewelry_Crafting_Bench",ring.bench());
        assertEquals(5,ring.skillLevel());assertEquals(1,ring.outputCount());
        assertEquals("crafting",fireworks.workplace());assertEquals("Workbench",fireworks.bench());
        assertEquals(4,fireworks.skillLevel());assertEquals(4,fireworks.outputCount());
        for(int selected=0;selected<plans.size();selected++) {
            var town=new TownRecord();assertTrue(plans.stream().noneMatch(p->p.unlocked(town)));
            assertTrue(WorkplaceRecipeUnlocks.grant(town,plans,plans.get(selected).output()));
            var restored=GSON.fromJson(GSON.toJson(town),TownRecord.class);
            for(int i=0;i<plans.size();i++)assertEquals(i==selected,plans.get(i).unlocked(restored));
        }
        for(var entry:Map.of("Miner",ring,"Pyrotechnic",fireworks).entrySet()) {
            var rewards=read("Server/Aetherhaven/Villagers/Aetherhaven_"+entry.getKey()+".json").getAsJsonArray("reputationMilestones");
            assertTrue(rewards.asList().stream().map(e->e.getAsJsonObject()).anyMatch(r->
                r.get("minReputation").getAsInt()==75 && entry.getValue().output().equals(r.get("learnRecipeItemId").getAsString())));
        }
    }

    @Test void decorativeFriendshipPlansResolveWithoutMetadataAndUnlockOnlyTheirOwnTownRecipe() throws Exception {
        var plans=new ArrayList<WorkplaceRecipe>();
        for(String name:List.of("festival_flag","balloons","plant_arch","spooky_terrarium","palm_streetlight","weapon_rack","large_glow_ring")) {
            Path root=name.equals("large_glow_ring")?Path.of("subplugin-assets/Jewelry"):ROOT;
            var plan=GSON.fromJson(Files.readString(root.resolve("Server/Aetherhaven/WorkplaceRecipes/craft_"+name+".json")),WorkplaceRecipe.class);
            assertTrue(plan.valid(),name);assertTrue(plan.requiresKnowledge());assertTrue(plan.townOnly());
            assertEquals(1,plan.benchTier(),"These plans must work at benches with no tier upgrades");
            assertEquals(1,plan.outputCount());assertNull(plan.rarity());
            var item=JsonParser.parseString(Files.readString(root.resolve("Server/Item/Items/Aetherhaven/"+plan.output()+".json"))).getAsJsonObject();
            assertTrue(!item.has("Recipe")||item.get("Recipe").isJsonNull(),"Plans must not add player crafting");
            assertTrue(Files.isRegularFile(root.resolve("Common/"+item.get("Icon").getAsString()))
                ||Files.isRegularFile(ROOT.resolve("Common/"+item.get("Icon").getAsString())),name+" needs an item icon");
            if(!name.equals("large_glow_ring")) {
                assertEquals("Aetherhaven_Prop_Item",item.get("Parent").getAsString());
                assertEquals(name,com.hexvane.aetherhaven.prop.PropShopItemIds.propIdFromItemId(plan.output()),"Crafted stacks have no prop metadata");
                var prop=read("Server/Aetherhaven/Props/"+name+".json");
                assertTrue(Files.isRegularFile(ROOT.resolve("Server/Prefabs/"+prop.get("prefabPath").getAsString())));
            } else {
                assertEquals("jewelry",plan.workplace());assertEquals("Aetherhaven_Jewelry_Crafting_Bench",plan.bench());
                assertTrue(plan.inputs().stream().anyMatch(i->"Aetherhaven_Ring_Glow".equals(i.itemId())&&i.count()==1),"The stronger ring must consume its predecessor");
            }
            plans.add(plan);
        }
        for(var selected:plans) {
            var town=new TownRecord();assertTrue(plans.stream().noneMatch(p->p.unlocked(town)));
            assertTrue(WorkplaceRecipeUnlocks.grant(town,plans,selected.output()));
            var restored=GSON.fromJson(GSON.toJson(town),TownRecord.class);
            for(var other:plans)assertEquals(other==selected,other.unlocked(restored));
        }
    }

    @Test void remainingVillagerRewardsHaveTranslatedDialogueAndReachableGuidePages() throws Exception {
        String lang=Files.readString(ROOT.resolve("Server/Languages/en-US/aetherhaven_friendship_4.lang"));
        String directory=Files.readString(ROOT.resolve("Server/Aetherhaven/GuideTopics/en-US/villagers.md"));
        for(String role:List.of("Bard","Builder","Clown","Crystal_Keeper","Florist","Furniture_Merchant")) {
            String kind=role.toLowerCase(Locale.ROOT);
            assertTrue(directory.lines().anyMatch(line->line.trim().equals("- villager_"+kind)),role);
            String guide=Files.readString(ROOT.resolve("Server/Aetherhaven/GuideTopics/en-US/villager_"+kind+".md"));
            var villager=read("Server/Aetherhaven/Villagers/Aetherhaven_"+role+".json");
            var nodes=read("Server/Aetherhaven/Dialogue/"+villager.get("residentTreeId").getAsString()+".json").getAsJsonObject("nodes");
            for(var value:villager.getAsJsonArray("reputationMilestones")) {
                var reward=value.getAsJsonObject();int level=reward.get("minReputation").getAsInt();
                var node=nodes.getAsJsonObject(reward.get("dialogueNodeId").getAsString());
                assertEquals("aetherhaven_friendship_4."+kind+"."+level,node.get("text").getAsString());
                assertTrue(lang.contains(kind+"."+level+"="));assertTrue(guide.contains("**Friendship "+level+":**"));
            }
            if(role.equals("Builder"))assertFalse(nodes.toString().contains("rep_miner_"),"Builder must not retain copied Miner reward actions");
        }
    }
}
