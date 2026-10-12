package com.hexvane.aetherhaven.shop;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.town.PlotInstance;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * The place a shop building marks for its clerk. It is saved with the building as an important spot tagged
 * {@link #TAG}, so every copy of the building has it, turned with the building. A shop that marks none keeps
 * choosing a place by its counter.
 */
public final class ShopClerkStand {
    /** Tag of the building spot. Not a work, shop or visit spot, so nothing else is drawn to it. */
    public static final String TAG = "CLERK_STAND";

    private ShopClerkStand() {}

    /** The building's Clerk Stand spot, or null when it marks none. */
    @Nullable
    public static BuildingPoisDefinition.PoiRow marker(@Nullable ConstructionDefinition definition) {
        if (definition == null) {
            return null;
        }
        for (BuildingPoisDefinition.PoiRow row : definition.getPois()) {
            if (row != null && row.getTags().contains(TAG)) {
                return row;
            }
        }
        return null;
    }

    /**
     * The world cell the clerk's feet occupy in this placed building, or null when the building marks no stand or
     * the plot has not been built yet.
     */
    @Nullable
    public static Vector3i cell(@Nullable ConstructionDefinition definition, @Nonnull PlotInstance plot) {
        BuildingPoisDefinition.PoiRow row = marker(definition);
        if (row == null || !plot.hasStoredPrefabWorldAnchor()) {
            return null;
        }
        return plot.getStoredPrefabWorldAnchor()
            .add(PrefabLocalOffset.rotate(plot.resolvePrefabYaw(), row.getLocalX(), row.getLocalY(), row.getLocalZ()));
    }
}
