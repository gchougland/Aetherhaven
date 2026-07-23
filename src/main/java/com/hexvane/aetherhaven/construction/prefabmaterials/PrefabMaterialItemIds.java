package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Normalizes unobtainable prefab block ids to player-supplyable item ids for building materials. */
public final class PrefabMaterialItemIds {
    private static final String TRUNK_FULL = "_Trunk_Full";
    private static final String TRUNK = "_Trunk";

    private PrefabMaterialItemIds() {}

    /**
     * Maps full trunk blocks to their regular trunk item (e.g. {@code Wood_Beech_Trunk_Full} → {@code Wood_Beech_Trunk}).
     */
    @Nonnull
    public static String normalize(@Nonnull String itemId) {
        if (itemId.contains(TRUNK_FULL)) {
            return itemId.replace(TRUNK_FULL, TRUNK);
        }
        return itemId;
    }

    /** Merges duplicate rows after {@link #normalize(String)} on item requirements. */
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
            items.merge(normalize(itemId.trim()), requirement.getCount(), Integer::sum);
        }
        return PrefabMaterialsGenerator.toSortedRequirements(items, resources);
    }
}
