package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerDedupUtil;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementValidator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Registers a finished plot creator build as a completed town plot at the marked anchor. The structure is already built
 * in the world — no plot sign and no prefab paste.
 */
public final class PlotCreatorWorldRegistrar {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BREAK_SETTINGS = 10;

    private PlotCreatorWorldRegistrar() {}

    /**
     * @return null on success, or a short error token for {@code aetherhaven.plotcreator.error.*} / raw placement text
     */
    @Nullable
    public static String registerInTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Store<EntityStore> entityStore
    ) {
        Vector3i anchor = draft.getPlotAnchor();
        if (anchor == null || draft.getConstructionId() == null) {
            return "incomplete";
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForOwnerInWorld(playerUuid);
        if (town == null) {
            return "noTown";
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(draft.getConstructionId().trim());
        if (def == null) {
            return "unknownConstruction";
        }
        if (PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath()) == null) {
            LOGGER.atWarning().log(
                "Plot creator register: prefab not found for %s (prefabPath=%s)",
                def.getId(),
                def.getPrefabPath()
            );
            return "prefab_missing";
        }
        Rotation yaw = parseYaw(def.getRotationYaw());
        PlotInstance existing = findExistingPlot(town, draft);
        UUID plotId = existing != null ? existing.getPlotId() : UUID.randomUUID();
        UUID excludePlotId = existing != null ? plotId : null;

        String placementErr =
            PlotPlacementValidator.validate(world, tm, town, playerUuid, anchor, yaw, def, plugin, excludePlotId);
        if (placementErr != null) {
            return placementErr;
        }

        if (existing != null) {
            removeOldPlotSignIfPresent(world, existing);
            town.removePlotInstance(plotId);
        }

        Vector3i prefabAnchor = def.resolvePrefabAnchorWorld(anchor, yaw);
        PlotFootprintRecord footprint;
        IPrefabBuffer buf = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        try {
            footprint = PlotFootprintUtil.computeFootprint(prefabAnchor, yaw, buf);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Plot creator footprint failed for %s", def.getId());
            Vector3i min = draft.boundsMin();
            Vector3i max = draft.boundsMax();
            footprint = new PlotFootprintRecord(min.x, min.y, min.z, max.x, max.y, max.z);
        }

        long now = System.currentTimeMillis();
        PlotInstance inst =
            new PlotInstance(
                plotId,
                def.getId(),
                PlotInstanceState.COMPLETE,
                footprint,
                anchor.x,
                anchor.y,
                anchor.z,
                now
            );
        inst.setPlacementPrefabYaw(yaw);
        inst.setPrefabWorldPlacement(prefabAnchor.x, prefabAnchor.y, prefabAnchor.z, yaw);
        town.addPlotInstance(inst);
        tm.updateTown(town);

        PlotInstance registered = town.findPlotById(plotId);
        if (registered != null) {
            PoiMarkerDedupUtil.dedupeInPlot(entityStore, registered);
        }

        ConstructionCompleter.finishBuild(world, plugin, playerUuid, plotId, prefabAnchor, yaw);
        LOGGER.atInfo().log(
            "Plot creator registered %s as complete plot %s in town %s at %d,%d,%d (no sign, no paste)",
            def.getId(),
            plotId,
            town.getTownId(),
            anchor.x,
            anchor.y,
            anchor.z
        );
        return null;
    }

    @Nonnull
    private static Rotation parseYaw(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Rotation.None;
        }
        try {
            return Rotation.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return Rotation.None;
        }
    }

    @Nullable
    private static PlotInstance findExistingPlot(@Nonnull TownRecord town, @Nonnull PlotCreatorDraft draft) {
        String lookup = draft.getEditingConstructionId();
        if (lookup == null || lookup.isBlank()) {
            lookup = draft.getConstructionId();
        }
        if (lookup == null) {
            return null;
        }
        String id = lookup.trim();
        for (PlotInstance p : town.getPlotInstances()) {
            if (id.equals(p.getConstructionId())) {
                return p;
            }
        }
        return null;
    }

    private static void removeOldPlotSignIfPresent(@Nonnull World world, @Nonnull PlotInstance existing) {
        BlockType bt = world.getBlockType(existing.getSignX(), existing.getSignY(), existing.getSignZ());
        if (bt != null && AetherhavenConstants.PLOT_SIGN_ITEM_ID.equals(bt.getId())) {
            world.breakBlock(existing.getSignX(), existing.getSignY(), existing.getSignZ(), BREAK_SETTINGS);
        }
    }
}
