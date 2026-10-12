package com.hexvane.aetherhaven.ui;

import static com.hexvane.aetherhaven.ui.HandMirrorLoadoutPanel.t;

import com.hexvane.aetherhaven.jewelry.JewelrySlot;
import com.hexvane.aetherhaven.jewelry.PlayerJewelryLoadout;
import com.hexvane.aetherhaven.ui.HandMirrorView.Piece;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Hand Mirror: the player's two rings and necklace on the left, the jewelry in their bag that fits the chosen slot
 * on the right. Choosing a bag card wears that piece at once; Take off returns the chosen slot's piece to the bag.
 *
 * <p>Everything drawn comes from {@link #draw}, which needs only a {@link HandMirrorView}, so the picture tests send
 * the same commands a player's client gets.</p>
 */
public final class HandMirrorPage extends AetherhavenInteractiveCustomUIPage<HandMirrorPage.PageData> {
    static final String PAGE = "Aetherhaven/HandMirror.ui";
    static final String SLOT_CARD = "Aetherhaven/HandMirrorSlot.ui";
    static final String PIECE_ROW = "Aetherhaven/HandMirrorPieceRow.ui";
    static final String PIECE_CARD = "Aetherhaven/HandMirrorPiece.ui";

    private static final String C_NOTE = "#dec68d";
    private static final String C_ERROR = "#e8907c";
    private static final String C_NAME = "#eee0bf";
    private static final String C_TEXT = "#d8d0c8";
    private static final String C_FAINT = "#8f9aa8";

    private boolean appended;
    /** The slot whose bag pieces are listed; chosen from the loadout when the page first opens. */
    private int selected = -1;
    /** Counts the pictures sent. A press made on an older picture is ignored, so a double press cannot undo itself. */
    private int revision;
    /** Bag cards the client holds, so they are only rebuilt (losing the scroll place) when their number changes. */
    private int cardsShown = -1;
    @Nullable
    private Message note;
    private boolean noteIsError;

    public HandMirrorPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store) {
        HandMirrorView view = view(ref, store);
        if (!HandMirrorView.validSlot(selected)) {
            selected = view.firstSelection();
        }
        cardsShown = draw(commandBuilder, eventBuilder, view, !appended, selected, revision, appended ? cardsShown : -1, note, noteIsError);
        appended = true;
    }

    @Override
    protected void rebuild() {
        // A whole rebuild empties the client's page, so the document and its cards are sent again.
        appended = false;
        super.rebuild();
    }

    @Nonnull
    private static HandMirrorView view(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerJewelryLoadout loadout = HandMirrorLoadoutActions.loadoutOrCreate(ref, store);
        CombinedItemContainer bag = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        ItemStack[] worn = {loadout.getRing1(), loadout.getRing2(), loadout.getNecklace()};
        return HandMirrorView.of(worn, bag, HandMirrorPage::nameKey);
    }

    /** The item's own translation key, as the item asset declares it. */
    @Nullable
    private static String nameKey(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item != null ? item.getTranslationKey() : null;
    }

    /**
     * Every command and event binding of one picture of the page.
     *
     * @param first the client has nothing yet: send the document and the three slot cards too
     * @param cardsShown bag cards the client already holds, or -1 for none
     * @param note the line beside the buttons, or null for the standing explanation
     * @return the number of bag cards the client holds afterwards
     */
    static int draw(
        @Nonnull UICommandBuilder c,
        @Nonnull UIEventBuilder e,
        @Nonnull HandMirrorView view,
        boolean first,
        int selected,
        int revision,
        int cardsShown,
        @Nullable Message note,
        boolean noteIsError
    ) {
        String rev = String.valueOf(revision);
        if (first) {
            c.append(PAGE);
            for (int slot = 0; slot < JewelrySlot.COUNT; slot++) {
                c.append("#WornSlots", SLOT_CARD);
            }
            c.set("#MirrorTitle.TextSpans", t("title"));
            c.set("#TakeOff.TextSpans", t("takeOff"));
            c.set("#Done.TextSpans", t("done"));
        }
        int wearing = 0;
        for (int slot = 0; slot < JewelrySlot.COUNT; slot++) {
            drawSlot(c, e, view, slot, slot == selected, rev);
            wearing += view.worn(slot) != null ? 1 : 0;
        }
        c.set("#WornHeading.TextSpans", t("wornCount").param("worn", wearing).param("slots", JewelrySlot.COUNT));

        boolean rings = HandMirrorView.ringSlot(selected);
        Piece current = view.worn(selected);
        List<Piece> cards = view.cards(selected);
        c.set("#BagHeading.TextSpans", t(rings ? "bagRings" : "bagNecklaces"));
        c.set("#BagCount.Visible", !cards.isEmpty());
        c.set("#BagCount.TextSpans", t("bagCount").param("count", view.fitting(selected).size()));
        c.set("#BagHint.TextSpans", bagHint(selected, current, rings, cards.isEmpty()));
        c.set("#BagEmpty.Visible", cards.isEmpty());
        c.set("#BagEmpty.TextSpans", t(rings ? "bagEmptyRings" : "bagEmptyNecklaces"));
        boolean fresh = cards.size() != cardsShown;
        if (fresh) {
            c.clear("#BagRows");
        }
        for (int i = 0; i < cards.size(); i++) {
            int row = i / HandMirrorView.COLUMNS;
            int column = i % HandMirrorView.COLUMNS;
            if (fresh) {
                if (column == 0) {
                    c.append("#BagRows", PIECE_ROW);
                }
                c.append("#BagRows[" + row + "]", PIECE_CARD);
            }
            drawPiece(c, e, "#BagRows[" + row + "][" + column + "]", cards.get(i), selected, current, rev);
        }

        c.set("#MirrorNote.TextSpans", note != null ? note : t("noteStart"));
        c.set("#MirrorNote.Style.TextColor", noteIsError ? C_ERROR : C_NOTE);
        c.set("#TakeOff.Disabled", current == null);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#TakeOff", EventData.of("Action", "TakeOff").append("Rev", rev), false);
        e.addEventBinding(CustomUIEventBindingType.Activating, "#Done", EventData.of("Action", "Done"), false);
        return cards.size();
    }

    /** One of the three worn slots: the piece, its rarity and what it gives, or what the bag holds for an empty slot. */
    private static void drawSlot(
        @Nonnull UICommandBuilder c, @Nonnull UIEventBuilder e, @Nonnull HandMirrorView view, int slot, boolean chosen, @Nonnull String rev
    ) {
        String at = "#WornSlots[" + slot + "]";
        Piece piece = view.worn(slot);
        c.set(at + " #SlotSelected.Visible", chosen);
        c.set(at + " #SlotTitle.TextSpans", HandMirrorLoadoutPanel.slotName(slot));
        c.set(at + " #SlotIcon.Visible", piece != null);
        if (piece != null) {
            c.set(at + " #SlotIcon.ItemId", piece.itemId());
        }
        c.set(at + " #SlotName.TextSpans", piece != null ? HandMirrorLoadoutPanel.name(piece) : t("slotEmpty"));
        c.set(at + " #SlotName.Style.TextColor", piece != null ? C_NAME : C_FAINT);
        boolean rare = piece != null && piece.rarity() != null;
        c.set(at + " #SlotRarity.Visible", rare);
        c.set(at + " #SlotRarityBar.Visible", rare);
        if (rare) {
            String colour = HandMirrorLoadoutPanel.rarityColour(piece);
            c.set(at + " #SlotRarity.TextSpans", HandMirrorLoadoutPanel.rarity(piece.rarity()));
            c.set(at + " #SlotRarity.Style.TextColor", colour);
            c.setObject(at + " #SlotRarityBar.Background", new PatchStyle().setColor(Value.of(colour)));
        }
        c.set(at + " #SlotDetail.TextSpans", piece != null ? HandMirrorLoadoutPanel.detail(piece, true) : fits(view, slot));
        c.set(at + " #SlotDetail.Style.TextColor", piece != null ? C_TEXT : C_FAINT);
        c.set(at + ".TooltipTextSpans", piece != null
            ? t("tipSlotWorn").param("slot", HandMirrorLoadoutPanel.slotName(slot)).param("name", HandMirrorLoadoutPanel.name(piece))
            : t("tipSlotEmpty").param("slot", HandMirrorLoadoutPanel.slotName(slot)));
        e.addEventBinding(CustomUIEventBindingType.Activating, at,
            EventData.of("Action", "Slot").append("Target", String.valueOf(slot)).append("Rev", rev), false);
    }

    /** What an empty slot says: how many pieces in the bag would fit it. */
    @Nonnull
    private static Message fits(@Nonnull HandMirrorView view, int slot) {
        int count = view.fitting(slot).size();
        String kind = HandMirrorView.ringSlot(slot) ? "Ring" : "Necklace";
        return count == 0 ? t("fit" + kind + "None") : count == 1 ? t("fit" + kind + "One") : t("fit" + kind + "Many").param("count", count);
    }

    /** The line over the bag cards: what choosing one will do. */
    @Nonnull
    private static Message bagHint(int selected, @Nullable Piece current, boolean rings, boolean empty) {
        if (empty) {
            return Message.raw("");
        }
        if (rings) {
            Message hint = t(current == null ? "chooseRing" : "swapRing").param("slot", HandMirrorLoadoutPanel.slotName(selected));
            return current == null ? hint : hint.param("worn", HandMirrorLoadoutPanel.name(current));
        }
        return current == null ? t("chooseNecklace") : t("swapNecklace").param("worn", HandMirrorLoadoutPanel.name(current));
    }

    /** One piece in the bag. Choosing it wears it in the chosen slot. */
    private static void drawPiece(
        @Nonnull UICommandBuilder c,
        @Nonnull UIEventBuilder e,
        @Nonnull String at,
        @Nonnull Piece piece,
        int selected,
        @Nullable Piece current,
        @Nonnull String rev
    ) {
        c.set(at + " #PieceName.TextSpans", HandMirrorLoadoutPanel.name(piece));
        c.set(at + " #PieceIcon.ItemId", piece.itemId());
        c.set(at + " #PieceCount.Visible", piece.quantity() > 1);
        c.set(at + " #PieceCount.TextSpans", t("quantity").param("count", piece.quantity()));
        boolean rare = piece.rarity() != null;
        c.set(at + " #PieceRarity.Visible", rare);
        if (rare) {
            c.set(at + " #PieceRarity.TextSpans", HandMirrorLoadoutPanel.rarity(piece.rarity()));
            c.set(at + " #PieceRarity.Style.TextColor", HandMirrorLoadoutPanel.rarityColour(piece));
        }
        c.set(at + " #PieceDetail.TextSpans", HandMirrorLoadoutPanel.detail(piece, false));
        Message tip = t(current == null ? "tipWear" : "tipSwap")
            .param("name", HandMirrorLoadoutPanel.name(piece))
            .param("slot", HandMirrorLoadoutPanel.slotName(selected));
        c.set(at + ".TooltipTextSpans", current == null ? tip : tip.param("worn", HandMirrorLoadoutPanel.name(current)));
        e.addEventBinding(CustomUIEventBindingType.Activating, at,
            EventData.of("Action", "Wear").append("InvSlot", String.valueOf(piece.bagSlot())).append("Item", piece.itemId()).append("Rev", rev), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        if ("Done".equals(data.action)) {
            close();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        // A press on a picture the player no longer has (a second press before the first was answered) changes nothing.
        if (player == null || parse(data.rev, -1) != revision) {
            return;
        }
        HandMirrorView view = view(ref, store);
        if (!HandMirrorView.validSlot(selected)) {
            selected = view.firstSelection();
        }
        note = null;
        noteIsError = false;
        switch (data.action) {
            case "Slot" -> {
                int slot = parse(data.target, -1);
                if (HandMirrorView.validSlot(slot)) {
                    selected = slot;
                }
            }
            case "Wear" -> wear(ref, store, player, view, (short) parse(data.invSlot, -1), data.item);
            case "TakeOff" -> takeOff(ref, store, player, view);
            default -> {}
        }
        revision++;
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        build(ref, commands, events, store);
        sendUpdate(commands, events, false);
    }

    private void wear(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull HandMirrorView view,
        short bagSlot,
        @Nullable String itemId
    ) {
        Piece piece = view.inBag(selected, bagSlot);
        CombinedItemContainer bag = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        // The bag may have changed while the page was open. Only the piece the card showed is worn; otherwise the
        // page is simply drawn again with what the bag holds now.
        if (piece == null || bag == null || !piece.itemId().equals(itemId)) {
            return;
        }
        Piece before = view.worn(selected);
        switch (HandMirrorLoadoutActions.equipFromInventory(ref, store, player, bag, bagSlot, selected)) {
            case SUCCESS -> {
                note = before == null
                    ? t("noteWorn").param("name", HandMirrorLoadoutPanel.name(piece))
                    : t("noteSwapped").param("name", HandMirrorLoadoutPanel.name(piece)).param("old", HandMirrorLoadoutPanel.name(before));
                selected = view(ref, store).afterWear(selected, before == null);
            }
            case COULD_NOT_RETURN_PREVIOUS, INVENTORY_UPDATE_FAILED -> fail("equipFailed");
            case NOT_JEWELRY, INVALID_FOR_SLOT -> fail("cannotEquipThere");
            case SLOT_EMPTY -> {}
        }
    }

    private void takeOff(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Player player, @Nonnull HandMirrorView view
    ) {
        Piece worn = view.worn(selected);
        if (worn == null) {
            return;
        }
        if (HandMirrorLoadoutActions.takeFromLoadout(ref, store, player, selected)) {
            note = t("noteTookOff").param("name", HandMirrorLoadoutPanel.name(worn));
        } else {
            fail("inventoryFull");
        }
    }

    private void fail(@Nonnull String key) {
        note = t(key);
        noteIsError = true;
    }

    private static int parse(@Nullable String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("InvSlot", Codec.STRING), (d, v) -> d.invSlot = v, d -> d.invSlot)
            .add()
            .append(new KeyedCodec<>("Target", Codec.STRING), (d, v) -> d.target = v, d -> d.target)
            .add()
            .append(new KeyedCodec<>("Item", Codec.STRING), (d, v) -> d.item = v, d -> d.item)
            .add()
            .append(new KeyedCodec<>("Rev", Codec.STRING), (d, v) -> d.rev = v, d -> d.rev)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String invSlot;
        @Nullable
        private String target;
        @Nullable
        private String item;
        @Nullable
        private String rev;
    }
}
