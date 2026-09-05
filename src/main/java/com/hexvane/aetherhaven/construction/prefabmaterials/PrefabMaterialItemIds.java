package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Normalizes unobtainable prefab block ids to player-supplyable item ids for building materials. */
public final class PrefabMaterialItemIds {
    private static final String TRUNK_FULL = "_Trunk_Full";
    private static final String TRUNK = "_Trunk";
    private static final String CHEST_LARGE = "_Chest_Large";
    private static final String CHEST_SMALL = "_Chest_Small";
    private static final String FURNITURE_PREFIX = "Furniture_";

    private PrefabMaterialItemIds() {}

    /**
     * Maps full trunk blocks to their regular trunk item (e.g. {@code Wood_Beech_Trunk_Full} → {@code Wood_Beech_Trunk}).
     * Large chests are remapped with quantity in {@link #mergeNormalized} only — do not collapse them here or the
     * generator would count them 1:1 as small chests.
     */
    @Nonnull
    public static String normalize(@Nonnull String itemId) {
        if (itemId.contains(TRUNK_FULL)) {
            return itemId.replace(TRUNK_FULL, TRUNK);
        }
        return itemId;
    }

    /**
     * Merges duplicate rows after {@link #normalize(String)} on item requirements. Large chests become two small
     * chests of the same type. Furniture with no crafting recipe is dropped (unobtainable).
     */
    @Nonnull
    public static List<MaterialRequirement> mergeNormalized(@Nonnull List<MaterialRequirement> source) {
        Map<String, Integer> items = new HashMap<>();
        Map<String, Integer> resources = new HashMap<>();
        for (MaterialRequirement requirement : source) {
            String resourceTypeId = requirement.getResourceTypeId();
            if (resourceTypeId != null && !resourceTypeId.isBlank()) {
                resources.merge(resourceTypeId.trim(), requirement.getCount(), Integer::sum);
                continue;
            }
            String itemId = requirement.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            String original = itemId.trim();
            int count = requirement.getCount();
            if (count <= 0) {
                continue;
            }
            String normalized = normalize(original);
            if (normalized.endsWith(CHEST_LARGE)) {
                normalized = normalized.substring(0, normalized.length() - CHEST_LARGE.length()) + CHEST_SMALL;
                count *= 2;
            }
            if (shouldDropUncraftableFurniture(normalized)) {
                continue;
            }
            items.merge(normalized, count, Integer::sum);
        }
        return PrefabMaterialsGenerator.toSortedRequirements(items, resources);
    }

    /**
     * True when this furniture item exists in the item catalog and has no crafting recipe. When assets are unavailable
     * (tests / early boot), keep the requirement.
     */
    static boolean shouldDropUncraftableFurniture(@Nonnull String itemId) {
        if (!itemId.startsWith(FURNITURE_PREFIX)) {
            return false;
        }
        try {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) {
                return false;
            }
            return !item.hasRecipesToGenerate();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
