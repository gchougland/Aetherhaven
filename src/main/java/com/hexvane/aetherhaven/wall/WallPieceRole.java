package com.hexvane.aetherhaven.wall;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The job a piece does inside a wall style. One piece per role makes a complete style. */
public enum WallPieceRole {
    /** Straight run piece with a connection at each end. */
    SEGMENT("segment", List.of(WallCardinal.NORTH, WallCardinal.SOUTH)),
    /** Straight run piece with a doorway, same two connections as a segment. */
    GATE("gate", List.of(WallCardinal.NORTH, WallCardinal.SOUTH)),
    /** Tower that closes the end of a run: one connection. */
    TOWER_END("tower_end", List.of(WallCardinal.SOUTH)),
    /** Tower the run passes straight through: two opposite connections. */
    TOWER_STRAIGHT("tower_straight", List.of(WallCardinal.NORTH, WallCardinal.SOUTH)),
    /** Tower where the run turns: two connections at right angles. */
    TOWER_CORNER("tower_corner", List.of(WallCardinal.SOUTH, WallCardinal.EAST));

    /** Order shown in the wall style authoring flow and in the crafting bench preview arrows. */
    public static final WallPieceRole[] AUTHORING_ORDER =
        new WallPieceRole[] {SEGMENT, GATE, TOWER_END, TOWER_STRAIGHT, TOWER_CORNER};

    private final String serialized;
    private final List<WallCardinal> connectionFaces;

    WallPieceRole(@Nonnull String serialized, @Nonnull List<WallCardinal> connectionFaces) {
        this.serialized = serialized;
        this.connectionFaces = connectionFaces;
    }

    @Nonnull
    public String serialized() {
        return serialized;
    }

    public int connectionCount() {
        return connectionFaces.size();
    }

    /**
     * The sides this role opens onto while it is being built, in the order the plot creator asks for them. Fixing them
     * up front means the creator can name the side it wants instead of guessing from where the player clicked, which
     * never worked for walls only one or two blocks thick. Rotation takes care of the rest when the wand places a piece.
     */
    @Nonnull
    public List<WallCardinal> connectionFaces() {
        return connectionFaces;
    }

    /** The side asked for at substep {@code index}, or null when the role has no such connection. */
    @Nullable
    public WallCardinal connectionFace(int index) {
        return index >= 0 && index < connectionFaces.size() ? connectionFaces.get(index) : null;
    }

    public boolean isTower() {
        return this == TOWER_END || this == TOWER_STRAIGHT || this == TOWER_CORNER;
    }

    @Nullable
    public static WallPieceRole fromSerialized(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (WallPieceRole role : values()) {
            if (role.serialized.equals(key) || role.name().toLowerCase(Locale.ROOT).equals(key)) {
                return role;
            }
        }
        return null;
    }
}
