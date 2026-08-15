package com.hexvane.aetherhaven.festival.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class MarketRewardsTest {
    @BeforeEach
    void installCatalog() {
        MarketItemCatalog.replaceForTests(
            MarketItemCatalog.loadFromJson(
                """
                {
                  "categoryBonus": 10,
                  "items": {
                    "Plant_Crop_Carrot_Item": { "category": "vegetables", "points": 3 },
                    "Food_Bread": { "category": "cooked_food", "points": 6 },
                    "Weapon_Sword_Iron": { "category": "weapons", "points": 8 },
                    "Rock_Gem_Ruby": { "category": "gemstones", "points": 12 },
                    "Weapon_Sword_Adamantite": { "category": "weapons", "points": 18 }
                  }
                }
                """
            )
        );
    }

    @Test
    void standKindsAreRecognized() {
        assertTrue(MarketIds.isStandKind("market_stand_0"));
        assertTrue(MarketIds.isStandKind("Market_Stand_3"));
        assertFalse(MarketIds.isStandKind("elder"));
        assertFalse(MarketIds.isStandKind("market_shop"));
        assertEquals("market_stand_2", MarketIds.standKind(2));
    }

    @Test
    void emptyStallScoresNothingAndPlacesLast() {
        MarketScore.Breakdown empty = MarketScore.scoreSlots(List.of());
        assertEquals(0, empty.itemPoints());
        assertEquals(0, empty.categoryBonus());
        assertEquals(0, empty.total());
        assertEquals(4, MarketScore.place(empty.total()));
        assertEquals(MarketIds.RIVAL_BRAMBLEFORD_ID, MarketScore.winnerId(empty.total(), "Oakrest"));
        assertEquals(0, MarketRewards.ticketCount(empty.total(), MarketScore.place(empty.total())));
        assertFalse(MarketRewards.grantsPlushie(MarketScore.place(empty.total())));
    }

    @Test
    void unlistedItemsAreWorthOnePointAndCountAsOther() {
        MarketScore.Breakdown breakdown = MarketScore.scoreSlots(List.of("Mystery_Widget", "Mystery_Widget"));
        assertEquals(2, breakdown.itemPoints());
        assertEquals(10, breakdown.categoryBonus());
        assertEquals(12, breakdown.total());
        assertEquals(1, breakdown.uniqueCategories());
    }

    @Test
    void categoryBonusAddsTenPerUniqueCategory() {
        MarketScore.Breakdown one = MarketScore.scoreSlots(List.of("Plant_Crop_Carrot_Item"));
        assertEquals(3, one.itemPoints());
        assertEquals(10, one.categoryBonus());
        assertEquals(13, one.total());

        MarketScore.Breakdown mixed =
            MarketScore.scoreSlots(List.of("Plant_Crop_Carrot_Item", "Food_Bread", "Weapon_Sword_Iron"));
        assertEquals(17, mixed.itemPoints());
        assertEquals(30, mixed.categoryBonus());
        assertEquals(47, mixed.total());
    }

    @Test
    void sameCategoryCommonsLandFourth() {
        List<String> carrots = List.of(
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Carrot_Item"
        );
        int score = MarketScore.scoreSlots(carrots).total();
        assertEquals(37, score);
        assertEquals(4, MarketScore.place(score));
        assertEquals(MarketIds.RIVAL_BRAMBLEFORD_ID, MarketScore.winnerId(score, "Oakrest"));
    }

    @Test
    void mixedCommonsCanTakeThirdAndVarietyCanTakeFirst() {
        assertEquals(3, MarketScore.place(46));
        assertEquals(MarketIds.RIVAL_MILLSHADE_ID, MarketScore.winnerId(46, "Oakrest"));
        assertEquals(2, MarketScore.place(86));
        assertEquals(MarketIds.RIVAL_GOLDHOLLOW_ID, MarketScore.winnerId(86, "Oakrest"));
        assertEquals(1, MarketScore.place(126));
        assertEquals("Oakrest", MarketScore.winnerId(126, "Oakrest"));
        assertTrue(MarketRewards.grantsPlushie(1));
        assertFalse(MarketRewards.grantsPlushie(2));
    }

    @Test
    void equalScoresLoseToTheRival() {
        assertEquals(4, MarketScore.place(MarketIds.RIVAL_BRAMBLEFORD));
        assertEquals(3, MarketScore.place(MarketIds.RIVAL_MILLSHADE));
        assertEquals(2, MarketScore.place(MarketIds.RIVAL_GOLDHOLLOW));
    }

    @Test
    void ticketsAreScoreOverTwelvePlusPlaceBonus() {
        assertEquals(0, MarketRewards.ticketCount(0, 4));
        assertEquals(3, MarketRewards.ticketCount(37, 4));
        assertEquals(5, MarketRewards.ticketCount(46, 3));
        assertEquals(9, MarketRewards.ticketCount(86, 2));
        assertEquals(15, MarketRewards.ticketCount(126, 1));
    }

    @Test
    void scoreboardKeepsTheHigherTownScore() {
        MarketLeaderboardWorldFile file = new MarketLeaderboardWorldFile();
        assertTrue(file.recordBest("t1", "Oakrest", 80));
        assertFalse(file.recordBest("t1", "Oakrest", 40));
        assertTrue(file.recordBest("t1", "Oakrest", 90));
        assertTrue(file.recordBest("t2", "Pineford", 70));
        assertEquals(90, file.find("t1").score());
        assertEquals("Oakrest", file.find("t1").townName());
        assertEquals(70, file.find("t2").score());
        assertFalse(file.recordBest("t3", "Empty", 0));
    }
}
