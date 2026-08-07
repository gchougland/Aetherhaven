package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
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
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
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
    private static final int MAX_NAV_SHAPES = 2400;
    private static final double NODE_PICK_RADIUS = 0.45;
    private static final double PICK_RAY_MAX = 128.0;
    private static final ConcurrentHashMap<UUID, Long> LAST_DEBUG_SIGNATURE = new ConcurrentHashMap<>();
    /** Resend debug shapes periodically so client-side expiry does not leave the preview blank. */
    private static final ConcurrentHashMap<UUID, Long> LAST_DEBUG_SENT_MS = new ConcurrentHashMap<>();
    private static final long DEBUG_REFRESH_INTERVAL_MS = 2_000L;
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
                LAST_DEBUG_SENT_MS.remove(id);
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
        @Nullable
        UUID hoveredNodeId = null;
        @Nullable
        UUID hoveredRemovePathId = null;
        if (st.getGizmoMode() == PathToolGizmoMode.Remove) {
            hoveredRemovePathId = pickHoveredRemovePath(ref, store, pathReg);
        } else {
            hoveredNodeId = pickHoveredControlNode(st, ref, store);
        }
        long debugSig =
            PathToolPreviewSignature.compute(
                st,
                registryRevision,
                ref,
                store,
                hoveredNodeId,
                hoveredRemovePathId
            );
        Long prev = LAST_DEBUG_SIGNATURE.get(playerUuid);
        long nowMs = System.currentTimeMillis();
        Long lastSentMs = LAST_DEBUG_SENT_MS.get(playerUuid);
        boolean refreshDue =
            lastSentMs == null || nowMs - lastSentMs >= DEBUG_REFRESH_INTERVAL_MS;
        if (prev != null && prev == debugSig && !refreshDue) {
            return;
        }
        LAST_DEBUG_SIGNATURE.put(playerUuid, debugSig);
        LAST_DEBUG_SENT_MS.put(playerUuid, nowMs);
        PathDebugPreviewUtil.clear(pr);
        if (st.getGizmoMode() == PathToolGizmoMode.Remove) {
            drawCommittedPaths(pr, pathReg, st.getSelectedRemovePathId(), hoveredRemovePathId);
            return;
        }
        if (st.getGizmoMode() == PathToolGizmoMode.StyleDesigner) {
            return;
        }
        for (PathToolNode n : st.getNodes()) {
            boolean sel = n.getId().equals(st.getSelectedNodeId());
            boolean look =
                hoveredNodeId != null && hoveredNodeId.equals(n.getId()) && !sel;
            PathDebugPreviewUtil.drawPathControlNode(
                pr,
                n.getPosition(),
                n.getYawDeg(),
                sel,
                look
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
                PathDebugPreviewUtil.pathControlNodeLinePoint(a.position),
                PathDebugPreviewUtil.pathControlNodeLinePoint(b.position),
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
        var styles = cfg.getPathToolStyleDefinitions();
        PathToolStyleDefinition activeStyle = null;
        if (!styles.isEmpty()) {
            activeStyle = styles.get(Math.floorMod(st.getPathStyleIndex(), styles.size()));
        }
        boolean columnStyleLayout = activeStyle != null && activeStyle.hasColumnLayout();
        for (PathPlannedCell.Planned cell : plan) {
            if (c++ > MAX_PLANNED) {
                break;
            }
            boolean isCenter = columnStyleLayout || cell.role == PathPlannedCell.CellRole.Center;
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

    @Nullable
    private static UUID pickHoveredRemovePath(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PathToolRegistry pathReg
    ) {
        Transform look = TargetUtil.getLook(ref, store);
        if (look == null) {
            return null;
        }
        return PathToolRemovePick.pickPathId(
            look.getPosition(),
            look.getDirection(),
            PICK_RAY_MAX,
            pathReg.all()
        );
    }

    @Nullable
    private static UUID pickHoveredControlNode(
        @Nonnull PathToolPlayerComponent st,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PathToolGizmoMode mode = st.getGizmoMode();
        if (mode != PathToolGizmoMode.Commit
            && mode != PathToolGizmoMode.Translate
            && mode != PathToolGizmoMode.Rotate
            && mode != PathToolGizmoMode.ReplaceFilter) {
            return null;
        }
        if (st.getNodes().isEmpty()) {
            return null;
        }
        Transform look = TargetUtil.getLook(ref, store);
        if (look == null) {
            return null;
        }
        @Nullable
        PathToolNode looked =
            PathToolRayPick.pickNode(
                look.getPosition(),
                look.getDirection(),
                PICK_RAY_MAX,
                new ArrayList<>(st.getNodes()),
                NODE_PICK_RADIUS
            );
        return looked != null ? looked.getId() : null;
    }

    private static void drawCommittedPaths(
        @Nonnull PlayerRef pr,
        @Nonnull PathToolRegistry reg,
        @Nullable UUID selectedPathId,
        @Nullable UUID hoveredPathId
    ) {
        int shapes = 0;
        Vector3f normal = new Vector3f(0.85f, 0.45f, 0.12f);
        Vector3f selected = new Vector3f(0.98f, 0.88f, 0.15f);
        Vector3f hovered = PathDebugPreviewUtil.COLOR_KEYFRAME_LOOK;
        for (PathCommitRecord rec : reg.all()) {
            if (rec == null || shapes >= MAX_NAV_SHAPES) {
                return;
            }
            UUID pathId;
            try {
                pathId = rec.getIdUuid();
            } catch (Exception e) {
                continue;
            }
            boolean sel = selectedPathId != null && selectedPathId.equals(pathId);
            boolean look = !sel && hoveredPathId != null && hoveredPathId.equals(pathId);
            boolean emphasized = sel || look;
            Vector3f col = sel ? selected : (look ? hovered : normal);
            List<PathNavPoint> nav = PathToolNavPreviewUtil.navPointsForPreview(rec);
            if (nav.size() < 2) {
                continue;
            }
            for (int i = 0; i + 1 < nav.size() && shapes < MAX_NAV_SHAPES; i++) {
                PathNavPoint a = nav.get(i);
                PathNavPoint b = nav.get(i + 1);
                if (a == null || b == null) {
                    continue;
                }
                PathDebugPreviewUtil.drawLine(
                    pr,
                    PathDebugPreviewUtil.navNodeLinePoint(a.x, a.y, a.z, emphasized),
                    PathDebugPreviewUtil.navNodeLinePoint(b.x, b.y, b.z, emphasized),
                    col,
                    emphasized ? 0.09 : 0.07
                );
                shapes++;
            }
            for (int i = 0; i < nav.size() && shapes < MAX_NAV_SHAPES; i++) {
                PathNavPoint p = nav.get(i);
                if (p == null) {
                    continue;
                }
                boolean endpoint = i == 0 || i == nav.size() - 1;
                PathDebugPreviewUtil.drawNavNodeCube(pr, p.x, p.y, p.z, col, emphasized, endpoint);
                shapes++;
            }
        }
    }
}
