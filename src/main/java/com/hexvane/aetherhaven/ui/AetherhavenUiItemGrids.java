package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipMessages;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipText;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Helpers for {@code ItemGrid.Slots}.
 *
 * <p>Retail clients decode each slot's {@link ItemStack} metadata into a fixed {@code ClientItemMetadata} shape; mod
 * BSON (see {@link JewelryMetadata#BSON_KEY}) throws before the UI loads. DynamicTooltipsLib can rewrite some custom UI
 * payloads, but that intercept is not guaranteed in every server layout, so jewelry grids use a stack with
 * {@code null} metadata on the wire plus {@link ItemGridSlot#setDescription(String)}.</p>
 *
 * <p>Description lines match {@link com.hexvane.aetherhaven.jewelry.AetherhavenJewelryDynamicTooltipProvider} /
 * {@link JewelryTooltipText#dynamicDescriptionLines} when jewelry BSON is present; otherwise we fall back to
 * {@link JewelryTooltipMessages}.</p>
 */
public final class AetherhavenUiItemGrids {
    private AetherhavenUiItemGrids() {}

    @Nonnull
    public static ItemGridSlot jewelrySlotForUi(@Nonnull ItemStack inventoryJewelryStack) {
        ItemStack wire =
            new ItemStack(
                inventoryJewelryStack.getItemId(),
                inventoryJewelryStack.getQuantity(),
                inventoryJewelryStack.getDurability(),
                inventoryJewelryStack.getMaxDurability(),
                null
            );
        ItemGridSlot slot = new ItemGridSlot(wire);

        if (ItemStack.isEmpty(inventoryJewelryStack) || !JewelryItemIds.isJewelry(inventoryJewelryStack.getItemId())) {
            return slot;
        }

        List<String> lines = JewelryTooltipText.dynamicDescriptionLines(inventoryJewelryStack);
        String desc;
        if (!lines.isEmpty()) {
            desc = String.join("\n", lines);
        } else {
            desc = JewelryTooltipMessages.toPlainEnglishDescription(inventoryJewelryStack);
        }
        if (desc != null && !desc.isBlank()) {
            slot.setDescription(desc);
        }
        return slot;
    }

    public static void setSingleSlot(@Nonnull UICommandBuilder commandBuilder, @Nonnull String itemGridSelector, @Nonnull ItemGridSlot slot) {
        commandBuilder.set(itemGridSelector + ".Slots", new ItemGridSlot[] {slot});
    }

    public static void setSingleSlot(@Nonnull UICommandBuilder commandBuilder, @Nonnull String itemGridSelector, @Nonnull ItemStack stack) {
        commandBuilder.set(itemGridSelector + ".Slots", new ItemGridSlot[] {new ItemGridSlot(stack)});
    }

    public static void setSingleSlotEmpty(@Nonnull UICommandBuilder commandBuilder, @Nonnull String itemGridSelector) {
        commandBuilder.set(itemGridSelector + ".Slots", new ItemGridSlot[] {new ItemGridSlot()});
    }

    public static void setSlots(@Nonnull UICommandBuilder commandBuilder, @Nonnull String itemGridSelector, @Nonnull ItemGridSlot[] slots) {
        commandBuilder.set(itemGridSelector + ".Slots", slots);
    }

    public static void hide(@Nonnull UICommandBuilder commandBuilder, @Nonnull String itemGridSelector) {
        commandBuilder.set(itemGridSelector + ".Slots", new ItemGridSlot[0]);
    }
}
