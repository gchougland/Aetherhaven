package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import com.hexvane.aetherhaven.ui.HandMirrorView.Piece;
import com.hexvane.aetherhaven.ui.HandMirrorView.State;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The Hand Mirror: what it reads from real jewelry stacks, which slot it chooses, and the commands it sends.
 *
 * <p>The pictures in {@code tests/hytale/mirror.test.json} are drawn from the commands {@link HandMirrorPage#draw}
 * really sends for each state, built here from real item stacks in a real container. One step is added for the picture
 * tool, which does not resolve translations: every message is written out in English from the en-US language files and
 * sent as plain text to the same element. A message whose key is missing from those files fails the test.</p>
 *
 * <p>After an intended change run the tests once with the environment variable AETHERHAVEN_WRITE_UI_CASES set, run the
 * render cases and look at the new pictures before keeping them.</p>
 */
@Tag("town")
class HandMirrorPageTest {
    private static final Path UI = Path.of("subplugin-assets/Jewelry/Common/UI/Custom/Aetherhaven");
    private static final Path ITEMS = Path.of("subplugin-assets/Jewelry/Server/Item/Items/Aetherhaven");
    private static final Path LANG = Path.of("src/main/resources/Server/Languages/en-US");
    private static final Path CASES = Path.of("tests", "hytale", "mirror.test.json");
    private static final String PAGE_FILE = "subplugin-assets/Jewelry/Common/UI/Custom/Aetherhaven/HandMirror.ui";
    private static final String KEYS = "aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.";

    private static Object previousItemStore;

    // Supply an item asset map so real ItemStacks and containers work without starting a server.
    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, new DefaultAssetMap<>()); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }

    @BeforeAll static void assets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true);
        previousItemStore = field.get(null); field.set(null, new ItemStore.Config().build());
        RUBY_RING = rolled("Aetherhaven_Ring_Gold_Ruby", 1, JewelryRarity.RARE, true, "Health", 12, "Stamina", 2.4, "Ammo", 3);
        SAPPHIRE_NECKLACE = rolled("Aetherhaven_Necklace_Silver_Sapphire", 1, JewelryRarity.UNCOMMON, false, "Mana", 9, "Oxygen", 30);
        EMERALD_RING = rolled("Aetherhaven_Ring_Silver_Emerald", 1, JewelryRarity.LEGENDARY, true, "Health", 18, "Mana", 14, "Oxygen", 40);
        VOID_RING = rolled("Aetherhaven_Ring_Gold_Voidstone", 1, JewelryRarity.MYTHIC, false, "Mana", 11, "SignatureEnergy", -6, "Ammo", 4);
        ZEPHYR_RING = rolled("Aetherhaven_Ring_Silver_Zephyr", 1, JewelryRarity.COMMON, true, "Stamina", 0.8);
        VOID_NECKLACE = rolled("Aetherhaven_Necklace_Silver_Voidstone", 1, JewelryRarity.MYTHIC, true, "Mana", 16, "SignatureEnergy", -8, "Ammo", 6);
        DIAMOND_NECKLACE = rolled("Aetherhaven_Necklace_Gold_Diamond", 1, JewelryRarity.RARE, true, "Health", 20, "Stamina", 3.5, "Mana", 15);
    }

    @AfterAll static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true); field.set(null, previousItemStore);
    }

    // --- real stacks ---------------------------------------------------------------------------------------------------

    /** A rolled gem piece as the game stores it: rarity, appraisal and affinities in the stack's own metadata. */
    private static ItemStack rolled(String id, int quantity, JewelryRarity rarity, boolean appraised, Object... statsAndAmounts) {
        var traits = new BsonArray();
        for (int i = 0; i < statsAndAmounts.length; i += 2) {
            traits.add(new BsonDocument()
                .append("i", new BsonInt32(i / 2))
                .append("stat", new BsonString((String) statsAndAmounts[i]))
                .append("calc", new BsonString("ADDITIVE"))
                .append("amt", new BsonDouble(((Number) statsAndAmounts[i + 1]).doubleValue())));
        }
        var root = new BsonDocument()
            .append("rarity", new BsonString(rarity.wireName()))
            .append("appraised", BsonBoolean.valueOf(appraised))
            .append("traits", traits);
        return new ItemStack(id, quantity).withMetadata(JewelryMetadata.BSON_KEY, root);
    }

    private static ItemStack plain(String id, int quantity) {
        return new ItemStack(id, quantity);
    }

    private static SimpleItemContainer bag(Object... slotsAndStacks) {
        var bag = new SimpleItemContainer((short) 45);
        for (int i = 0; i < slotsAndStacks.length; i += 2) {
            bag.setItemStackForSlot(((Number) slotsAndStacks[i]).shortValue(), (ItemStack) slotsAndStacks[i + 1], false);
        }
        return bag;
    }

    private static final Map<String, String> NAME_KEYS = new HashMap<>();

    /** The translation key the item's own asset file declares, which is what the game hands the page. */
    private static String nameKey(String itemId) {
        return NAME_KEYS.computeIfAbsent(itemId, id -> {
            try {
                var json = JsonParser.parseString(Files.readString(ITEMS.resolve(id + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
                return json.getAsJsonObject("TranslationProperties").get("Name").getAsString();
            } catch (IOException e) {
                throw new AssertionError("No item file for " + id, e);
            }
        });
    }

    private static HandMirrorView view(ItemStack ring1, ItemStack ring2, ItemStack necklace, SimpleItemContainer bag) {
        return HandMirrorView.of(new ItemStack[] {ring1, ring2, necklace}, bag, HandMirrorPageTest::nameKey);
    }

    // Made once the item map above is in place: a stack looks its item up when it is created.
    private static ItemStack RUBY_RING, SAPPHIRE_NECKLACE, EMERALD_RING, VOID_RING, ZEPHYR_RING, VOID_NECKLACE, DIAMOND_NECKLACE;

    private static SimpleItemContainer typicalBag() {
        return bag(2, EMERALD_RING, 5, plain("Aetherhaven_Ring_Glow", 1), 9, VOID_RING, 14, plain("Aetherhaven_Ring_Silver_Topaz", 1),
            20, DIAMOND_NECKLACE, 21, plain("Aetherhaven_Necklace_Gold_Topaz", 1));
    }

    // --- what the page reads -------------------------------------------------------------------------------------------

    @Test void aPieceSaysWhatItsStackHolds() {
        Piece ruby = Piece.of(RUBY_RING, (short) 4, HandMirrorPageTest::nameKey);
        assertNotNull(ruby);
        assertEquals(State.APPRAISED, ruby.state());
        assertTrue(ruby.ring());
        assertEquals(JewelryRarity.RARE, ruby.rarity());
        assertEquals("aetherhaven_jewelry_geode.items.Aetherhaven_Ring_Gold_Ruby.name", ruby.nameKey());
        assertEquals(List.of("Health", "Stamina", "Ammo"), ruby.traits().stream().map(HandMirrorView.Trait::statId).toList());
        assertEquals(List.of(1, 2, 3), ruby.traits().stream().map(HandMirrorView.Trait::facet).toList(), "Facets count from 1");
        assertEquals(4, ruby.bagSlot());

        Piece hidden = Piece.of(SAPPHIRE_NECKLACE, (short) -1, HandMirrorPageTest::nameKey);
        assertEquals(State.HIDDEN, hidden.state());
        assertFalse(hidden.ring());
        assertEquals(JewelryRarity.UNCOMMON, hidden.rarity(), "Rarity shows before appraisal, as it always did");
        assertEquals(2, hidden.hidden());
        assertTrue(hidden.traits().isEmpty(), "An unappraised piece never names its affinities");

        Piece fresh = Piece.of(plain("Aetherhaven_Ring_Silver_Topaz", 1), (short) 0, HandMirrorPageTest::nameKey);
        assertEquals(State.UNATTUNED, fresh.state());
        assertNull(fresh.rarity(), "A piece nobody has rolled is not shown with a made up rarity");

        Piece glow = Piece.of(plain("Aetherhaven_Ring_Large_Glow", 3), (short) 0, HandMirrorPageTest::nameKey);
        assertEquals(State.LIGHT, glow.state());
        assertEquals(3, glow.quantity());
        assertNull(glow.rarity());

        assertNull(Piece.of(plain("Aetherhaven_Hand_Mirror", 1), (short) 0, HandMirrorPageTest::nameKey), "Only jewelry is listed");
        assertNull(Piece.of(null, (short) 0, HandMirrorPageTest::nameKey));
        assertNull(Piece.of(ItemStack.EMPTY, (short) 0, HandMirrorPageTest::nameKey));
    }

    @Test void eachSlotListsOnlyThePiecesThatFitItInBagOrder() {
        var view = view(RUBY_RING, null, SAPPHIRE_NECKLACE, typicalBag());
        assertEquals(List.of((short) 2, (short) 5, (short) 9, (short) 14), view.fitting(0).stream().map(Piece::bagSlot).toList());
        assertEquals(view.fitting(0), view.fitting(1), "Both ring slots take the same rings");
        assertEquals(List.of((short) 20, (short) 21), view.fitting(2).stream().map(Piece::bagSlot).toList());
        assertTrue(view.fitting(2).stream().noneMatch(Piece::ring));
        assertNotNull(view.inBag(1, (short) 9));
        assertNull(view.inBag(2, (short) 9), "A ring is not offered for the necklace slot");
        assertNull(view.inBag(0, (short) 20), "A necklace is not offered for a ring slot");
        assertNull(view.inBag(0, (short) 3), "An empty bag slot offers nothing");
        assertTrue(view.fitting(3).isEmpty());
        assertNull(view.worn(1));
        assertEquals("Aetherhaven_Ring_Gold_Ruby", view.worn(0).itemId());

        // A stack in the wrong kind of slot (never written by the game) is not shown as worn.
        var wrong = view(SAPPHIRE_NECKLACE, null, RUBY_RING, bag());
        assertNull(wrong.worn(0));
        assertNull(wrong.worn(2));
    }

    @Test void theMirrorOpensOnTheFirstEmptySlotTheBagCanFill() {
        assertEquals(0, view(null, null, null, typicalBag()).firstSelection());
        assertEquals(1, view(RUBY_RING, null, null, typicalBag()).firstSelection());
        assertEquals(2, view(RUBY_RING, EMERALD_RING, null, typicalBag()).firstSelection());
        assertEquals(2, view(null, null, null, bag(20, DIAMOND_NECKLACE)).firstSelection(), "No rings to wear: start at the necklace");
        assertEquals(0, view(null, null, null, bag()).firstSelection(), "Nothing to wear at all: the first empty slot");
        assertEquals(1, view(RUBY_RING, null, SAPPHIRE_NECKLACE, bag()).firstSelection());
        assertEquals(0, view(RUBY_RING, EMERALD_RING, SAPPHIRE_NECKLACE, typicalBag()).firstSelection(), "Everything worn: Ring I");
    }

    @Test void fillingAnEmptyRingSlotMovesOnAndASwapStaysPut() {
        var oneOn = view(RUBY_RING, null, null, typicalBag());
        assertEquals(1, oneOn.afterWear(0, true), "Ring I was empty and is now filled: Ring II is next");
        assertEquals(0, oneOn.afterWear(0, false), "A swap in Ring I stays on Ring I");
        var otherOn = view(null, RUBY_RING, null, typicalBag());
        assertEquals(0, otherOn.afterWear(1, true));
        var bothOn = view(RUBY_RING, EMERALD_RING, null, typicalBag());
        assertEquals(1, bothOn.afterWear(1, true), "Both rings on: stay");
        var lastRing = view(RUBY_RING, null, null, bag(20, DIAMOND_NECKLACE));
        assertEquals(0, lastRing.afterWear(0, true), "No ring left in the bag: nothing to move on to");
        var necklaceOn = view(null, null, SAPPHIRE_NECKLACE, typicalBag());
        assertEquals(2, necklaceOn.afterWear(2, true), "The necklace has no second slot");
        assertEquals(0, necklaceOn.afterWear(7, true), "A slot that does not exist falls back to the opening choice");
    }

    @Test void aLongBagIsCutAtTheOldLimit() {
        var bag = new SimpleItemContainer((short) 60);
        for (short slot = 0; slot < 60; slot++) {
            bag.setItemStackForSlot(slot, plain("Aetherhaven_Ring_Glow", 1), false);
        }
        var view = view(null, null, null, bag);
        assertEquals(60, view.fitting(0).size());
        assertEquals(HandMirrorView.MAX_CARDS, view.cards(0).size());
    }

    // --- what the page sends -------------------------------------------------------------------------------------------

    private record Sent(List<CustomUICommand> commands, List<CustomUIEventBinding> events, int cards) {}

    private static Sent draw(HandMirrorView view, boolean first, int selected, int revision, int cardsShown, Message note, boolean error) {
        var c = new UICommandBuilder();
        var e = new UIEventBuilder();
        int cards = HandMirrorPage.draw(c, e, view, first, selected, revision, cardsShown, note, error);
        return new Sent(Arrays.asList(c.getCommands()), Arrays.asList(e.getEvents()), cards);
    }

    private static Sent open(HandMirrorView view, int selected) {
        return draw(view, true, selected, 0, -1, null, false);
    }

    private static String data(Sent sent, String selector) {
        return sent.commands().stream().filter(c -> selector.equals(c.selector) && c.type == CustomUICommandType.Set)
            .reduce((a, b) -> b).map(c -> c.data).orElse(null);
    }

    private static String text(Sent sent, String selector) {
        String data = data(sent, selector);
        return data == null ? null : english(JsonParser.parseString(data).getAsJsonObject().get("0"));
    }

    private static CustomUIEventBinding event(Sent sent, String selector) {
        return sent.events().stream().filter(e -> selector.equals(e.selector)).reduce((a, b) -> b).orElse(null);
    }

    @Test void wornSlotsShowThePieceItsRarityAndWhatItGives() {
        var sent = open(view(RUBY_RING, null, SAPPHIRE_NECKLACE, typicalBag()), 1);
        assertEquals(3, sent.commands().stream().filter(c -> c.type == CustomUICommandType.Append && "#WornSlots".equals(c.selector)).count());
        assertEquals("Jewelry", text(sent, "#MirrorTitle.TextSpans"));
        assertEquals("Worn 2 / 3", text(sent, "#WornHeading.TextSpans"));

        assertEquals("Ring I", text(sent, "#WornSlots[0] #SlotTitle.TextSpans"));
        assertEquals("Gold Ring (Ruby)", text(sent, "#WornSlots[0] #SlotName.TextSpans"));
        assertEquals("Rare", text(sent, "#WornSlots[0] #SlotRarity.TextSpans"));
        assertEquals("{\"0\": \"#5E9BFF\"}", data(sent, "#WornSlots[0] #SlotRarity.Style.TextColor"));
        assertTrue(data(sent, "#WornSlots[0] #SlotRarityBar.Background").contains("#5E9BFF"));
        assertEquals("{\"0\": \"Aetherhaven_Ring_Gold_Ruby\"}", data(sent, "#WornSlots[0] #SlotIcon.ItemId"));
        assertEquals("+12 Max health  · Ruby (facet 1)\n+2.4 Max stamina  · Ruby (facet 2)\n+3 Max ammo  · Ruby (facet 3)",
            text(sent, "#WornSlots[0] #SlotDetail.TextSpans"));
        assertEquals("{\"0\": false}", data(sent, "#WornSlots[0] #SlotSelected.Visible"));

        assertEquals("Ring II", text(sent, "#WornSlots[1] #SlotTitle.TextSpans"));
        assertEquals("Empty", text(sent, "#WornSlots[1] #SlotName.TextSpans"));
        assertEquals("4 rings in your bag fit here.", text(sent, "#WornSlots[1] #SlotDetail.TextSpans"));
        assertEquals("{\"0\": false}", data(sent, "#WornSlots[1] #SlotIcon.Visible"));
        assertNull(data(sent, "#WornSlots[1] #SlotIcon.ItemId"), "An empty slot is sent no item");
        assertEquals("{\"0\": false}", data(sent, "#WornSlots[1] #SlotRarity.Visible"));
        assertEquals("{\"0\": true}", data(sent, "#WornSlots[1] #SlotSelected.Visible"));

        assertEquals("Necklace", text(sent, "#WornSlots[2] #SlotTitle.TextSpans"));
        assertEquals("Uncommon", text(sent, "#WornSlots[2] #SlotRarity.TextSpans"));
        assertEquals("2 affinities are hidden until this piece is appraised. They still work.", text(sent, "#WornSlots[2] #SlotDetail.TextSpans"));
        assertFalse(data(sent, "#WornSlots[2] #SlotDetail.TextSpans").contains("jewelry.stat."), "Hidden affinities are never sent to the client");

        assertEquals("1 ring in your bag fits here.", text(open(view(null, null, null, bag(3, ZEPHYR_RING)), 0), "#WornSlots[1] #SlotDetail.TextSpans"));
        assertEquals("No necklaces in your bag.", text(open(view(null, null, null, bag(3, ZEPHYR_RING)), 0), "#WornSlots[2] #SlotDetail.TextSpans"));
    }

    @Test void theBagListsWhatFitsTheChosenSlotAndSaysWhatChoosingWillDo() {
        var view = view(RUBY_RING, null, SAPPHIRE_NECKLACE, typicalBag());
        var emptySlot = open(view, 1);
        assertEquals(4, emptySlot.cards());
        assertEquals("Rings in your bag", text(emptySlot, "#BagHeading.TextSpans"));
        assertEquals("Pieces: 4", text(emptySlot, "#BagCount.TextSpans"));
        assertEquals("Choose a ring to wear it as Ring II.", text(emptySlot, "#BagHint.TextSpans"));
        assertEquals("{\"0\": false}", data(emptySlot, "#BagEmpty.Visible"));
        assertEquals("Silver Ring (Emerald)", text(emptySlot, "#BagRows[0][0] #PieceName.TextSpans"));
        assertEquals("Legendary", text(emptySlot, "#BagRows[0][0] #PieceRarity.TextSpans"));
        assertEquals("+18 Max health\n+14 Max mana\n+40 Max oxygen", text(emptySlot, "#BagRows[0][0] #PieceDetail.TextSpans"));
        assertEquals("Glow Ring", text(emptySlot, "#BagRows[0][1] #PieceName.TextSpans"));
        assertEquals("Sheds light while worn.", text(emptySlot, "#BagRows[0][1] #PieceDetail.TextSpans"));
        assertEquals("{\"0\": false}", data(emptySlot, "#BagRows[0][1] #PieceRarity.Visible"));
        assertEquals("3 affinities are hidden until this piece is appraised. They still work.", text(emptySlot, "#BagRows[1][0] #PieceDetail.TextSpans"));
        assertEquals("Not attuned yet. Wear or appraise to set affinities.", text(emptySlot, "#BagRows[1][1] #PieceDetail.TextSpans"));
        assertEquals("Wear Silver Ring (Emerald) as Ring II.", text(emptySlot, "#BagRows[0][0].TooltipTextSpans"));
        assertEquals("{\"0\": true}", data(emptySlot, "#TakeOff.Disabled"), "Nothing to take off an empty slot");
        assertEquals("Worn jewelry leaves your bag. Taking a piece off needs a free slot in your bag.", text(emptySlot, "#MirrorNote.TextSpans"));

        var wornSlot = open(view, 0);
        assertEquals("Choose a ring to wear it as Ring I in place of Gold Ring (Ruby).", text(wornSlot, "#BagHint.TextSpans"));
        assertEquals("Wear Silver Ring (Emerald) as Ring I. Gold Ring (Ruby) goes back to your bag.", text(wornSlot, "#BagRows[0][0].TooltipTextSpans"));
        assertEquals("{\"0\": false}", data(wornSlot, "#TakeOff.Disabled"));
        assertEquals("Ring I: Gold Ring (Ruby). Choose this slot to swap it or take it off.", text(wornSlot, "#WornSlots[0].TooltipTextSpans"));
        assertEquals("Ring II is empty. Choose this slot, then pick a piece from your bag.", text(wornSlot, "#WornSlots[1].TooltipTextSpans"));

        var necklace = open(view, 2);
        assertEquals(2, necklace.cards());
        assertEquals("Necklaces in your bag", text(necklace, "#BagHeading.TextSpans"));
        assertEquals("Choose a necklace to wear it in place of Silver Necklace (Sapphire).", text(necklace, "#BagHint.TextSpans"));
        assertEquals("Gold Necklace (Diamond)", text(necklace, "#BagRows[0][0] #PieceName.TextSpans"));

        var nothing = open(view(null, null, null, bag()), 0);
        assertEquals(0, nothing.cards());
        assertEquals("{\"0\": true}", data(nothing, "#BagEmpty.Visible"));
        assertEquals("No rings in your bag. Rings you carry in your hotbar or bag show up here.", text(nothing, "#BagEmpty.TextSpans"));
        assertEquals("", text(nothing, "#BagHint.TextSpans"));
        assertEquals("{\"0\": false}", data(nothing, "#BagCount.Visible"));
        assertEquals("No necklaces in your bag. Necklaces you carry in your hotbar or bag show up here.", text(open(view(null, null, null, bag()), 2), "#BagEmpty.TextSpans"));

        var stacked = open(view(null, null, null, bag(0, plain("Aetherhaven_Ring_Glow", 3), 1, ZEPHYR_RING)), 0);
        assertEquals("{\"0\": true}", data(stacked, "#BagRows[0][0] #PieceCount.Visible"));
        assertEquals("x3", text(stacked, "#BagRows[0][0] #PieceCount.TextSpans"));
        assertEquals("{\"0\": false}", data(stacked, "#BagRows[0][1] #PieceCount.Visible"));
        assertEquals("+0.8 Max stamina", text(stacked, "#BagRows[0][1] #PieceDetail.TextSpans"));
    }

    @Test void everyPressNamesItsActionAndThePictureItWasMadeOn() {
        var sent = draw(view(RUBY_RING, null, SAPPHIRE_NECKLACE, typicalBag()), false, 1, 7, 4, null, false);
        for (int slot = 0; slot < 3; slot++) {
            var press = JsonParser.parseString(event(sent, "#WornSlots[" + slot + "]").data).getAsJsonObject();
            assertEquals("Slot", press.get("Action").getAsString());
            assertEquals(String.valueOf(slot), press.get("Target").getAsString());
            assertEquals("7", press.get("Rev").getAsString());
        }
        var wear = JsonParser.parseString(event(sent, "#BagRows[1][0]").data).getAsJsonObject();
        assertEquals("Wear", wear.get("Action").getAsString());
        assertEquals("9", wear.get("InvSlot").getAsString(), "The card carries the bag slot of its own stack");
        assertEquals("Aetherhaven_Ring_Gold_Voidstone", wear.get("Item").getAsString(), "and names the piece, so a bag that changed is noticed");
        assertEquals("7", wear.get("Rev").getAsString());
        assertEquals("TakeOff", JsonParser.parseString(event(sent, "#TakeOff").data).getAsJsonObject().get("Action").getAsString());
        assertEquals("Done", JsonParser.parseString(event(sent, "#Done").data).getAsJsonObject().get("Action").getAsString());
        assertEquals(3 + 4 + 2, sent.events().size(), "Three slots, four cards, two buttons");
        // The page data must be able to read every key a press sends.
        var keys = new HashSet<String>();
        for (var press : sent.events()) keys.addAll(JsonParser.parseString(press.data).getAsJsonObject().keySet());
        assertEquals(Set.of("Action", "Target", "InvSlot", "Item", "Rev"), keys);
        for (String key : keys) {
            assertTrue(HandMirrorPage.PageData.CODEC.getEntries().containsKey(key), "The page cannot read " + key);
        }
    }

    @Test void cardsAreOnlyRebuiltWhenTheirNumberChanges() {
        var view = view(RUBY_RING, null, SAPPHIRE_NECKLACE, typicalBag());
        var same = draw(view, false, 0, 1, 4, null, false);
        assertTrue(same.commands().stream().noneMatch(c -> c.type == CustomUICommandType.Clear || c.type == CustomUICommandType.Append),
            "Choosing the other ring slot keeps the cards and the scroll place");
        assertEquals("Silver Ring (Emerald)", text(same, "#BagRows[0][0] #PieceName.TextSpans"), "Their text is still sent");
        var other = draw(view, false, 2, 1, 4, null, false);
        assertEquals(1, other.commands().stream().filter(c -> c.type == CustomUICommandType.Clear && "#BagRows".equals(c.selector)).count());
        assertEquals(1, other.commands().stream().filter(c -> c.type == CustomUICommandType.Append && "#BagRows".equals(c.selector)).count(), "Two cards, one row");
        assertEquals(2, other.commands().stream().filter(c -> c.type == CustomUICommandType.Append && "#BagRows[0]".equals(c.selector)).count());
        var five = open(view(null, null, null, bag(0, ZEPHYR_RING, 1, ZEPHYR_RING, 2, ZEPHYR_RING, 3, ZEPHYR_RING, 4, ZEPHYR_RING)), 0);
        assertEquals(3, five.commands().stream().filter(c -> c.type == CustomUICommandType.Append && "#BagRows".equals(c.selector)).count(), "Five cards, three rows");
        assertNotNull(data(five, "#BagRows[2][0] #PieceName.TextSpans"));
        assertNull(data(five, "#BagRows[2][1] #PieceName.TextSpans"));
    }

    @Test void aRefusalIsShownOnThePageInItsOwnColour() {
        var view = view(RUBY_RING, null, SAPPHIRE_NECKLACE, typicalBag());
        var refused = draw(view, false, 0, 2, 4, HandMirrorLoadoutPanel.t("inventoryFull"), true);
        assertEquals("Not enough room in your inventory to return that item.", text(refused, "#MirrorNote.TextSpans"));
        String warning = data(refused, "#MirrorNote.Style.TextColor");
        var fine = draw(view, false, 0, 2, 4, HandMirrorLoadoutPanel.t("noteWorn").param("name", HandMirrorLoadoutPanel.name(view.worn(0))), false);
        assertEquals("You put on Gold Ring (Ruby).", text(fine, "#MirrorNote.TextSpans"));
        assertFalse(warning.equals(data(fine, "#MirrorNote.Style.TextColor")));
    }

    // --- the documents and the commands agree --------------------------------------------------------------------------

    private static final Pattern ELEMENT = Pattern.compile("([A-Za-z$.@]+)\\s+#([A-Za-z0-9]+)\\s*\\{");
    private static final Pattern ROOT = Pattern.compile("(?m)^([A-Za-z$.@]+)\\s*(?:#[A-Za-z0-9]+\\s*)?\\{");

    /** What the page may set on each kind of element. Every entry is a property that kind of element really has. */
    private static final Map<String, Set<String>> SETTABLE = Map.of(
        "Label", Set.of("TextSpans", "Visible", "Style.TextColor"),
        "Button", Set.of("TooltipTextSpans"),
        "TextButton", Set.of("TextSpans", "Disabled"),
        "Group", Set.of("Visible", "Background"),
        "ItemIcon", Set.of("ItemId", "Visible"));

    private static String kind(String written) {
        return written.endsWith("TextButton") ? "TextButton" : written.startsWith("$") ? "Template" : written;
    }

    private static Map<String, String> ids(String document) throws IOException {
        var ids = new LinkedHashMap<String, String>();
        Matcher found = ELEMENT.matcher(Files.readString(UI.resolve(document), StandardCharsets.UTF_8));
        while (found.find()) {
            assertNull(ids.put(found.group(2), kind(found.group(1))), document + " names #" + found.group(2) + " twice");
        }
        return ids;
    }

    private static String rootKind(String document) throws IOException {
        Matcher found = ROOT.matcher(Files.readString(UI.resolve(document), StandardCharsets.UTF_8));
        assertTrue(found.find(), document);
        return kind(found.group(1));
    }

    @Test void everyIdIsUniqueAcrossThePageAndItsCards() throws IOException {
        var seen = new HashMap<String, String>();
        for (String document : List.of("HandMirror.ui", "HandMirrorSlot.ui", "HandMirrorPieceRow.ui", "HandMirrorPiece.ui")) {
            for (String id : ids(document).keySet()) {
                assertNull(seen.put(id, document), "#" + id + " is in " + document + " and in " + seen.get(id)
                    + ". The client takes the first match in the page, so an id may be used once.");
                assertTrue(id.matches("[A-Za-z0-9]+"));
            }
        }
        // The game's own window and buttons bring these; nothing of the mirror's may share them.
        for (String taken : List.of("Title", "Content", "Icon", "Label")) {
            assertFalse(seen.containsKey(taken), "#" + taken);
        }
    }

    private enum Use { SET, FILL, PRESS }

    /** Checks one selector against the documents: every id exists where it is looked for, and the property is one that element has. */
    private static void check(String selector, Use use, Map<String, String> page, Map<String, String> slot, Map<String, String> piece,
                              String slotRoot, String rowRoot, String pieceRoot) {
        String target = selector;
        String sent = null;
        if (use == Use.SET) {
            int dot = selector.indexOf('.', selector.lastIndexOf('#'));
            assertTrue(dot > 0, selector);
            target = selector.substring(0, dot);
            sent = selector.substring(dot + 1);
        }
        String[] parts = target.split(" ");
        assertTrue(parts.length <= 2, "Rows are addressed through their container index: " + selector);
        Matcher first = Pattern.compile("#([A-Za-z0-9]+)((?:\\[\\d+])*)").matcher(parts[0]);
        assertTrue(first.matches(), selector);
        String id = first.group(1);
        int depth = first.group(2).isEmpty() ? 0 : first.group(2).split("]").length;
        assertTrue(page.containsKey(id), "#" + id + " is not in HandMirror.ui: " + selector);
        String kind;
        Map<String, String> inside;
        if (depth == 0) {
            kind = page.get(id);
            inside = null;
        } else if (id.equals("WornSlots")) {
            assertEquals(1, depth, selector);
            kind = slotRoot;
            inside = slot;
        } else {
            assertEquals("BagRows", id, selector);
            kind = depth == 1 ? rowRoot : pieceRoot;
            inside = depth == 2 ? piece : null;
        }
        if (parts.length == 2) {
            assertNotNull(inside, "No card to look inside: " + selector);
            String child = parts[1].substring(1);
            assertTrue(parts[1].startsWith("#") && inside.containsKey(child), "#" + child + " is not in that card: " + selector);
            kind = inside.get(child);
        }
        switch (use) {
            case SET -> assertTrue(SETTABLE.getOrDefault(kind, Set.of()).contains(sent), "A " + kind + " is sent ." + sent + ": " + selector);
            case FILL -> assertEquals("Group", kind, "Cards are appended to and cleared from groups: " + selector);
            case PRESS -> assertTrue(kind.equals("Button") || kind.equals("TextButton"), "Only buttons are pressed: " + selector);
        }
    }

    @Test void everyCommandAddressesARealElementAndAPropertyThatElementHas() throws IOException {
        var page = ids("HandMirror.ui");
        var slot = ids("HandMirrorSlot.ui");
        var piece = ids("HandMirrorPiece.ui");
        String slotRoot = rootKind("HandMirrorSlot.ui"), rowRoot = rootKind("HandMirrorPieceRow.ui"), pieceRoot = rootKind("HandMirrorPiece.ui");
        assertEquals("Button", slotRoot);
        assertEquals("Group", rowRoot);
        assertEquals("Button", pieceRoot);
        int checked = 0;
        for (var scene : scenes()) {
            var sent = scene.sent();
            for (var command : sent.commands()) {
                if (command.selector == null) {
                    assertEquals(CustomUICommandType.Append, command.type);
                    assertEquals(HandMirrorPage.PAGE, command.text);
                    continue;
                }
                assertFalse(command.selector.contains("_") || command.selector.contains("-"), command.selector);
                assertTrue(command.type == CustomUICommandType.Set || command.type == CustomUICommandType.Append || command.type == CustomUICommandType.Clear);
                check(command.selector, command.type == CustomUICommandType.Set ? Use.SET : Use.FILL, page, slot, piece, slotRoot, rowRoot, pieceRoot);
                if (command.type == CustomUICommandType.Append) {
                    assertTrue(Files.isRegularFile(UI.resolve(command.text.substring("Aetherhaven/".length()))), command.text);
                    assertTrue(command.text.startsWith("Aetherhaven/"));
                }
                checked++;
            }
            for (var press : sent.events()) {
                check(press.selector, Use.PRESS, page, slot, piece, slotRoot, rowRoot, pieceRoot);
                checked++;
            }
        }
        assertTrue(checked > 500, "Only " + checked + " commands were checked");
    }

    @Test void everySentenceOfThePageIsInTheEnglishLanguageFileWithoutDashPauses() throws IOException {
        for (String key : List.of("title", "wornCount", "ring1", "ring2", "necklace", "slotEmpty", "fitRingNone", "fitRingOne", "fitRingMany",
                "fitNecklaceNone", "fitNecklaceOne", "fitNecklaceMany", "bagRings", "bagNecklaces", "bagCount", "chooseRing", "chooseNecklace",
                "swapRing", "swapNecklace", "bagEmptyRings", "bagEmptyNecklaces", "hiddenOne", "hiddenMany", "quantity", "takeOff", "done",
                "noteStart", "noteWorn", "noteSwapped", "noteTookOff", "tipSlotWorn", "tipSlotEmpty", "tipWear", "tipSwap", "traitsUnattuned",
                "traitFrom", "inventoryFull", "cannotEquipThere", "equipFailed")) {
            String text = english().get(KEYS + key);
            assertNotNull(text, "Missing " + key);
            assertFalse(text.isBlank(), key);
            assertFalse(text.contains(" - ") || text.contains("—") || text.contains("–"), key);
        }
    }

    // --- English, for the pictures -------------------------------------------------------------------------------------

    private static Map<String, String> english;

    /** Every en-US line under the key the game gives it: the file's name, a dot, the line's key. */
    private static Map<String, String> english() {
        if (english != null) return english;
        var lines = new HashMap<String, String>();
        try (var files = Files.list(LANG)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".lang")).toList()) {
                String bundle = file.getFileName().toString().replace(".lang", "");
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int equals = line.indexOf('=');
                    if (line.startsWith("#") || equals <= 0) continue;
                    lines.put(bundle + "." + line.substring(0, equals).trim(), line.substring(equals + 1));
                }
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        english = lines;
        return lines;
    }

    /** A sent message as an English reader sees it. Fails when a key is not in the language files. */
    private static String english(JsonElement sent) {
        if (sent == null || sent.isJsonNull()) return "";
        if (sent.isJsonPrimitive()) return sent.getAsString();
        var message = sent.getAsJsonObject();
        var text = new StringBuilder();
        if (message.has("RawText")) text.append(message.get("RawText").getAsString());
        if (message.has("MessageId")) {
            String key = message.get("MessageId").getAsString();
            String line = english().get(key);
            assertNotNull(line, "No English text for " + key);
            if (message.has("Params")) {
                for (var param : message.getAsJsonObject("Params").entrySet()) {
                    var value = param.getValue().getAsJsonPrimitive();
                    String shown = value.isNumber() && value.getAsDouble() == Math.rint(value.getAsDouble()) ? String.valueOf(value.getAsLong()) : value.getAsString();
                    line = line.replace("{" + param.getKey() + "}", shown);
                }
            }
            if (message.has("MessageParams")) {
                for (var param : message.getAsJsonObject("MessageParams").entrySet()) {
                    line = line.replace("{" + param.getKey() + "}", english(param.getValue()));
                }
            }
            assertFalse(line.contains("{"), "A parameter of " + key + " was not sent: " + line);
            text.append(line);
        }
        if (message.has("Children")) {
            for (var child : message.getAsJsonArray("Children")) text.append(english(child));
        }
        return text.toString();
    }

    // --- tests/hytale/mirror.test.json ---------------------------------------------------------------------------------

    private record Scene(String name, String golden, Sent sent) {}

    private static List<Scene> scenes() {
        var typical = view(RUBY_RING, null, SAPPHIRE_NECKLACE, typicalBag());

        var manyRings = new SimpleItemContainer((short) 45);
        String[] metals = {"Silver", "Gold"};
        String[] gems = {"Zephyr", "Topaz", "Emerald", "Diamond", "Sapphire", "Ruby", "Voidstone"};
        JewelryRarity[] rarities = JewelryRarity.values();
        String[][] stats = {{"Stamina", "SignatureEnergy", "Oxygen"}, {"Stamina", "SignatureEnergy", "Ammo"}, {"Health", "Mana", "Oxygen"},
            {"Health", "Stamina", "Mana"}, {"Mana", "Stamina", "Oxygen"}, {"Health", "Stamina", "Ammo"}, {"Mana", "SignatureEnergy", "Ammo"}};
        for (int i = 0; i < 23; i++) {
            int gem = i % gems.length;
            JewelryRarity rarity = rarities[i % rarities.length];
            var amounts = new ArrayList<Object>();
            for (int t = 0; t < rarity.traitCount(); t++) {
                String stat = stats[gem][t];
                amounts.add(stat);
                amounts.add(stat.equals("SignatureEnergy") ? -(2 + i % 5) : stat.equals("Stamina") ? 0.6 + i * 0.2 : stat.equals("Oxygen") ? 10 + i * 2 : 3 + i);
            }
            ItemStack ring = i == 4 ? plain("Aetherhaven_Ring_Glow", 3) : i == 11 ? plain("Aetherhaven_Ring_Large_Glow", 1)
                : rolled("Aetherhaven_Ring_" + metals[i % 2] + "_" + gems[gem], 1, rarity, i % 4 != 3, amounts.toArray());
            manyRings.setItemStackForSlot((short) (i + (i > 8 ? 4 : 0)), ring, false);
        }
        manyRings.setItemStackForSlot((short) 40, DIAMOND_NECKLACE, false);
        var longBag = view(RUBY_RING, EMERALD_RING, null, manyRings);

        var allWorn = view(
            rolled("Aetherhaven_Ring_Gold_Voidstone", 1, JewelryRarity.LEGENDARY, true, "Mana", 22, "SignatureEnergy", -10, "Ammo", 8),
            plain("Aetherhaven_Ring_Large_Glow", 1),
            VOID_NECKLACE,
            bag(0, DIAMOND_NECKLACE, 3, plain("Aetherhaven_Necklace_Gold_Topaz", 1),
                7, rolled("Aetherhaven_Necklace_Silver_Voidstone", 1, JewelryRarity.LEGENDARY, false, "Mana", 20, "SignatureEnergy", -9, "Ammo", 7),
                12, rolled("Aetherhaven_Necklace_Gold_Sapphire", 1, JewelryRarity.COMMON, true, "Oxygen", 25), 30, ZEPHYR_RING));

        var firstOpen = view(null, null, null, typicalBag());
        var nothing = view(null, null, null, bag());

        return List.of(
            new Scene("first open: nothing worn, the bag has rings and necklaces", "mirror-first-open", open(firstOpen, firstOpen.firstSelection())),
            new Scene("typical: two pieces worn, choosing a ring for the empty slot", "mirror-typical", open(typical, typical.firstSelection())),
            new Scene("a long bag: twenty three rings to swap into Ring I", "mirror-long-bag", open(longBag, 0)),
            new Scene("nothing to choose: no jewelry at all", "mirror-nothing", open(nothing, nothing.firstSelection())),
            new Scene("everything worn, the necklace chosen, every kind of piece and the longest names", "mirror-all-worn", open(allWorn, 2)),
            new Scene("after a swap: the line says what happened", "mirror-after-swap",
                draw(typical, true, 0, 3, -1, HandMirrorLoadoutPanel.t("noteSwapped")
                    .param("name", HandMirrorLoadoutPanel.name(typical.worn(0))).param("old", HandMirrorLoadoutPanel.name(typical.fitting(0).get(0))), false)),
            new Scene("an error line: no room in the bag to take the necklace off", "mirror-error",
                draw(typical, true, 2, 4, -1, HandMirrorLoadoutPanel.t("inventoryFull"), true))
        );
    }

    /**
     * One command per line, in the shape the picture tool takes. The opening append of the page is left out (the tool
     * opens the document itself), and every message becomes its English text on the same element.
     */
    private static String commandLines(List<CustomUICommand> commands) {
        var gson = new GsonBuilder().disableHtmlEscaping().create();
        var lines = new ArrayList<String>();
        for (var command : commands) {
            if (command.type == CustomUICommandType.Append && command.selector == null) continue;
            var row = new JsonObject();
            row.addProperty("type", command.type.name());
            String selector = command.selector;
            JsonElement data = command.data == null ? null : JsonParser.parseString(command.data);
            if (selector != null && selector.endsWith("TextSpans")) {
                selector = selector.substring(0, selector.length() - "TextSpans".length()) + "Text";
                var plain = new JsonObject();
                plain.addProperty("0", english(data.getAsJsonObject().get("0")));
                data = plain;
            }
            if (selector != null) row.addProperty("selector", selector);
            if (command.text != null) row.addProperty("text", command.text);
            if (data != null) row.add("data", data);
            lines.add("      " + gson.toJson(row));
        }
        return "[\n" + String.join(",\n", lines) + "\n     ]";
    }

    private static String cases(String project) {
        var steps = new ArrayList<String>();
        for (var scene : scenes()) {
            assertTrue(scene.sent().commands().size() < 2000, scene.name());
            steps.add(" {\"name\": \"hand mirror: " + scene.name() + "\",\n  \"steps\": [\n"
                + "   {\"tool\": \"ui_render\", \"name\": \"" + scene.name() + "\",\n    \"arguments\": {\"root\": \"projects\", \"path\": \"" + project + "/" + PAGE_FILE
                + "\",\n     \"commands\": " + commandLines(scene.sent().commands()) + "},\n    \"expect\": {\"valid\": true, \"errors\": 0}, \"golden\": \"tests/hytale/golden/"
                + scene.golden() + ".png\"}\n  ]}");
        }
        String text = "{\"cases\": [\n" + String.join(",\n", steps) + "\n]}\n";
        JsonParser.parseString(text);
        return text;
    }

    /** Where the picture tool finds this checkout from the Hytale-Modding folder, so the cases can also be run from a worktree. */
    private static String thisCheckout() {
        Path here = Path.of("").toAbsolutePath();
        for (Path up = here.getParent(); up != null; up = up.getParent()) {
            if (up.getFileName() != null && up.getFileName().toString().equals("Hytale-Modding")) {
                return up.relativize(here).toString().replace('\\', '/');
            }
        }
        return "Aetherhaven";
    }

    @Test void thePictureCasesAreWhatThePageSends() throws IOException {
        // The committed cases name the main checkout, like the other case files. A copy for this checkout goes to build/.
        String committed = cases("Aetherhaven");
        Path local = Path.of("build", "mirror-ui", "mirror.test.json");
        Files.createDirectories(local.getParent());
        Files.writeString(local, cases(thisCheckout()), StandardCharsets.UTF_8);
        if (System.getenv("AETHERHAVEN_WRITE_UI_CASES") != null) {
            Files.writeString(CASES, committed, StandardCharsets.UTF_8);
        }
        assertTrue(Files.isRegularFile(CASES), CASES + " is missing. Run the tests once with AETHERHAVEN_WRITE_UI_CASES set.");
        // Compared as a plain yes or no (a failed assertEquals would print both files), ignoring Windows line ends.
        assertTrue(committed.equals(Files.readString(CASES, StandardCharsets.UTF_8).replace("\r\n", "\n")),
            CASES + " no longer matches what the Hand Mirror sends. Run the tests once with AETHERHAVEN_WRITE_UI_CASES set, run the render cases and look at the pictures.");
    }
}
