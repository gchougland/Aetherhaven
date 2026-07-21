package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import org.joml.Vector3f;
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
    /** Same signature as debug preview; cleared whenever the path-tool HUD is removed so reopening always refreshes. */
    private static final ConcurrentHashMap<UUID, Long> LAST_HUD_SIGNATURE = new ConcurrentHashMap<>();
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
        @Nullable
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        if (!pr.hasPermission(AetherhavenConstants.PERMISSION_PATH_TOOL)) {
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
        boolean holdingPathTool =
            hand != null && !hand.isEmpty() && AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(hand.getItemId());
        boolean customPageOpen = p.getPageManager().getCustomPage() != null;
        boolean pathHudApplicable = holdingPathTool && !customPageOpen;

        if (!pathHudApplicable) {
            if (PathToolHudSupport.isPathToolHudActive(p)) {
                PathToolHudSupport.removePathToolHud(p, pr);
            }
            LAST_HUD_SIGNATURE.remove(pr.getUuid());
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
        PathToolPlayerComponent.ensurePresent(ref, commandBuffer);
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }
        var cfg = AetherhavenPlugin.get() != null ? AetherhavenPlugin.get().getConfig().get() : null;
        if (cfg == null) {
            return;
        }
        st.clampPathStyleIndex(cfg.getPathToolStyleDefinitions().size());
        PathToolRegistry pathReg = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(w, AetherhavenPlugin.get());
        long registryRevision = pathReg.revisionHash();
        UUID playerUuid = pr.getUuid();
        long sig = PathToolPreviewSignature.compute(st, registryRevision, ref, store);
        Long prevHud = LAST_HUD_SIGNATURE.get(playerUuid);
        if (prevHud == null || prevHud != sig) {
            LAST_HUD_SIGNATURE.put(playerUuid, sig);
            PathToolHudSupport.obtainPathToolHud(p, pr).refresh(st, cfg, pr);
        }
        @Nullable
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return;
        }
        Long prev = LAST_DEBUG_SIGNATURE.get(playerUuid);
        if (prev != null && prev == sig) {
            return;
        }
        LAST_DEBUG_SIGNATURE.put(playerUuid, sig);
        PathDebugPreviewUtil.clear(pr);
        if (st.getGizmoMode() == PathToolGizmoMode.Remove) {
            drawCommittedPaths(pr, w, pathReg, st.getSelectedRemovePathId());
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.StyleDesigner || st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter) {
            return;
        }
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
        @Nullable
        java.util.Set<String> replaceArg = PathToolReplaceFilterResolver.nullableAllowlistForPredicate(ref, store, st);
        for (PathPlannedCell.Planned cell : plan) {
            if (c++ > MAX_PLANNED) {
                break;
            }
            boolean isCenter = cell.role == PathPlannedCell.CellRole.Center;
            boolean ok = PathToolReplacePredicate.isReplaceable(cfg, w, cell.pos.x(), cell.pos.y(), cell.pos.z(), replaceArg);
            Vector3f col;
            if (isCenter) {
                col = ok ? new Vector3f(0.5f, 0.32f, 0.12f) : new Vector3f(0.45f, 0.1f, 0.05f);
            } else {
                col = ok ? new Vector3f(0.18f, 0.55f, 0.22f) : new Vector3f(0.4f, 0.05f, 0.05f);
            }
            PathDebugPreviewUtil.drawPlannedBlock(pr, cell.pos.x(), cell.pos.y(), cell.pos.z(), col, w);
        }
    }

    private static void drawCommittedPaths(
        @Nonnull PlayerRef pr,
        @Nonnull World world,
        @Nonnull PathToolRegistry reg,
        @Nullable UUID selectedPathId
    ) {
        int drawn = 0;
        Vector3f normal = new Vector3f(0.85f, 0.45f, 0.12f);
        Vector3f selected = new Vector3f(0.98f, 0.88f, 0.15f);
        for (PathCommitRecord rec : reg.all()) {
            if (rec == null) {
                continue;
            }
            boolean sel;
            try {
                sel = selectedPathId != null && selectedPathId.equals(rec.getIdUuid());
            } catch (Exception e) {
                sel = false;
            }
            Vector3f col = sel ? selected : normal;
            if (rec.undo != null && !rec.undo.isEmpty()) {
                for (PathToolUndoCell c : rec.undo) {
                    if (c == null || drawn++ > MAX_PLANNED) {
                        return;
                    }
                    PathDebugPreviewUtil.drawPlannedBlock(pr, c.x, c.y, c.z, col, world);
                }
            } else if (rec.navNodes != null && rec.navNodes.size() >= 2) {
                for (int i = 0; i + 1 < rec.navNodes.size() && drawn < MAX_PLANNED; i++) {
                    PathNavPoint a = rec.navNodes.get(i);
                    PathNavPoint b = rec.navNodes.get(i + 1);
                    if (a == null || b == null) {
                        continue;
                    }
                    PathDebugPreviewUtil.drawLine(
                        pr,
                        new org.joml.Vector3d(a.x, a.y, a.z),
                        new org.joml.Vector3d(b.x, b.y, b.z),
                        col,
                        0.08
                    );
                    drawn++;
                }
                for (PathNavPoint p : rec.navNodes) {
                    if (p == null || drawn++ > MAX_PLANNED) {
                        return;
                    }
                    PathDebugPreviewUtil.drawPlannedBlock(pr, (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), col, world);
                }
            }
        }
    }
}
