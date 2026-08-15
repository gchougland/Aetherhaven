package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * One piece of a wall style while it is being authored. Bounds, anchor, and connection cells are kept in world
 * coordinates so the wireframe and the markers can use them directly; they become prefab local when the style is
 * saved.
 */
public final class PlotCreatorWallPieceDraft {
    /** A connection point: the last solid cell of the piece on that face of its build box. */
    public record Connection(@Nonnull WallCardinal face, @Nonnull Vector3i worldCell) {}

    @Nonnull
    private final WallPieceRole role;
    @Nullable
    private Vector3i cornerFirst;
    @Nullable
    private Vector3i cornerSecond;
    @Nullable
    private Vector3i anchor;
    @Nullable
    private String constructionId;
    @Nullable
    private String prefabPath;
    @Nullable
    private int[] plotAnchorOffset;
    @Nonnull
    private final List<Connection> connections = new ArrayList<>();
    @Nonnull
    private final List<MaterialRequirement> materials = new ArrayList<>();

    public PlotCreatorWallPieceDraft(@Nonnull WallPieceRole role) {
        this.role = role;
    }

    @Nonnull
    public WallPieceRole getRole() {
        return role;
    }

    @Nullable
    public Vector3i getCornerFirst() {
        return cornerFirst;
    }

    public void setCornerFirst(@Nullable Vector3i cornerFirst) {
        this.cornerFirst = cornerFirst != null ? new Vector3i(cornerFirst) : null;
    }

    @Nullable
    public Vector3i getCornerSecond() {
        return cornerSecond;
    }

    public void setCornerSecond(@Nullable Vector3i cornerSecond) {
        this.cornerSecond = cornerSecond != null ? new Vector3i(cornerSecond) : null;
    }

    @Nullable
    public Vector3i getAnchor() {
        return anchor;
    }

    public void setAnchor(@Nullable Vector3i anchor) {
        this.anchor = anchor != null ? new Vector3i(anchor) : null;
    }

    @Nullable
    public String getConstructionId() {
        return constructionId;
    }

    public void setConstructionId(@Nullable String constructionId) {
        this.constructionId = constructionId;
    }

    @Nullable
    public String getPrefabPath() {
        return prefabPath;
    }

    public void setPrefabPath(@Nullable String prefabPath) {
        this.prefabPath = prefabPath;
    }

    /**
     * Anchor offset this piece was already saved with, kept so re-saving a style loaded in the building editor does not
     * move it up or down. Null for a piece being authored from scratch.
     */
    @Nullable
    public int[] getPlotAnchorOffset() {
        return plotAnchorOffset != null ? plotAnchorOffset.clone() : null;
    }

    public void setPlotAnchorOffset(@Nullable int[] plotAnchorOffset) {
        this.plotAnchorOffset =
            plotAnchorOffset != null && plotAnchorOffset.length == 3 ? plotAnchorOffset.clone() : null;
    }

    @Nonnull
    public List<Connection> getConnections() {
        return connections;
    }

    @Nonnull
    public List<MaterialRequirement> getMaterials() {
        return materials;
    }

    public boolean hasBounds() {
        return cornerFirst != null && cornerSecond != null;
    }

    @Nonnull
    public Vector3i boundsMin() {
        return PlotCreatorBoundsValidation.min(
            cornerFirst != null ? cornerFirst : new Vector3i(0, 0, 0),
            cornerSecond != null ? cornerSecond : new Vector3i(0, 0, 0)
        );
    }

    @Nonnull
    public Vector3i boundsMax() {
        return PlotCreatorBoundsValidation.max(
            cornerFirst != null ? cornerFirst : new Vector3i(0, 0, 0),
            cornerSecond != null ? cornerSecond : new Vector3i(0, 0, 0)
        );
    }

    /** Drops the build box, the anchor, and every connection point on this piece. */
    public void clearShape() {
        cornerFirst = null;
        cornerSecond = null;
        anchor = null;
        connections.clear();
    }

    @Nullable
    public Connection connectionOn(@Nonnull WallCardinal face) {
        for (Connection c : connections) {
            if (c.face() == face) {
                return c;
            }
        }
        return null;
    }

    /** Replaces the connection at {@code index}, appending when the index is past the end. */
    public void setConnection(int index, @Nonnull Connection connection) {
        if (index >= 0 && index < connections.size()) {
            connections.set(index, connection);
            return;
        }
        connections.add(connection);
    }

    public void removeConnection(int index) {
        if (index >= 0 && index < connections.size()) {
            connections.remove(index);
        }
    }

    public boolean isComplete() {
        return hasBounds() && anchor != null && connections.size() == role.connectionCount();
    }
}
