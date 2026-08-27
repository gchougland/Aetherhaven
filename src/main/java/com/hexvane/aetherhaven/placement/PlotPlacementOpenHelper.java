package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.plot.PlotTokenPlacementOption;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.CharterRelocationPage;
import com.hexvane.aetherhaven.ui.PlotPlacementPage;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PlotPlacementOpenHelper {
    private PlotPlacementOpenHelper() {}

    /**
     * Opens relocation placement UI for a completed plot (management block). Does not require a plot token.
     */
    @Nullable
    public static PlotPlacementPage openForMove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nonnull UUID plotId
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
            return null;
        }
        if (!world.getName().equals(town.getWorldName())) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.townNotInThisWorld"));
            return null;
        }
        if (!town.playerCanPlacePlots(uc.getUuid())) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noMoveBuildingsPermission"));
            return null;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.buildingCannotMove"));
            return null;
        }
        Rotation yaw = plot.resolvePrefabYaw();
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        Vector3i signAnchor;
        if (def != null) {
            // Sign sits at footprint center after placement; session anchor must invert stored prefab origin.
            signAnchor = def.resolvePreviewSignAnchorWorld(plot.resolvePrefabAnchorWorld(def), yaw);
        } else {
            signAnchor = new Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ());
        }
        int steps = PlotPlacementSession.rotationStepsFromPrefabYaw(yaw);
        PlotPlacementClientPrefabPreview.hide(playerRef);
        PlotPlacementSession session =
            PlotPlacementSession.forRelocatingPlot(world, signAnchor, steps, plot.getConstructionId(), plotId);
        PlotPlacementSessions.put(uc.getUuid(), session);
        return new PlotPlacementPage(playerRef, session);
    }

    @Nullable
    public static CustomUIPage tryOpen(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        BlockPosition tb = context.getTargetBlock();
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        CharterRelocationSession charterReloc = CharterRelocationSessions.get(uc.getUuid());
        if (charterReloc != null && charterReloc.getWorld().getName().equals(world.getName())) {
            PlacementGizmoService.exitGizmoModeForPlayer(uc.getUuid(), playerRef);
            return new CharterRelocationPage(playerRef, charterReloc);
        }
        PlotPlacementSession existing = PlotPlacementSessions.get(uc.getUuid());
        if (existing != null && existing.getWorld().getName().equals(world.getName())) {
            PlacementGizmoService.exitGizmoModeForPlayer(uc.getUuid(), playerRef);
            // Active preview: do not move anchor on block right-click; only Cancel clears the session so a new
            // right-click on a block can start placement elsewhere.
            return new PlotPlacementPage(playerRef, existing);
        }
        if (tb == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.lookAtBlockPlot"));
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        if (plugin == null || inv == null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_common.aetherhaven.common.carryPlotToken")
            );
            return null;
        }
        PlotTokenPlacementOption option = PlotTokenInventory.defaultPlacementOption(plugin, inv);
        if (option == null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_common.aetherhaven.common.carryPlotToken")
            );
            return null;
        }
        Vector3i anchor = PlotPlacementAnchorUtil.pickAnchor(world, tb);
        CharterRelocationSession droppedCharter = CharterRelocationSessions.removeAndGet(uc.getUuid());
        if (droppedCharter != null) {
            PlotPreviewSpawner.clear(store, droppedCharter.getPreviewEntityRefs());
            PlotPlacementWireframeOverlay.clearFor(playerRef);
        }
        PlotPlacementClientPrefabPreview.hide(playerRef);
        float yaw = PlotPlacementNudgeUtil.getPlayerYawRadians(ref, store);
        existing = PlotPlacementSessionFactory.createFromOption(world, anchor, option, plugin, yaw);
        if (existing == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.buildingCannotMove"));
            return null;
        }
        PlotPlacementSessions.put(uc.getUuid(), existing);
        return new PlotPlacementPage(playerRef, existing);
    }

    /**
     * Clears an active plot placement preview (wireframe + prefab ghosts) and drops the server session. Use before
     * opening another placement UI (e.g. charter relocation).
     */
    public static void cancelActivePlotPlacement(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef pr) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        PlotPlacementSession s = PlotPlacementSessions.get(uc.getUuid());
        if (s == null) {
            return;
        }
        PlacementGizmoService.exitGizmoModeForPlayer(uc.getUuid(), pr);
        PlotPlacementSessions.remove(uc.getUuid());
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                PlotPlacementPreviewSync.hideSpectators(world, uc.getUuid(), s);
                PlotPlacementClientPrefabPreview.clearWorldPreview(store, s);
                PlotPlacementClientPrefabPreview.hide(pr);
                PlotPlacementWireframeOverlay.clearFor(pr);
            }
        );
    }
}
