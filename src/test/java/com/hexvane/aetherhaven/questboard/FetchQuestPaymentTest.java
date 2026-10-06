package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class FetchQuestPaymentTest {
    private static final Gson GSON = new Gson();
    private QuestBoardFetchEntryJson entry(int bonus, int coins) {
        return GSON.fromJson("{\"materialPaymentBonusGold\":"+bonus+",\"rewards\":["
            +"{\"kind\":\"item\",\"itemId\":\"Aetherhaven_Gold_Coin\",\"count\":"+coins+",\"grantTo\":\"player\"},"
            +"{\"kind\":\"reputation\",\"amount\":3,\"npcRoleId\":\"Aetherhaven_Guild_Master\"}]}", QuestBoardFetchEntryJson.class);
    }
    private QuestBoardItemRequirement item(String id,int count) { return new QuestBoardItemRequirement(id,count); }
    private ShopPriceCatalog prices(String entries) {return ShopPriceCatalog.parseJson("{\"defaultGoldPrice\":5,\"prices\":"+entries+"}");}

    @Test void selectedGoodsArePaidOnceAndAuthoredRewardsRemainUnchanged() {
        var request=entry(18,18);
        var prices=prices("{\"Armor\":82,\"Bread\":10}");
        var armor=FetchQuestPayment.quote(request,List.of(item("Armor",1)),prices).orElseThrow();
        var bread=FetchQuestPayment.quote(request,List.of(item("Bread",12)),prices).orElseThrow();
        assertEquals(100,armor.getFirst().count());assertEquals(138,bread.getFirst().count());
        assertEquals(18,request.rewardsOrEmpty().getFirst().count());
        assertEquals(3,armor.get(1).amount());assertEquals("Aetherhaven_Guild_Master",armor.get(1).npcRoleId());
        assertEquals("player",armor.getFirst().grantTo());
    }

    @Test void batchPricesAndDuplicateLinesDoNotMultiplyThePayment() {
        var catalog=prices("{\"Stone\":{\"gold\":5,\"batchSize\":50}}");
        var split=FetchQuestPayment.quote(entry(18,18),List.of(item("Stone",24),item("Stone",24)),catalog).orElseThrow();
        var combined=FetchQuestPayment.quote(entry(18,18),List.of(item("Stone",48)),catalog).orElseThrow();
        assertEquals(23,combined.getFirst().count());assertEquals(combined.getFirst().count(),split.getFirst().count());
    }

    @Test void oldCustomEntriesAndHigherAuthoredRewardsKeepTheirPayments() {
        assertEquals(18,FetchQuestPayment.quote(entry(0,18),List.of(item("Armor",1)),prices("{\"Armor\":82}")).orElseThrow().getFirst().count());
        assertEquals(200,FetchQuestPayment.quote(entry(18,200),List.of(item("Armor",1)),prices("{\"Armor\":82}")).orElseThrow().getFirst().count());
        var noGold=GSON.fromJson("{\"materialPaymentBonusGold\":18,\"rewards\":[{\"kind\":\"reputation\",\"amount\":2}]}",QuestBoardFetchEntryJson.class);
        assertEquals(1,FetchQuestPayment.quote(noGold,List.of(item("Armor",1)),prices("{\"Armor\":82}")).orElseThrow().size());
    }

    @Test void postedQuoteSurvivesSavingAndLaterPriceChanges() {
        var request=entry(18,18);var items=List.of(item("Armor",1));var slot=new QuestBoardSlotRecord();
        slot.setRequiredItems(items);slot.setRewards(FetchQuestPayment.quote(request,items,prices("{\"Armor\":82}")).orElseThrow());
        var saved=GSON.toJson(slot);
        assertEquals(218,FetchQuestPayment.quote(request,items,prices("{\"Armor\":200}")).orElseThrow().getFirst().count());
        assertEquals(100,GSON.fromJson(saved,QuestBoardSlotRecord.class).rewardsOrEmpty().getFirst().count());
        assertEquals(1,GSON.fromJson(saved,QuestBoardSlotRecord.class).requiredItemsOrEmpty().getFirst().count());
    }

    @Test void defaultPricesApplyAndExtremeConfiguredPricesCannotOverflow() {
        assertEquals(98,FetchQuestPayment.quote(entry(18,18),List.of(item("UnknownMeat",16)),prices("{}")).orElseThrow().getFirst().count());
        assertTrue(FetchQuestPayment.quote(entry(18,18),List.of(item("Rare",Integer.MAX_VALUE)),prices("{\"Rare\":9223372036854775807}")).isEmpty());
    }
}
