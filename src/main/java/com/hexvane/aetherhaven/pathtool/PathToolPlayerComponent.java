package com.hexvane.aetherhaven.pathtool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spline path edit session: ordered nodes, gizmo mode, and selection. Nodes are JSON-encoded in the player component
 * so they survive chunk saves while editing.
 */
public final class PathToolPlayerComponent implements Component<EntityStore> {
    private static final Gson GSON = new GsonBuilder().create();
    @Nonnull
    private static final UUID NO_SELECTION = new UUID(0L, 0L);

    @Nonnull
    public static final BuilderCodec<PathToolPlayerComponent> CODEC = BuilderCodec.builder(
            PathToolPlayerComponent.class,
            PathToolPlayerComponent::new
        )
        .append(
            new KeyedCodec<>("Gizmo", Codec.STRING),
            (c, s) -> c.gizmoMode = parseGizmo(s),
            c -> c.gizmoMode.name()
        )
        .add()
        .append(
            new KeyedCodec<>("SelectedNodeId", Codec.UUID_BINARY),
            (c, u) -> c.selectedNodeId = u != null && !NO_SELECTION.equals(u) ? u : null,
            c -> c.selectedNodeId != null ? c.selectedNodeId : NO_SELECTION
        )
        .add()
        .append(
            new KeyedCodec<>("NodePayload", Codec.STRING),
            (c, s) -> c.decodeNodes(s),
            c -> c.encodeNodes()
        )
        .add()
        .append(
            new KeyedCodec<>("PathWidth", Codec.INTEGER),
            (c, w) ->
                c.pathWidthBlocks =
                    w != null && w > 0
                        ? Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, w))
                        : 5,
            c -> c.pathWidthBlocks
        )
        .add()
        .append(
            new KeyedCodec<>("PathStyleIndex", Codec.INTEGER),
            (c, i) -> c.pathStyleIndex = i != null && i >= 0 ? i : 0,
            c -> c.pathStyleIndex
        )
        .add()
        .append(
            new KeyedCodec<>("SelectedRemovePathId", Codec.UUID_BINARY),
            (c, u) -> c.selectedRemovePathId = u,
            c -> c.selectedRemovePathId
        )
        .add()
        .append(
            new KeyedCodec<>("ReplaceFilterBlockIds", Codec.STRING),
            (c, s) -> c.replaceFilterBlockIdsCsv = s != null ? s : "",
            c -> c.replaceFilterBlockIdsCsv != null ? c.replaceFilterBlockIdsCsv : ""
        )
        .add()
        .append(
            new KeyedCodec<>("VillagerNav", Codec.BOOLEAN),
            (c, v) -> c.villagerNav = v == null || v,
            c -> c.villagerNav
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PathToolPlayerComponent> componentType;

    @Nonnull
    private PathToolGizmoMode gizmoMode = PathToolGizmoMode.Commit;
    private int pathWidthBlocks = 5;
    private int pathStyleIndex;
    @Nullable
    private UUID selectedNodeId;
    @Nullable
    private UUID selectedRemovePathId;
    @Nonnull
    private String replaceFilterBlockIdsCsv = "";
    private boolean villagerNav = true;
    @Nonnull
    private final List<PathToolNode> nodes = new ArrayList<>();

    @Nonnull
    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            PathToolPlayerComponent.class,
            "AetherhavenPathTool",
            PathToolPlayerComponent.CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, PathToolPlayerComponent> getComponentType() {
        ComponentType<EntityStore, PathToolPlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PathToolPlayerComponent not registered");
        }
        return t;
    }

    /** Ensures the path-tool edit session component exists (safe from tick systems without loading {@link PathToolInteractions}). */
    public static void ensurePresent(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer.getComponent(playerRef, getComponentType()) == null) {
            commandBuffer.addComponent(playerRef, getComponentType(), new PathToolPlayerComponent());
        }
    }

    @Nonnull
    public List<PathToolNode> getNodes() {
        return nodes;
    }

    public void clearPath() {
        nodes.clear();
        selectedNodeId = null;
    }

    @Nonnull
    public PathToolGizmoMode getGizmoMode() {
        return gizmoMode;
    }

    public void setGizmoMode(@Nonnull PathToolGizmoMode gizmoMode) {
        this.gizmoMode = gizmoMode;
    }

    /** Cycles: Place -> Move -> Rotate -> Remove -> Replace filter -> Style designer -> Place. */
    public void cycleGizmoMode() {
        this.gizmoMode = switch (gizmoMode) {
            case Commit -> PathToolGizmoMode.Translate;
            case Translate -> PathToolGizmoMode.Rotate;
            case Rotate -> PathToolGizmoMode.Remove;
            case Remove -> PathToolGizmoMode.ReplaceFilter;
            case ReplaceFilter -> PathToolGizmoMode.StyleDesigner;
            case StyleDesigner -> PathToolGizmoMode.Commit;
        };
    }

    /** Tab id for {@link com.hexvane.aetherhaven.pathtool.PathToolStatusHud} mode tabs (visual only). */
    @Nonnull
    public String modeTabId() {
        return switch (gizmoMode) {
            case Commit -> "Place";
            case Translate -> "Move";
            case Rotate -> "Rotate";
            case Remove -> "Remove";
            case ReplaceFilter -> "ReplaceFilter";
            case StyleDesigner -> "StyleDesigner";
        };
    }

    /** Non-empty: only these block ids (plus path output blocks) may be replaced; empty uses server defaults. */
    @Nonnull
    public Set<String> getReplaceFilterBlockIds() {
        return parseReplaceFilterCsv(replaceFilterBlockIdsCsv);
    }

    public boolean isVillagerNav() {
        return villagerNav;
    }

    public void setVillagerNav(boolean villagerNav) {
        this.villagerNav = villagerNav;
    }

    public void toggleVillagerNav() {
        this.villagerNav = !this.villagerNav;
    }

    public void setReplaceFilterBlockIds(@Nonnull Set<String> ids) {
        if (ids.isEmpty()) {
            replaceFilterBlockIdsCsv = "";
            return;
        }
        LinkedHashSet<String> sorted = new LinkedHashSet<>(ids);
        replaceFilterBlockIdsCsv = String.join(",", sorted);
    }

    @Nonnull
    private static Set<String> parseReplaceFilterCsv(@Nullable String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public int getPathWidthBlocks() {
        return pathWidthBlocks;
    }

    public void setPathWidthBlocks(int blocks) {
        this.pathWidthBlocks = Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, blocks));
    }

    /** Cycles width: 1..{@link PathToolStyleDefinition#MAX_PATH_WIDTH_BLOCKS} -> 1. */
    public void cyclePathWidth() {
        int w = pathWidthBlocks + 1;
        this.pathWidthBlocks = w > PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS ? 1 : w;
    }

    public int getPathStyleIndex() {
        return pathStyleIndex;
    }

    public void setPathStyleIndex(int pathStyleIndex) {
        this.pathStyleIndex = Math.max(0, pathStyleIndex);
    }

    /** Keeps the index in range when the number of styles in config changes. */
    public void clampPathStyleIndex(int styleCount) {
        if (styleCount <= 0) {
            return;
        }
        this.pathStyleIndex = Math.floorMod(pathStyleIndex, styleCount);
    }

    public void cyclePathStyle(int styleCount) {
        if (styleCount <= 0) {
            return;
        }
        this.pathStyleIndex = (pathStyleIndex + 1) % styleCount;
    }

    @Nullable
    public UUID getSelectedRemovePathId() {
        return selectedRemovePathId;
    }

    public void setSelectedRemovePathId(@Nullable UUID selectedRemovePathId) {
        this.selectedRemovePathId = selectedRemovePathId;
    }

    @Nullable
    public UUID getSelectedNodeId() {
        return selectedNodeId;
    }

    public void setSelectedNodeId(@Nullable UUID selectedNodeId) {
        this.selectedNodeId = selectedNodeId;
    }

    @Nullable
    public PathToolNode findNode(@Nullable UUID id) {
        if (id == null) {
            return null;
        }
        for (PathToolNode n : nodes) {
            if (id.equals(n.getId())) {
                return n;
            }
        }
        return null;
    }

    public void setNodesFromList(@Nonnull List<PathToolNode> copy) {
        nodes.clear();
        nodes.addAll(copy);
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PathToolPlayerComponent c = new PathToolPlayerComponent();
        c.gizmoMode = this.gizmoMode;
        c.pathWidthBlocks = this.pathWidthBlocks;
        c.pathStyleIndex = this.pathStyleIndex;
        c.selectedNodeId = this.selectedNodeId;
        c.selectedRemovePathId = this.selectedRemovePathId;
        c.replaceFilterBlockIdsCsv = this.replaceFilterBlockIdsCsv;
        c.villagerNav = this.villagerNav;
        c.nodes.addAll(this.nodes);
        return c;
    }

    @Nonnull
    private static PathToolGizmoMode parseGizmo(@Nullable String s) {
        if (s == null) {
            return PathToolGizmoMode.Commit;
        }
        try {
            return PathToolGizmoMode.valueOf(s);
        } catch (Exception e) {
            return PathToolGizmoMode.Commit;
        }
    }

    private void decodeNodes(@Nullable String json) {
        nodes.clear();
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            NodeRow[] rows = GSON.fromJson(json, NodeRow[].class);
            if (rows == null) {
                return;
            }
            for (NodeRow r : rows) {
                if (r == null || r.id == null) {
                    continue;
                }
                try {
                    UUID u = UUID.fromString(r.id);
                    nodes.add(
                        new PathToolNode(
                            u,
                            new Vector3d(
                                r.x,
                                r.y,
                                r.z
                            ),
                            r.yawDeg
                        )
                    );
                } catch (Exception ignored) {
                    // skip bad row
                }
            }
        } catch (Exception e) {
            // leave empty
        }
    }

    @Nonnull
    private String encodeNodes() {
        if (nodes.isEmpty()) {
            return "[]";
        }
        NodeRow[] rows = new NodeRow[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            PathToolNode n = nodes.get(i);
            NodeRow r = new NodeRow();
            r.id = n.getId().toString();
            r.x = n.getX();
            r.y = n.getY();
            r.z = n.getZ();
            r.yawDeg = n.getYawDeg();
            rows[i] = r;
        }
        return GSON.toJson(rows);
    }

    private static final class NodeRow {
        @SerializedName("id")
        String id;
        @SerializedName("x")
        double x;
        @SerializedName("y")
        double y;
        @SerializedName("z")
        double z;
        @SerializedName("yawDeg")
        double yawDeg;
    }
}
