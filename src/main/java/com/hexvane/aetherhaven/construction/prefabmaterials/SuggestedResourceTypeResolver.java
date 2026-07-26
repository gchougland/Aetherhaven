package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Maps prefab block item ids to simplified build cost resource types or specialty items. */
public final class SuggestedResourceTypeResolver {
    private static final List<String> CANONICAL_PRIORITY = List.of(
        "Wood_All",
        "Rock",
        "Soils",
        "Rubble",
        "Sands"
    );

    private SuggestedResourceTypeResolver() {}

    /** Outcome of resolving one prefab anchor block to a build cost line. */
    public sealed interface Target permits Target.Skip, Target.SpecialtyItem, Target.ResourceType {
        record Skip() implements Target {}

        record SpecialtyItem(@Nonnull String itemId) implements Target {}

        record ResourceType(@Nonnull String resourceTypeId) implements Target {}
    }

    @Nonnull
    public static Target resolve(
        @Nonnull String itemId,
        @Nonnull PrefabMaterialConversionTable conversions
    ) {
        String id = itemId.trim();
        if (id.isEmpty()) {
            return new Target.Skip();
        }
        if (id.startsWith("Bench_")) {
            return new Target.SpecialtyItem(id);
        }
        ConversionRule rule = conversions.lookup(id);
        if (rule != null && rule.skip) {
            return new Target.Skip();
        }
        Item item = lookupItem(id);
        String resourceType = resolveCanonicalResourceType(id, item);
        if (resourceType != null) {
            return new Target.ResourceType(resourceType);
        }
        return new Target.Skip();
    }

    @Nullable
    static String resolveCanonicalResourceType(@Nonnull String itemId, @Nullable Item item) {
        String fromTypes = canonicalFromItemResourceTypes(item);
        if (fromTypes != null) {
            return fromTypes;
        }
        return canonicalFromItemIdHeuristic(itemId, item);
    }

    @Nullable
    private static String canonicalFromItemResourceTypes(@Nullable Item item) {
        if (item == null) {
            return null;
        }
        ItemResourceType[] types = item.getResourceTypes();
        if (types == null || types.length == 0) {
            return null;
        }
        String best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (ItemResourceType type : types) {
            if (type == null || type.id == null || type.id.isBlank()) {
                continue;
            }
            String canonical = canonicalizeResourceTypeId(type.id.trim());
            if (canonical == null) {
                continue;
            }
            int priority = CANONICAL_PRIORITY.indexOf(canonical);
            if (priority >= 0 && priority < bestPriority) {
                bestPriority = priority;
                best = canonical;
            }
        }
        return best;
    }

    @Nullable
    static String canonicalizeResourceTypeId(@Nonnull String resourceTypeId) {
        if (resourceTypeId.equals("Wood_All")) {
            return "Wood_All";
        }
        if (resourceTypeId.startsWith("Wood_")) {
            return "Wood_All";
        }
        if (resourceTypeId.equals("Rock") || resourceTypeId.startsWith("Rock_")) {
            return "Rock";
        }
        if (resourceTypeId.equals("Soils")) {
            return "Soils";
        }
        if (resourceTypeId.equals("Rubble")) {
            return "Rubble";
        }
        if (resourceTypeId.equals("Sands")) {
            return "Sands";
        }
        return null;
    }

    @Nullable
    private static String canonicalFromItemIdHeuristic(@Nonnull String itemId, @Nullable Item item) {
        if (itemId.startsWith("Rock_")) {
            return "Rock";
        }
        if (itemId.startsWith("Rubble_")) {
            return "Rubble";
        }
        if (itemId.startsWith("Soil_")) {
            return "Soils";
        }
        if (itemId.startsWith("Sand_")) {
            return "Sands";
        }
        if (itemId.startsWith("Wood_") || itemId.contains("_Branch")) {
            return "Wood_All";
        }
        if (itemId.startsWith("Furniture_")) {
            if (item != null && InventoryMaterials.itemHasResourceType(item, "Rock")) {
                return "Rock";
            }
            return "Wood_All";
        }
        if (itemId.startsWith("Cloth_")) {
            return null;
        }
        return null;
    }

    @Nullable
    private static Item lookupItem(@Nonnull String itemId) {
        var store = Item.getAssetStore();
        if (store == null) {
            return null;
        }
        return store.getAssetMap().getAsset(itemId);
    }
}
