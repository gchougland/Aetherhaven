package com.hexvane.aetherhaven.production;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Merges base {@link ProductionCatalog.Entry} with {@link WorkplaceUnlockCatalog} gating and extra outputs. */
public final class ProductionEffectiveCatalog {
    private ProductionEffectiveCatalog() {}

    @Nullable
    public static ProductionCatalog.Entry effective(
        @Nonnull ProductionCatalog baseCatalog,
        @Nonnull WorkplaceUnlockCatalog unlockCatalog,
        @Nonnull String constructionId,
        @Nonnull PlotProductionState state
    ) {
        ProductionCatalog.Entry base = baseCatalog.get(constructionId);
        if (base == null || base.catalogSize() <= 0) {
            return null;
        }
        Map<String, WorkplaceUnlockCatalog.UnlockLine> umap = unlockCatalog.byItemId(constructionId);
        List<String> ids = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        List<Long> maxS = new ArrayList<>();
        int n = base.catalogSize();
        for (int i = 0; i < n; i++) {
            String id = base.itemAtCursor(i);
            WorkplaceUnlockCatalog.UnlockLine ul = umap.get(id.toLowerCase(Locale.ROOT));
            if (ul == null) {
                append(ids, ticks, maxS, id, base.ticksAtCursor(i), base.maxStorageAtCursor(i));
            } else if (ul.defaultUnlocked() || state.isWorkplaceOutputUnlocked(id)) {
                append(ids, ticks, maxS, id, base.ticksAtCursor(i), base.maxStorageAtCursor(i));
            }
        }
        for (WorkplaceUnlockCatalog.UnlockLine ul : unlockCatalog.linesForWorkplace(constructionId)) {
            if (base.containsItemId(ul.itemId())) {
                continue;
            }
            if (ul.defaultUnlocked() || state.isWorkplaceOutputUnlocked(ul.itemId())) {
                append(ids, ticks, maxS, ul.itemId(), ul.ticks(), ul.maxStorage());
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        int[] ta = new int[ticks.size()];
        for (int i = 0; i < ta.length; i++) {
            ta[i] = ticks.get(i);
        }
        long[] ma = new long[maxS.size()];
        for (int i = 0; i < ma.length; i++) {
            ma[i] = maxS.get(i);
        }
        return ProductionCatalog.Entry.create(ids, ta, ma);
    }

    private static void append(List<String> ids, List<Integer> ticks, List<Long> maxS, String id, int t, long mx) {
        ids.add(id);
        ticks.add(Math.max(1, t));
        maxS.add(Math.max(1L, Math.min(ProductionCatalog.MAX_STORAGE_PER_OUTPUT, mx)));
    }
}
