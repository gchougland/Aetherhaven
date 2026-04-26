package com.hexvane.aetherhaven.pathtool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
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
            (c, w) -> c.pathWidthBlocks = w != null && w > 0 ? w : 1,
            c -> c.pathWidthBlocks
        )
        .add()
        .append(
            new KeyedCodec<>("PathStyleIndex", Codec.INTEGER),
            (c, i) -> c.pathStyleIndex = i != null && i >= 0 ? i : 0,
            c -> c.pathStyleIndex
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PathToolPlayerComponent> componentType;

    @Nonnull
    private PathToolGizmoMode gizmoMode = PathToolGizmoMode.Translate;
    private int pathWidthBlocks = 1;
    private int pathStyleIndex;
    @Nullable
    private UUID selectedNodeId;
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

    /** Cycles: Translate -> Rotate -> Commit -> Translate. */
    public void cycleGizmoMode() {
        this.gizmoMode = switch (gizmoMode) {
            case Translate -> PathToolGizmoMode.Rotate;
            case Rotate -> PathToolGizmoMode.Commit;
            case Commit -> PathToolGizmoMode.Translate;
        };
    }

    public int getPathWidthBlocks() {
        return pathWidthBlocks;
    }

    public void setPathWidthBlocks(int blocks) {
        this.pathWidthBlocks = Math.max(1, Math.min(8, blocks));
    }

    /** Cycles width: 1..8 -> 1. */
    public void cyclePathWidth() {
        int w = pathWidthBlocks + 1;
        this.pathWidthBlocks = w > 8 ? 1 : w;
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
        c.nodes.addAll(this.nodes);
        return c;
    }

    @Nonnull
    private static PathToolGizmoMode parseGizmo(@Nullable String s) {
        if (s == null) {
            return PathToolGizmoMode.Translate;
        }
        try {
            return PathToolGizmoMode.valueOf(s);
        } catch (Exception e) {
            return PathToolGizmoMode.Translate;
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
