package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-tick per-player {@link com.hypixel.hytale.protocol.packets.player.DisplayDebug} for spline and planned voxels
 * (no world mutation).
 */
public final class PathToolPreviewSystem extends EntityTickingSystem<EntityStore> {
    private static final int MAX_PLANNED = 800;
    private static final ConcurrentHashMap<UUID, Long> LAST_DEBUG_SIGNATURE = new ConcurrentHashMap<>();
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
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

    public PathToolPreviewSystem(@Nonnull AetherhavenPlugin plugin) {
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
        World w = store.getExternalData().getWorld();
        if (p == null) {
            return;
        }
        if (!p.hasPermission(AetherhavenConstants.PERMISSION_PATH_TOOL)) {
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
        @Nullable
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        if (hand == null
            || hand.isEmpty()
            || !AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(hand.getItemId())) {
            PathToolHudSupport.removePathToolHud(p, pr);
            @Nullable
            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                UUID id = uuidComp.getUuid();
                // Only clear debug when we had path preview state — clearing every tick wipes other DisplayDebug
                // overlays (e.g. plot placement wireframes) for anyone with path-tool permission.
                if (LAST_DEBUG_SIGNATURE.remove(id) != null) {
                    PathDebugPreviewUtil.clear(pr);
                }
            }
            return;
        }
        // Custom pages (e.g. plot placement) replace the client's custom HUD; path-tool HUD partial updates then fail
        // (#ModeName.TextSpans not found) and can disconnect the client.
        if (p.getPageManager().getCustomPage() != null) {
            PathToolHudSupport.removePathToolHud(p, pr);
            @Nullable
            UUIDComponent uuidBlocked = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidBlocked != null) {
                UUID id = uuidBlocked.getUuid();
                if (LAST_DEBUG_SIGNATURE.remove(id) != null) {
                    PathDebugPreviewUtil.clear(pr);
                }
            }
            return;
        }
        PathToolInteractions.ensureState(ref, commandBuffer);
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        var cfg = AetherhavenPlugin.get() != null ? AetherhavenPlugin.get().getConfig().get() : null;
        if (cfg == null) {
            return;
        }
        st.clampPathStyleIndex(cfg.getPathToolStyleDefinitions().size());
        PathToolHudSupport.obtainPathToolHud(p, pr).refresh(st, cfg);
        @Nullable
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return;
        }
        UUID playerUuid = uuidComp.getUuid();
        long sig = PathToolPreviewSignature.compute(st);
        Long prev = LAST_DEBUG_SIGNATURE.get(playerUuid);
        if (prev != null && prev == sig) {
            return;
        }
        LAST_DEBUG_SIGNATURE.put(playerUuid, sig);
        PathDebugPreviewUtil.clear(pr);
        for (PathToolNode n : st.getNodes()) {
            boolean sel = n.getId().equals(st.getSelectedNodeId());
            PathDebugPreviewUtil.drawMachinimaNode(
                pr,
                n.getPosition(),
                n.getYawDeg(),
                sel
            );
        }
        if (st.getNodes().size() < 2) {
            return;
        }
        var samples = PathSplineUtil.sample(st.getNodes(), cfg.getPathToolSamplesPerBlock());
        for (int i = 0; i + 1 < samples.size(); i++) {
            if (i > 600) {
                break;
            }
            PathSplineUtil.PathSample a = samples.get(i);
            PathSplineUtil.PathSample b = samples.get(i + 1);
            PathDebugPreviewUtil.drawLine(
                pr,
                a.position,
                b.position,
                PathDebugPreviewUtil.COLOR_PATH_EDGE,
                0.05
            );
        }
        List<PathPlannedCell.Planned> plan =
            PathPlannedCell.build(
                w,
                samples,
                st.getPathWidthBlocks(),
                cfg.getPathToolRayStartAboveY(),
                cfg.getPathToolMaxRayDown()
            );
        int c = 0;
        for (PathPlannedCell.Planned cell : plan) {
            if (c++ > MAX_PLANNED) {
                break;
            }
            boolean isCenter = cell.role == PathPlannedCell.CellRole.Center;
            boolean ok = PathToolReplacePredicate.isReplaceable(cfg, w, cell.pos.getX(), cell.pos.getY(), cell.pos.getZ());
            Vector3f col;
            if (isCenter) {
                col = ok ? new Vector3f(0.5f, 0.32f, 0.12f) : new Vector3f(0.45f, 0.1f, 0.05f);
            } else {
                col = ok ? new Vector3f(0.18f, 0.55f, 0.22f) : new Vector3f(0.4f, 0.05f, 0.05f);
            }
            PathDebugPreviewUtil.drawPlannedBlock(pr, cell.pos.getX(), cell.pos.getY(), cell.pos.getZ(), col, w);
        }
    }
}
