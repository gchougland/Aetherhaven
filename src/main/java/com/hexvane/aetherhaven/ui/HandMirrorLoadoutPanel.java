package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import com.hexvane.aetherhaven.jewelry.JewelrySlot;
import com.hexvane.aetherhaven.jewelry.JewelryStatTuning;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipText;
import com.hexvane.aetherhaven.ui.HandMirrorView.Piece;
import com.hexvane.aetherhaven.ui.HandMirrorView.Trait;
import com.hypixel.hytale.server.core.Message;
import javax.annotation.Nonnull;

/**
 * The words on the Hand Mirror's cards: what a slot is called, what a piece is called, how rare it is and what it
 * gives. Every sentence comes from the English language file; only numbers and signs are put together here.
 *
 * <p>A message is changed in place by {@code insert}, {@code param} and {@code color}, so every method returns a new
 * one and none is kept.</p>
 */
public final class HandMirrorLoadoutPanel {
    static final String LANG = "aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.";
    private static final String JEWELRY = "aetherhaven_jewelry_geode.aetherhaven.jewelry.";

    private static final String C_DIM = "#6B7080";
    private static final String C_STAT = "#C9B6E8";
    /** The colour of a rarity nobody has rolled: the rarity line's own grey. */
    static final String C_NO_RARITY = "#9BA0AA";

    private HandMirrorLoadoutPanel() {}

    @Nonnull
    static Message t(@Nonnull String key) {
        return Message.translation(LANG + key);
    }

    /** Ring I, Ring II or Necklace. */
    @Nonnull
    public static Message slotName(int slot) {
        return t(slot == JewelrySlot.RING_1.index() ? "ring1" : slot == JewelrySlot.RING_2.index() ? "ring2" : "necklace");
    }

    /** The item's own name. */
    @Nonnull
    public static Message name(@Nonnull Piece piece) {
        return piece.nameKey() != null ? Message.translation(piece.nameKey()) : Message.raw(piece.itemId());
    }

    /** "Rare", for a piece that has a rarity. */
    @Nonnull
    public static Message rarity(@Nonnull JewelryRarity rarity) {
        return Message.translation(JEWELRY + "rarity." + rarity.wireName());
    }

    /** The colour the item tooltips give this rarity. */
    @Nonnull
    public static String rarityColour(@Nonnull Piece piece) {
        return piece.rarity() != null ? JewelryTooltipText.rarityColorHex(piece.rarity().wireName()) : C_NO_RARITY;
    }

    /**
     * What the piece gives, one affinity to a line. An unappraised piece says how many it hides, a piece never rolled
     * says how to set them, and a glow ring says that it sheds light.
     *
     * @param facets also name the gem facet behind each affinity, as the worn slots do
     */
    @Nonnull
    public static Message detail(@Nonnull Piece piece, boolean facets) {
        return switch (piece.state()) {
            case LIGHT -> Message.translation(JEWELRY + "artifact.wornLight");
            case UNATTUNED -> t("traitsUnattuned");
            case HIDDEN -> piece.hidden() == 1 ? t("hiddenOne") : t("hiddenMany").param("count", piece.hidden());
            case APPRAISED -> {
                if (piece.traits().isEmpty()) {
                    yield Message.raw("");
                }
                // The first line carries the others, as the old affinity list did.
                Message lines = traitLine(piece, piece.traits().get(0), facets);
                for (int i = 1; i < piece.traits().size(); i++) {
                    lines.insert(Message.raw("\n")).insert(traitLine(piece, piece.traits().get(i), facets));
                }
                yield lines;
            }
        };
    }

    @Nonnull
    private static Message traitLine(@Nonnull Piece piece, @Nonnull Trait trait, boolean facets) {
        String amount = JewelryStatTuning.formatForDisplay(trait.statId(), trait.amount());
        String sign = trait.amount() >= 0f ? "+" : "";
        Message line = Message.raw(sign + amount + " ")
            .insert(Message.translation(JEWELRY + "stat." + trait.statId()).color(C_STAT));
        if (facets && piece.gem() != null) {
            line.insert(Message.raw("  "))
                .insert(t("traitFrom")
                    .param("gem", Message.translation(JEWELRY + "gem." + piece.gem().name()).color(C_DIM))
                    .param("facet", Message.raw(String.valueOf(trait.facet())).color(C_DIM))
                    .color(C_DIM));
        }
        return line;
    }
}
