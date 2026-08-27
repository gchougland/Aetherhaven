package com.hexvane.aetherhaven.festival.wintertide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
final class WintertideGiftsCrossmodTest {
    @Test
    void villagerJsonWintertideGiftsAreUsedForUnknownKinds() {
        VillagerDefinition def = parseVillager(
            """
            {
              "npcRoleId": "Example_Angler",
              "dialogueVillagerKind": "angler",
              "wintertideGifts": [
                { "itemId": "Fish_Salmon_Item", "count": 8 },
                { "itemId": "CozyFishing_Wooden_Rod", "count": 1 }
              ]
            }
            """
        );
        assertEquals(
            List.of(
                new WintertideGifts.Stack("Fish_Salmon_Item", 8),
                new WintertideGifts.Stack("CozyFishing_Wooden_Rod", 1)
            ),
            WintertideGifts.pick("angler", def, new Random(1L))
        );
    }

    @Test
    void villagerJsonWintertideGiftsWinOverBuiltInKindTables() {
        VillagerDefinition def = parseVillager(
            """
            {
              "npcRoleId": "Example_Miner",
              "dialogueVillagerKind": "miner",
              "wintertideGifts": [
                { "itemId": "Example_Deep_Ore", "count": 4 }
              ]
            }
            """
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Example_Deep_Ore", 4)),
            WintertideGifts.pick("miner", def, new Random(1L))
        );
    }

    @Test
    void villagerJsonWintertideGiftsCanPickOneFromAList() {
        VillagerDefinition def = parseVillager(
            """
            {
              "npcRoleId": "Example_Florist",
              "dialogueVillagerKind": "mechanic",
              "wintertideGifts": [
                {
                  "pickOne": ["Plant_Flower_Orchid_Blue", "Plant_Flower_Orchid_Red"],
                  "count": 1,
                  "repeats": 3
                }
              ]
            }
            """
        );
        List<WintertideGifts.Stack> stacks = WintertideGifts.pick("mechanic", def, new Random(9L));
        assertEquals(3, stacks.size());
        for (WintertideGifts.Stack stack : stacks) {
            assertTrue(
                stack.itemId().equals("Plant_Flower_Orchid_Blue")
                    || stack.itemId().equals("Plant_Flower_Orchid_Red")
            );
            assertEquals(1, stack.count());
        }
    }

    private static VillagerDefinition parseVillager(String json) {
        VillagerDefinition def = new Gson().fromJson(json, VillagerDefinition.class);
        assertNotNull(def);
        return def;
    }
}
