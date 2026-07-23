package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Renders {@link MaterialRequirement} stacks on dialogue choice rows (right side item grid). */
public final class DialogueChoiceRequirementsUi {
    private DialogueChoiceRequirementsUi() {}

    @Nonnull
    public static String rowDocument(@Nonnull List<MaterialRequirement> requirements) {
        return requirements.isEmpty() ? "Aetherhaven/DialogueChoiceRow.ui" : "Aetherhaven/DialogueChoiceRowWithItems.ui";
    }

    public static void applyItemGrid(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String rowSelector,
        @Nonnull List<MaterialRequirement> requirements
    ) {
        if (requirements.isEmpty()) {
            return;
        }
        String gridSel = rowSelector + " #ItemRequirementsPanel #RequirementItems";

        List<ItemGridSlot> slots = new ArrayList<>();
        for (MaterialRequirement req : requirements) {
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
