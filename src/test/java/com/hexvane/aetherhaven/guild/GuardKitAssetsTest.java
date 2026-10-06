package com.hexvane.aetherhaven.guild;

import com.google.gson.*;
import com.hexvane.aetherhaven.equipment.*;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.workplace.WorkplaceRecipe;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class GuardKitAssetsTest {
    private static final Path ROOT=Path.of("src/main/resources");
    @Test void meleeGuardsResolveTheNativeSimpleAttackSelector() throws Exception {
        // NPC_Attack_Melee_Simple delegates its next interaction to this variable.
        // Omitting it aborts the native attack before its damage interaction.
        for(String role:new String[]{"Knight","Rogue"}) {
            var vars=JsonParser.parseString(Files.readString(ROOT.resolve(
                "Server/NPC/Roles/Aetherhaven/Aetherhaven_Guard_"+role+".json")))
                .getAsJsonObject().getAsJsonObject("InteractionVars");
            assertEquals("NPC_Attack_Melee_Simple",vars.getAsJsonObject("Melee_Start")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject().get("Parent").getAsString());
            assertEquals("NPC_Attack_Selector_Left",vars.getAsJsonObject("Melee_Selector")
                .getAsJsonArray("Interactions").get(0).getAsString());
            assertTrue(vars.has("Melee_Damage"));
        }
    }
    @Test void attributeArmorOmitsNativeKnockbackMapsForAllDamageCauses() throws Exception {
        // Native ArmorKnockbackReduction skips null, but unboxes map.get(cause)
        // without a default. Empty maps crash; fixed cause lists miss modded damage.
        try (var files=Files.list(ROOT.resolve("Server/Item/Items/Aetherhaven/Guard_Equipment"))) {
            var armorFiles=files.filter(p->p.getFileName().toString().startsWith("Aetherhaven_Guard_Armor_")).toList();
            assertFalse(armorFiles.isEmpty());
            for (var path:armorFiles) {
                var armor=JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("Armor");
                assertFalse(armor.has("KnockbackResistances"),path.toString());
                assertFalse(armor.has("KnockbackEnhancements"),path.toString());
            }
        }
    }
    @Test void everyKitHasACompleteProfileOriginalAssetsAndRankGatedTownRecipe() throws Exception {
        for(String cls:new String[]{"knight","archer","rogue","mage"}) {
            GuardCombatStats previous=null;
            for(String tier:new String[]{"copper","iron","thorium","cobalt","adamantite"}) {
                String name=tier+"_"+cls+"_kit";
                var kit=new Gson().fromJson(Files.readString(ROOT.resolve("Server/Aetherhaven/GuardKits/"+name+".json")),GuardKit.class);
                assertTrue(kit.valid());
                var profile=new Gson().fromJson(Files.readString(ROOT.resolve("Server/Aetherhaven/Equipment/"+kit.equipmentProfile()+".json")),EquipmentProfileDefinition.class);
                assertTrue(profile.usesAttributeCombat());assertEquals(4,profile.getArmorItemIds().size());
                var combat=GuardCombatStats.derive(new TownsfolkProgress(),profile);
                if(previous!=null){assertTrue(combat.damageMultiplier()>previous.damageMultiplier());assertTrue(combat.health()>previous.health());}
                previous=combat;
                for(String armorId:profile.getArmorItemIds()) {
                    var armor=JsonParser.parseString(Files.readString(ROOT.resolve("Server/Item/Items/Aetherhaven/Guard_Equipment/"+armorId+".json"))).getAsJsonObject();
                    assertFalse(armor.has("Parent"));assertFalse(armor.has("Recipe"));
                    var stats=armor.getAsJsonObject("Armor");assertEquals(0,stats.get("BaseDamageResistance").getAsInt());
                    assertTrue(stats.getAsJsonObject("StatModifiers").isEmpty());assertTrue(stats.getAsJsonObject("DamageResistance").isEmpty());
                }
                var item=JsonParser.parseString(Files.readString(ROOT.resolve("Server/Item/Items/Aetherhaven/Guard_Equipment/"+kit.itemId()+".json"))).getAsJsonObject();
                assertFalse(item.has("Recipe"));assertFalse(item.has("Parent"));assertEquals(1,item.get("MaxStack").getAsInt());
                for(String field:new String[]{"Model","Texture","Icon"})assertTrue(Files.exists(ROOT.resolve("Common/"+item.get(field).getAsString())));
                var recipe=new Gson().fromJson(Files.readString(ROOT.resolve("Server/Aetherhaven/WorkplaceRecipes/craft_"+name+".json")),WorkplaceRecipe.class);
                assertTrue(recipe.valid());assertTrue(recipe.townOnly());assertEquals(TownsfolkWorkType.ARMOR_CRAFTING,recipe.type());
                assertEquals(kit.itemId(),recipe.output());assertTrue(recipe.rankUnlocked(4));
                if(!tier.equals("copper"))assertFalse(recipe.rankUnlocked(0));
                String expectedModel="Items/Aetherhaven/Guard_Kits/"+Character.toUpperCase(cls.charAt(0))+cls.substring(1)+"_Kit.blockymodel";
                assertEquals(expectedModel,item.get("Model").getAsString());
                var texture=javax.imageio.ImageIO.read(ROOT.resolve("Common/"+item.get("Texture").getAsString()).toFile());
                assertEquals(64,texture.getWidth());assertEquals(64,texture.getHeight());
                if(tier.equals("cobalt")){assertFalse(recipe.rankUnlocked(2));assertTrue(recipe.rankUnlocked(3));assertEquals(2,recipe.benchTier());assertEquals(8,recipe.skillLevel());}
                if(tier.equals("adamantite")){assertFalse(recipe.rankUnlocked(3));assertEquals(3,recipe.benchTier());assertEquals(10,recipe.skillLevel());}
                if(tier.equals("thorium")){assertFalse(recipe.rankUnlocked(1));assertEquals(2,recipe.benchTier());assertEquals(6,recipe.skillLevel());}
            }
        }
    }
}
