package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Groups marketplace festival looks under the holiday they count as. */
public final class CommunityFestivalLookGrouping {
    public static final String GROUP_PREFIX = "festivallook:";

    private CommunityFestivalLookGrouping() {}

    public static boolean isFestivalLookGroupKey(@Nullable String groupKey) {
        return groupKey != null && groupKey.startsWith(GROUP_PREFIX);
    }

    @Nonnull
    public static String groupKeyFor(@Nonnull CommunityManifestEntry entry) {
        String base = entry.getCountsAsFestivalId();
        if (base == null || base.isBlank()) {
            return GROUP_PREFIX + entry.getId().toLowerCase(Locale.ROOT);
        }
        return GROUP_PREFIX + base.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public static String groupKeyForBase(@Nonnull String baseFestivalId) {
        return GROUP_PREFIX + baseFestivalId.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public static List<PlotCraftingCatalog.GroupEntry> toGroups(
        @Nonnull Map<String, List<CommunityManifestEntry>> byBase,
        @Nonnull Map<String, String> baseDisplayNames
    ) {
        List<PlotCraftingCatalog.GroupEntry> out = new ArrayList<>(byBase.size());
        for (Map.Entry<String, List<CommunityManifestEntry>> e : byBase.entrySet()) {
            List<CommunityManifestEntry> looks = new ArrayList<>(e.getValue());
            looks.sort(Comparator.comparing(a -> a.getDisplayName().toLowerCase(Locale.ROOT)));
            List<PlotCraftingCatalog.VariantEntry> variants = new ArrayList<>(looks.size());
            for (CommunityManifestEntry look : looks) {
                variants.add(
                    new PlotCraftingCatalog.VariantEntry(look.getId(), look.getDisplayName(), look.prefabPathKey())
                );
            }
            String baseId = e.getKey().startsWith(GROUP_PREFIX) ? e.getKey().substring(GROUP_PREFIX.length()) : e.getKey();
            String display = baseDisplayNames.getOrDefault(baseId, looks.isEmpty() ? baseId : looks.get(0).getDisplayName());
            out.add(new PlotCraftingCatalog.GroupEntry(e.getKey(), display, variants));
        }
        out.sort(Comparator.comparing(g -> g.displayName().toLowerCase(Locale.ROOT)));
        return out;
    }
}
