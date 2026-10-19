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
        assertEquals(0,d.skills.get("jewelry")); assertEquals(20,d.attributes.get("speed")); assertEquals(64,d.items.get("Wood"));
        d.adjust("skill","jewelry",100); assertEquals(0,d.skills.get("jewelry"));
        assertDoesNotThrow(() -> export(d));
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
