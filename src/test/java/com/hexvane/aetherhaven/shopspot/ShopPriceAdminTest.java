package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.questboard.FetchQuestPayment;
import com.hexvane.aetherhaven.questboard.QuestBoardItemRequirement;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.shopspot.ShopPriceAdmin.Outcome;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog.Source;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3i;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Changing item prices on a running server. A tiny stand-in server keeps the shipped table as text, the price file in
 * a temporary folder and the live catalog in a field, and reloads the way the plugin does: by reading both again with
 * {@link ShopPriceFiles#withServerFile}. Everything else is the code the command runs.
 */
@Tag("town")
class ShopPriceAdminTest {
    private static final Gson GSON = new Gson();
    /** The table shipped in the jar: never written by anything in these tests. */
    private static final String SHIPPED = """
        {"catalogRevision":1,"defaultGoldPrice":5,"defaultBatchSize":1,"prices":{
          "Tool_Pickaxe_Iron":18,
          "Wood_Oak_Trunk":{"gold":1,"batchSize":10},
          "Armor":82
        }}""";

    @TempDir Path dir;
    private Path file;
    private ShopPriceCatalog live;
    private final AtomicInteger reloads = new AtomicInteger();
    private ShopPriceAdmin prices;

    /** What AetherhavenPlugin.reloadShopPriceCatalog does, and what a server start does. */
    private void restart() {
        try {
            live = ShopPriceFiles.withServerFile(ShopPriceCatalog.parseJson(SHIPPED), file, 0);
            reloads.incrementAndGet();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach void server() {
        file = dir.resolve(ShopPriceFiles.SHOP_PRICES_FILE_NAME);
        restart();
        prices = new ShopPriceAdmin(file, () -> live, this::restart);
    }

    private String fileText() throws Exception {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    // ---- set, change, remove, show, list

    @Test void settingAPriceForAnItemThatHadNoneMakesItPricedAtOnceAndAfterARestart() throws Exception {
        var before = prices.show("Modded_Gem");
        assertEquals(Source.DEFAULT, before.source());
        assertEquals(5, before.gold());
        assertFalse(live.hasExplicitPrice("Modded_Gem"), "players cannot list an item that has no price");

        var result = prices.set("Modded_Gem", 75, null, 100);
        assertEquals(Outcome.SET, result.outcome());
        assertEquals(Source.DEFAULT, result.before().source());
        assertEquals(75, result.now().gold());
        assertEquals(1, result.now().lot());
        assertEquals(Source.OVERRIDE, result.now().source());
        assertNull(result.now().beneath(), "nothing shipped lies under it");
        assertTrue(live.hasExplicitPrice("Modded_Gem"), "now it can be listed");
        assertEquals(75, live.getGoldPrice("Modded_Gem"));

        // A restart reads the file again and finds the same price.
        restart();
        assertEquals(75, prices.show("Modded_Gem").gold());
        assertEquals(Source.OVERRIDE, prices.show("Modded_Gem").source());
        assertEquals(List.of("Modded_Gem"), List.copyOf(prices.list().keySet()));
    }

    @Test void changingAShippedPriceKeepsItsLotSizeAndRemembersWhatItReplaced() throws Exception {
        var shipped = prices.show("Wood_Oak_Trunk");
        assertEquals(Source.GENERATED, shipped.source());
        assertEquals(1, shipped.gold());
        assertEquals(10, shipped.lot());

        var set = prices.set("Wood_Oak_Trunk", 3, null, 100);
        assertEquals(Outcome.SET, set.outcome());
        assertEquals(3, set.now().gold());
        assertEquals(10, set.now().lot(), "3 gold for 10, not 3 gold each");
        assertEquals(Source.GENERATED, set.now().beneath().source());
        assertEquals(1, set.now().beneath().entry().getGoldPerBatch());

        var changed = prices.set("Wood_Oak_Trunk", 4, 20, 100);
        assertEquals(Outcome.CHANGED, changed.outcome());
        assertEquals(3, changed.before().gold());
        assertEquals(4, changed.now().gold());
        assertEquals(20, changed.now().lot());
        assertEquals(1, changed.now().beneath().entry().getGoldPerBatch(), "still the shipped price underneath");
        assertEquals(1, prices.list().size());

        // The file holds only what was set, in a form an admin can read and edit.
        var root = JsonParser.parseString(fileText()).getAsJsonObject();
        assertEquals(1, root.get("catalogRevision").getAsInt());
        assertEquals(1, root.getAsJsonObject("prices").size());
        assertEquals(4, root.getAsJsonObject("prices").getAsJsonObject("Wood_Oak_Trunk").get("gold").getAsInt());
        assertEquals(20, root.getAsJsonObject("prices").getAsJsonObject("Wood_Oak_Trunk").get("batchSize").getAsInt());
        prices.set("Tool_Pickaxe_Iron", 25, null, 1);
        assertEquals(25, JsonParser.parseString(fileText()).getAsJsonObject().getAsJsonObject("prices").get("Tool_Pickaxe_Iron").getAsInt());
        assertTrue(fileText().contains("\n"), "written on several lines");
        // The shipped table itself is untouched.
        assertEquals(18, ShopPriceCatalog.parseJson(SHIPPED).getGoldPrice("Tool_Pickaxe_Iron"));
    }

    @Test void removingAnOverrideGoesBackToTheShippedPriceOrToNoPrice() throws Exception {
        prices.set("Tool_Pickaxe_Iron", 60, null, 1);
        prices.set("Modded_Gem", 75, null, 100);

        var back = prices.remove("Tool_Pickaxe_Iron");
        assertEquals(Outcome.REMOVED, back.outcome());
        assertEquals(60, back.before().gold());
        assertEquals(18, back.now().gold());
        assertEquals(Source.GENERATED, back.now().source());
        assertEquals(18, live.getGoldPrice("Tool_Pickaxe_Iron"));

        var gone = prices.remove("Modded_Gem");
        assertEquals(Outcome.REMOVED, gone.outcome());
        assertEquals(Source.DEFAULT, gone.now().source());
        assertFalse(live.hasExplicitPrice("Modded_Gem"), "it cannot be listed again");
        assertTrue(prices.list().isEmpty());
        restart();
        assertEquals(18, prices.show("Tool_Pickaxe_Iron").gold());
        assertTrue(prices.list().isEmpty());

        // Nothing to remove: said so, and nothing is rewritten or reloaded.
        String text = fileText();
        int reloadsBefore = reloads.get();
        var none = prices.remove("Armor");
        assertEquals(Outcome.NOT_OVERRIDDEN, none.outcome());
        assertEquals(82, none.now().gold());
        assertEquals(Source.GENERATED, none.now().source());
        assertEquals(text, fileText());
        assertEquals(reloadsBefore, reloads.get());
        // An id of an item this server no longer has can still be removed, and an unknown one is simply not there.
        assertEquals(Outcome.NOT_OVERRIDDEN, prices.remove("No_Such_Item").outcome());
    }

    // ---- bad input

    @Test void pricesAndLotSizesOutOfRangeAreRefusedAndChangeNothing() throws Exception {
        prices.set("Armor", 90, null, 1);
        String text = fileText();
        int reloadsBefore = reloads.get();
        record Bad(long gold, Integer lot, int stack, ShopPriceOverrides.Refusal why) {}
        for (var bad : List.of(
            new Bad(0, null, 100, ShopPriceOverrides.Refusal.TOO_LOW),
            new Bad(-5, null, 100, ShopPriceOverrides.Refusal.TOO_LOW),
            new Bad(Long.MIN_VALUE, null, 100, ShopPriceOverrides.Refusal.TOO_LOW),
            new Bad(ShopPriceOverrides.MAX_GOLD + 1, null, 100, ShopPriceOverrides.Refusal.TOO_HIGH),
            new Bad(Long.MAX_VALUE, null, 100, ShopPriceOverrides.Refusal.TOO_HIGH),
            new Bad(10, 0, 100, ShopPriceOverrides.Refusal.LOT_TOO_SMALL),
            new Bad(10, -3, 100, ShopPriceOverrides.Refusal.LOT_TOO_SMALL),
            new Bad(10, 101, 100, ShopPriceOverrides.Refusal.LOT_TOO_LARGE),
            new Bad(10, 2, 1, ShopPriceOverrides.Refusal.LOT_TOO_LARGE),
            new Bad(10, ShopPriceOverrides.MAX_LOT + 1, Integer.MAX_VALUE, ShopPriceOverrides.Refusal.LOT_TOO_LARGE)
        )) {
            var result = prices.set("Armor", bad.gold(), bad.lot(), bad.stack());
            assertEquals(Outcome.REFUSED, result.outcome(), bad.toString());
            assertEquals(bad.why(), result.refusal(), bad.toString());
            assertFalse(result.changedPrices());
        }
        assertEquals(text, fileText(), "a refused price never reaches the file");
        assertEquals(reloadsBefore, reloads.get());
        assertEquals(90, live.getGoldPrice("Armor"));
        // The limits themselves are allowed.
        assertEquals(Outcome.CHANGED, prices.set("Armor", 1, 1, 1).outcome());
        assertEquals(Outcome.CHANGED, prices.set("Armor", ShopPriceOverrides.MAX_GOLD, 100, 100).outcome());
    }

    @Test void aPriceFileWithAMistakeIsNeverOverwrittenAndNeverTakesPricesDown() throws Exception {
        prices.set("Armor", 90, null, 1);
        // An admin edits the file by hand and leaves a comma out.
        String broken = "{\n  \"catalogRevision\": 1,\n  \"prices\": {\n    \"Armor\": 95\n    \"Tool_Pickaxe_Iron\": 30\n  }\n}\n";
        Files.writeString(file, broken, StandardCharsets.UTF_8);

        var reload = prices.reload();
        assertEquals(Outcome.FILE_UNREADABLE, reload.outcome());
        assertNotNull(reload.detail());
        assertEquals(90, live.getGoldPrice("Armor"), "the prices in force stay until the file is fixed");

        var set = prices.set("Tool_Pickaxe_Iron", 40, null, 1);
        assertEquals(Outcome.FILE_UNREADABLE, set.outcome());
        assertEquals(Outcome.FILE_UNREADABLE, prices.remove("Armor").outcome());
        assertEquals(broken, fileText(), "the admin's edit is still there to be fixed");
        assertEquals(18, live.getGoldPrice("Tool_Pickaxe_Iron"));

        // A server that starts with the broken file runs on the shipped prices; it does not fail to start.
        restart();
        assertEquals(82, live.getGoldPrice("Armor"));
        assertEquals(18, live.getGoldPrice("Tool_Pickaxe_Iron"));
        assertEquals(broken, fileText());

        // Other mistakes are named in plain words too.
        for (String wrong : List.of(
            "{\"prices\":{\"Armor\":\"ninety\"}}",
            "{\"prices\":[1,2]}",
            "[]",
            "{\"prices\":{\"Armor\":-4}}",
            "{\"prices\":{\"Armor\":0}}",
            "{\"prices\":{\"Armor\":5000000}}",
            "{\"prices\":{\"Armor\":{\"gold\":5,\"batchSize\":5000}}}"
        )) {
            Files.writeString(file, wrong, StandardCharsets.UTF_8);
            assertNotNull(ShopPriceOverrides.problem(file), wrong);
            assertEquals(Outcome.FILE_UNREADABLE, prices.reload().outcome(), wrong);
        }

        // Fixed by hand, then reloaded: the hand edit is live, and the command can write again.
        Files.writeString(file, "{\"catalogRevision\":1,\"note\":\"winter sale\",\"prices\":{\"Armor\":95,\"Wood_Oak_Trunk\":{\"gold\":2,\"batchSize\":10}}}", StandardCharsets.UTF_8);
        assertEquals(Outcome.RELOADED, prices.reload().outcome());
        assertEquals(95, live.getGoldPrice("Armor"));
        assertEquals(2, live.getGoldPrice("Wood_Oak_Trunk"));
        assertEquals(2, prices.list().size());
        assertEquals(Outcome.SET, prices.set("Tool_Pickaxe_Iron", 40, null, 1).outcome());
        var root = JsonParser.parseString(fileText()).getAsJsonObject();
        assertEquals("winter sale", root.get("note").getAsString(), "whatever else the admin wrote is kept");
        assertEquals(3, root.getAsJsonObject("prices").size());
        assertEquals(95, root.getAsJsonObject("prices").get("Armor").getAsInt());
    }

    @Test void aMissingOrEmptyFileIsNotAMistake() throws Exception {
        assertFalse(Files.exists(file));
        assertNull(ShopPriceOverrides.problem(file));
        assertEquals(Outcome.RELOADED, prices.reload().outcome());
        assertTrue(prices.list().isEmpty());
        Files.writeString(file, "", StandardCharsets.UTF_8);
        assertEquals(Outcome.RELOADED, prices.reload().outcome());
        // The template the mod writes on first start.
        Files.writeString(file, "{\n  \"catalogRevision\": 1,\n  \"prices\": {}\n}\n", StandardCharsets.UTF_8);
        assertEquals(Outcome.SET, prices.set("Armor", 90, null, 1).outcome());
        assertEquals(90, live.getGoldPrice("Armor"));
    }

    // ---- where prices come from

    @Test void aPriceIsTracedToTheShippedTableAnotherModOrThisServer() throws Exception {
        var pack = ShopPriceCatalog.parseJson("{\"prices\":{\"Armor\":70,\"Pack_Item\":12}}");
        // A stand-in server that also has another mod's price pack.
        Runnable withPack = () -> {
            try {
                live = ShopPriceFiles.withServerFile(ShopPriceCatalog.parseJson(SHIPPED).withLayer(pack, Source.PACK), file, 2);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };
        withPack.run();
        var modded = new ShopPriceAdmin(file, () -> live, withPack);
        assertEquals(Source.GENERATED, modded.show("Tool_Pickaxe_Iron").source());
        assertEquals(Source.PACK, modded.show("Armor").source());
        assertEquals(70, modded.show("Armor").gold());
        assertEquals(Source.PACK, modded.show("Pack_Item").source());
        assertEquals(Source.DEFAULT, modded.show("Nothing").source());

        assertEquals(Outcome.SET, modded.set("Armor", 99, null, 1).outcome());
        var shown = modded.show("Armor");
        assertEquals(Source.OVERRIDE, shown.source());
        assertEquals(Source.PACK, shown.beneath().source(), "removing it goes back to the other mod's price");
        assertEquals(70, shown.beneath().entry().getGoldPerBatch());
        assertEquals(70, modded.remove("Armor").now().gold());
    }

    // ---- what a changed price reaches

    private static ShopSpotRecord stall(boolean playerControlled, String item, int stock) {
        var r = new ShopSpotRecord();
        r.setSpotId(UUID.randomUUID()); r.setWorldName("default"); r.setBlockPosition(new Vector3i(1, 64, 0));
        r.setTownId(UUID.randomUUID()); r.setPlotId(UUID.randomUUID()); r.setPlayerControlled(playerControlled);
        r.setItemId(item); r.setStock(stock);
        return r;
    }

    /** What ShopSpotPurchaseService.handleListOrRemove records for a new listing: the catalog's price at that moment. */
    private ShopSpotRecord listNow(String item, int stock) {
        var r = stall(true, item, stock);
        assertTrue(live.hasExplicitPrice(item), "the listing gate");
        r.setQuote(live.getEntry(item));
        return r;
    }

    private static QuestBoardFetchEntryJson delivery() {
        return GSON.fromJson("{\"materialPaymentBonusGold\":18,\"rewards\":[{\"kind\":\"item\",\"itemId\":\"Aetherhaven_Gold_Coin\",\"count\":18,\"grantTo\":\"player\"}]}", QuestBoardFetchEntryJson.class);
    }

    private int quoted(List<QuestReward> rewards) {
        return rewards.getFirst().count();
    }

    @Test void aChangedPriceIsLiveEverywhereAtOnceButNothingAlreadyPostedChanges() throws Exception {
        var wanted = List.of(new QuestBoardItemRequirement("Armor", 1));
        // Posted before the change: a player's listing, the merchant's stock for the day, and a guild delivery job.
        var postedListing = listNow("Armor", 2);
        var merchantStock = stall(false, "Armor", 3);
        var postedJob = new QuestBoardSlotRecord();
        postedJob.setRequiredItems(wanted);
        postedJob.setRewards(FetchQuestPayment.quote(delivery(), wanted, live).orElseThrow());
        assertEquals(100, quoted(postedJob.rewardsOrEmpty()));
        String savedJob = GSON.toJson(postedJob);
        var registry = new ShopSpotRegistry(null);
        registry.put(postedListing);
        String savedListing = GSON.toJson(ShopSpotWorldFile.fromRegistry(registry));

        assertEquals(Outcome.SET, prices.set("Armor", 200, null, 1).outcome());

        // At once, with no restart: every lookup that reads the catalog.
        assertEquals(200, live.getGoldPrice("Armor"), "the price shown on item tooltips");
        assertEquals(200, ShopSpotPricing.entry(live, merchantStock).getGoldPerBatch(), "NPC shop stalls");
        assertEquals(200, ShopSpotPricing.entry(live, listNow("Armor", 1)).getGoldPerBatch(), "an item listed from now on");
        assertEquals(218, quoted(FetchQuestPayment.quote(delivery(), wanted, live).orElseThrow()), "a job posted from now on");

        // Never: what was already posted, in memory or read back from its save.
        assertEquals(82, ShopSpotPricing.entry(live, postedListing).getGoldPerBatch(), "a listing keeps the price it went up at");
        var reloadedListing = ShopSpotWorldFile.toRecords(GSON.fromJson(savedListing, ShopSpotWorldFile.class)).getFirst();
        assertEquals(82, ShopSpotPricing.entry(live, reloadedListing).getGoldPerBatch());
        assertEquals(100, quoted(postedJob.rewardsOrEmpty()), "a posted job keeps its quoted payment");
        assertEquals(100, quoted(GSON.fromJson(savedJob, QuestBoardSlotRecord.class).rewardsOrEmpty()));

        // The same holds when the override is taken away again.
        var listedAtTwoHundred = listNow("Armor", 1);
        assertEquals(Outcome.REMOVED, prices.remove("Armor").outcome());
        assertEquals(82, ShopSpotPricing.entry(live, merchantStock).getGoldPerBatch());
        assertEquals(200, ShopSpotPricing.entry(live, listedAtTwoHundred).getGoldPerBatch());
        assertEquals(100, quoted(FetchQuestPayment.quote(delivery(), wanted, live).orElseThrow()));
    }

    @Test void theTooltipPriceFollowsTheLiveCatalog() throws Exception {
        // The inventory tooltip footer is built from the catalog entry, see ShopPriceTooltipMessages.footerFor.
        assertEquals(18, live.getEntry("Tool_Pickaxe_Iron").getGoldPerBatch());
        prices.set("Tool_Pickaxe_Iron", 33, null, 1);
        assertEquals(33, live.getEntry("Tool_Pickaxe_Iron").getGoldPerBatch());
        assertTrue(live.hasExplicitPrice("Tool_Pickaxe_Iron"));
        prices.set("Modded_Gem", 7, 5, 100);
        assertEquals(7, live.getEntry("Modded_Gem").getGoldPerBatch());
        assertEquals(5, live.getEntry("Modded_Gem").getBatchSize());
        assertTrue(live.hasExplicitPrice("Modded_Gem"), "an item with no price shows none; now it shows one");
    }
}
