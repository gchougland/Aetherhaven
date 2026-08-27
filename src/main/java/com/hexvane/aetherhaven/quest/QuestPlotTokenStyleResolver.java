package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunityCatalogService;
import com.hexvane.aetherhaven.community.CommunityManifestEntry;
import com.hexvane.aetherhaven.community.CommunityRequiredMods;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotBuildingStyles;
import com.hexvane.aetherhaven.town.TownBuildingStyleShowcase;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Picks which construction id to grant as a plot token for a town's preferred building style. */
public final class QuestPlotTokenStyleResolver {
    private QuestPlotTokenStyleResolver() {}

    @Nonnull
    public static String resolveConstructionId(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String baseConstructionId,
        @Nullable TownRecord town
    ) {
        return resolveConstructionId(catalog, baseConstructionId, town, ThreadLocalRandom.current());
    }

    @Nonnull
    public static String resolveConstructionId(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String baseConstructionId,
        @Nullable TownRecord town,
        @Nonnull Random random
    ) {
        String base = baseConstructionId.trim();
        if (base.isEmpty()) {
            return base;
        }
        String preferred =
            town != null
                ? TownBuildingStyleShowcase.effectiveStyleId(town.getPreferredBuildingStyleId())
                : TownBuildingStyleShowcase.DEFAULT_STYLE_ID;
        CommunityCatalogService community = communityCatalogOrNull();
        List<ConstructionDefinition> candidates = new ArrayList<>();
        for (ConstructionDefinition def : catalog.list()) {
            String style = PlotBuildingStyles.styleIdOf(def);
            if (style == null || !preferred.equals(style)) {
                continue;
            }
            String id = def.getId();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!requiredModsSatisfied(def, community)) {
                continue;
            }
            if (!catalog.matchesGameplayConstruction(id, base)) {
                continue;
            }
            if (AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(base) && !isHouseOnly(catalog, id)) {
                continue;
            }
            if (def.isLegacyPlotSupport()) {
                continue;
            }
            candidates.add(def);
        }
        if (candidates.isEmpty()) {
            return base;
        }
        ConstructionDefinition picked = candidates.get(random.nextInt(candidates.size()));
        String id = picked.getId();
        return id != null && !id.isBlank() ? id.trim() : base;
    }

    /** True when the stored construction counts as a house and nothing else. */
    private static boolean isHouseOnly(@Nonnull ConstructionCatalog catalog, @Nonnull String storedId) {
        List<String> gameplayIds = catalog.resolveGameplayConstructionIds(storedId);
        return gameplayIds.size() == 1
            && AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(gameplayIds.get(0));
    }

    private static boolean requiredModsSatisfied(
        @Nonnull ConstructionDefinition def,
        @Nullable CommunityCatalogService community
    ) {
        if (!CommunityRequiredMods.isSatisfied(def.getRequiredMods())) {
            return false;
        }
        if (community == null) {
            return true;
        }
        CommunityManifestEntry entry = community.findEntry(def.getId());
        if (entry == null) {
            return true;
        }
        return CommunityRequiredMods.isSatisfied(entry.getRequiredMods());
    }

    @Nullable
    private static CommunityCatalogService communityCatalogOrNull() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        return plugin != null ? plugin.getCommunityCatalogService() : null;
    }
}
