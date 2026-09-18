package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Renders what a dialogue choice costs on its row: the gold, drawn by the economy provider in {@code #Cost} after
 * the text, and the {@link MaterialRequirement} items in the grid on the right.
 */
public final class DialogueChoiceRequirementsUi {
    /** FontSize of $C.@DefaultLabelStyle, the choice text the gold is drawn next to. */
    private static final int FONT_SIZE = 16;

    private DialogueChoiceRequirementsUi() {}

    @Nonnull
    public static String rowDocument(@Nonnull List<MaterialRequirement> requirements) {
        return items(requirements).isEmpty() ? "Aetherhaven/DialogueChoiceRow.ui" : "Aetherhaven/DialogueChoiceRowWithItems.ui";
    }

    /** The gold among the requirements: coins are not an item to show, the provider draws them. */
    public static long coins(@Nonnull List<MaterialRequirement> requirements) {
        long coins = 0L;
        for (MaterialRequirement req : requirements) {
            if (GoldCoinPayment.coinItemId().equals(req.getItemId())) {
                coins += Math.max(0, req.getCount());
            }
        }
        return coins;
    }

    /** The requirements that are items, the coins left out. */
    @Nonnull
    public static List<MaterialRequirement> items(@Nonnull List<MaterialRequirement> requirements) {
        List<MaterialRequirement> items = new ArrayList<>();
        for (MaterialRequirement req : requirements) {
            if (!GoldCoinPayment.coinItemId().equals(req.getItemId())) {
                items.add(req);
            }
        }
        return items;
    }

    /** Draws {@code gold} after the choice text, nothing when the choice is free. */
    public static void applyCost(@Nonnull UICommandBuilder commandBuilder, @Nonnull String rowSelector, long gold) {
        if (gold > 0L) {
            AetherhavenEconomy.show(commandBuilder, rowSelector + " #Cost", gold, FONT_SIZE);
        }
    }

    public static void applyItemGrid(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String rowSelector,
        @Nonnull List<MaterialRequirement> requirements
    ) {
        List<MaterialRequirement> items = items(requirements);
        if (items.isEmpty()) {
            return;
        }
        String gridSel = rowSelector + " #ItemRequirementsPanel #RequirementItems";

        List<ItemGridSlot> slots = new ArrayList<>();
        for (MaterialRequirement req : items) {
            String itemId = req.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ItemGridSlot slot = AetherhavenUiItemGrids.slotForKnownItem(itemId, Math.max(1, req.getCount()));
            if (slot != null) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            AetherhavenUiItemGrids.hide(commandBuilder, gridSel);
            return;
        }
        AetherhavenUiItemGrids.setSlots(commandBuilder, gridSel, slots.toArray(ItemGridSlot[]::new));
    }
}
