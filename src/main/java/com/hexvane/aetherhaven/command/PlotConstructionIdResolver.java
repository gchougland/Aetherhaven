package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.Message;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Matches plots by gameplay construction id with deterministic index ordering. */
public final class PlotConstructionIdResolver {
    private PlotConstructionIdResolver() {}

    public record PlotMatch(@Nonnull PlotInstance plot, int index) {}

    public record ResolveResult(@Nullable PlotMatch single, @Nullable List<PlotMatch> ambiguous, @Nullable Message error) {
        public boolean isOk() {
            return single != null && error == null;
        }
    }

    @Nonnull
    public static ResolveResult resolve(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String constructionIdInput,
        @Nullable Integer indexOneBased
    ) {
        String want = constructionIdInput.trim();
        if (want.isEmpty()) {
            return new ResolveResult(null, null, Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructNoId"));
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        List<PlotInstance> matches = new ArrayList<>();
        for (PlotInstance plot : town.getPlotInstances()) {
            if (matchesConstructionId(catalog, plot, want)) {
                matches.add(plot);
            }
        }
        matches.sort(plotSort());
        if (matches.isEmpty()) {
            return new ResolveResult(
                null,
                null,
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructNoMatch").param("id", want)
            );
        }
        List<PlotMatch> indexed = new ArrayList<>(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            indexed.add(new PlotMatch(matches.get(i), i + 1));
        }
        if (matches.size() == 1 && indexOneBased == null) {
            return new ResolveResult(indexed.get(0), null, null);
        }
        if (indexOneBased != null) {
            int idx = indexOneBased;
            if (idx < 1 || idx > indexed.size()) {
                return new ResolveResult(null, indexed, Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructBadIndex"));
            }
            return new ResolveResult(indexed.get(idx - 1), null, null);
        }
        return new ResolveResult(null, indexed, null);
    }

    private static boolean matchesConstructionId(
        @Nonnull ConstructionCatalog catalog, @Nonnull PlotInstance plot, @Nonnull String want
    ) {
        String stored = plot.getConstructionId();
        if (stored == null) {
            return false;
        }
        if (want.equals(stored)) {
            return true;
        }
        return want.equals(catalog.resolveGameplayConstructionId(stored));
    }

    @Nonnull
    private static Comparator<PlotInstance> plotSort() {
        return Comparator.comparingInt(PlotInstance::getSignY)
            .thenComparingInt(PlotInstance::getSignX)
            .thenComparingInt(PlotInstance::getSignZ)
            .thenComparing(p -> p.getPlotId().toString());
    }

    @Nonnull
    public static String formatAmbiguousLine(@Nonnull PlotMatch match) {
        PlotInstance p = match.plot();
        String state = p.getState() != null ? p.getState().name() : PlotInstanceState.COMPLETE.name();
        String cid = p.getConstructionId() != null ? p.getConstructionId() : "";
        return "[" + match.index() + "] " + cid + " — " + state + " at " + p.getSignX() + ", " + p.getSignY() + ", " + p.getSignZ();
    }
}
