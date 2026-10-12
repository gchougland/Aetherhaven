package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.jewelry.JewelryGem;
import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryPieceKind;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import com.hexvane.aetherhaven.jewelry.JewelrySlot;
import com.hexvane.aetherhaven.jewelry.JewelrySlotRules;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What the Hand Mirror shows: the three worn slots and the jewelry in the bag, read from the real stacks. It holds no
 * screen code and needs no world, so the rules of the page (what fits where, which slot is chosen first, what a piece
 * says about itself) can be tested directly.
 */
public final class HandMirrorView {
    /** Bag cards in one row of the page. */
    public static final int COLUMNS = 2;
    /** Most bag cards drawn for one slot, the limit the old list had. */
    public static final int MAX_CARDS = 48;

    /** What a piece can say about its affinities. */
    public enum State {
        /** Appraised: every affinity is listed. */
        APPRAISED,
        /** Rolled but not appraised: the rarity shows, the affinities are counted and not named. They still apply. */
        HIDDEN,
        /** A gem piece that has never been rolled: wearing or appraising it sets its affinities. */
        UNATTUNED,
        /** A glow ring: no affinities, it sheds light while worn. */
        LIGHT
    }

    /** One affinity of an appraised piece. {@code facet} counts from 1. */
    public record Trait(@Nonnull String statId, float amount, int facet) {}

    /**
     * One piece of jewelry, worn or in the bag.
     *
     * @param nameKey the item's own translation key, or null when the item has none and its id is shown
     * @param hidden how many affinities an unappraised piece keeps to itself
     * @param bagSlot where the stack sits in the bag, or -1 for a worn piece
     */
    public record Piece(
        @Nonnull String itemId,
        @Nullable String nameKey,
        boolean ring,
        @Nonnull State state,
        @Nullable JewelryRarity rarity,
        @Nullable JewelryGem gem,
        @Nonnull List<Trait> traits,
        int hidden,
        int quantity,
        short bagSlot
    ) {
        /** Reads a stack. Null when it is empty or not jewelry. */
        @Nullable
        public static Piece of(@Nullable ItemStack stack, short bagSlot, @Nonnull Function<String, String> nameKeys) {
            if (stack == null || ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
                return null;
            }
            String id = stack.getItemId();
            boolean ring = JewelryItemIds.isRing(id);
            String nameKey = nameKeys.apply(id);
            if (nameKey != null && nameKey.isBlank()) {
                nameKey = null;
            }
            int quantity = Math.max(1, stack.getQuantity());
            if (JewelryPieceKind.isArtifact(id)) {
                return new Piece(id, nameKey, ring, State.LIGHT, null, null, List.of(), 0, quantity, bagSlot);
            }
            JewelryGem gem = JewelryGem.fromItemId(id);
            if (!JewelryMetadata.hasJewelryMeta(stack)) {
                return new Piece(id, nameKey, ring, State.UNATTUNED, null, gem, List.of(), 0, quantity, bagSlot);
            }
            JewelryRarity read = JewelryMetadata.readRarity(stack);
            JewelryRarity rarity = read != null ? read : JewelryRarity.COMMON;
            List<JewelryMetadata.RolledTrait> rolled = JewelryMetadata.readTraits(stack);
            if (!JewelryMetadata.isAppraised(stack)) {
                int hidden = !rolled.isEmpty() ? rolled.size() : rarity.traitCount();
                return new Piece(id, nameKey, ring, State.HIDDEN, rarity, gem, List.of(), hidden, quantity, bagSlot);
            }
            List<Trait> traits = new ArrayList<>(rolled.size());
            for (JewelryMetadata.RolledTrait trait : rolled) {
                traits.add(new Trait(trait.statId(), trait.amount(), trait.gemTraitIndex() + 1));
            }
            return new Piece(id, nameKey, ring, State.APPRAISED, rarity, gem, List.copyOf(traits), 0, quantity, bagSlot);
        }
    }

    private final Piece[] worn = new Piece[JewelrySlot.COUNT];
    private final List<Piece> rings = new ArrayList<>();
    private final List<Piece> necklaces = new ArrayList<>();

    private HandMirrorView() {}

    /**
     * @param wornStacks Ring I, Ring II and Necklace in that order; missing or empty entries are empty slots
     * @param bag the player's hotbar and storage, or null when it cannot be read
     * @param nameKeys an item id to that item's translation key, or null when it has none
     */
    @Nonnull
    public static HandMirrorView of(
        @Nonnull ItemStack[] wornStacks, @Nullable ItemContainer bag, @Nonnull Function<String, String> nameKeys
    ) {
        HandMirrorView view = new HandMirrorView();
        for (int slot = 0; slot < JewelrySlot.COUNT; slot++) {
            ItemStack stack = slot < wornStacks.length ? wornStacks[slot] : null;
            Piece piece = Piece.of(stack, (short) -1, nameKeys);
            // A piece in the wrong kind of slot is not shown as worn; the loadout never accepts one.
            view.worn[slot] = piece != null && JewelrySlotRules.canAccept(slot, piece.itemId()) ? piece : null;
        }
        if (bag != null) {
            for (short slot = 0; slot < bag.getCapacity(); slot++) {
                Piece piece = Piece.of(bag.getItemStack(slot), slot, nameKeys);
                if (piece != null) {
                    (piece.ring() ? view.rings : view.necklaces).add(piece);
                }
            }
        }
        return view;
    }

    public static boolean validSlot(int slot) {
        return slot >= 0 && slot < JewelrySlot.COUNT;
    }

    /** True for Ring I and Ring II. */
    public static boolean ringSlot(int slot) {
        return slot == JewelrySlot.RING_1.index() || slot == JewelrySlot.RING_2.index();
    }

    /** The piece worn in a slot, or null. */
    @Nullable
    public Piece worn(int slot) {
        return validSlot(slot) ? worn[slot] : null;
    }

    /** Every piece in the bag that fits a slot, in bag order (hotbar first). */
    @Nonnull
    public List<Piece> fitting(int slot) {
        if (!validSlot(slot)) {
            return List.of();
        }
        return ringSlot(slot) ? rings : necklaces;
    }

    /** The bag cards drawn for a slot: the pieces that fit, cut at {@link #MAX_CARDS}. */
    @Nonnull
    public List<Piece> cards(int slot) {
        List<Piece> all = fitting(slot);
        return all.size() > MAX_CARDS ? all.subList(0, MAX_CARDS) : all;
    }

    /** The bag piece at a bag slot, when it fits the given loadout slot. */
    @Nullable
    public Piece inBag(int slot, short bagSlot) {
        for (Piece piece : fitting(slot)) {
            if (piece.bagSlot() == bagSlot) {
                return piece;
            }
        }
        return null;
    }

    /**
     * The slot chosen when the mirror opens: the first empty slot the bag has a piece for, then the first empty slot,
     * then Ring I.
     */
    public int firstSelection() {
        for (int slot = 0; slot < JewelrySlot.COUNT; slot++) {
            if (worn[slot] == null && !fitting(slot).isEmpty()) {
                return slot;
            }
        }
        for (int slot = 0; slot < JewelrySlot.COUNT; slot++) {
            if (worn[slot] == null) {
                return slot;
            }
        }
        return JewelrySlot.RING_1.index();
    }

    /**
     * The slot chosen after a piece was put on, asked of the view as it is afterwards. Filling an empty ring slot moves
     * on to the other ring slot while that one is empty and a ring is left in the bag, so two rings go on with two
     * choices. A swap stays where it is, so the player can try one ring after another in the same slot.
     */
    public int afterWear(int slot, boolean wasEmpty) {
        if (!validSlot(slot)) {
            return firstSelection();
        }
        if (wasEmpty && ringSlot(slot)) {
            int other = slot == JewelrySlot.RING_1.index() ? JewelrySlot.RING_2.index() : JewelrySlot.RING_1.index();
            if (worn[other] == null && !rings.isEmpty()) {
                return other;
            }
        }
        return slot;
    }
}
