package com.hexvane.aetherhaven.inn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class InnVisitorShopPromotionTest {

    private final Gson gson = new Gson();

    @Test
    void resolveResidentKind_prefersDialogueVillagerKind() {
        VillagerDefinition def = gson.fromJson(
            """
            {
              "npcRoleId": "Mod_Angler",
              "dialogueVillagerKind": "angler",
              "visitorBindingKind": "visitor_angler"
            }
            """,
            VillagerDefinition.class
        );
        assertEquals("angler", InnVisitorShopPromotion.resolveResidentKind(def));
    }

    @Test
    void resolveResidentKind_stripsVisitorPrefixWhenDialogueKindMissing() {
        VillagerDefinition def = gson.fromJson(
            """
            {
              "npcRoleId": "Mod_Angler",
              "visitorBindingKind": "visitor_angler"
            }
            """,
            VillagerDefinition.class
        );
        assertEquals("angler", InnVisitorShopPromotion.resolveResidentKind(def));
    }

    @Test
    void findShopQuestId_matchesGrantPlotBlueprintConstructionId() {
        VillagerDefinition def = gson.fromJson(
            """
            {
              "npcRoleId": "Mod_Angler",
              "dialogueVillagerKind": "angler",
              "workConstructionId": "plot_fishing_shop",
              "innPoolEligible": true,
              "visitorBindingKind": "visitor_angler"
            }
            """,
            VillagerDefinition.class
        );
        QuestDefinition quest = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "id": "q_fishing_shop",
              "grantPlotBlueprintConstructionId": "plot_fishing_shop"
            }
            """,
            QuestDefinition.class
        );
        Map<String, QuestDefinition> map = new LinkedHashMap<>();
        map.put("q_fishing_shop", quest);
        String found =
            InnVisitorShopPromotion.findShopQuestId(
                QuestCatalog.of(map),
                ConstructionCatalog.empty(),
                def,
                "plot_fishing_shop"
            );
        assertEquals("q_fishing_shop", found);
    }

    @Test
    void constructionMatchesWork_exactAndGameplayIds() {
        ConstructionCatalog constructions = ConstructionCatalog.empty();
        assertTrue(
            InnVisitorShopPromotion.constructionMatchesWork(
                constructions,
                "plot_fishing_shop",
                "plot_fishing_shop",
                "plot_fishing_shop"
            )
        );
        assertTrue(
            InnVisitorShopPromotion.constructionMatchesWork(
                constructions,
                "plot_fishing_shop",
                "plot_fishing_shop_variant",
                "plot_fishing_shop"
            )
        );
    }

    @Test
    void constructionMatchesWork_rejectsGenericHouseForHobbitShopWork() {
        ConstructionDefinition hobbitShop =
            gson.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"Hobbit.prefab.json","plotTokenItemId":"Token","countsAsConstructionId":"plot_house"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog constructions = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));

        assertTrue(
            InnVisitorShopPromotion.constructionMatchesWork(
                constructions,
                "plot_hobbit_shop",
                "plot_hobbit_shop",
                "plot_house"
            )
        );
        assertFalse(
            InnVisitorShopPromotion.constructionMatchesWork(
                constructions,
                "plot_hobbit_shop",
                "plot_house",
                "plot_house"
            )
        );
    }

    @Test
    void findShopQuestId_prefersAssignNpcRoleIdMatch() {
        VillagerDefinition bilbo =
            gson.fromJson(
                """
                {
                  "npcRoleId": "Lotr_Bilbo_Baggins",
                  "workConstructionId": "plot_hobbit_shop",
                  "innPoolEligible": true,
                  "visitorBindingKind": "visitor_lotr_bilbo"
                }
                """,
                VillagerDefinition.class
            );
        QuestDefinition quest =
            gson.fromJson(
                """
                {
                  "schemaVersion": 1,
                  "id": "q_lotr_hobbit_shop",
                  "assignNpcRoleId": "Lotr_Bilbo_Baggins",
                  "grantPlotTokenConstructionId": "plot_hobbit_shop"
                }
                """,
                QuestDefinition.class
            );
        String found =
            InnVisitorShopPromotion.findShopQuestId(
                QuestCatalog.of(Map.of("q_lotr_hobbit_shop", quest)),
                ConstructionCatalog.empty(),
                bilbo,
                "plot_hobbit_shop"
            );
        assertEquals("q_lotr_hobbit_shop", found);
    }

    @Test
    void resolveResidentKind_nullWhenNeitherKindPresent() {
        VillagerDefinition def = gson.fromJson(
            """
            {
              "npcRoleId": "Mod_Angler"
            }
            """,
            VillagerDefinition.class
        );
        assertNull(InnVisitorShopPromotion.resolveResidentKind(def));
    }
}
