package com.hexvane.aetherhaven.festival.market;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Contest scoring for the shared town stall. */
public final class MarketScore {
    private MarketScore() {}

    public record Breakdown(int itemPoints, int categoryBonus, int total, int uniqueCategories) {}

    @Nonnull
    public static Breakdown scoreSlots(@Nullable List<String> itemIds) {
        MarketItemCatalog catalog = MarketItemCatalog.get();
        int itemPoints = 0;
        Set<String> categories = new LinkedHashSet<>();
        if (itemIds != null) {
            for (String raw : itemIds) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String id = raw.trim();
                MarketItemCatalog.Entry entry = catalog.entry(id);
                if (entry == null) {
                    itemPoints += MarketIds.UNLISTED_POINTS;
                    categories.add(MarketIds.UNLISTED_CATEGORY);
                } else {
                    itemPoints += entry.points();
                    categories.add(entry.category());
                }
            }
        }
        int bonus = categories.size() * catalog.categoryBonus();
        return new Breakdown(itemPoints, bonus, itemPoints + bonus, categories.size());
    }

    /** 1st through 4th. Equal scores lose to the rival stand. */
    public static int place(int townScore) {
        int better = 0;
        if (townScore <= MarketIds.RIVAL_BRAMBLEFORD) {
            better++;
        }
        if (townScore <= MarketIds.RIVAL_MILLSHADE) {
            better++;
        }
        if (townScore <= MarketIds.RIVAL_GOLDHOLLOW) {
            better++;
        }
        return better + 1;
    }

    @Nonnull
    public static String winnerId(int townScore, @Nonnull String townName) {
        if (townScore > MarketIds.RIVAL_GOLDHOLLOW) {
            return townName;
        }
        if (townScore > MarketIds.RIVAL_MILLSHADE) {
            return MarketIds.RIVAL_GOLDHOLLOW_ID;
        }
        if (townScore > MarketIds.RIVAL_BRAMBLEFORD) {
            return MarketIds.RIVAL_MILLSHADE_ID;
        }
        return MarketIds.RIVAL_BRAMBLEFORD_ID;
    }

    public static boolean isRivalWinnerId(@Nullable String winnerId) {
        if (winnerId == null) {
            return false;
        }
        String key = winnerId.trim().toLowerCase(Locale.ROOT);
        return MarketIds.RIVAL_BRAMBLEFORD_ID.equals(key)
            || MarketIds.RIVAL_MILLSHADE_ID.equals(key)
            || MarketIds.RIVAL_GOLDHOLLOW_ID.equals(key);
    }
}
