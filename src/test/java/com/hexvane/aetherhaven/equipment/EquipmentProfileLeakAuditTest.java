package com.hexvane.aetherhaven.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.bard.BardPerformanceService;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorWorkActivityOptions;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiEquipmentProfiles;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerPlacementService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Only the Bard may hold the lute. The building editor once stamped the bard's profile on every spot given the
 * Leisure animation, and it spread from there through building definitions into saved towns. This audit fails
 * when any building, work spot, villager, townsfolk, prefab or editor option hands a profession's own prop to
 * someone else.
 */
@Tag("town")
@Tag("autonomy")
class EquipmentProfileLeakAuditTest {
    private static final Path MAIN = Path.of("src/main/resources");
    private static final Path SUBPLUGINS = Path.of("subplugin-assets");
    private static final String BARD_PROFILE = "work_bard";

    @Test void theLuteProfileBelongsToTheBardAlone() throws IOException {
        EquipmentProfileCatalog catalog = catalog();
        EquipmentProfileDefinition lute = catalog.byId(BARD_PROFILE);
        assertNotNull(lute, "the bard profile must stay loadable, saved towns still name it");
        assertEquals(List.of(TownVillagerBinding.KIND_BARD), lute.getAllowedResidentKinds());
        for (EquipmentProfileDefinition profile : catalog.allById().values()) {
            boolean holdsLute = BardPerformanceService.LUTE_ITEM_ID.equals(profile.getOffhandItemId())
                || profile.getHotbarSlots().stream()
                    .anyMatch(slot -> BardPerformanceService.LUTE_ITEM_ID.equals(slot.getItemId()));
            if (holdsLute) {
                assertEquals(List.of(TownVillagerBinding.KIND_BARD), profile.getAllowedResidentKinds(),
                    profile.getId() + " hands out the lute and must be restricted to the bard");
            }
        }
    }

    @Test void aRestrictedProfileAdmitsOnlyItsOwners() throws IOException {
        EquipmentProfileDefinition lute = catalog().byId(BARD_PROFILE);
        assertTrue(lute.isResidentRestricted());
        assertTrue(VillagerEquipmentService.mayHold(lute, TownVillagerBinding.KIND_BARD));
        assertTrue(VillagerEquipmentService.mayHold(lute, TownVillagerBinding.KIND_VISITOR_BARD));
        // Every other resident kind, including the two the old special case named and all it missed.
        for (String kind : new String[] {"doctor", "alchemist", "warehouse_manager", "clown", "halloween",
            "monster_hunter", TownVillagerBinding.KIND_TOWNSFOLK, TownVillagerBinding.KIND_GUARD,
            TownVillagerBinding.KIND_MERCHANT, TownVillagerBinding.KIND_BUILDER, "", "  "}) {
            assertFalse(VillagerEquipmentService.mayHold(lute, kind), kind);
        }
        // An entity with no town binding (an editor preview) is nobody's bard.
        assertFalse(VillagerEquipmentService.mayHold(lute, null));
        EquipmentProfileDefinition hammer = catalog().byId("work_builder");
        assertFalse(hammer.isResidentRestricted());
        assertTrue(VillagerEquipmentService.mayHold(hammer, "doctor"));
        assertTrue(VillagerEquipmentService.mayHold(hammer, null));
    }

    @Test void noBuildingSpotHandsARestrictedProfileToAnotherResident() throws IOException {
        EquipmentProfileCatalog catalog = catalog();
        List<String> leaks = new ArrayList<>();
        int spots = 0;
        for (Path file : jsonUnder("Server/Aetherhaven/Buildings")) {
            JsonObject root = object(file);
            if (root == null || !root.has("pois") || !root.get("pois").isJsonArray()) continue;
            int index = 0;
            for (JsonElement element : root.getAsJsonArray("pois")) {
                JsonObject poi = element.getAsJsonObject();
                spots++;
                String profile = string(poi, "equipmentProfileId");
                Set<String> tags = new HashSet<>();
                if (poi.has("tags")) poi.getAsJsonArray("tags").forEach(tag -> tags.add(tag.getAsString()));
                String kept = PoiEquipmentProfiles.permitted(catalog, profile, string(poi, "workResidentKind"), tags);
                if (profile != null && !profile.equals(kept)) {
                    leaks.add(file + " pois[" + index + "] gives " + profile + " to "
                        + string(poi, "workResidentKind"));
                }
                if (profile != null) {
                    assertNotNull(catalog.byId(profile), file + " names unknown equipment profile " + profile);
                }
                index++;
            }
        }
        assertTrue(spots > 100, "expected to audit every building spot, saw " + spots);
        assertEquals(List.of(), leaks);
    }

    @Test void noVillagerOrTownsfolkDefinitionUsesAnotherProfessionsProfile() throws IOException {
        EquipmentProfileCatalog catalog = catalog();
        List<String> leaks = new ArrayList<>();
        int villagers = 0;
        for (Path file : jsonUnder("Server/Aetherhaven/Villagers")) {
            JsonObject root = object(file);
            if (root == null) continue;
            villagers++;
            String profile = string(root, "workEquipmentProfileId");
            String kind = string(root, "dialogueVillagerKind");
            if (profile == null) continue;
            assertNotNull(catalog.byId(profile), file + " names unknown equipment profile " + profile);
            if (!catalog.permits(profile, kind)) leaks.add(file + " (" + kind + ") works with " + profile);
        }
        int townsfolk = 0;
        for (Path file : jsonUnder("Server/Aetherhaven/Townsfolk")) {
            JsonObject root = object(file);
            if (root == null) continue;
            townsfolk++;
            String profile = string(root, "equipmentProfileId");
            // Townsfolk are never a named profession; a restricted profile can never be theirs.
            if (profile != null && !catalog.permits(profile, TownVillagerBinding.KIND_TOWNSFOLK)) {
                leaks.add(file + " is equipped with " + profile);
            }
        }
        assertTrue(villagers >= 20, "expected every core villager, saw " + villagers);
        assertTrue(townsfolk >= 40, "expected every townsfolk character, saw " + townsfolk);
        assertEquals(List.of(), leaks);
    }

    /** Catches every other carrier: prefab work spot markers, NPC roles, guard kits, quests, future asset kinds. */
    @Test void nothingElseInTheAssetPacksNamesARestrictedProfileOrArmsARoleWithTheLute() throws IOException {
        EquipmentProfileCatalog catalog = catalog();
        List<String> restricted = catalog.allById().values().stream()
            .filter(EquipmentProfileDefinition::isResidentRestricted).map(EquipmentProfileDefinition::getId).toList();
        assertTrue(restricted.contains(BARD_PROFILE));
        List<String> leaks = new ArrayList<>();
        int scanned = 0;
        for (Path file : jsonUnder("Server")) {
            String path = file.toString().replace('\\', '/');
            // Profiles define themselves and building spots are audited field by field above.
            if (path.contains("/Server/Aetherhaven/Equipment/")
                || path.contains("/Server/Aetherhaven/Buildings/")) continue;
            String text = Files.readString(file);
            scanned++;
            for (String id : restricted) {
                if (text.contains('"' + id + '"')) leaks.add(path + " names " + id);
            }
            if (path.contains("/Server/NPC/") && text.contains(BardPerformanceService.LUTE_ITEM_ID)) {
                leaks.add(path + " gives a role the lute");
            }
        }
        assertTrue(scanned > 1000, "expected to sweep the whole pack, saw " + scanned);
        assertEquals(List.of(), leaks);
    }

    @Test void noEditorAnimationChoiceHandsOutARestrictedProfile() throws IOException {
        EquipmentProfileCatalog catalog = catalog();
        for (String activity : PlotCreatorWorkActivityOptions.allSelectable()) {
            String profile = PlotCreatorWorkActivityOptions.equipmentProfileFor(activity);
            if (profile == null) continue;
            EquipmentProfileDefinition def = catalog.byId(profile);
            assertNotNull(def, activity + " names unknown equipment profile " + profile);
            assertFalse(def.isResidentRestricted(), activity + " would hand " + profile + " to any resident");
        }
        // Leisure is the default animation for most residents, so it must hand out nothing.
        assertNull(PlotCreatorWorkActivityOptions.equipmentProfileFor("leisure"));
        assertNull(PlotCreatorWorkActivityOptions.equipmentProfileFor("read"));
        for (String action : new String[] {"Mine", "Chop", "Water", "Till", "Smith", "Craft", "Read", "Leisure"}) {
            String profile = PoiMarkerPlacementService.equipmentForWorkAction(action);
            assertTrue(profile == null || !catalog.byId(profile).isResidentRestricted(), action);
        }
    }

    @Test void savedSpotsLoseOnlyTheProfilesThatAreNotTheirs() throws IOException {
        EquipmentProfileCatalog catalog = catalog();
        PoiEntry clinic = spot("doctor", BARD_PROFILE, "WORK", "workActivity:leisure");
        PoiEntry counter = spot(null, BARD_PROFILE, "WORK", "workActivity:leisure");
        PoiEntry stage = spot("bard", BARD_PROFILE, "WORK", "BARD");
        PoiEntry taggedStage = spot(null, BARD_PROFILE, "WORK", "BARD");
        PoiEntry field = spot("farmer", "work_farmer_hoe", "WORK", "workActivity:till");
        PoiEntry bench = spot("doctor", null, "WORK");
        List<PoiEntry> saved = List.of(clinic, counter, stage, taggedStage, field, bench);

        List<PoiEntry> loaded = PoiEquipmentProfiles.migrate(saved, catalog);

        assertEquals(saved.size(), loaded.size());
        assertNull(loaded.get(0).getEquipmentProfileId());
        assertNull(loaded.get(1).getEquipmentProfileId());
        // Everything else about a corrected spot survives.
        assertEquals(clinic.getId(), loaded.get(0).getId());
        assertEquals(clinic.getTags(), loaded.get(0).getTags());
        assertEquals("doctor", loaded.get(0).getWorkResidentKind());
        assertEquals(clinic.getInteractionTargetYawRadians(), loaded.get(0).getInteractionTargetYawRadians());
        assertSame(stage, loaded.get(2));
        assertSame(taggedStage, loaded.get(3));
        assertSame(field, loaded.get(4));
        assertSame(bench, loaded.get(5));
        // A clean save is returned untouched, so it is not rewritten on every load.
        assertSame(loaded, PoiEquipmentProfiles.migrate(loaded, catalog));
    }

    private static PoiEntry spot(String workResidentKind, String profile, String... tags) {
        return new PoiEntry(UUID.randomUUID(), UUID.randomUUID(), 1, 2, 3, new HashSet<>(List.of(tags)), 1,
            UUID.randomUUID(), "Wood_Softwood_Planks", PoiInteractionKind.WORK_SURFACE, false, profile,
            null, null, null, 1.5F, workResidentKind);
    }

    private static EquipmentProfileCatalog catalog() throws IOException {
        Gson gson = new Gson();
        List<EquipmentProfileDefinition> profiles = new ArrayList<>();
        for (Path file : jsonUnder("Server/Aetherhaven/Equipment")) {
            profiles.add(gson.fromJson(Files.readString(file), EquipmentProfileDefinition.class));
        }
        assertTrue(profiles.size() >= 30, "expected every equipment profile, saw " + profiles.size());
        return EquipmentProfileCatalog.of(profiles);
    }

    /** Every JSON file under this pack folder, in the main pack and in each bundled sub pack. */
    private static List<Path> jsonUnder(String packFolder) throws IOException {
        List<Path> roots = new ArrayList<>();
        roots.add(MAIN.resolve(packFolder));
        try (Stream<Path> packs = Files.list(SUBPLUGINS)) {
            packs.filter(Files::isDirectory).forEach(pack -> roots.add(pack.resolve(packFolder)));
        }
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(out::add);
            }
        }
        return out;
    }

    private static JsonObject object(Path file) throws IOException {
        String text = Files.readString(file);
        if (text.startsWith("﻿")) text = text.substring(1);
        JsonElement root = JsonParser.parseString(text);
        return root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() || value.getAsString().isBlank() ? null : value.getAsString().trim();
    }
}
