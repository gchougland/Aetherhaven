package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.alchemy.AlchemyBrew;
import com.hexvane.aetherhaven.alchemy.AlchemyFlask;
import com.hexvane.aetherhaven.backpack.Backpack;
import com.hexvane.aetherhaven.inventory.ItemStackData;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.StringParamValue;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.util.MessageUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.joml.Vector3i;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An item put up for sale at a player shop keeps everything the engine holds on its stack. Each test lists real
 * {@link ItemStack}s with the calls the stall interaction makes, and reads them back with the calls a purchase, a
 * cancelled listing and a returned listing make.
 */
@Tag("town")
class ShopListingTest {
    private static final Gson GSON = new Gson();
    private static final UUID TOWN = UUID.randomUUID(), PLOT = UUID.randomUUID(), SELLER = UUID.randomUUID();
    private static final ShopPriceEntry QUOTE = new ShopPriceEntry(40, 1);
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

    private static final List<String> MIXTURE = List.of("Plant_Crop_Health2", "Plant_Crop_Mana3", "Plant_Crop_Mushroom_Boomshroom_Small");

    /** A three charge flask, brewed well, one charge already drunk, and engraved by another mod. */
    private static ItemStack flask() {
        var flask = AlchemyFlask.upgrade(AlchemyFlask.upgrade(new ItemStack(AlchemyFlask.ITEM, 1)));
        flask = AlchemyFlask.consume(AlchemyFlask.fill(flask, MIXTURE, AlchemyBrew.Quality.values()[AlchemyBrew.Quality.values().length - 1]));
        return flask.withMetadata("engraving", new BsonDocument("text", new BsonString("Orin")));
    }

    /** An iron pickaxe worn down to 37.5 of 150, of the third quality, with an enchantment another mod wrote. */
    private static ItemStack wornPickaxe() {
        return new ItemStack("Tool_Pickaxe_Iron", 1, 37.5, 150, 2, new BsonDocument("Enchantments", new BsonDocument("Fortune", new BsonInt32(3))));
    }

    private static ItemStack appraisedRing() {
        var trait = new BsonDocument("i", new BsonInt32(1)).append("stat", new BsonString("health")).append("calc", new BsonString("ADDITIVE")).append("amt", new BsonDouble(12.5));
        var root = new BsonDocument("rarity", new BsonString("RARE")).append("appraised", BsonBoolean.TRUE).append("traits", new BsonArray(List.of(trait)));
        return new ItemStack("Aetherhaven_Ring_Gold_Ruby", 1).withMetadata(JewelryMetadata.BSON_KEY, root).withQuality(3);
    }

    private static ItemStack packedBackpack() {
        var bag = Backpack.create();
        var pack = Backpack.read(bag);
        assertTrue(pack.rename("Road Pack"));
        assertTrue(pack.insert(new ItemStack("Ingredient_Bar_Copper", 7)) > 0);
        assertEquals(1, pack.insert(flask()));
        return pack.write(bag);
    }

    private static ItemStack namedSword() {
        return new ItemStack("Weapon_Sword_Iron", 1, 80, 80, 0, null)
            .withMetadata(ItemDisplayMetadata.KEYED_CODEC, new ItemDisplayMetadata(Message.raw("Dawnbreaker"), Message.raw("Forged for the founder.")));
    }

    /** Twenty identical potions that carry metadata, as one stack. */
    private static ItemStack blessedPotions() {
        return new ItemStack("Potion_Health", 20, 0, 0, 0, new BsonDocument("Blessed", BsonBoolean.TRUE));
    }

    // ---- the shop

    private static ShopSpotRecord stall(int x) {
        var r = new ShopSpotRecord();
        r.setSpotId(UUID.randomUUID()); r.setWorldName("default"); r.setBlockPosition(new Vector3i(x, 64, 0));
        r.setTownId(TOWN); r.setPlotId(PLOT); r.setPlayerControlled(true);
        return r;
    }

    /** What ShopSpotPurchaseService.handleListOrRemove does with the stack in the seller's hand. */
    private static ShopSpotRecord list(int x, ItemStack hand, int quantity) {
        var r = stall(x);
        ShopListing.list(r, hand, quantity, QUOTE);
        r.setSellerUuid(SELLER); r.setSellerName("Seller");
        return r;
    }

    /** What a purchase hands over, then what it does to the stall. */
    private static ItemStack buy(ShopSpotRecord stall, int quantity) {
        var bought = ShopSpotJewelrySupport.buildListingStack(stall.getItemId(), quantity, stall);
        ShopListing.sold(stall, quantity);
        return bought;
    }

    /** What taking a listing back hands over, then what it does to the stall. */
    private static ItemStack cancel(ShopSpotRecord stall) {
        var back = ShopSpotJewelrySupport.buildListingStack(stall.getItemId(), stall.getStock(), stall);
        ShopListing.clear(stall);
        return back;
    }

    /** A server restart: the stalls are written to a real file and read back. The registry needs no world for this. */
    private static List<ShopSpotRecord> saveAndLoad(Path dir, ShopSpotRecord... stalls) throws Exception {
        var registry = new ShopSpotRegistry(null);
        for (var s : stalls) registry.put(s);
        Path file = dir.resolve("shop_spots.json");
        ShopSpotWorldFile.fromRegistry(registry).writeAtomic(file);
        var loaded = ShopSpotWorldFile.toRecords(ShopSpotWorldFile.readOrEmpty(file));
        assertEquals(stalls.length, loaded.size());
        // File order is the registry's, not the caller's.
        return java.util.Arrays.stream(stalls).map(s -> loaded.stream().filter(l -> l.getSpotId().equals(s.getSpotId())).findFirst().orElseThrow()).toList();
    }

    /** Every field the engine keeps on a stack, compared one by one so a failure names the field that was lost. */
    private static void assertSameStack(ItemStack listed, ItemStack got, String what) {
        assertNotSame(listed, got, what + ": the stall must hand out its own stack");
        assertEquals(listed.getItemId(), got.getItemId(), what + ": item id");
        assertEquals(listed.getQuantity(), got.getQuantity(), what + ": quantity");
        assertEquals(listed.getDurability(), got.getDurability(), what + ": durability");
        assertEquals(listed.getMaxDurability(), got.getMaxDurability(), what + ": maximum durability");
        assertEquals(listed.getQualityIndex(), got.getQualityIndex(), what + ": quality");
        assertEquals(ItemStackData.metadata(listed), ItemStackData.metadata(got), what + ": metadata");
        assertEquals(listed, got, what + ": the engine's own equality");
        assertTrue(listed.isStackableWith(got), what + ": the engine would stack it with the original");
    }

    // ---- tests

    @Test void everyKindOfStackComesBackWholeAfterListSaveLoadAndBuy(@TempDir Path dir) throws Exception {
        var goods = List.of(flask(), wornPickaxe(), appraisedRing(), packedBackpack(), namedSword());
        var stalls = new ShopSpotRecord[goods.size()];
        for (int i = 0; i < goods.size(); i++) stalls[i] = list(i, goods.get(i), 1);
        var loaded = saveAndLoad(dir, stalls);
        for (int i = 0; i < goods.size(); i++) {
            var stall = loaded.get(i);
            assertEquals(SELLER, stall.getSellerUuid());
            var bought = buy(stall, 1);
            assertSameStack(goods.get(i), bought, goods.get(i).getItemId());
            assertFalse(stall.hasStock(), "sold out");
            assertNull(stall.getItemId());
            assertNull(stall.getStackData(), "nothing of a sold stack stays on the stall");
            assertNull(stall.getQuote());
            assertNull(stall.getSellerUuid());
        }
        // What the buyer holds is the item itself, not a look-alike: the flask still has its mixture and charges.
        var boughtFlask = buy(saveAndLoad(dir, list(9, flask(), 1)).getFirst(), 1);
        assertEquals(MIXTURE, AlchemyFlask.ingredients(boughtFlask));
        assertEquals(3, AlchemyFlask.capacity(boughtFlask));
        assertEquals(2, AlchemyFlask.charges(boughtFlask));
        assertEquals(AlchemyFlask.quality(flask()), AlchemyFlask.quality(boughtFlask));
        var boughtBag = Backpack.read(buy(saveAndLoad(dir, list(10, packedBackpack(), 1)).getFirst(), 1));
        assertEquals("Road Pack", boughtBag.displayName());
        assertEquals(2, boughtBag.used());
    }

    @Test void theStallInteractionTakesTheStackOutOfTheSellersSlotAndKeepsItWhole() {
        // The step ShopSpotPurchaseService.handleListOrRemove runs once it knows the player may sell here.
        var prices = ShopPriceCatalog.parseJson("{\"prices\":{\"Tool_Pickaxe_Iron\":18,\"Potion_Health\":{\"gold\":30,\"batchSize\":8}}}");
        var hotbar = new SimpleItemContainer((short) 4);
        hotbar.setItemStackForSlot((short) 0, wornPickaxe(), false);
        hotbar.setItemStackForSlot((short) 1, flask(), false);
        hotbar.setItemStackForSlot((short) 2, blessedPotions(), false);

        // A worn tool is no longer turned away, and nothing about it is lost on the way to the stall.
        var tool = stall(1);
        var listed = ShopSpotPurchaseService.listFromSlot(prices, tool, hotbar, (short) 0, SELLER, "Seller");
        assertTrue(listed.done());
        assertEquals(1, listed.listed());
        assertTrue(ItemStack.isEmpty(hotbar.getItemStack((short) 0)), "it left the seller's hand");
        assertEquals(SELLER, tool.getSellerUuid());
        assertEquals("Seller", tool.getSellerName());
        assertEquals(18, tool.getQuote().getGoldPerBatch(), "the price it went up at is recorded on the listing");
        assertSameStack(wornPickaxe(), buy(tool, 1), "listed from the hotbar");

        // An item with no price cannot be listed, and stays where it was, untouched.
        var unpriced = stall(2);
        var refused = ShopSpotPurchaseService.listFromSlot(prices, unpriced, hotbar, (short) 1, SELLER, "Seller");
        assertFalse(refused.done());
        assertEquals("noPrice", refused.refusal());
        assertSameStack(flask(), hotbar.getItemStack((short) 1).cleanCopy(), "still in the hotbar");
        assertFalse(unpriced.hasStock());
        assertNull(unpriced.getSellerUuid());
        // Once an admin gives it a price (/ah price set), the same call lists it, mixture and all.
        var priced = ShopPriceCatalog.parseJson("{\"prices\":{\"" + AlchemyFlask.ITEM + "\":45}}");
        assertTrue(ShopSpotPurchaseService.listFromSlot(priced, unpriced, hotbar, (short) 1, SELLER, "Seller").done());
        assertEquals(45, unpriced.getQuote().getGoldPerBatch());
        assertSameStack(flask(), buy(unpriced, 1), "listed once priced");

        // Whole lots only: 20 potions in lots of 8 list 16, and the 4 left in hand keep their metadata.
        var potions = stall(3);
        var lots = ShopSpotPurchaseService.listFromSlot(prices, potions, hotbar, (short) 2, SELLER, "Seller");
        assertEquals(16, lots.listed());
        assertEquals(4, lots.kept());
        assertSameStack(blessedPotions().withQuantity(4), hotbar.getItemStack((short) 2).cleanCopy(), "left in the hotbar");
        assertEquals(16, potions.getStock());
        assertSameStack(blessedPotions().withQuantity(8), buy(potions, 8), "one lot");
        // Fewer than one lot is refused with the lot size, and nothing moves.
        var few = ShopSpotPurchaseService.listFromSlot(prices, stall(4), hotbar, (short) 2, SELLER, "Seller");
        assertEquals("wrongBatchSize", few.refusal());
        assertEquals(8, few.lot());
        assertEquals(4, hotbar.getItemStack((short) 2).getQuantity());
        // An empty hand lists nothing and says nothing.
        var empty = ShopSpotPurchaseService.listFromSlot(prices, stall(5), hotbar, (short) 3, SELLER, "Seller");
        assertFalse(empty.done());
        assertNull(empty.refusal());
    }

    @Test void takingAListingBackReturnsTheSameStack(@TempDir Path dir) throws Exception {
        for (var hand : List.of(flask(), wornPickaxe(), appraisedRing(), packedBackpack())) {
            var stall = saveAndLoad(dir, list(1, hand, 1)).getFirst();
            assertSameStack(hand, cancel(stall), "cancelled " + hand.getItemId());
            assertFalse(stall.hasStock());
            assertNull(stall.getStackData());
        }
        // The same call hands a listing back when its stall is removed (ShopSpotListingReturns.deliver).
        var registry = new ShopSpotRegistry(null);
        var orphan = list(3, wornPickaxe(), 1);
        registry.put(orphan);
        assertTrue(registry.removeKeepingListing(orphan));
        var file = GSON.fromJson(GSON.toJson(ShopSpotWorldFile.fromRegistry(registry)), ShopSpotWorldFile.class);
        var held = ShopSpotWorldFile.toHeldReturns(file).getFirst();
        assertSameStack(wornPickaxe(), ShopSpotJewelrySupport.buildListingStack(held.getItemId(), held.getStock(), held), "held return");
    }

    @Test void aStackOfManyKeepsItsMetadataThroughPartialSales(@TempDir Path dir) throws Exception {
        var hand = blessedPotions();
        var stall = saveAndLoad(dir, list(1, hand, 20)).getFirst();
        assertEquals(20, stall.getStock());
        assertSameStack(hand.withQuantity(3), buy(stall, 3), "three of twenty");
        assertEquals(17, stall.getStock());
        assertEquals(SELLER, stall.getSellerUuid(), "still listed");
        stall = saveAndLoad(dir, stall).getFirst();
        assertSameStack(hand.withQuantity(5), buy(stall, 5), "five more after a restart");
        assertSameStack(hand.withQuantity(12), cancel(stall), "the rest, taken back");
        // A seller who lists part of a stack keeps the rest in hand; the stall holds only what was listed.
        var part = list(2, hand, 10);
        assertEquals(10, part.getStock());
        assertSameStack(hand.withQuantity(10), cancel(part), "ten of the twenty in hand");
    }

    @Test void stacksTheEngineWouldNotStackAreAlwaysSeparateListings(@TempDir Path dir) throws Exception {
        var healing = flask();
        var other = AlchemyFlask.fill(AlchemyFlask.upgrade(AlchemyFlask.upgrade(new ItemStack(AlchemyFlask.ITEM, 1))), List.of("Plant_Crop_Mana3"));
        var worn = wornPickaxe();
        var fresh = new ItemStack("Tool_Pickaxe_Iron", 1, 150, 150, 2, ItemStackData.metadata(worn));
        assertFalse(healing.isStackableWith(other), "different mixtures");
        assertFalse(worn.isStackableWith(fresh), "different wear");

        // One stall holds one stack, so four stacks are four listings. Nothing merges two stalls.
        var loaded = saveAndLoad(dir, list(1, healing, 1), list(2, other, 1), list(3, worn, 1), list(4, fresh, 1));
        assertEquals(4, loaded.stream().map(ShopSpotRecord::getStackData).distinct().count(), "each listing keeps its own stack");
        assertNotEquals(
            ShopSpotJewelrySupport.listingDisplaySignature(AlchemyFlask.ITEM, loaded.get(0)),
            ShopSpotJewelrySupport.listingDisplaySignature(AlchemyFlask.ITEM, loaded.get(1)),
            "a flask with other contents gets its own display"
        );
        var bag = new SimpleItemContainer((short) 8);
        for (var stall : loaded) assertTrue(bag.addItemStack(buy(stall, 1)).succeeded());
        // The engine's own container keeps them apart too: four slots, nothing folded into anything else.
        assertSameStack(healing, bag.getItemStack((short) 0), "slot 0");
        assertSameStack(other, bag.getItemStack((short) 1), "slot 1");
        assertSameStack(worn, bag.getItemStack((short) 2), "slot 2");
        assertSameStack(fresh, bag.getItemStack((short) 3), "slot 3");

        // Stacks the engine does stack are the same goods: two sales from one listing fold into one slot.
        var potions = saveAndLoad(dir, list(5, blessedPotions(), 20)).getFirst();
        var purse = new SimpleItemContainer((short) 2);
        assertTrue(purse.addItemStack(buy(potions, 4)).succeeded());
        assertTrue(purse.addItemStack(buy(potions, 6)).succeeded());
        assertSameStack(blessedPotions().withQuantity(10), purse.getItemStack((short) 0), "two sales of one listing");
        assertTrue(ItemStack.isEmpty(purse.getItemStack((short) 1)));

        // An occupied stall is never topped up: the listing call replaces, and the interaction refuses before it.
        var stall = list(6, healing, 1);
        assertTrue(stall.hasStock(), "handleListOrRemove refuses to list onto a stall that has stock");
    }

    @Test void listingsSavedBeforeStacksWereKeptLoadAsPlainItemsAndLoseNothing(@TempDir Path dir) throws Exception {
        String old = """
            {"spots":[
              {"spotId":"11111111-1111-1111-1111-111111111111","worldName":"default","blockX":1,"blockY":64,"blockZ":0,
               "townId":"%s","plotId":"%s","playerControlled":true,"lootTableId":"","itemId":"Wood_Oak_Planks","stock":40,
               "stockEpochDay":-9223372036854775808,"sellerUuid":"%s","sellerName":"Seller"},
              {"spotId":"22222222-2222-2222-2222-222222222222","worldName":"default","blockX":2,"blockY":64,"blockZ":0,
               "townId":"%s","plotId":"%s","playerControlled":true,"lootTableId":"","itemId":"Aetherhaven_Ring_Gold_Ruby","stock":1,
               "stockEpochDay":0,"jewelryMetaJson":"{\\"rarity\\": \\"RARE\\", \\"appraised\\": true, \\"traits\\": []}","sellerUuid":"%s"}
            ]}""".formatted(TOWN, PLOT, SELLER, TOWN, PLOT, SELLER);
        Path file = dir.resolve("shop_spots.json");
        Files.writeString(file, old);
        var loaded = ShopSpotWorldFile.toRecords(ShopSpotWorldFile.readOrEmpty(file));
        assertEquals(2, loaded.size());
        var planks = loaded.get(0);
        var ring = loaded.get(1);
        assertEquals("Wood_Oak_Planks", planks.getItemId());
        assertEquals(40, planks.getStock());
        assertEquals(SELLER, planks.getSellerUuid());
        assertNull(planks.getStackData());
        assertFalse(ShopListing.keepsWholeStack(planks));
        assertNull(planks.getQuote(), "no price was recorded, so the catalog's price applies as before");
        assertEquals(new ItemStack("Wood_Oak_Planks", 15), buy(planks, 15), "a plain item, as it always was");
        assertEquals(25, planks.getStock());
        // A ring from an old save still has the stats it was listed with. (Turning them back into a stack needs the
        // running game's text tables; `ah test shopmeta` buys such a ring on a real server.)
        assertEquals("{\"rarity\": \"RARE\", \"appraised\": true, \"traits\": []}", ring.getJewelryMetaJson());
        assertEquals("RARE", ShopSpotJewelrySupport.listingRarity(ring).wireName());
        assertNull(ring.getStackData());

        // Saving an old listing again adds nothing to it: the file keeps its old shape for plain goods.
        var registry = new ShopSpotRegistry(null);
        registry.put(planks);
        var row = JsonParser.parseString(GSON.toJson(ShopSpotWorldFile.fromRegistry(registry))).getAsJsonObject().getAsJsonArray("spots").get(0).getAsJsonObject();
        assertFalse(row.has("stack"));
        assertFalse(row.has("quoteGold"));
        assertFalse(row.has("quoteBatch"));
        assertEquals(25, row.get("stock").getAsInt());
    }

    @Test void aKeptStackNeverOutlivesItsListingOrPassesToAnotherItem() {
        var stall = list(1, flask(), 1);
        assertNotNull(stall.getStackData());
        // The stall is emptied and something else is rolled or listed there.
        stall.setItemId("Food_Bread");
        stall.setStock(5);
        assertNull(stall.getStackData(), "bread must not come out as a flask");
        assertNull(stall.getQuote());
        assertEquals(new ItemStack("Food_Bread", 2), buy(stall, 2));

        // A stack that names another item (a damaged or hand edited file) is not this listing's.
        var odd = stall(2);
        odd.setItemId("Food_Bread");
        odd.setStock(3);
        odd.setStackData(ShopListing.encode(wornPickaxe()));
        assertFalse(ShopListing.keepsWholeStack(odd));
        assertEquals(new ItemStack("Food_Bread", 3), buy(odd, 3));

        // Unreadable text is not an exception and not a lost listing.
        var broken = stall(3);
        broken.setItemId("Food_Bread");
        broken.setStock(1);
        broken.setStackData("{not a stack");
        assertEquals(new ItemStack("Food_Bread", 1), buy(broken, 1));
        assertNull(ShopListing.decode(""));
        assertNull(ShopListing.encode(null));
    }

    @Test void theStallKeepsItsOwnCopySoNothingDoneToAHandedOutStackChangesTheListing() {
        var hand = wornPickaxe();
        var stall = list(1, hand, 1);
        // The floating display marks its stack; that mark must never reach a buyer.
        var display = ShopSpotJewelrySupport.buildDisplayStack(stall.getItemId(), stall);
        assertTrue(display.getOverrideDroppedItemAnimation());
        assertEquals(1, display.getQuantity());
        var first = ShopSpotJewelrySupport.buildListingStack(stall.getItemId(), 1, stall);
        var second = ShopSpotJewelrySupport.buildListingStack(stall.getItemId(), 1, stall);
        assertNotSame(first, second);
        assertFalse(first.getOverrideDroppedItemAnimation());
        assertSameStack(hand, first, "after a display was made");
        // Moving a building copies the row; the copy carries the stack and the price.
        var moved = ShopSpotPlotRelocation.copyForRelocation(stall);
        assertEquals(stall.getStackData(), moved.getStackData());
        assertEquals(40, moved.getQuote().getGoldPerBatch());
        assertSameStack(hand, cancel(moved), "after a move");
    }

    @Test void aBuyerIsToldWhatTheItemsOwnTooltipSays() {
        // A flask: its own name, and its charges and effects.
        var flaskStall = list(1, flask(), 1);
        var flaskDisplay = flask().getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC);
        assertEquals(flaskDisplay.getName().getFormattedMessage(), ShopListingText.name(flaskStall).getFormattedMessage());
        String flaskText = MessageUtil.formatMessageToPlainString(ShopListingText.details(flaskStall).getFormattedMessage());
        assertTrue(flaskText.contains("Charges: 2 / 3"), flaskText);
        assertEquals(
            MessageUtil.formatMessageToPlainString(flaskDisplay.getDescription().getFormattedMessage()),
            flaskText,
            "exactly the flask's tooltip, with no second durability line for its charges"
        );

        // A worn tool: how much is left of it.
        var wear = ShopListingText.details(list(2, wornPickaxe(), 1));
        assertEquals("aetherhaven_shop.aetherhaven.shop.listing.durability", wear.getMessageId());
        assertEquals(new StringParamValue("38"), wear.getFormattedMessage().params.get("left"));
        assertEquals(new StringParamValue("150"), wear.getFormattedMessage().params.get("full"));

        // A named item: its name, its own description, and no wear line while it is whole.
        var sword = list(3, namedSword(), 1);
        assertEquals("Dawnbreaker", ShopListingText.name(sword).getRawText());
        assertEquals("Forged for the founder.", ShopListingText.details(sword).getRawText());

        // An appraised ring: the same message its inventory tooltip is built from.
        var ring = ShopListingText.details(list(4, appraisedRing(), 1));
        assertEquals(
            com.hexvane.aetherhaven.jewelry.JewelryTooltipMessages.forStack(appraisedRing()).getFormattedMessage(),
            ring.getFormattedMessage()
        );

        // An ordinary item in mint condition has nothing more to say than its name.
        var planks = list(5, new ItemStack("Wood_Oak_Planks", 40), 40);
        assertNull(ShopListingText.details(planks));
        assertFalse(ShopListingText.isWorn(new ItemStack("Wood_Oak_Planks", 40)));
        assertFalse(ShopListingText.isWorn(new ItemStack("Tool_Pickaxe_Iron", 1, 150, 150, 0, null)));
        assertTrue(ShopListingText.isWorn(wornPickaxe()));
    }

    @Test void aListingKeepsThePriceItWentUpAt(@TempDir Path dir) throws Exception {
        var before = ShopPriceCatalog.parseJson("{\"prices\":{\"Tool_Pickaxe_Iron\":18,\"Food_Bread\":{\"gold\":5,\"batchSize\":5}}}");
        var after = ShopPriceCatalog.parseJson("{\"prices\":{\"Tool_Pickaxe_Iron\":60,\"Food_Bread\":{\"gold\":9,\"batchSize\":10}}}");
        var posted = stall(1);
        ShopListing.list(posted, wornPickaxe(), 1, before.getEntry("Tool_Pickaxe_Iron"));
        posted = saveAndLoad(dir, posted).getFirst();
        assertEquals(18, ShopSpotPricing.entry(after, posted).getGoldPerBatch(), "the price belongs to the listing");
        var listedLater = stall(2);
        ShopListing.list(listedLater, wornPickaxe(), 1, after.getEntry("Tool_Pickaxe_Iron"));
        assertEquals(60, ShopSpotPricing.entry(after, listedLater).getGoldPerBatch());

        // A lot size that changes later cannot strand items that were listed in the old lot size.
        var bread = stall(3);
        ShopListing.list(bread, new ItemStack("Food_Bread", 15), 15, before.getEntry("Food_Bread"));
        var entry = ShopSpotPricing.entry(after, saveAndLoad(dir, bread).getFirst());
        assertEquals(5, entry.getBatchSize());
        assertEquals(3, entry.batchCountFromItemStock(15));

        // Rolled NPC stock and listings from older saves recorded no price and follow the catalog.
        var npc = stall(4);
        npc.setPlayerControlled(false);
        npc.setItemId("Tool_Pickaxe_Iron");
        npc.setStock(2);
        assertEquals(18, ShopSpotPricing.entry(before, npc).getGoldPerBatch());
        assertEquals(60, ShopSpotPricing.entry(after, npc).getGoldPerBatch());
        assertEquals(before.getDefaultGoldPrice(), ShopSpotPricing.entry(before, stall(5)).getGoldPerBatch());
    }
}
