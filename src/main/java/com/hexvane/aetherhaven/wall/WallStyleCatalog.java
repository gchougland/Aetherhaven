package com.hexvane.aetherhaven.wall;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Every installed wall style, grouped from the wall buildings in the construction catalog by their style id. This is
 * what the wand picks pieces from and what replaces the old hand written prefab tables.
 */
public final class WallStyleCatalog {
    /** Style id used by the built in wall pieces and by any wall building that omits one. */
    public static final String DEFAULT_STYLE_ID = "core";

    @Nullable
    private static volatile WallStyleCatalog cached;

    @Nullable
    private static volatile ConstructionCatalog cachedSource;

    @Nonnull
    private final Map<String, WallStyle> styles;

    @Nonnull
    private final Map<String, String> styleIdByConstructionId;

    private WallStyleCatalog(
        @Nonnull Map<String, WallStyle> styles, @Nonnull Map<String, String> styleIdByConstructionId
    ) {
        this.styles = styles;
        this.styleIdByConstructionId = styleIdByConstructionId;
    }

    @Nonnull
    public static WallStyleCatalog empty() {
        return new WallStyleCatalog(Map.of(), Map.of());
    }

    @Nonnull
    public static WallStyleCatalog get() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            WallStyleCatalog ref = cached;
            return ref != null ? ref : empty();
        }
        return get(plugin.getConstructionCatalog());
    }

    @Nonnull
    public static WallStyleCatalog get(@Nonnull ConstructionCatalog constructions) {
        WallStyleCatalog ref = cached;
        if (ref != null && cachedSource == constructions) {
            return ref;
        }
        synchronized (WallStyleCatalog.class) {
            ref = cached;
            if (ref != null && cachedSource == constructions) {
                return ref;
            }
            ref = from(constructions);
            cached = ref;
            cachedSource = constructions;
            return ref;
        }
    }

    /** Drops the cache so the next lookup rebuilds from the reloaded construction catalog. */
    public static void invalidate() {
        synchronized (WallStyleCatalog.class) {
            cached = null;
            cachedSource = null;
        }
    }

    /** Tests install a catalog without a running plugin. */
    public static void setForTests(@Nullable WallStyleCatalog catalog) {
        synchronized (WallStyleCatalog.class) {
            cached = catalog;
            cachedSource = null;
        }
    }

    @Nonnull
    public static WallStyleCatalog from(@Nonnull ConstructionCatalog constructions) {
        Map<String, WallStyle.Builder> builders = new LinkedHashMap<>();
        Map<String, String> styleByConstruction = new LinkedHashMap<>();
        for (ConstructionDefinition def : constructions.list()) {
            if (!def.isWallSegment()) {
                continue;
            }
            WallPieceDefinition piece = def.getWallPiece();
            if (piece == null || !piece.isValid()) {
                continue;
            }
            WallPieceRole role = piece.role();
            if (role == null) {
                continue;
            }
            String styleId = normalizeStyleId(def.getStyleId());
            int[] anchorOffset = def.getPlotAnchorOffset();
            WallPieceDefinition inSignSpace =
                anchorOffset != null && anchorOffset.length == 3
                    ? piece.translatedLocal(anchorOffset[0], anchorOffset[2])
                    : piece;
            builders
                .computeIfAbsent(styleId, WallStyle::builder)
                .displayName(role == WallPieceRole.SEGMENT ? def.getDisplayName() : null)
                .add(def.getId(), role, inSignSpace);
            styleByConstruction.put(def.getId(), styleId);
        }
        Map<String, WallStyle> styles = new LinkedHashMap<>();
        for (Map.Entry<String, WallStyle.Builder> e : builders.entrySet()) {
            styles.put(e.getKey(), e.getValue().build());
        }
        return new WallStyleCatalog(
            Collections.unmodifiableMap(styles), Collections.unmodifiableMap(styleByConstruction)
        );
    }

    @Nonnull
    public static String normalizeStyleId(@Nullable String raw) {
        return raw == null || raw.isBlank() ? DEFAULT_STYLE_ID : raw.trim();
    }

    public boolean isEmpty() {
        return styles.isEmpty();
    }

    /** Style ids that provide every role, default style first. */
    @Nonnull
    public List<String> completeStyleIds() {
        List<String> out = new ArrayList<>();
        if (styles.containsKey(DEFAULT_STYLE_ID) && styles.get(DEFAULT_STYLE_ID).isComplete()) {
            out.add(DEFAULT_STYLE_ID);
        }
        List<String> rest = new ArrayList<>();
        for (Map.Entry<String, WallStyle> e : styles.entrySet()) {
            if (e.getKey().equals(DEFAULT_STYLE_ID) || !e.getValue().isComplete()) {
                continue;
            }
            rest.add(e.getKey());
        }
        Collections.sort(rest);
        out.addAll(rest);
        return out;
    }

    @Nonnull
    public List<WallStyle> completeStyles() {
        List<WallStyle> out = new ArrayList<>();
        for (String id : completeStyleIds()) {
            WallStyle style = styles.get(id);
            if (style != null) {
                out.add(style);
            }
        }
        return out;
    }

    @Nullable
    public WallStyle style(@Nullable String styleId) {
        return styleId == null ? null : styles.get(normalizeStyleId(styleId));
    }

    /** The style to use when a session has no explicit pick: the default style, else the first complete one. */
    @Nullable
    public WallStyle defaultStyle() {
        WallStyle core = styles.get(DEFAULT_STYLE_ID);
        if (core != null && core.isComplete()) {
            return core;
        }
        List<WallStyle> complete = completeStyles();
        if (!complete.isEmpty()) {
            return complete.get(0);
        }
        return styles.isEmpty() ? null : styles.values().iterator().next();
    }

    @Nullable
    public String styleIdForConstruction(@Nullable String constructionId) {
        return constructionId == null ? null : styleIdByConstructionId.get(constructionId);
    }

    /** Finds a placed piece in any style, so the wand can continue a wall someone else started. */
    @Nullable
    public WallStyle.Piece pieceByConstructionId(@Nullable String constructionId) {
        String styleId = styleIdForConstruction(constructionId);
        if (styleId == null) {
            return null;
        }
        WallStyle style = styles.get(styleId);
        return style == null ? null : style.pieceByConstructionId(constructionId);
    }

    @Nullable
    public WallPieceDefinition definitionFor(@Nullable String constructionId) {
        WallStyle.Piece piece = pieceByConstructionId(constructionId);
        return piece == null ? null : piece.definition();
    }

    @Nullable
    public WallPieceRole roleFor(@Nullable String constructionId) {
        WallStyle.Piece piece = pieceByConstructionId(constructionId);
        return piece == null ? null : piece.role();
    }

    /** Faces a placed piece opens onto, so the wand can continue a wall that is already in the world. */
    @Nullable
    public java.util.EnumSet<WallCardinal> connectionsForPlacedPiece(
        @Nullable String constructionId, int rotationSteps
    ) {
        WallPieceDefinition def = definitionFor(constructionId);
        return def == null ? null : def.worldFaces(rotationSteps);
    }

    public boolean isTowerConstructionId(@Nullable String constructionId) {
        WallPieceRole role = roleFor(constructionId);
        return role != null && role.isTower();
    }
}
