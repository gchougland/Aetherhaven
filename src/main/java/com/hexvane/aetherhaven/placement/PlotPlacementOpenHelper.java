package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        Vector3i anchor = new Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ());
        Rotation yaw = plot.resolvePrefabYaw();
        int steps = PlotPlacementSession.rotationStepsFromPrefabYaw(yaw);
        PlotPlacementSession session =
            PlotPlacementSession.forRelocatingPlot(world, anchor, steps, plot.getConstructionId(), plotId);
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
            return new CharterRelocationPage(playerRef, charterReloc);
        }
        PlotPlacementSession existing = PlotPlacementSessions.get(uc.getUuid());
        if (existing != null && existing.getWorld().getName().equals(world.getName())) {
            // Active preview: do not move anchor on block right-click; only Cancel clears the session so a new
            // right-click on a block can start placement elsewhere.
            return new PlotPlacementPage(playerRef, existing);
        }
        if (tb == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.lookAtBlockPlot"));
            return null;
        }
        Vector3i anchor = pickAnchor(world, tb);
        String cons = PlotPlacementPage.defaultConstructionFromInventory(store, ref);
        if (cons == null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_common.aetherhaven.common.carryPlotToken")
            );
            return null;
        }
        CharterRelocationSession droppedCharter = CharterRelocationSessions.removeAndGet(uc.getUuid());
        if (droppedCharter != null) {
            PlotPreviewSpawner.clear(store, droppedCharter.getPreviewEntityRefs());
            PlotPlacementWireframeOverlay.clearFor(playerRef);
        }
        existing = new PlotPlacementSession(world, anchor, 0, cons);
        PlotPlacementSessions.put(uc.getUuid(), existing);
        return new PlotPlacementPage(playerRef, existing);
    }

    @Nonnull
    private static Vector3i pickAnchor(@Nonnull World world, @Nonnull BlockPosition tb) {
        Vector3i above = new Vector3i(tb.x, tb.y + 1, tb.z);
        Vector3i picked;
        if (isReplaceable(world, above.x, above.y, above.z)) {
            picked = above;
        } else {
            Vector3i on = new Vector3i(tb.x, tb.y, tb.z);
            if (isReplaceable(world, on.x, on.y, on.z)) {
                picked = on;
            } else {
                picked = above;
            }
        }
        return new Vector3i(
            picked.x,
            picked.y + AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
            picked.z
        );
    }

    private static boolean isReplaceable(@Nonnull World world, int x, int y, int z) {
        BlockType t = world.getBlockType(x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
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
        PlotPlacementSessions.remove(uc.getUuid());
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                PlotPreviewSpawner.clear(store, s.getPreviewEntityRefs());
                PlotPlacementWireframeOverlay.clearFor(pr);
            }
        );
    }
}
