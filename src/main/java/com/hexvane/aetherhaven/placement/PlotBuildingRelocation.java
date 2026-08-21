package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.prop.PropPlotTeardown;
import com.hexvane.aetherhaven.shopspot.ShopSpotPlotRelocation;
import com.hexvane.aetherhaven.shopspot.ShopSpotRegistry;
import com.hexvane.aetherhaven.tourist.TouristPortalPlotRelocation;
import com.hexvane.aetherhaven.tourist.TouristPortalRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PlotBuildingRelocation {
    private static final int SIGN_BREAK_SETTINGS = 10;
    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;

    private PlotBuildingRelocation() {}

    /**
     * Clears the old prefab volume, updates {@link PlotInstance} metadata, and rebuilds at the new pose.
     *
     * @return true if relocation was committed (session should be cleared by caller).
     */
    public static boolean tryCommit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotPlacementSession session,
        @Nonnull UUID playerUuid
    ) {
        UUID movePlotId = session.getMovePlotId();
        if (movePlotId == null) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            sendError(store, ref, "Aetherhaven not loaded.");
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownOwningPlot(movePlotId);
        if (town == null) {
            sendError(store, ref, "No town owns this plot.");
            return false;
        }
        if (!town.playerCanManageConstructions(playerUuid)) {
            sendError(store, ref, "You do not have permission to move this building.");
            return false;
        }
        PlotInstance plot = town.findPlotById(movePlotId);
        if (plot == null) {
            sendError(store, ref, "Plot data missing.");
            return false;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            sendError(store, ref, "Unknown construction: " + plot.getConstructionId());
            return false;
        }
        Vector3i previewAnchor = session.getAnchor();
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            sendError(store, ref, "Prefab not found for construction: " + def.getId());
            return false;
        }
        IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
        PlotPlacementHeights.ResolvedPlacement resolved =
            PlotPlacementHeights.resolve(world, previewAnchor, def, session.getPrefabYaw(), buffer);
        Vector3i signPos = resolved.signCell();
        Vector3i prefabOrigin = resolved.buildingPrefabAnchor();
        String err =
            PlotPlacementValidator.validateWithResolvedHeights(
                world,
                tm,
                town,
                playerUuid,
                previewAnchor,
                signPos,
                prefabOrigin,
                session.getPrefabYaw(),
                def,
                plugin,
                movePlotId
            );
        if (err != null) {
            sendError(store, ref, err);
            return false;
        }

        PlotFootprintRecord oldFootprint = plot.toFootprint();
        Rotation oldYaw = plot.resolvePrefabYaw();
        Vector3i oldAnchor =
            PlotInPlacePrefabAnchor.resolveInPlaceAnchor(plot, oldYaw, buffer, oldFootprint);
        if (oldAnchor == null) {
            sendError(store, ref, "Could not resolve the current building anchor.");
            return false;
        }

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        reg.unregisterByPlotId(movePlotId);

        ShopSpotRegistry shopRegistry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotPlotRelocation.beginPlotMove(world, plugin, store, shopRegistry, movePlotId, plot, def);

        TouristPortalRegistry touristRegistry =
            AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        TouristPortalPlotRelocation.beginPlotMove(
            world, plugin, store, touristRegistry, movePlotId, plot, def, town
        );

        relocateTownNpcsOutOfFootprint(store, town, oldFootprint);

        PropPlotTeardown.packageIntersecting(world, plugin, oldFootprint, ref, store);

        PrefabFootprintClearUtil.removePrefabOnlyEntitiesInFootprint(store, oldFootprint, town);
        PrefabFootprintClearUtil.clearPrefabCellsAtAnchor(
            world, oldAnchor, oldYaw, buffer, def.isPreserveWater()
        );

        PlotFootprintRecord newFp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buffer, def);
        plot.applySignAndFootprint(signPos.x, signPos.y, signPos.z, newFp);
        plot.setPrefabWorldPlacement(prefabOrigin.x, prefabOrigin.y, prefabOrigin.z, session.getPrefabYaw());
        tm.updateTown(town);

        Runnable onComplete =
            () -> {
                world.breakBlock(signPos.x, signPos.y, signPos.z, SIGN_BREAK_SETTINGS);
                ConstructionCompleter.finishBuild(world, plugin, playerUuid, movePlotId, prefabOrigin, session.getPrefabYaw());
            };
        ConstructionAnimator.start(
            plugin,
            world,
            prefabOrigin,
            session.getPrefabYaw(),
            true,
            def.isPreserveWater(),
            buffer,
            store,
            INSTANT_BLOCKS_PER_BATCH,
            INSTANT_BATCH_DELAY_MS,
            onComplete,
            plot.getBlockPaletteSelections()
        );

        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.placement.movingBuilding"));
        }
        return true;
    }

    public static void relocateTownNpcsOutOfFootprint(
        @Nonnull Store<EntityStore> store, @Nonnull TownRecord town, @Nonnull PlotFootprintRecord fp
    ) {
        double tx = town.getCharterX() + 0.5;
        double ty = town.getCharterY() + 0.02;
        double tz = town.getCharterZ() + 0.5;
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        town.collectTrackedNpcEntityUuids(ids);
        UUID nil = new UUID(0L, 0L);
        for (UUID u : ids) {
            if (u == null || nil.equals(u)) {
                continue;
            }
            Ref<EntityStore> er = store.getExternalData().getRefFromUUID(u);
            if (er == null || !er.isValid()) {
                continue;
            }
            TransformComponent tc = store.getComponent(er, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d p = tc.getPosition();
            if (!footprintContainsBlockColumn(fp, p.x, p.y, p.z)) {
                continue;
            }
            p.x = tx;
            p.y = ty;
            p.z = tz;
            store.putComponent(er, TransformComponent.getComponentType(), tc);
        }
    }

    private static boolean footprintContainsBlockColumn(@Nonnull PlotFootprintRecord fp, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY()
            && by <= fp.getMaxY()
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }

    private static void sendError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }
}
