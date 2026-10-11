package com.hexvane.aetherhaven.town;
import com.google.gson.Gson;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ResidentHouseRevivalTest {
    @TempDir Path temp;
    @Test void namedAndTownsfolkKeepHomesAcrossDeathRegistrationReloadAndRevival() throws Exception {
        for (boolean townsfolk : new boolean[]{false, true}) {
            UUID old = UUID.randomUUID(), other = UUID.randomUUID(), revived = UUID.randomUUID();
            String role = townsfolk ? "Aetherhaven_Townsfolk" : "Aetherhaven_Chef";
            String kind = townsfolk ? "townsfolk" : "chef";
            TownRecord town = new TownRecord();
            PlotInstance home = new Gson().fromJson("{\"constructionId\":\"plot_house\",\"state\":\"COMPLETE\"}", PlotInstance.class);
            home.setHomeResidentAt(0, other); home.setHomeResidentAt(2, old);
            town.getPlotInstances().add(home);
            var row = new ResidentNpcRecord(role, kind, null, old);
            row.setPendingDawnRevival(true);
            if (townsfolk) {
                row.setTownsfolkIdentity(new TownsfolkCharacterBinding("prowl", "cheerful", "tourist", "Prowl", List.of("cheerful")));
                town.getTouristRecords().add(new TouristRecord("prowl", old, UUID.randomUUID(), true, true));
            }
            town.getResidentNpcRecords().add(row);
            town.addActiveQuest("q_home"); town.setQuestTargetEntityUuid("q_home", old);
            // Registration can run again before the dead entity has left its chunk.
            ResidentRegistryService.upsertRecord(town, role, kind, null, old);
            assertTrue(town.getResidentNpcRecords().getFirst().isPendingDawnRevival());
            var file = new TownWorldFile(); file.getTowns().add(town); Path path = temp.resolve("towns.json"); file.writeAtomic(path);
            town = TownWorldFile.readOrEmpty(path).getTowns().getFirst();
            assertEquals(old, town.getPlotInstances().getFirst().getHomeResidentAt(2));
            assertTrue(ResidentRegistryService.replaceEntityReferences(town, old, revived));
            assertFalse(ResidentRegistryService.replaceEntityReferences(town, revived, revived));
            file = new TownWorldFile(); file.getTowns().add(town); file.writeAtomic(path);
            town = TownWorldFile.readOrEmpty(path).getTowns().getFirst();
            assertEquals(revived, town.getPlotInstances().getFirst().getHomeResidentAt(2));
            assertEquals(other, town.getPlotInstances().getFirst().getHomeResidentAt(0));
            assertEquals(revived, town.getQuestTargetEntityUuid("q_home"));
            assertFalse(town.getResidentNpcRecords().getFirst().isPendingDawnRevival());
            if (townsfolk) {
                assertEquals("prowl", town.getResidentNpcRecords().getFirst().getTownsfolkIdentity().getCharacterId());
                assertEquals(revived, town.getTouristRecords().getFirst().getEntityUuid());
            }
        }
    }
}
