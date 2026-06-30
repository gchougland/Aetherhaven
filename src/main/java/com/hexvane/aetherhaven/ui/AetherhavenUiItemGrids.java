package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipMessages;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipWire;
import com.hexvane.aetherhaven.plot.PlotTokenIconWire;
import com.hexvane.aetherhaven.plot.PlotTokenVirtualItemRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.nio.file.Path;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helpers for {@code ItemGrid.Slots}.
 *
 * <p>Jewelry grids use {@link JewelryTooltipWire#forItemGrid} (virtual id, no metadata) plus plain
 * {@link ItemGridSlot#setDescription(String)}. {@link com.hexvane.aetherhaven.jewelry.JewelryTooltipPacketAdapter}
 * strips any leaked metadata from outbound {@code CustomPage} packets.</p>
 */
public final class AetherhavenUiItemGrids {
    private AetherhavenUiItemGrids() {}

    /**
     * Stack safe for {@link ItemGridSlot}: custom UI decodes metadata as {@code ClientItemMetadata}; strip BSON /
     * {@code ItemDisplay} blobs that crash the client (see {@link com.hexvane.aetherhaven.jewelry.JewelryTooltipWire}).
     */
    @Nonnull
    public static ItemStack plainStackForUi(@Nonnull String itemId, int quantity) {
        ItemStack probe = new ItemStack(itemId, quantity);
        return new ItemStack(itemId, probe.getQuantity(), probe.getDurability(), probe.getMaxDurability(), null);
    }

    @Nonnull
    public static ItemGridSlot plainSlotForUi(@Nonnull String itemId) {
        return new ItemGridSlot(plainStackForUi(itemId, 1));
    }

    /** True when {@code itemId} is registered (unknown ids crash client {@code ItemGrid} tooltips). */
    public static boolean isKnownItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return Item.getAssetMap().getAsset(itemId.trim()) != null;
    }

    /**
     * {@link ItemGridSlot} with metadata stripped for custom UI. Returns null when the id is missing from the asset
     * map so callers can skip the slot instead of crashing the client.
     */
    /** Plot token icon for custom UI; uses virtual ids for unified tokens (see {@link PlotTokenIconWire#forItemGrid}). */
    @Nullable
    public static ItemGridSlot plotTokenSlotForConstruction(
        @Nonnull String plotStoredConstructionId,
        @Nullable ConstructionCatalog catalog
    ) {
        ItemStack stack = PlotTokenIconWire.forItemGrid(plotStoredConstructionId, catalog);
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        String itemId = stack.getItemId();
        if (PlotTokenVirtualItemRegistry.isVirtualId(itemId)) {
            Path dataDir = null;
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin != null) {
                dataDir = plugin.getDataDirectory();
            }
            if (!ConstructionTokenIconPath.isIconAvailable(plotStoredConstructionId.trim(), dataDir)) {
                return null;
            }
        }
        if (PlotTokenVirtualItemRegistry.isVirtualId(itemId) || isKnownItemId(itemId)) {
            return new ItemGridSlot(plainStackForUi(itemId, stack.getQuantity()));
        }
        return null;
    }

    @Nullable
    public static ItemGridSlot slotForKnownItem(@Nonnull String itemId, int quantity) {
        String id = itemId.trim();
        if (!isKnownItemId(id)) {
            return null;
        }
        ItemStack stack = plainStackForUi(id, Math.max(1, quantity));
        if (JewelryItemIds.isJewelry(id)) {
            return jewelrySlotForUi(stack);
        }
        return new ItemGridSlot(stack);
    }

    @Nonnull
    public static ItemGridSlot jewelrySlotForUi(@Nonnull ItemStack inventoryJewelryStack) {
        ItemGridSlot slot = new ItemGridSlot(JewelryTooltipWire.forItemGrid(inventoryJewelryStack));
        if (!ItemStack.isEmpty(inventoryJewelryStack) && JewelryItemIds.isJewelry(inventoryJewelryStack.getItemId())) {
            String desc = JewelryTooltipMessages.toPlainEnglishDescription(inventoryJewelryStack);
            if (desc != null && !desc.isBlank()) {
                slot.setDescription(desc);
            }
        }
        return slot;
    }

    public static void setSingleSlot(@Nonnull UICommandBuilder commandBuilder, @Nonnull String itemGridSelector, @Nonnull ItemGridSlot slot) {
        commandBuilder.set(itemGridSelector + ".Slots", new ItemGridSlot[] {slot});
    }

    public static void setSingleSlot(@Nonnull UICommandBuilder commandBuilder, @Nonnull String itemGridSelector, @Nonnull ItemStack stack) {
        if (!ItemStack.isEmpty(stack) && JewelryItemIds.isJewelry(stack.getItemId())) {
            setSingleSlot(commandBuilder, itemGridSelector, jewelrySlotForUi(stack));
            return;
        }
        commandBuilder.set(itemGridSelector + ".Slots", new ItemGridSlot[] {new ItemGridSlot(plainStackForUi(stack.getItemId(), stack.getQuantity()))});
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
