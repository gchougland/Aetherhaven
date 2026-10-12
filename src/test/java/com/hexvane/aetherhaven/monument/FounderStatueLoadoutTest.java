package com.hexvane.aetherhaven.monument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.inventory.ItemStackData;
import com.hexvane.aetherhaven.monument.FounderStatueArmorTransfer.Result;
import com.hexvane.aetherhaven.monument.FounderStatueLoadout.Slot;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Real armor on a founder statue is never lost and never doubled, whatever the player or the world does. */
@Tag("entity")
class FounderStatueLoadoutTest {
    private static Object previousItemStore;

    // Supply an item asset map so real ItemStacks and inventory transactions work without starting a server.
    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);

        ItemStore(Config config) {
            super(config);
        }

        @Override
        protected IEventBus getEventBus() {
            return events;
        }

        @Override
        public void addFileMonitor(String pack, Path path) {}

        @Override
        public void removeFileMonitor(Path path) {}

        @Override
        protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}

        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() {
                super(String.class, Item.class, new DefaultAssetMap<>());
                setPath("Items");
                setCodec(Item.CODEC);
                setKeyFunction(Item::getId);
            }

            @Override
            public ItemStore build() {
                return new ItemStore(this);
            }
        }
    }

    @BeforeAll
    static void setUpAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousItemStore = field.get(null);
        field.set(null, new ItemStore.Config().build());
    }

    @AfterAll
    static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
    }

    private static ItemStack piece(String id) {
        return new ItemStack(id, 1);
    }

    /** A worn, engraved piece: its wear and extra data must come back exactly as they went in. */
    private static ItemStack heirloom(String id) {
        return new ItemStack(id, 1, 37.5, 120.0, new BsonDocument("engraved", new BsonString("Mertie")));
    }

    private static void put(ItemContainer pack, int slot, ItemStack stack) {
        pack.setItemStackForSlot((short) slot, stack, false);
    }

    private static int count(ItemContainer pack, String id) {
        int[] total = new int[1];
        pack.forEach((slot, stack) -> {
            if (id.equals(stack.getItemId())) {
                total[0] += stack.getQuantity();
            }
        });
        return total[0];
    }

    private static int count(FounderStatueLoadout loadout, String id) {
        int total = 0;
        for (Slot slot : Slot.values()) {
            ItemStack worn = loadout.get(slot);
            if (worn != null && id.equals(worn.getItemId())) {
                total += worn.getQuantity();
            }
        }
        return total;
    }

    private static int count(List<ItemStack> stacks, String id) {
        return stacks.stream().filter(s -> id.equals(s.getItemId())).mapToInt(ItemStack::getQuantity).sum();
    }

    private static void assertSamePiece(ItemStack expected, ItemStack actual) {
        assertNotNull(actual);
        assertEquals(expected.getItemId(), actual.getItemId());
        assertEquals(expected.getQuantity(), actual.getQuantity());
        assertEquals(expected.getDurability(), actual.getDurability());
        assertEquals(expected.getMaxDurability(), actual.getMaxDurability());
        assertEquals(ItemStackData.metadata(expected), ItemStackData.metadata(actual));
    }

    @Test
    void statueSlotsAreTheGamesArmorSlotsInTheGamesOrder() {
        assertEquals(ItemArmorSlot.VALUES.length, Slot.values().length);
        for (ItemArmorSlot game : ItemArmorSlot.VALUES) {
            Slot slot = Slot.byKey(game.name());
            assertNotNull(slot, "No statue slot for the game's armor slot " + game);
            assertEquals(game.ordinal(), slot.ordinal(), "The statue shows a piece in the armor slot of the same number");
        }
        assertNull(Slot.byKey("Tail"));
        assertNull(Slot.byKey(null));
    }

    @Test
    void puttingInTakingOutAndSwappingMoveOnePieceAtATime() {
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        ItemStack helm = heirloom("Armor_Iron_Head");
        ItemStack crown = piece("Armor_Gold_Head");
        assertFalse(loadout.hasArmor());
        assertNull(loadout.take(Slot.HEAD), "An empty slot gives nothing");

        assertNull(loadout.put(Slot.HEAD, helm), "Nothing was replaced");
        assertSame(helm, loadout.get(Slot.HEAD), "Looking does not move the piece");
        assertSame(helm, loadout.get(Slot.HEAD));
        assertTrue(loadout.hasArmor());

        assertSame(helm, loadout.put(Slot.HEAD, crown), "A swap hands back the piece that was there");
        assertSame(crown, loadout.get(Slot.HEAD));
        assertNull(loadout.get(Slot.CHEST), "Other slots are untouched");

        assertSame(crown, loadout.take(Slot.HEAD));
        assertNull(loadout.take(Slot.HEAD), "A piece can be taken out once");
        assertFalse(loadout.hasArmor());
        assertThrows(IllegalArgumentException.class, () -> loadout.put(Slot.HEAD, ItemStack.EMPTY));
    }

    @Test
    void breakingTheStatueReturnsEveryPieceExactlyOnce() {
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        loadout.put(Slot.HEAD, heirloom("Armor_Iron_Head"));
        loadout.put(Slot.CHEST, piece("Armor_Iron_Chest"));
        loadout.put(Slot.LEGS, piece("Armor_Iron_Legs"));

        List<ItemStack> dropped = new ArrayList<>();
        assertEquals(3, FounderStatueArmorTransfer.release(loadout, dropped::add));
        assertEquals(3, dropped.size());
        assertSamePiece(heirloom("Armor_Iron_Head"), dropped.get(0));
        assertFalse(loadout.hasArmor());

        // The block-removed hook and a dissolved town can both ask; only the first finds anything.
        assertEquals(0, FounderStatueArmorTransfer.release(loadout, dropped::add));
        assertTrue(loadout.takeAll().isEmpty());
        assertEquals(3, dropped.size());
    }

    @Test
    void aPieceThatCannotBeSetDownStaysOnTheStatue() {
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        loadout.put(Slot.HEAD, piece("Armor_Iron_Head"));
        loadout.put(Slot.HANDS, piece("Armor_Iron_Hands"));

        List<ItemStack> dropped = new ArrayList<>();
        int released = FounderStatueArmorTransfer.release(
            loadout,
            stack -> stack.getItemId().endsWith("Hands") && dropped.add(stack)
        );

        assertEquals(1, released);
        assertEquals(1, dropped.size());
        assertNotNull(loadout.get(Slot.HEAD), "A refused piece is still owned by the statue, not discarded");
        assertNull(loadout.get(Slot.HANDS));
    }

    @Test
    void saveRoundTripKeepsEveryPieceItsWearAndThePose() {
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        loadout.put(Slot.HEAD, heirloom("Armor_Iron_Head"));
        loadout.put(Slot.LEGS, piece("Armor_Iron_Legs"));
        loadout.setPoseId(FounderStatuePose.CHEER.savedId());

        FounderStatueLoadout restored =
            FounderStatueLoadout.CODEC.decode(FounderStatueLoadout.CODEC.encode(loadout, new ExtraInfo()), new ExtraInfo());

        assertSamePiece(heirloom("Armor_Iron_Head"), restored.get(Slot.HEAD));
        assertSamePiece(piece("Armor_Iron_Legs"), restored.get(Slot.LEGS));
        assertNull(restored.get(Slot.CHEST));
        assertNull(restored.get(Slot.HANDS));
        assertEquals(FounderStatuePose.CHEER, FounderStatuePose.byId(restored.getPoseId()));
    }

    @Test
    void thePedestalBlockSavesItsStatuesArmorWithTheRestOfTheMonument() {
        FounderMonumentBlock block = new FounderMonumentBlock("town", "statue", "{\"skin\":1}", "Ada", 0f, 3.1f, 0f);
        block.getLoadout().put(Slot.CHEST, heirloom("Armor_Iron_Chest"));
        block.getLoadout().setPoseId(FounderStatuePose.SIT.savedId());

        FounderMonumentBlock restored =
            FounderMonumentBlock.CODEC.decode(FounderMonumentBlock.CODEC.encode(block, new ExtraInfo()), new ExtraInfo());

        assertEquals("town", restored.getTownId());
        assertEquals("Ada", restored.getLabel());
        assertEquals(3.1f, restored.getYaw());
        assertSamePiece(heirloom("Armor_Iron_Chest"), restored.getLoadout().get(Slot.CHEST));
        assertEquals(FounderStatuePose.SIT, FounderStatuePose.byId(restored.getLoadout().getPoseId()));
    }

    @Test
    void pedestalsSavedBeforeArmorExistedLoadAsBareStandingStatues() {
        BsonDocument old = BsonDocument.parse(
            "{\"TownId\":\"town\",\"StatueEntityUuid\":\"statue\",\"SkinJson\":\"{}\",\"Label\":\"Ada\",\"Yaw\":1.5}"
        );
        FounderMonumentBlock restored = FounderMonumentBlock.CODEC.decode(old, new ExtraInfo());

        assertEquals("town", restored.getTownId());
        assertEquals("Ada", restored.getLabel());
        assertNotNull(restored.getLoadout());
        assertFalse(restored.getLoadout().hasArmor());
        assertEquals(FounderStatuePose.STANDING, FounderStatuePose.byId(restored.getLoadout().getPoseId()));
    }

    @Test
    void anEngineCopyOfThePedestalHasItsOwnSlots() {
        FounderMonumentBlock block = new FounderMonumentBlock("town", "statue", "{}", "Ada", 0f, 1f, 0f);
        block.getLoadout().put(Slot.HEAD, piece("Armor_Iron_Head"));
        FounderMonumentBlock copy = (FounderMonumentBlock) block.clone();

        assertNotNull(copy);
        assertNotSame(block.getLoadout(), copy.getLoadout());
        assertNotNull(copy.getLoadout().get(Slot.HEAD));
        copy.getLoadout().take(Slot.HEAD);
        assertNotNull(block.getLoadout().get(Slot.HEAD), "Emptying the copy must not empty the pedestal");
    }

    @Test
    void aReplacedPedestalHandsItsMonumentAndArmorToItsReplacement() {
        FounderMonumentBlock old = new FounderMonumentBlock("town", "statue", "{\"skin\":1}", "Ada", 0f, 2f, 0f);
        old.getLoadout().put(Slot.HEAD, heirloom("Armor_Iron_Head"));
        old.getLoadout().put(Slot.CHEST, piece("Armor_Iron_Chest"));
        old.getLoadout().setPoseId(FounderStatuePose.WAVE.savedId());
        FounderMonumentBlock fresh = new FounderMonumentBlock();

        old.moveStatueTo(fresh);

        assertEquals("town", fresh.getTownId());
        assertEquals("statue", fresh.getStatueEntityUuid());
        assertEquals("{\"skin\":1}", fresh.getSkinJson());
        assertEquals("Ada", fresh.getLabel());
        assertEquals(2f, fresh.getYaw());
        assertSamePiece(heirloom("Armor_Iron_Head"), fresh.getLoadout().get(Slot.HEAD));
        assertNotNull(fresh.getLoadout().get(Slot.CHEST));
        assertEquals(FounderStatuePose.WAVE, FounderStatuePose.byId(fresh.getLoadout().getPoseId()));

        // The old block entity is removed right after: it must have nothing left to drop and no statue to remove.
        assertFalse(old.getLoadout().hasArmor());
        assertNull(old.getStatueEntityUuid());
        assertEquals(0, FounderStatueArmorTransfer.release(old.getLoadout(), stack -> true));
    }

    @Test
    void movingArmorNeverOverwritesAPieceAlreadyThere() {
        FounderStatueLoadout from = new FounderStatueLoadout();
        from.put(Slot.HEAD, piece("Armor_Iron_Head"));
        from.put(Slot.LEGS, piece("Armor_Iron_Legs"));
        FounderStatueLoadout to = new FounderStatueLoadout();
        to.put(Slot.HEAD, piece("Armor_Gold_Head"));

        to.takeOver(from);

        assertEquals("Armor_Gold_Head", to.get(Slot.HEAD).getItemId());
        assertEquals("Armor_Iron_Legs", to.get(Slot.LEGS).getItemId());
        assertEquals("Armor_Iron_Head", from.get(Slot.HEAD).getItemId(), "The piece that had no free slot keeps its owner");
        assertNull(from.get(Slot.LEGS));
    }

    @Test
    void wearingFromThePackMovesTheRealPieceOntoTheStatue() {
        ItemContainer pack = new SimpleItemContainer((short) 3);
        put(pack, 1, heirloom("Armor_Iron_Head"));
        put(pack, 2, piece("Junk"));
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        List<ItemStack> setDown = new ArrayList<>();

        Result result = FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) 1, "Armor_Iron_Head", stack -> true, pack, setDown::add);

        assertEquals(Result.DONE, result);
        assertSamePiece(heirloom("Armor_Iron_Head"), loadout.get(Slot.HEAD));
        assertTrue(ItemStack.isEmpty(pack.getItemStack((short) 1)), "The piece left the pack");
        assertEquals(0, count(pack, "Armor_Iron_Head"));
        assertEquals(1, count(pack, "Junk"));
        assertTrue(setDown.isEmpty());

        assertEquals(Result.DONE, FounderStatueArmorTransfer.takeOff(loadout, Slot.HEAD, pack, setDown::add));
        assertFalse(loadout.hasArmor());
        assertEquals(1, count(pack, "Armor_Iron_Head"));
        ItemStack back = null;
        for (short slot = 0; slot < pack.getCapacity(); slot++) {
            ItemStack stack = pack.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && "Armor_Iron_Head".equals(stack.getItemId())) {
                back = stack;
            }
        }
        assertSamePiece(heirloom("Armor_Iron_Head"), back);
        assertEquals(Result.NOTHING_THERE, FounderStatueArmorTransfer.takeOff(loadout, Slot.HEAD, pack, setDown::add));
        assertEquals(1, count(pack, "Armor_Iron_Head"), "Taking off twice gives the piece back once");
        assertTrue(setDown.isEmpty());
    }

    @Test
    void swappingReturnsTheOldPieceToThePackEvenWhenThePackWasFull() {
        ItemContainer pack = new SimpleItemContainer((short) 2);
        put(pack, 0, piece("Armor_Gold_Head"));
        put(pack, 1, piece("Junk"));
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        loadout.put(Slot.HEAD, heirloom("Armor_Iron_Head"));
        List<ItemStack> setDown = new ArrayList<>();

        Result result = FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) 0, "Armor_Gold_Head", stack -> true, pack, setDown::add);

        assertEquals(Result.DONE, result);
        assertEquals("Armor_Gold_Head", loadout.get(Slot.HEAD).getItemId());
        assertEquals(1, count(pack, "Armor_Iron_Head"), "The old piece took the slot the new one left");
        assertEquals(0, count(pack, "Armor_Gold_Head"));
        assertTrue(setDown.isEmpty());
    }

    @Test
    void aFullPackNeverDeletesAPiece() {
        ItemContainer pack = new SimpleItemContainer((short) 2);
        put(pack, 0, piece("Junk"));
        put(pack, 1, piece("More_Junk"));
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        loadout.put(Slot.CHEST, heirloom("Armor_Iron_Chest"));
        List<ItemStack> setDown = new ArrayList<>();

        assertEquals(Result.DONE, FounderStatueArmorTransfer.takeOff(loadout, Slot.CHEST, pack, setDown::add));

        assertFalse(loadout.hasArmor());
        assertEquals(0, count(pack, "Armor_Iron_Chest"));
        assertEquals(1, setDown.size(), "With no room in the pack the piece is set down at the statue");
        assertSamePiece(heirloom("Armor_Iron_Chest"), setDown.get(0));
        assertEquals(1, count(pack, "Junk"));
        assertEquals(1, count(pack, "More_Junk"));
    }

    @Test
    void aSwapWithNoRoomSetsTheOldPieceDownInsteadOfLosingIt() {
        // Two of the same helmet share the only pack slot, so taking one out frees no slot for the old piece.
        ItemContainer pack = new SimpleItemContainer((short) 1);
        put(pack, 0, new ItemStack("Armor_Gold_Head", 2));
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        loadout.put(Slot.HEAD, heirloom("Armor_Iron_Head"));
        List<ItemStack> setDown = new ArrayList<>();

        Result result = FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) 0, "Armor_Gold_Head", stack -> true, pack, setDown::add);

        assertEquals(Result.DONE, result);
        assertEquals(1, count(loadout, "Armor_Gold_Head"), "Exactly one piece went onto the statue");
        assertEquals(1, count(pack, "Armor_Gold_Head"), "The rest of the stack stayed in the pack");
        assertEquals(1, count(pack, "Armor_Iron_Head") + count(setDown, "Armor_Iron_Head"));
        assertEquals(1, setDown.size());
        assertSamePiece(heirloom("Armor_Iron_Head"), setDown.get(0));
    }

    @Test
    void aStaleOrWrongChoiceChangesNothing() {
        ItemContainer pack = new SimpleItemContainer((short) 2);
        put(pack, 0, piece("Armor_Iron_Chest"));
        FounderStatueLoadout loadout = new FounderStatueLoadout();
        loadout.put(Slot.HEAD, piece("Armor_Iron_Head"));
        List<ItemStack> setDown = new ArrayList<>();

        // The player moved things around after the screen was drawn.
        assertEquals(Result.PACK_CHANGED, FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) 0, "Armor_Gold_Head", stack -> true, pack, setDown::add));
        assertEquals(Result.PACK_CHANGED, FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) 1, "Armor_Gold_Head", stack -> true, pack, setDown::add));
        assertEquals(Result.PACK_CHANGED, FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) 9, "Armor_Iron_Chest", stack -> true, pack, setDown::add));
        assertEquals(Result.PACK_CHANGED, FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) -1, "Armor_Iron_Chest", stack -> true, pack, setDown::add));
        // A chest piece is not a helmet.
        assertEquals(Result.WRONG_PIECE, FounderStatueArmorTransfer.wear(
            loadout, Slot.HEAD, pack, (short) 0, "Armor_Iron_Chest", stack -> false, pack, setDown::add));

        assertEquals("Armor_Iron_Head", loadout.get(Slot.HEAD).getItemId());
        assertEquals(1, count(pack, "Armor_Iron_Chest"));
        assertTrue(setDown.isEmpty());
    }
}
