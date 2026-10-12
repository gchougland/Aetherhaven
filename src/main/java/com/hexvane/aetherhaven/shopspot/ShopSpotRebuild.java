package com.hexvane.aetherhaven.shopspot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Saved-state rules for a plot's stalls when its building is pasted again. A rebuilt stall is a fresh prefab block,
 * so the saved row decides what it sells: the row and its listing stay unless the stall is gone or changed kind.
 */
public final class ShopSpotRebuild {
    private ShopSpotRebuild() {}

    /**
     * @param existing the row already saved at the rebuilt stall's block, if any
     * @param templateConfigured whether the pasted block carries a stall setup from the prefab
     * @param templatePlayerControlled the prefab's stall kind, meaningful only when configured
     * @return true when the pasted stall should keep using {@code existing}, listing included
     */
    public static boolean keepsRow(
        @Nullable ShopSpotRecord existing,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        boolean templateConfigured,
        boolean templatePlayerControlled
    ) {
        if (existing == null || !townId.equals(existing.getTownId()) || !plotId.equals(existing.getPlotId())) {
            return false;
        }
        return !templateConfigured || templatePlayerControlled == existing.isPlayerControlled();
    }

    /** Rows of the plot whose stall did not come back; their listings must be handed to the sellers. */
    @Nonnull
    public static List<ShopSpotRecord> orphans(
        @Nonnull ShopSpotRegistry registry,
        @Nonnull UUID plotId,
        @Nonnull Predicate<ShopSpotRecord> stallStillStands
    ) {
        List<ShopSpotRecord> out = new ArrayList<>();
        for (ShopSpotRecord record : registry.listForPlot(plotId)) {
            if (!stallStillStands.test(record)) {
                out.add(record);
            }
        }
        return out;
    }
}
