package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

/** Builds client-safe jewelry stacks for inventory packets and custom UI item grids. */
public final class JewelryTooltipWire {

    private JewelryTooltipWire() {}

    /**
     * Stack for {@link com.hypixel.hytale.server.core.ui.ItemGridSlot}: virtual id for rarity border, no metadata.
     * Custom UI decodes stack metadata as {@code ClientItemMetadata}; any BSON object (including {@code ItemDisplay})
     * fails — use {@link com.hypixel.hytale.server.core.ui.ItemGridSlot#setDescription(String)} for hover text instead.
     */
    @Nonnull
    public static ItemStack forItemGrid(@Nonnull ItemStack inventoryStack) {
        if (ItemStack.isEmpty(inventoryStack) || !JewelryItemIds.isJewelry(inventoryStack.getItemId())) {
            return inventoryStack;
        }
        if (JewelryPieceKind.isArtifact(inventoryStack.getItemId())) {
            return inventoryStack;
        }
        ItemStack prepared = JewelryMetadata.ensureRolled(inventoryStack);
        String itemId = prepared.getItemId();
        JewelryRarity rarity = JewelryMetadata.readRarity(prepared);
        if (rarity != null) {
            itemId = JewelryVirtualItemRegistry.generateVirtualId(itemId, rarity.wireName());
        }
        return new ItemStack(
            itemId,
            prepared.getQuantity(),
            prepared.getDurability(),
            prepared.getMaxDurability(),
            null
        );
    }

    /**
     * Registers a virtual {@link ItemBase} for this stack if needed. Call before sending custom UI when possible.
     */
    public static void collectVirtualDefinition(
        @Nonnull ItemStack stack,
        @Nonnull JewelryVirtualItemRegistry registry,
        @Nonnull Map<String, ItemBase> out
    ) {
        if (ItemStack.isEmpty(stack) || !JewelryMetadata.hasJewelryMeta(stack)) {
            return;
        }
        String baseId = stack.getItemId();
        if (JewelryVirtualItemRegistry.isVirtualId(baseId)) {
            String resolved = JewelryVirtualItemRegistry.getBaseItemId(baseId);
            if (resolved == null) {
                return;
            }
            baseId = resolved;
        }
        if (!JewelryItemIds.isJewelry(baseId)) {
            return;
        }
        JewelryRarity rarity = readRarity(stack);
        if (rarity == null) {
            return;
        }
        putVirtualDefinition(baseId, rarity, registry, out);
    }

    @Nullable
    private static JewelryRarity readRarity(@Nonnull ItemStack stack) {
        JewelryRarity rarity = JewelryMetadata.readRarity(stack);
        if (rarity != null) {
            return rarity;
        }
        String wire = JewelryVirtualItemRegistry.getRarityWireFromVirtualId(stack.getItemId());
        return wire != null ? JewelryRarity.fromWire(wire) : null;
    }

    /**
     * Virtualize a packet/UI item stack document in place. Returns true if the document was modified.
     */
    public static boolean applyToItemStackDocument(
        @Nonnull BsonDocument itemStackDoc,
        @Nonnull JewelryVirtualItemRegistry registry,
        @Nonnull Map<String, ItemBase> out
    ) {
        String idKey = "Id";
        BsonValue itemIdValue = itemStackDoc.get(idKey);
        if (itemIdValue == null || !itemIdValue.isString()) {
            idKey = "ItemId";
            itemIdValue = itemStackDoc.get(idKey);
            if (itemIdValue == null || !itemIdValue.isString()) {
                return false;
            }
        }

        String itemId = itemIdValue.asString().getValue();
        String baseId = itemId;
        if (JewelryVirtualItemRegistry.isVirtualId(itemId)) {
            String resolved = JewelryVirtualItemRegistry.getBaseItemId(itemId);
            if (resolved != null) {
                baseId = resolved;
            }
        }

        BsonValue metadataValue = itemStackDoc.get("Metadata");
        String metadataJson = null;
        if (metadataValue != null && metadataValue.isDocument()) {
            metadataJson = metadataValue.asDocument().toJson();
        }

        boolean modified = false;
        JewelryRarity rarity = JewelryMetadata.readRarityFromMetadataJson(metadataJson);
        if (rarity == null && JewelryVirtualItemRegistry.isVirtualId(itemId)) {
            String wire = JewelryVirtualItemRegistry.getRarityWireFromVirtualId(itemId);
            rarity = wire != null ? JewelryRarity.fromWire(wire) : null;
        }
        if (JewelryItemIds.isJewelry(baseId) && rarity != null) {
            String virtualId = JewelryVirtualItemRegistry.generateVirtualId(baseId, rarity.wireName());
            ItemBase virtualBase = putVirtualDefinition(baseId, rarity, registry, out);
            if (!virtualId.equals(itemId)) {
                itemStackDoc.put(idKey, new BsonString(virtualId));
                modified = true;
            }
            if (virtualBase != null) {
                itemStackDoc.put("Quality", new org.bson.BsonInt32(virtualBase.qualityIndex));
                modified = true;
            }
        }

        if (metadataValue != null) {
            itemStackDoc.remove("Metadata");
            modified = true;
        }
        return modified;
    }

    @Nullable
    private static ItemBase putVirtualDefinition(
        @Nonnull String baseId,
        @Nonnull JewelryRarity rarity,
        @Nonnull JewelryVirtualItemRegistry registry,
        @Nonnull Map<String, ItemBase> out
    ) {
        String virtualId = JewelryVirtualItemRegistry.generateVirtualId(baseId, rarity.wireName());
        int qualityIndex = JewelryItemQualityIndex.forRarity(rarity, baseId);
        ItemBase virtualBase = registry.getOrCreateVirtualItemBase(baseId, virtualId, qualityIndex);
        if (virtualBase != null) {
            out.put(virtualId, virtualBase);
        }
        return virtualBase;
    }

}
