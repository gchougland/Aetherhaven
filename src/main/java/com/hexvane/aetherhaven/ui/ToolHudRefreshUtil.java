package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.patrol.PatrolRouteRecord;
import com.hexvane.aetherhaven.patrol.PatrolRouteRegistry;
import com.hexvane.aetherhaven.patrol.PatrolWandHudSupport;
import com.hexvane.aetherhaven.patrol.PatrolWandPlayerComponent;
import com.hexvane.aetherhaven.pathtool.PathToolHudSupport;
import com.hexvane.aetherhaven.pathtool.PathToolPlayerComponent;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorHudSupport;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSessions;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Re-refreshes active tool HUD overlays after key label preferences change. */
public final class ToolHudRefreshUtil {
    private ToolHudRefreshUtil() {}

    public static void refreshActiveToolHuds(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        refreshPathToolHud(ref, store, player, playerRef);
        refreshPlotCreatorHud(player, playerRef);
        refreshPatrolWandHud(ref, store, player, playerRef);
    }

    private static void refreshPathToolHud(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef
    ) {
        if (!PathToolHudSupport.isPathToolHudActive(player)) {
            return;
        }
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (st == null || plugin == null) {
            return;
        }
        PathToolHudSupport.obtainPathToolHud(player, playerRef).refresh(st, plugin.getConfig().get(), playerRef);
    }

    private static void refreshPlotCreatorHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        if (!PlotCreatorHudSupport.isActive(player)) {
            return;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session != null) {
            PlotCreatorHudSupport.refreshAll(player, playerRef, session);
        }
    }

    private static void refreshPatrolWandHud(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef
    ) {
        if (!PatrolWandHudSupport.isPatrolWandHudActive(player)) {
            return;
        }
        PatrolWandPlayerComponent st = store.getComponent(ref, PatrolWandPlayerComponent.getComponentType());
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (st == null || plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        TownRecord town = null;
        if (tc != null) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            town =
                tm.findTownContainingBlock(
                    world.getName(),
                    (int) Math.floor(tc.getPosition().x),
                    (int) Math.floor(tc.getPosition().z)
                );
        }
        PatrolRouteRecord selected = resolvePatrolRoute(world, plugin, st);
        PatrolWandHudSupport.obtainPatrolWandHud(player, playerRef).refresh(st, selected, town);
    }

    @Nullable
    private static PatrolRouteRecord resolvePatrolRoute(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PatrolWandPlayerComponent st
    ) {
        UUID id = st.getSelectedRouteId() != null ? st.getSelectedRouteId() : st.getEditingRouteId();
        if (id == null) {
            return null;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        return reg.get(id);
    }
}
