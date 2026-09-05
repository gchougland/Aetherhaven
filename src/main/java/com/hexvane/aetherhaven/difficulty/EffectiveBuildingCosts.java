package com.hexvane.aetherhaven.difficulty;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialItemIds;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Resolves building material and gold costs from town difficulty settings. */
public final class EffectiveBuildingCosts {
    private final List<MaterialRequirement> materials;
    private final long treasuryGoldCoinCost;

    private EffectiveBuildingCosts(@Nonnull List<MaterialRequirement> materials, long treasuryGoldCoinCost) {
        this.materials = materials;
        this.treasuryGoldCoinCost = treasuryGoldCoinCost;
    }

    @Nonnull
    public static EffectiveBuildingCosts forDefinition(
        @Nonnull ConstructionDefinition def,
        @Nonnull TownDifficultySettings townDifficulty,
        @Nonnull PrefabMaterialsCatalog prefabMaterials
    ) {
        TownDifficultySettings effective = townDifficulty.effectiveForGameplay();
        List<MaterialRequirement> base;
        if (effective.isRequireAllPrefabBlocks()) {
            String cid = def.getId();
            if (prefabMaterials.has(cid)) {
                base = prefabMaterials.getMaterials(cid);
            } else {
                base = def.getMaterials();
            }
            // Re-apply at gameplay time so recipe checks see loaded Item assets (catalog may load earlier).
            base = PrefabMaterialItemIds.mergeNormalized(base);
        } else {
            base = scaleMaterials(def.getMaterials(), effective.getResourceCostMultiplier());
        }
        long gold = scaleGold(def.getTreasuryGoldCoinCost(), effective.getGoldCostMultiplier());
        return new EffectiveBuildingCosts(base, gold);
    }

    @Nonnull
    public List<MaterialRequirement> getMaterials() {
        return materials;
    }

    public long getTreasuryGoldCoinCost() {
        return treasuryGoldCoinCost;
    }

    @Nonnull
    private static List<MaterialRequirement> scaleMaterials(@Nonnull List<MaterialRequirement> source, double multiplier) {
        if (multiplier == 1.0 || source.isEmpty()) {
            return source;
        }
        List<MaterialRequirement> out = new ArrayList<>(source.size());
        for (MaterialRequirement m : source) {
            int scaled = scaleCount(m.getCount(), multiplier);
            if (scaled <= 0) {
                continue;
            }
            if (m.getResourceTypeId() != null && !m.getResourceTypeId().isBlank()) {
                out.add(MaterialRequirement.ofResourceType(m.getResourceTypeId(), scaled));
            } else if (m.getItemId() != null && !m.getItemId().isBlank()) {
                out.add(MaterialRequirement.ofItem(m.getItemId(), scaled));
            }
        }
        return List.copyOf(out);
    }

    private static int scaleCount(int count, double multiplier) {
        if (count <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(count * multiplier));
    }

    private static long scaleGold(long gold, double multiplier) {
        if (gold <= 0) {
            return 0L;
        }
        return Math.max(0L, (long) Math.floor(gold * multiplier));
    }
}
