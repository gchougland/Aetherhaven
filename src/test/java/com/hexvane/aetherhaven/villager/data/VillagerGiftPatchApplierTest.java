package com.hexvane.aetherhaven.villager.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class VillagerGiftPatchApplierTest {

    private final Gson gson = new Gson();

    @Test
    void appendsGiftListsWithoutReplacingVillager() {
        VillagerDefinition merchant = gson.fromJson(
            """
            {
              "npcRoleId": "Aetherhaven_Merchant",
              "giftLoves": ["Aetherhaven_Hand_Mirror"],
              "giftLikes": ["Food_Bread"]
            }
            """,
            VillagerDefinition.class
        );
        Map<String, VillagerDefinition> byRole = new LinkedHashMap<>();
        byRole.put("Aetherhaven_Merchant", merchant);

        VillagerGiftPatchDefinition patch = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "targetNpcRoleId": "Aetherhaven_Merchant",
              "addGiftLoves": ["Fish_Salmon_Item", "Aetherhaven_Hand_Mirror"],
              "addGiftLikes": ["CozyFishing_Wooden_Rod"],
              "addGiftDislikes": ["Deco_Trash"]
            }
            """,
            VillagerGiftPatchDefinition.class
        );

        assertTrue(VillagerGiftPatchApplier.applyPatch(byRole, patch, "test"));
        assertEquals(2, merchant.getGiftLoves().size());
        assertEquals("Aetherhaven_Hand_Mirror", merchant.getGiftLoves().get(0));
        assertEquals("Fish_Salmon_Item", merchant.getGiftLoves().get(1));
        assertEquals(2, merchant.getGiftLikes().size());
        assertEquals(1, merchant.getGiftDislikes().size());
    }
}
