package com.hexvane.aetherhaven.townsfolk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class TownsfolkCharacterAvailabilityTest {
    private final Gson gson = new Gson();

    @Test
    void eligibleWhenNoPluginRequirement() {
        TownsfolkCharacterDefinition def = gson.fromJson(
            """
            {"id":"test","personalityIds":["crafty"],"allowedAssignmentKinds":["tourist"],"modelAssetId":"Mekhi"}
            """,
            TownsfolkCharacterDefinition.class
        );
        assertTrue(TownsfolkCharacterAvailability.isEligibleForPoolDraw(def));
    }

    @Test
    void notEligibleWhenRequiredPluginMissing() {
        TownsfolkCharacterDefinition def = gson.fromJson(
            """
            {
              "id":"robot",
              "personalityIds":["crafty"],
              "allowedAssignmentKinds":["tourist"],
              "modelAssetId":"Reginald_Volt",
              "requiresOptionalPlugin": {"group": "Hexvane", "name": "Machinaria_NotInstalled_Test"}
            }
            """,
            TownsfolkCharacterDefinition.class
        );
        assertFalse(TownsfolkCharacterAvailability.isEligibleForPoolDraw(def));
    }
}
