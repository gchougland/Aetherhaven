package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.routevisual.RouteParticleAssets;
import com.hexvane.aetherhaven.routevisual.RouteParticleConfig;
import com.hexvane.aetherhaven.routevisual.RouteParticleRenderer;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Per player patrol route particle preview while holding the patrol wand. */
public final class PatrolWandPreviewSystem extends EntityTickingSystem<EntityStore> {
    private static final ConcurrentHashMap<UUID, Long> LAST_HUD_SIGNATURE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> TICK_COUNTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> LAST_HOLDING_WAND = new ConcurrentHashMap<>();

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    private final AetherhavenPlugin plugin;

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    public PatrolWandPreviewSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player p = chunk.getComponent(index, Player.getComponentType());
        World world = store.getExternalData().getWorld();
        if (p == null) {
            return;
        }
        @Nullable
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        UUID playerUuid = pr.getUuid();
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
        boolean holding =
            hand != null
                && !hand.isEmpty()
                && AetherhavenConstants.PATROL_WAND_ITEM_ID.equals(hand.getItemId());
        boolean customPageOpen = p.getPageManager().getCustomPage() != null;
        boolean applicable = holding && !customPageOpen;
        if (!applicable) {
            if (PatrolWandHudSupport.isPatrolWandHudActive(p)) {
                PatrolWandHudSupport.removePatrolWandHud(p, pr);
            }
            LAST_HUD_SIGNATURE.remove(playerUuid);
            TICK_COUNTERS.remove(playerUuid);
            LAST_HOLDING_WAND.put(playerUuid, false);
            return;
        }
        PatrolWandPlayerComponent st = PatrolWandInteractions.resolveState(ref, commandBuffer);
        if (st == null) {
            return;
        }
        Boolean wasHolding = LAST_HOLDING_WAND.get(playerUuid);
        boolean holdingChanged = wasHolding == null || !wasHolding;
        LAST_HOLDING_WAND.put(playerUuid, true);
        if (holdingChanged
            && st.getDraftNodes().isEmpty()
            && st.getEditingRouteId() == null
            && st.getSelectedRouteId() == null) {
            st.setMode(PatrolWandMode.Build);
            commandBuffer.putComponent(ref, PatrolWandPlayerComponent.getComponentType(), st);
        }
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
        long hudSig = computeHudSignature(st, town);
        Long prevHud = LAST_HUD_SIGNATURE.get(playerUuid);
        if (prevHud == null || prevHud != hudSig) {
            LAST_HUD_SIGNATURE.put(playerUuid, hudSig);
            PatrolRouteRecord selected = resolveSelectedRoute(world, st);
            PatrolWandHudSupport.obtainPatrolWandHud(p, pr).refresh(st, selected, town);
        }
        int counter = TICK_COUNTERS.getOrDefault(playerUuid, 0) + 1;
        if (counter < RouteParticleConfig.DEFAULT.getTickRate()) {
            TICK_COUNTERS.put(playerUuid, counter);
            return;
        }
        TICK_COUNTERS.put(playerUuid, 0);
        if (town == null) {
            return;
        }
        RouteParticleAssets.validateOnce();
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        List<Ref<EntityStore>> audience = Collections.singletonList(ref);
        for (PatrolRouteRecord route : reg.listByTown(town.getTownId())) {
            if (route == null) {
                continue;
            }
            List<Vector3d> points = route.nodePositions();
            if (points.isEmpty()) {
                continue;
            }
            UUID rid = route.getIdUuid();
            boolean highlight = isRouteHighlighted(st, rid);
            if (highlight && !st.getDraftNodes().isEmpty() && st.getMode() == PatrolWandMode.Build) {
                points = new ArrayList<>();
                for (PatrolWandNode n : st.getDraftNodes()) {
                    points.add(n.getPosition());
                }
            }
            RouteParticleConfig cfg = highlight ? RouteParticleConfig.SELECTED : RouteParticleConfig.DIM;
            boolean closedLoop =
                highlight && st.getMode() == PatrolWandMode.Build
                    ? st.isDraftClosedLoop()
                    : route.isClosedLoop();
            if (points.size() >= 2) {
                RouteParticleRenderer.renderPolyline(world, store, points, audience, cfg);
                if (closedLoop) {
                    RouteParticleRenderer.renderTrailToward(
                        world,
                        store,
                        points.get(points.size() - 1),
                        points.get(0),
                        audience,
                        cfg
                    );
                }
            }
            if (!points.isEmpty()) {
                RouteParticleRenderer.renderNodeMarkers(world, store, points, audience, cfg);
            }
        }
        if (st.getMode() == PatrolWandMode.Build && !st.getDraftNodes().isEmpty() && st.getEditingRouteId() == null) {
            List<Vector3d> draft = new ArrayList<>();
            for (PatrolWandNode n : st.getDraftNodes()) {
                draft.add(n.getPosition());
            }
            if (draft.size() >= 2) {
                RouteParticleRenderer.renderPolyline(world, store, draft, audience, RouteParticleConfig.SELECTED);
                if (st.isDraftClosedLoop()) {
                    RouteParticleRenderer.renderTrailToward(
                        world,
                        store,
                        draft.get(draft.size() - 1),
                        draft.get(0),
                        audience,
                        RouteParticleConfig.SELECTED
                    );
                }
            }
            RouteParticleRenderer.renderNodeMarkers(world, store, draft, audience, RouteParticleConfig.SELECTED);
        }
    }

    private static boolean isRouteHighlighted(@Nonnull PatrolWandPlayerComponent st, @Nullable UUID routeId) {
        if (routeId == null) {
            return false;
        }
        if (st.getMode() == PatrolWandMode.Assign) {
            return routeId.equals(st.getSelectedRouteId());
        }
        return !st.getDraftNodes().isEmpty() && routeId.equals(st.getEditingRouteId());
    }

    @Nullable
    private PatrolRouteRecord resolveSelectedRoute(@Nonnull World world, @Nonnull PatrolWandPlayerComponent st) {
        UUID id = st.getSelectedRouteId() != null ? st.getSelectedRouteId() : st.getEditingRouteId();
        if (id == null) {
            return null;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        return reg.get(id);
    }

    private static long computeHudSignature(@Nonnull PatrolWandPlayerComponent st, @Nullable TownRecord town) {
        long h = st.getMode().ordinal();
        h = 31 * h + Objects.hashCode(st.getEditingRouteId());
        h = 31 * h + Objects.hashCode(st.getSelectedRouteId());
        h = 31 * h + st.getDraftNodes().size();
        h = 31 * h + (st.isDraftClosedLoop() ? 1 : 0);
        if (town != null) {
            h = 31 * h + town.getTownId().hashCode();
        }
        for (PatrolWandNode n : st.getDraftNodes()) {
            h = 31 * h + Double.hashCode(n.getX()) + Double.hashCode(n.getY()) + Double.hashCode(n.getZ());
        }
        return h;
    }
}
