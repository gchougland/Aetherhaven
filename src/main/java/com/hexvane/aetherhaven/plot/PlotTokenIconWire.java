package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

/** Builds client-safe unified plot token stacks for inventory packets and custom UI item grids. */
public final class PlotTokenIconWire {

    private PlotTokenIconWire() {}

    public static boolean isUnifiedPlotToken(@Nullable String itemId) {
        return itemId != null && AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(itemId);
    }

    public static boolean hasPlotTokenMeta(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return false;
        }
        if (!AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(stack.getItemId())) {
            return false;
        }
        return PlotTokenMetadata.readConstructionId(stack) != null;
    }

    public static boolean hasPlotTokenMetaFromMetadataJson(@Nullable String metadataJson) {
        return readConstructionIdFromMetadataJson(metadataJson) != null;
    }

    /**
     * Stack for {@link com.hypixel.hytale.server.core.ui.ItemGridSlot}: per-building virtual id for unified tokens,
     * legacy item id otherwise. Custom UI cannot carry plot-token BSON metadata — use the plot's stored construction
     * id so variant icons resolve correctly (not the gameplay parent id).
     */
    @Nonnull
    public static ItemStack forItemGrid(@Nonnull String plotStoredConstructionId, @Nullable ConstructionCatalog catalog) {
        String stored = plotStoredConstructionId.trim();
        if (stored.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ConstructionDefinition def = catalog != null ? catalog.get(stored) : null;
        if (def != null) {
            String legacy = def.getPlotTokenItemId();
            if (legacy != null
                && !legacy.isBlank()
                && !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(legacy.trim())) {
                return new ItemStack(legacy.trim(), 1);
            }
        }
        return new ItemStack(PlotTokenVirtualItemRegistry.generateVirtualId(stored), 1);
    }

    @Nullable
    public static String readConstructionIdFromMetadataJson(@Nullable String metadataJson) {
        BsonDocument root = readPlotTokenRootFromMetadataJson(metadataJson);
        if (root == null) {
            return null;
        }
        BsonValue v = root.get(PlotTokenMetadata.FIELD_CONSTRUCTION_ID);
        if (v == null || !v.isString()) {
            return null;
        }
        String s = v.asString().getValue();
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    @Nullable
    private static BsonDocument readPlotTokenRootFromMetadataJson(@Nullable String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            BsonDocument meta = BsonDocument.parse(metadataJson);
            BsonValue v = meta.get(PlotTokenMetadata.BSON_KEY);
            if (v == null || !v.isDocument()) {
                return null;
            }
            return v.asDocument();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Registers a virtual {@link ItemBase} for this stack if needed. Call before sending custom UI when possible.
     */
    public static void collectVirtualDefinition(
        @Nonnull ItemStack stack,
        @Nonnull PlotTokenVirtualItemRegistry registry,
        @Nonnull Map<String, ItemBase> out
    ) {
        if (!hasPlotTokenMeta(stack)) {
            return;
        }
        String constructionId = PlotTokenMetadata.readConstructionId(stack);
        if (constructionId == null) {
            return;
        }
        putVirtualDefinition(constructionId, registry, out);
    }

    /**
     * Virtualize a packet item in place. Returns true if {@code itemId} was swapped to a virtual id.
     */
    public static boolean applyToPacketItem(
        @Nonnull ItemWithAllMetadata item,
        @Nonnull PlotTokenVirtualItemRegistry registry,
        @Nonnull Map<String, ItemBase> out
    ) {
        String itemId = item.itemId;
        String baseId = itemId;
        if (PlotTokenVirtualItemRegistry.isVirtualId(itemId)) {
            baseId = AetherhavenConstants.PLOT_TOKEN_UNIFIED;
        }
        if (!AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(baseId)) {
            return false;
        }
        String constructionId = readConstructionIdFromMetadataJson(item.metadata);
        if (constructionId == null && PlotTokenVirtualItemRegistry.isVirtualId(itemId)) {
            constructionId = PlotTokenVirtualItemRegistry.getConstructionIdFromVirtualId(itemId);
        }
        if (constructionId == null) {
            return false;
        }
        String virtualId = PlotTokenVirtualItemRegistry.generateVirtualId(constructionId);
        ItemBase virtualBase = putVirtualDefinition(constructionId, registry, out);
        if (virtualBase == null) {
            return false;
        }
        if (!virtualId.equals(item.itemId)) {
            item.itemId = virtualId;
            return true;
        }
        return false;
    }

    /**
     * Virtualize a packet/UI item stack document in place. Returns true if the document was modified.
     */
    public static boolean applyToItemStackDocument(
        @Nonnull BsonDocument itemStackDoc,
        @Nonnull PlotTokenVirtualItemRegistry registry,
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
        String baseId = PlotTokenVirtualItemRegistry.isVirtualId(itemId)
            ? AetherhavenConstants.PLOT_TOKEN_UNIFIED
            : itemId;

        BsonValue metadataValue = itemStackDoc.get("Metadata");
        String metadataJson = null;
        if (metadataValue != null && metadataValue.isDocument()) {
            metadataJson = metadataValue.asDocument().toJson();
        }

        boolean modified = false;
        if (AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(baseId)) {
            String constructionId = readConstructionIdFromMetadataJson(metadataJson);
            if (constructionId == null && PlotTokenVirtualItemRegistry.isVirtualId(itemId)) {
                constructionId = PlotTokenVirtualItemRegistry.getConstructionIdFromVirtualId(itemId);
            }
            if (constructionId != null) {
                String virtualId = PlotTokenVirtualItemRegistry.generateVirtualId(constructionId);
                if (putVirtualDefinition(constructionId, registry, out) != null && !virtualId.equals(itemId)) {
                    itemStackDoc.put(idKey, new BsonString(virtualId));
                    modified = true;
                }
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
        @Nonnull String constructionId,
        @Nonnull PlotTokenVirtualItemRegistry registry,
        @Nonnull Map<String, ItemBase> out
    ) {
        String virtualId = PlotTokenVirtualItemRegistry.generateVirtualId(constructionId);
        ItemBase virtualBase = registry.getOrCreateVirtualItemBase(virtualId, constructionId);
        if (virtualBase != null) {
            out.put(virtualId, virtualBase);
        }
        return virtualBase;
    }
}
