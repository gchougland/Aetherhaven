package com.hexvane.aetherhaven.guild;

import com.google.gson.*;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.townsfolk.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town")
class GuardCombatStatsTest {
    private EquipmentProfileDefinition profile(String name) throws Exception {
        return new Gson().fromJson(Files.readString(Path.of("src/main/resources/Server/Aetherhaven/Equipment/guard_"+name+".json")),EquipmentProfileDefinition.class);
    }
    @Test void trainingAndEquipmentImproveCombatWithoutChangingLearnedAttributes() throws Exception {
        var p=new TownsfolkProgress();var equipment=profile("knight");
        var baseline=GuardCombatStats.derive(p,equipment);
        assertEquals(204,baseline.health());assertEquals(1.1f,baseline.damageMultiplier(),.001);
        assertEquals(5,p.attribute(TownsfolkAttribute.VITALITY));
        p.train(TownsfolkAttribute.STRENGTH);p.train(TownsfolkAttribute.VITALITY);
        var trained=GuardCombatStats.derive(p,equipment);
        assertTrue(trained.health()>baseline.health());assertTrue(trained.damageMultiplier()>baseline.damageMultiplier());
        assertEquals(trained,GuardCombatStats.derive(p,equipment));
    }
    @Test void classUsesRelevantAttributeAndDefenseHasACap() throws Exception {
        var p=new TownsfolkProgress();var mage=profile("mage");
        var before=GuardCombatStats.derive(p,mage);p.setAttribute(TownsfolkAttribute.STRENGTH,20);
        assertEquals(before.damageMultiplier(),GuardCombatStats.derive(p,mage).damageMultiplier());
        p.setAttribute(TownsfolkAttribute.INTELLIGENCE,20);
        assertTrue(GuardCombatStats.derive(p,mage).damageMultiplier()>before.damageMultiplier());
        p.setAttribute(TownsfolkAttribute.VITALITY,20);p.setAttribute(TownsfolkAttribute.DEXTERITY,20);
        assertTrue(GuardCombatStats.derive(p,mage).damageReduction()<=.4f);
        assertEquals(1,GuardCombatStats.movementMultiplier(5));
        assertTrue(GuardCombatStats.movementMultiplier(20)>1);
    }
    @Test void fullStarterArmorHasNoNativeStatOrResistanceBonusAndNoPlayerRecipe() throws Exception {
        for(String cls:new String[]{"knight","archer","rogue","mage"}) {
            var p=profile(cls);assertTrue(p.usesAttributeCombat());assertEquals(4,p.getArmorItemIds().size());
            for(String id:p.getArmorItemIds()) {
                var item=JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Item/Items/Aetherhaven/Guard_Equipment/"+id+".json"))).getAsJsonObject();
                assertFalse(item.has("Recipe"));assertFalse(item.has("Parent"));
                var armor=item.getAsJsonObject("Armor");
                assertEquals(0,armor.get("BaseDamageResistance").getAsInt());
                assertTrue(armor.getAsJsonObject("StatModifiers").isEmpty());
                assertTrue(armor.getAsJsonObject("DamageResistance").isEmpty());
            }
        }
    }
}
