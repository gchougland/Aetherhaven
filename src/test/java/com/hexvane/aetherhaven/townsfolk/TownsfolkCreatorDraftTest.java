package com.hexvane.aetherhaven.townsfolk;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownsfolkCreatorDraftTest {
    private static final UUID AUTHOR = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Set<String> TRAITS = Set.of("adventurer", "crafty", "grumpy");
    private TownsfolkCreatorDraft sheet() {
        var d = new TownsfolkCreatorDraft(); d.name = "Lone Star"; d.addItem("Wood", "Wood"::equals); return d;
    }
    private com.google.gson.JsonObject export(TownsfolkCreatorDraft d) {
        return d.toJson(AUTHOR, TRAITS, Set.of("human"), Set.of("WarmMale"), "Wood"::equals);
    }
    @Test void exportedSheetWorksInTheActualCharacterCatalogAndOmitsObsoleteEquipment() {
        var d = sheet(); d.skills.put("jewelry", 0);
        var json = export(d);
        var definition = new com.google.gson.Gson().fromJson(json, com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition.class);
        assertEquals("Lone Star", definition.getDisplayName());
        assertEquals(0, json.getAsJsonObject("startingSkills").get("jewelry").getAsInt());
        assertFalse(json.has("equipmentProfileId"));
        assertEquals(4, json.getAsJsonArray("allowedAssignmentKinds").size());
        assertEquals(5, json.getAsJsonObject("startingAttributes").size());
        assertEquals(12, json.getAsJsonObject("startingSkills").size());
        assertTrue(json.get("id").getAsString().matches("[a-z][a-z0-9_]{0,63}"));
        assertEquals("Townsfolk_" + json.get("id").getAsString(), json.get("modelAssetId").getAsString());
    }
    @Test void scoresAreClampedAndZeroRemainsIncapable() {
        var d = sheet();
        for (int i=0;i<100;i++) { d.adjust("skill","jewelry",-1); d.adjust("attribute","speed",1); d.adjust("item","Wood",1); }
        assertEquals(0,d.skills.get("jewelry")); assertEquals(8,d.attributes.get("speed")); assertEquals(64,d.items.get("Wood"));
        d.adjust("skill","jewelry",100); assertEquals(0,d.skills.get("jewelry"));
        assertDoesNotThrow(() -> export(d));
    }
    @Test void startsWithIndependentAttributeAndSkillBudgets() {
        var d = sheet();
        assertTrue(d.attributes.values().stream().allMatch(value -> value == 5));
        assertTrue(d.skills.values().stream().allMatch(value -> value == 1));
        assertEquals(5, d.attributePointsRemaining()); assertEquals(10, d.skillPointsRemaining());
        d.adjust("attribute", "strength", 1);
        assertEquals(4, d.attributePointsRemaining()); assertEquals(10, d.skillPointsRemaining());
        d.adjust("skill", "building", 1);
        assertEquals(4, d.attributePointsRemaining()); assertEquals(9, d.skillPointsRemaining());
    }
    @Test void spendingStopsAtZeroAndRefundsAllowReallocation() {
        var d = sheet();
        for (int i=0;i<3;i++) d.adjust("attribute", "strength", 1);
        for (int i=0;i<2;i++) d.adjust("attribute", "speed", 1);
        assertEquals(0, d.attributePointsRemaining());
        d.adjust("attribute", "vitality", 1); assertEquals(5, d.attributes.get("vitality"));
        d.adjust("attribute", "strength", -1); assertEquals(1, d.attributePointsRemaining());
        d.adjust("attribute", "vitality", 1); assertEquals(6, d.attributes.get("vitality"));
        for (int i=0;i<4;i++) { d.adjust("skill", "building", 1); d.adjust("skill", "crafting", 1); }
        for (int i=0;i<2;i++) d.adjust("skill", "farming", 1);
        assertEquals(0, d.skillPointsRemaining());
        d.adjust("skill", "mining", 1); assertEquals(1, d.skills.get("mining"));
        d.adjust("skill", "jewelry", -1); assertEquals(1, d.skillPointsRemaining());
        d.adjust("skill", "mining", 1); assertEquals(2, d.skills.get("mining"));
        assertEquals(0, d.attributePointsRemaining()); assertEquals(0, d.skillPointsRemaining());
        assertDoesNotThrow(() -> export(d));
    }
    @Test void loweringBelowDefaultsRefundsPointsButBoundsAndRepeatedClicksCannotCreatePoints() {
        var d = sheet();
        for (int i=0;i<100;i++) { d.adjust("attribute", "strength", -1); d.adjust("skill", "jewelry", -1); }
        assertEquals(1, d.attributes.get("strength")); assertEquals(9, d.attributePointsRemaining());
        assertEquals(0, d.skills.get("jewelry")); assertEquals(11, d.skillPointsRemaining());
        for (int i=0;i<100;i++) { d.adjust("attribute", "speed", 1); d.adjust("skill", "building", 1); }
        assertEquals(8, d.attributes.get("speed")); assertEquals(6, d.attributePointsRemaining());
        assertEquals(5, d.skills.get("building")); assertEquals(7, d.skillPointsRemaining());
        for (int i=0;i<100;i++) { d.adjust("skill", "mining", 1); d.adjust("skill", "mining", -1); }
        assertEquals(1, d.skills.get("mining")); assertEquals(7, d.skillPointsRemaining());
    }
    @Test void exportRejectsTamperedCapsAndOverBudgetScoresEvenWhenIndividuallyInRange() {
        var d = sheet(); d.attributes.put("strength", 9);
        assertThrows(IllegalArgumentException.class, () -> export(d));
        var capped = sheet(); capped.skills.put("building", 6);
        assertThrows(IllegalArgumentException.class, () -> export(capped));
        var attributes = sheet(); attributes.attributes.put("strength", 8); attributes.attributes.put("speed", 8);
        assertThrows(IllegalArgumentException.class, () -> export(attributes));
        var skills = sheet(); skills.skills.put("building", 5); skills.skills.put("crafting", 5); skills.skills.put("farming", 4);
        assertThrows(IllegalArgumentException.class, () -> export(skills));
    }
    @Test void validatesDistinctTraitsAndAvailableItemsBeforeExport() {
        var d = sheet(); d.traits.set(1,"adventurer"); assertThrows(IllegalArgumentException.class,()->export(d));
        d.traits.set(1,"crafty"); d.items.put("MissingModItem",1); assertThrows(IllegalArgumentException.class,()->export(d));
        d.items.clear(); d.addItem("MissingModItem","Wood"::equals); assertTrue(d.items.isEmpty());
        assertThrows(IllegalArgumentException.class,()->export(d));
    }
    @Test void limitsRequirementsAndRejectsInvalidIdentity() {
        var d = sheet(); for(int i=0;i<20;i++) d.addItem("Item"+i,id->true);
        assertEquals(8,d.items.size()); d.name=" "; assertThrows(IllegalArgumentException.class,()->export(d));
    }
}
