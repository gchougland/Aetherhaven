package com.hexvane.aetherhaven.testtown;

import java.util.ArrayList;
import java.util.List;

/**
 * Row packing for the test town. Pure arithmetic, no world access.
 *
 * <p>Items are laid out in rows that run along X and stack along Z on both sides of a central plaza that holds the
 * charter. Row 0 is the first row south of the plaza, row 1 the first row north of it, row 2 the second row south,
 * and so on, so the town stays centred on the charter and inside its square territory. Every item's front edge
 * touches the street on the plaza side of its row.
 */
public final class TestTownGrid {
    /** Something to place: {@code width} runs along X and {@code depth} along Z, both in blocks. */
    public record Item(String key, int width, int depth) {}

    /** Where an item landed. All bounds are inclusive block coordinates. */
    public record Cell(String key, int minX, int minZ, int maxX, int maxZ, int row, boolean south) {
        public int centerX() { return Math.floorDiv(minX + maxX, 2); }

        /** The Z of the edge that faces the street on the plaza side. */
        public int frontZ() { return south ? minZ : maxZ; }

        /** A block in the street in front of the item, {@code out} blocks beyond its front edge. */
        public int streetZ(int out) { return south ? minZ - out : maxZ + out; }
    }

    /** An east to west street: the band between two rows, or the plaza itself for row index -1. */
    public record Street(int minX, int maxX, int centerZ) {}

    public record Plan(List<Cell> cells, List<Street> streets, int minX, int minZ, int maxX, int maxZ) {
        public Cell find(String key) {
            for (Cell cell : cells) if (cell.key().equals(key)) return cell;
            return null;
        }
    }

    private TestTownGrid() {}

    /**
     * @param rowWidth the widest a row may be, items plus the gaps between them
     * @param street   clear blocks kept between neighbours in a row and between rows
     * @param plaza    clear blocks kept on each side of the charter before the first row starts
     */
    public static Plan pack(List<Item> items, int originX, int originZ, int rowWidth, int street, int plaza) {
        if (rowWidth < 1 || street < 0 || plaza < 1) throw new IllegalArgumentException("Bad grid settings");
        List<List<Item>> rows = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        int used = 0;
        for (Item item : items) {
            if (item.width() < 1 || item.depth() < 1) throw new IllegalArgumentException("Empty item " + item.key());
            int needed = current.isEmpty() ? item.width() : used + street + item.width();
            if (!current.isEmpty() && needed > rowWidth) {
                rows.add(current);
                current = new ArrayList<>();
                needed = item.width();
            }
            current.add(item);
            used = needed;
        }
        if (!current.isEmpty()) rows.add(current);

        List<Cell> cells = new ArrayList<>();
        List<Street> streets = new ArrayList<>();
        int southNext = originZ + plaza + 1;
        int northNext = originZ - plaza - 1;
        int minX = originX, maxX = originX, minZ = originZ - plaza, maxZ = originZ + plaza;
        for (int r = 0; r < rows.size(); r++) {
            List<Item> row = rows.get(r);
            boolean south = r % 2 == 0;
            int total = 0, deepest = 0;
            for (Item item : row) { total += item.width(); deepest = Math.max(deepest, item.depth()); }
            total += street * (row.size() - 1);
            int x = originX - total / 2;
            int rowMinX = x, rowMaxX = x + total - 1;
            for (Item item : row) {
                int cellMinZ = south ? southNext : northNext - item.depth() + 1;
                Cell cell = new Cell(item.key(), x, cellMinZ, x + item.width() - 1, cellMinZ + item.depth() - 1, r, south);
                cells.add(cell);
                x += item.width() + street;
            }
            if (r >= 2 && street > 0) {
                // The band in front of this row, between it and the row before it on the same side.
                int front = south ? southNext : northNext;
                streets.add(new Street(rowMinX, rowMaxX, south ? front - (street + 1) / 2 : front + (street + 1) / 2));
            }
            if (south) { maxZ = Math.max(maxZ, southNext + deepest - 1); southNext += deepest + street; }
            else { minZ = Math.min(minZ, northNext - deepest + 1); northNext -= deepest + street; }
            minX = Math.min(minX, rowMinX);
            maxX = Math.max(maxX, rowMaxX);
        }
        streets.addFirst(new Street(minX, maxX, originZ));
        return new Plan(List.copyOf(cells), List.copyOf(streets), minX, minZ, maxX, maxZ);
    }

    /** True when two cells are closer than {@code gap} clear blocks. Used by the tests and the preflight. */
    public static boolean tooClose(Cell a, Cell b, int gap) {
        return !(a.maxX() + gap < b.minX() || b.maxX() + gap < a.minX() || a.maxZ() + gap < b.minZ() || b.maxZ() + gap < a.minZ());
    }
}
