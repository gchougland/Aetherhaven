package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Packages every prop overlapping a plot footprint before the plot itself is cleared/moved — otherwise
 * {@link com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil} would silently delete player-placed props.
 */
public final class PropPlotTeardown {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PropPlotTeardown() {}

    /**
     * @param actingPlayerRefOrNull when set, packaged props are returned to this player's inventory (overflow drops
     *     at the prop's location); when {@code null}, every packaged prop item drops at its own location instead.
     * @return how many props were packaged
     */
    public static int packageIntersecting(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotFootprintRecord footprint,
        @Nullable Ref<EntityStore> actingPlayerRefOrNull,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        PropRegistry registry = PropWorldRegistries.getOrCreatePropRegistry(world, plugin);
        if (registry.size() == 0) {
            return 0;
        }
        PropCatalog catalog = plugin.getPropCatalog();
        List<PropInstance> toPackage = new ArrayList<>();
        for (PropInstance instance : registry.all()) {
            IPrefabBuffer buffer = PropLookupUtil.resolveBuffer(catalog, instance);
            if (buffer == null) {
                // Unresolvable prefab still occupies registry state at this location; package it out rather than
                // leaving an orphaned instance the next teardown can't find either.
                toPackage.add(instance);
                continue;
            }
            PlotFootprintRecord propFootprint = PropPrefabOps.footprint(instance.getAnchor(), instance.getYaw(), buffer);
            if (propFootprint.intersects(footprint)) {
                toPackage.add(instance);
            }
        }
        int packaged = 0;
        for (PropInstance instance : toPackage) {
            try {
                if (PropPackageCommit.packageInstance(world, plugin, instance, actingPlayerRefOrNull, accessor)) {
                    packaged++;
                }
            } catch (RuntimeException e) {
                LOGGER.atWarning().withCause(e).log("Failed to package prop %s during plot teardown", instance.getInstanceId());
            }
        }
        return packaged;
    }
}
