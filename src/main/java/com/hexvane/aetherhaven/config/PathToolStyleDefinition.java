package com.hexvane.aetherhaven.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.hexvane.aetherhaven.AetherhavenConstants;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One configurable path style: display {@link #name}, optional {@link #columns} (9 wide chest grid), or legacy
 * {@link #centerBlockIds} for the center strip.
 */
public final class PathToolStyleDefinition {
    public static final int STYLE_GRID_COLUMNS = 9;
    public static final int STYLE_GRID_ROWS = 6;
    public static final int STYLE_GRID_SLOTS = STYLE_GRID_COLUMNS * STYLE_GRID_ROWS;

    /** Maximum placed path width in blocks; matches {@link #STYLE_GRID_COLUMNS}. */
    public static final int MAX_PATH_WIDTH_BLOCKS = STYLE_GRID_COLUMNS;

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type LIST_TYPE = new TypeToken<List<PathToolStyleDefinition>>() {
    }.getType();

    @Nonnull
    public static final String DEFAULT_JSON = "["
        + "{\"name\":\"Soil path\",\"centerBlockIds\":[\"Soil_Pathway\",\"Soil_Mud_Dry\"]},"
        + "{\"name\":\"Cobblestone\",\"centerBlockIds\":[\"Rock_Stone_Cobble\",\"Rock_Stone_Cobble_Mossy\"]}"
        + "]";

    @SerializedName("name")
    private String name = "";

    @SerializedName("centerBlockIds")
    @Nullable
    private List<String> centerBlockIds;

    /** Up to 9 columns; each inner list is a weighted block id pool for that lateral path slot. */
    @SerializedName("columns")
    @Nullable
    private List<List<String>> columns;

    @Nonnull
    public String getName() {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return "Path";
    }

    public void setName(@Nullable String name) {
        this.name = name != null ? name.trim() : "";
    }

    @Nonnull
    public List<String> getCenterBlockIds() {
        if (centerBlockIds == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : centerBlockIds) {
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public void setCenterBlockIds(@Nullable List<String> ids) {
        if (ids == null) {
            this.centerBlockIds = null;
            return;
        }
        this.centerBlockIds = new ArrayList<>(ids);
    }

    @Nonnull
    public List<List<String>> getColumns() {
        if (columns == null) {
            return List.of();
        }
        List<List<String>> out = new ArrayList<>();
        for (List<String> col : columns) {
            if (col == null) {
                out.add(List.of());
                continue;
            }
            List<String> cleaned = new ArrayList<>();
            for (String id : col) {
                if (id != null && !id.isBlank()) {
                    cleaned.add(id.trim());
                }
            }
            out.add(Collections.unmodifiableList(cleaned));
        }
        return Collections.unmodifiableList(out);
    }

    public void setColumns(@Nullable List<List<String>> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : null;
    }

    public boolean hasColumnLayout() {
        for (List<String> col : getColumns()) {
            if (!col.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean isUsable() {
        return !getCenterBlockIds().isEmpty() || hasColumnLayout();
    }

    /** Center column of the 9-wide chest grid (column 4). */
    public static final int CENTER_COLUMN = STYLE_GRID_COLUMNS / 2;

    /**
     * Maps path lateral index (0 = left edge of the placed band) to a chest column. Left path cells consume filled
     * columns from the left edge inward, right cells from the right edge inward, and any remaining interior cells use
     * the middle designer column (column 4).
     */
    public int chestColumnForLateral(int lateralIndex, int pathWidthBlocks) {
        int w = Math.max(1, Math.min(MAX_PATH_WIDTH_BLOCKS, pathWidthBlocks));
        int i = Math.max(0, Math.min(w - 1, lateralIndex));
        if (w <= 1) {
            return defaultSingleWidthColumn();
        }

        int leftSlots = (w - 1) / 2;
        int rightSlots = (w - 1) / 2;

        if (i < leftSlots) {
            List<Integer> leftFilled = filledSideColumns(true);
            if (i < leftFilled.size()) {
                return leftFilled.get(i);
            }
            if (columnHasBlocks(getColumns(), CENTER_COLUMN)) {
                return CENTER_COLUMN;
            }
            return leftFilled.isEmpty() ? 0 : leftFilled.get(leftFilled.size() - 1);
        }
        if (i >= w - rightSlots) {
            int rightIndex = (w - 1) - i;
            List<Integer> rightFilled = filledSideColumns(false);
            if (rightIndex < rightFilled.size()) {
                return rightFilled.get(rightIndex);
            }
            if (columnHasBlocks(getColumns(), CENTER_COLUMN)) {
                return CENTER_COLUMN;
            }
            return rightFilled.isEmpty() ? STYLE_GRID_COLUMNS - 1 : rightFilled.get(rightFilled.size() - 1);
        }
        return CENTER_COLUMN;
    }

    private int defaultSingleWidthColumn() {
        List<Integer> leftFilled = filledSideColumns(true);
        if (!leftFilled.isEmpty()) {
            return leftFilled.get(0);
        }
        List<Integer> rightFilled = filledSideColumns(false);
        if (!rightFilled.isEmpty()) {
            return rightFilled.get(0);
        }
        return CENTER_COLUMN;
    }

    @Nonnull
    private List<Integer> filledSideColumns(boolean left) {
        List<Integer> out = new ArrayList<>();
        List<List<String>> cols = getColumns();
        if (left) {
            for (int c = 0; c < CENTER_COLUMN; c++) {
                if (columnHasBlocks(cols, c)) {
                    out.add(c);
                }
            }
        } else {
            for (int c = STYLE_GRID_COLUMNS - 1; c > CENTER_COLUMN; c--) {
                if (columnHasBlocks(cols, c)) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static boolean columnHasBlocks(@Nonnull List<List<String>> cols, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= cols.size()) {
            return false;
        }
        return !cols.get(columnIndex).isEmpty();
    }

    /**
     * Picks a block for a path cell using the chest column mapped from {@code lateralIndex} and
     * {@code pathWidthBlocks}.
     */
    @Nonnull
    public String pickBlockForPathCell(int lateralIndex, int pathWidthBlocks, @Nonnull Random r) {
        return pickBlockForColumn(chestColumnForLateral(lateralIndex, pathWidthBlocks), r);
    }

    /**
     * Picks a block for the given chest column index (0 = left edge of the 9-wide grid).
     */
    @Nonnull
    public String pickBlockForColumn(int columnIndex, @Nonnull Random r) {
        if (hasColumnLayout()) {
            List<List<String>> cols = getColumns();
            if (columnIndex >= 0 && columnIndex < cols.size()) {
                List<String> pool = cols.get(columnIndex);
                if (!pool.isEmpty()) {
                    return pool.get(r.nextInt(pool.size()));
                }
            }
            if (columnIndex != CENTER_COLUMN && columnIndex >= 0 && columnIndex < cols.size()) {
                List<String> midPool = cols.get(CENTER_COLUMN);
                if (!midPool.isEmpty()) {
                    return midPool.get(r.nextInt(midPool.size()));
                }
            }
        }
        List<String> legacy = getCenterBlockIds();
        if (!legacy.isEmpty()) {
            return legacy.get(r.nextInt(legacy.size()));
        }
        return r.nextBoolean() ? AetherhavenConstants.PATH_BLOCK_PATHWAY : AetherhavenConstants.PATH_BLOCK_MUD_DRY;
    }

    /** Basic dirt path for a new style: center column only. */
    @Nonnull
    public static PathToolStyleDefinition newDefaultDirtStyle() {
        PathToolStyleDefinition d = new PathToolStyleDefinition();
        d.setName("New path");
        List<List<String>> cols = new ArrayList<>();
        for (int i = 0; i < STYLE_GRID_COLUMNS; i++) {
            cols.add(new ArrayList<>());
        }
        cols.get(CENTER_COLUMN).add(AetherhavenConstants.PATH_BLOCK_PATHWAY);
        cols.get(CENTER_COLUMN).add(AetherhavenConstants.PATH_BLOCK_MUD_DRY);
        d.setColumns(cols);
        d.setCenterBlockIds(null);
        return d;
    }

    @Nonnull
    public static PathToolStyleDefinition copyOf(@Nonnull PathToolStyleDefinition src) {
        PathToolStyleDefinition d = new PathToolStyleDefinition();
        d.setName(src.getName());
        d.setCenterBlockIds(new ArrayList<>(src.getCenterBlockIds()));
        List<List<String>> cols = new ArrayList<>();
        for (List<String> col : src.getColumns()) {
            cols.add(new ArrayList<>(col));
        }
        d.setColumns(cols.isEmpty() ? null : cols);
        return d;
    }

    @Nonnull
    public static String serializeList(@Nonnull List<PathToolStyleDefinition> list) {
        return GSON.toJson(list);
    }

    @Nonnull
    public static List<PathToolStyleDefinition> parseList(@Nullable String json) {
        String s = json != null ? json.trim() : "";
        if (s.isEmpty()) {
            return parseList(DEFAULT_JSON);
        }
        try {
            List<PathToolStyleDefinition> list = GSON.fromJson(s, LIST_TYPE);
            if (list == null || list.isEmpty()) {
                return parseList(DEFAULT_JSON);
            }
            List<PathToolStyleDefinition> ok = new ArrayList<>();
            for (PathToolStyleDefinition d : list) {
                if (d != null && d.isUsable()) {
                    ok.add(d);
                }
            }
            return ok.isEmpty() ? parseList(DEFAULT_JSON) : Collections.unmodifiableList(ok);
        } catch (Exception e) {
            return parseList(DEFAULT_JSON);
        }
    }
}
