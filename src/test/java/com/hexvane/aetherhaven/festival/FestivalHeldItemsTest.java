package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.alchemy.AlchemyBrew;
import com.hexvane.aetherhaven.alchemy.AlchemyFlask;
import com.hexvane.aetherhaven.festival.market.MarketIds;
import com.hexvane.aetherhaven.festival.wintertide.WintertideSession;
import com.hexvane.aetherhaven.inventory.ItemStackData;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownWorldFile;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A player's item that a festival holds (goods in the Autumn Market stall, a Wintertide gift between two players) is
 * kept as the whole engine stack and is saved with the town, so a restart, the festival ending or a player leaving
 * neither changes it nor loses it nor hands it out twice. Real {@link ItemStack}s, and a real town file for restarts.
 */
@Tag("town")
class FestivalHeldItemsTest {
    private static final Gson GSON = new Gson();
    private static final short SLOTS = (short) MarketIds.SLOT_COUNT;
    private static final UUID GIVER = UUID.randomUUID(), RECEIVER = UUID.randomUUID(), KEEPER = UUID.randomUUID(), OWNER = UUID.randomUUID();
    private static Object previousStore;

    private static class Items extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        Items(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, new DefaultAssetMap<>()); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public Items build() { return new Items(this); }
        }
    }

    @BeforeAll static void assets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true);
        previousStore = field.get(null); field.set(null, new Items.Config().build());
    }

    @AfterAll static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true); field.set(null, previousStore);
    }

    // ---- the goods

    /** A two charge flask, brewed well, one charge already drunk. */
    private static ItemStack flask(String... mixture) {
        var flask = AlchemyFlask.upgrade(new ItemStack(AlchemyFlask.ITEM, 1));
        return AlchemyFlask.consume(AlchemyFlask.fill(flask, List.of(mixture), AlchemyBrew.Quality.GREAT));
    }

    private static ItemStack healingFlask() {
        return flask("Plant_Crop_Health2", "Plant_Crop_Mana3");
    }

    /** An iron pickaxe worn down to 37.5 of 150, of a quality of its own, with an enchantment another mod wrote. */
    private static ItemStack wornPickaxe() {
        return new ItemStack("Tool_Pickaxe_Iron", 1, 37.5, 150, 2, new BsonDocument("Enchantments", new BsonDocument("Fortune", new BsonInt32(3))));
    }

    private static ItemStack blessedPotions() {
        return new ItemStack("Potion_Health", 20, 0, 0, 0, new BsonDocument("Blessed", BsonBoolean.TRUE));
    }

    private static void assertSameStack(ItemStack held, ItemStack got, String what) {
        assertNotNull(got, what + ": nothing came back");
        assertEquals(held.getItemId(), got.getItemId(), what + ": item id");
        assertEquals(held.getQuantity(), got.getQuantity(), what + ": quantity");
        assertEquals(held.getDurability(), got.getDurability(), what + ": durability");
        assertEquals(held.getMaxDurability(), got.getMaxDurability(), what + ": maximum durability");
        assertEquals(held.getQualityIndex(), got.getQualityIndex(), what + ": quality");
        assertEquals(ItemStackData.metadata(held), ItemStackData.metadata(got), what + ": metadata");
        assertEquals(held, got, what + ": the engine's own equality");
    }

    // ---- the town and a restart

    private static TownRecord town() {
        var town = new TownRecord(UUID.randomUUID(), GIVER, "default", 0, 64, 0, 1, 1, 0);
        town.setDisplayName("Festival Fixture");
        return town;
    }

    /** A server restart: the town is written to its real file form and read back; nothing in memory survives. */
    private static TownRecord restart(Path dir, TownRecord town) throws Exception {
        Path file = dir.resolve("towns.json");
        TownWorldFile.writeBytesAtomic(file, TownWorldFile.toJsonBytes(List.of(town)));
        var towns = TownWorldFile.readOrEmpty(file).getTowns();
        assertEquals(1, towns.size());
        assertEquals(town.getTownId(), towns.getFirst().getTownId());
        return towns.getFirst();
    }

    private static SimpleItemContainer stockedStall() {
        var stall = new SimpleItemContainer(SLOTS);
        stall.setItemStackForSlot((short) 0, wornPickaxe(), false);
        stall.setItemStackForSlot((short) 1, healingFlask(), false);
        stall.setItemStackForSlot((short) 2, flask("Plant_Crop_Mana3"), false);
        stall.setItemStackForSlot((short) (SLOTS - 1), blessedPotions(), false);
        return stall;
    }

    // ---- Autumn Market stall

    @Test void stallGoodsSurviveARestartSlotForSlotAndWhole(@TempDir Path dir) throws Exception {
        var town = town();
        town.festivalHeldItems().saveMarketStall(stockedStall(), KEEPER);
        assertTrue(town.hasFestivalHeldItems());

        var restored = restart(dir, town).festivalHeldItems().restoreMarketStall(SLOTS);
        assertSameStack(wornPickaxe(), restored.getItemStack((short) 0), "worn tool");
        assertSameStack(healingFlask(), restored.getItemStack((short) 1), "filled flask");
        assertSameStack(flask("Plant_Crop_Mana3"), restored.getItemStack((short) 2), "flask with another mixture");
        assertSameStack(blessedPotions(), restored.getItemStack((short) (SLOTS - 1)), "potions");
        for (short i = 3; i < SLOTS - 1; i++) assertTrue(ItemStack.isEmpty(restored.getItemStack(i)), "slot " + i + " was empty");
        // The flask is the flask: its mixture, its one remaining charge, its brew.
        var flask = restored.getItemStack((short) 1);
        assertEquals(List.of("Plant_Crop_Health2", "Plant_Crop_Mana3"), AlchemyFlask.ingredients(flask));
        assertEquals(1, AlchemyFlask.charges(flask));
        assertEquals(2, AlchemyFlask.capacity(flask));
        assertEquals(AlchemyBrew.Quality.GREAT, AlchemyFlask.quality(flask));
        // Two flasks the engine would not stack stay two stacks. Nothing was merged on the way.
        assertFalse(restored.getItemStack((short) 1).isStackableWith(restored.getItemStack((short) 2)));

        // Reading the stall back does not take it out of the save: a second restart finds it again, once.
        var again = restart(dir, restart(dir, town)).festivalHeldItems().restoreMarketStall(SLOTS);
        assertSameStack(wornPickaxe(), again.getItemStack((short) 0), "after two restarts");
        assertEquals(20, again.getItemStack((short) (SLOTS - 1)).getQuantity(), "not doubled");
    }

    @Test void everyChangeToTheStallIsWhatTheTownSavesAndAnEmptyStallSavesNothing(@TempDir Path dir) throws Exception {
        var town = town();
        var stall = stockedStall();
        town.festivalHeldItems().saveMarketStall(stall, null);
        // A player takes the tool back out and splits the potions.
        stall.setItemStackForSlot((short) 0, ItemStack.EMPTY, false);
        stall.setItemStackForSlot((short) (SLOTS - 1), blessedPotions().withQuantity(12), false);
        town.festivalHeldItems().saveMarketStall(stall, null);
        var restored = restart(dir, town).festivalHeldItems().restoreMarketStall(SLOTS);
        assertTrue(ItemStack.isEmpty(restored.getItemStack((short) 0)), "the tool is with the player, not also in the stall");
        assertSameStack(blessedPotions().withQuantity(12), restored.getItemStack((short) (SLOTS - 1)), "twelve potions left");

        // Goods handed back: the stall is empty and the town saves nothing for it.
        town.festivalHeldItems().saveMarketStall(new SimpleItemContainer(SLOTS), null);
        assertFalse(town.festivalHeldItems().hasMarketStall());
        assertFalse(town.hasFestivalHeldItems());
        town.releaseFestivalHeldItemsIfEmpty();
        String saved = new String(TownWorldFile.toJsonBytes(List.of(town)), StandardCharsets.UTF_8);
        assertFalse(saved.contains("festivalHeldItems"), "a town that holds nothing saves as it always did");
        assertTrue(restart(dir, town).festivalHeldItems().restoreMarketStall(SLOTS).isEmpty());
    }

    @Test void goodsLeftWhenTheMarketEndsGoBackToWhoeverKeptTheStallExactlyOnce(@TempDir Path dir) throws Exception {
        var town = town();
        town.festivalHeldItems().saveMarketStall(stockedStall(), KEEPER);
        assertEquals(KEEPER, restart(dir, town).festivalHeldItems().marketKeeper(), "who kept the stall is saved with it");

        // The market ends with the goods still in the stall. They are set aside for the keeper, stack by stack.
        assertEquals(4, town.festivalHeldItems().endMarketStall(OWNER));
        assertFalse(town.festivalHeldItems().hasMarketStall(), "the stall itself is empty now");
        assertTrue(town.festivalHeldItems().restoreMarketStall(SLOTS).isEmpty(), "they are not also put back into a stall");
        assertEquals(0, town.festivalHeldItems().endMarketStall(OWNER), "ending twice sets nothing aside twice");

        var held = restart(dir, town).festivalHeldItems();
        assertEquals(Set.of(KEEPER), held.owedPlayers(), "owed to the keeper, not to the owner, after a restart too");
        // Next year's market opens before the keeper came back. Its stall and last year's goods do not touch.
        var nextYear = new SimpleItemContainer(SLOTS);
        nextYear.setItemStackForSlot((short) 0, new ItemStack("Food_Bread", 3), false);
        held.saveMarketStall(nextYear, OWNER);
        assertEquals(OWNER, held.marketKeeper());

        var back = held.takeOwed(KEEPER);
        assertEquals(4, back.size(), "four stacks went in, four come back, nothing merged");
        assertSameStack(wornPickaxe(), back.get(0), "worn tool");
        assertSameStack(healingFlask(), back.get(1), "filled flask");
        assertSameStack(flask("Plant_Crop_Mana3"), back.get(2), "other flask");
        assertSameStack(blessedPotions(), back.get(3), "potions");
        assertTrue(held.takeOwed(KEEPER).isEmpty(), "handed back once, never twice");
        assertSameStack(new ItemStack("Food_Bread", 3), held.restoreMarketStall(SLOTS).getItemStack((short) 0), "next year's stall is untouched");

        // A stall nobody is recorded as keeping goes to the town's owner.
        var unkept = town();
        unkept.festivalHeldItems().saveMarketStall(stockedStall(), null);
        assertNull(unkept.festivalHeldItems().marketKeeper());
        unkept.festivalHeldItems().endMarketStall(OWNER);
        assertEquals(Set.of(OWNER), unkept.festivalHeldItems().owedPlayers());
    }

    @Test void aMarketThatEndsWhileTheServerIsDownStillReturnsItsSavedStall(@TempDir Path dir) throws Exception {
        var town = town();
        town.festivalHeldItems().saveMarketStall(stockedStall(), KEEPER);
        // Restart: no live stall exists any more, only what the town saved. Then the festival is found to be over.
        var afterRestart = restart(dir, town);
        assertEquals(4, afterRestart.festivalHeldItems().endMarketStall(OWNER));
        var back = restart(dir, afterRestart).festivalHeldItems().takeOwed(KEEPER);
        assertEquals(4, back.size());
        assertSameStack(wornPickaxe(), back.get(0), "worn tool");
        assertSameStack(healingFlask(), back.get(1), "filled flask");
        // Ending a market that left nothing behind sets nothing aside.
        var empty = town();
        assertEquals(0, empty.festivalHeldItems().endMarketStall(OWNER));
        assertFalse(empty.hasFestivalHeldItems());
    }

    @Test void aNewGiftNeverWritesOverOneThatIsStillSaved(@TempDir Path dir) throws Exception {
        var town = town();
        // A gift was held when the server stopped. Before the minute check returns it, another gift is given.
        town.festivalHeldItems().holdWintertideGift(GIVER, RECEIVER, healingFlask());
        var held = restart(dir, town).festivalHeldItems();
        held.holdWintertideGift(KEEPER, RECEIVER, wornPickaxe());
        assertSameStack(wornPickaxe(), held.wintertideGift().stack(), "the new gift waits for its answer");
        assertEquals(KEEPER, held.wintertideGift().giver());
        assertEquals(Set.of(GIVER), held.owedPlayers(), "the older gift is set aside for its own giver");
        var back = held.takeOwed(GIVER);
        assertEquals(1, back.size());
        assertSameStack(healingFlask(), back.getFirst(), "the older gift");
    }

    // ---- Wintertide gift between two players

    @Test void aGiftOnItsWayIsTheItemItselfAndSurvivesARestart(@TempDir Path dir) throws Exception {
        // What the gift step records: the session's pending gift carries the stack, not an item id.
        var pending = new WintertideSession.PendingPlayerGift(GIVER, RECEIVER, healingFlask());
        assertSameStack(healingFlask(), pending.stack(), "pending gift");

        var town = town();
        town.festivalHeldItems().holdWintertideGift(GIVER, RECEIVER, pending.stack());
        var gift = restart(dir, town).festivalHeldItems().wintertideGift();
        assertNotNull(gift, "the gift is saved with the town");
        assertEquals(GIVER, gift.giver());
        assertEquals(RECEIVER, gift.receiver());
        assertSameStack(healingFlask(), gift.stack(), "gift after a restart");
        assertEquals(1, AlchemyFlask.charges(gift.stack()));

        // The flask is answered. The next gift, a worn tool, keeps its wear and its enchantment.
        town.festivalHeldItems().clearWintertideGift();
        town.festivalHeldItems().holdWintertideGift(GIVER, RECEIVER, wornPickaxe());
        assertSameStack(wornPickaxe(), restart(dir, town).festivalHeldItems().wintertideGift().stack(), "worn tool gift");

        // Answered: the receiver has it, so the town holds nothing.
        town.festivalHeldItems().clearWintertideGift();
        assertFalse(town.hasFestivalHeldItems());
    }

    @Test void aGiftThatIsNeverAnsweredIsSetAsideForItsGiverAndHandedBackExactlyOnce(@TempDir Path dir) throws Exception {
        var town = town();
        var held = town.festivalHeldItems();
        held.holdWintertideGift(GIVER, RECEIVER, healingFlask());
        // The festival ends (or the server restarts) with the gift unanswered and the giver not in the world: the
        // gift moves from "on its way" to "owed to the giver". This is what WintertideGiftService.returnPendingGift does.
        var gift = held.wintertideGift();
        held.clearWintertideGift();
        held.owe(gift.giver(), gift.stack());
        assertNull(held.wintertideGift());
        assertEquals(Set.of(GIVER), held.owedPlayers());

        var later = restart(dir, town).festivalHeldItems();
        assertEquals(Set.of(GIVER), later.owedPlayers(), "still owed after a restart");
        assertTrue(later.takeOwed(RECEIVER).isEmpty(), "only the giver gets it back");
        var back = later.takeOwed(GIVER);
        assertEquals(1, back.size());
        assertSameStack(healingFlask(), back.getFirst(), "handed back to the giver");
        assertTrue(later.takeOwed(GIVER).isEmpty(), "handed back once, never twice");
        assertTrue(later.isEmpty());

        // The giver's inventory was not reachable after all: the stack is put back and waits again.
        later.owe(GIVER, back.getFirst());
        later.owe(GIVER, wornPickaxe());
        var both = later.takeOwed(GIVER);
        assertEquals(2, both.size(), "two things owed are two stacks, nothing merged");
        assertSameStack(healingFlask(), both.get(0), "first");
        assertSameStack(wornPickaxe(), both.get(1), "second");
    }

    // ---- old saves and damaged saves

    @Test void townsSavedBeforeFestivalsHeldItemsLoadAsTheyAlwaysDid(@TempDir Path dir) throws Exception {
        var town = town();
        String before = new String(TownWorldFile.toJsonBytes(List.of(town)), StandardCharsets.UTF_8);
        assertFalse(before.contains("festivalHeldItems"));
        var loaded = restart(dir, town);
        assertFalse(loaded.hasFestivalHeldItems());
        assertNull(JsonParser.parseString(GSON.toJson(loaded)).getAsJsonObject().get("festivalHeldItems"), "asking does not create the record");
        assertTrue(loaded.festivalHeldItems().restoreMarketStall(SLOTS).isEmpty());
        assertNull(loaded.festivalHeldItems().wintertideGift());
        assertTrue(loaded.festivalHeldItems().owedPlayers().isEmpty());
    }

    @Test void savedTextThatCannotBeReadIsKeptAndNeverAnException() {
        var held = GSON.fromJson(
            "{\"marketStall\":\"{broken\",\"marketKeeper\":\"not a uuid\","
                + "\"wintertideGift\":{\"giver\":\"" + GIVER + "\",\"receiver\":\"nobody\",\"stack\":\"{no\"},"
                + "\"owed\":[{\"player\":\"" + GIVER + "\",\"stack\":\"{no\"}]}",
            FestivalHeldItems.class
        );
        assertTrue(held.restoreMarketStall(SLOTS).isEmpty());
        assertNull(held.marketKeeper());
        assertNull(held.wintertideGift().stack());
        assertNull(held.wintertideGift().receiver());
        assertTrue(held.takeOwed(GIVER).isEmpty());
        assertEquals(0, held.endMarketStall(OWNER));
        // Nothing readable was handed out, and nothing unreadable was thrown away.
        String after = GSON.toJson(held);
        assertTrue(after.contains("{broken") && after.contains("{no"), after);
        assertFalse(held.isEmpty());
        assertNull(ItemStackData.fromText("{not a stack"));
        assertNull(ItemStackData.fromText(null));
        assertNull(ItemStackData.toText(null));
        assertSameStack(wornPickaxe(), ItemStackData.fromText(ItemStackData.toText(wornPickaxe())), "text round trip");
    }
}
