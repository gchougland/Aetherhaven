package com.hexvane.aetherhaven.workplace;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3i;

/**
 * The benches of one kind that a workplace page lists, one row each. A row's button names the block the row stands for,
 * never the row's place in the list, so the second furnace's button can only ever open the second furnace.
 */
public final class WorkplaceBenchRows {
    private WorkplaceBenchRows() {}

    /**
     * How far past the player the base game's bench screen is kept open. The base game closes a bench screen once its
     * block is more than seven blocks from the player. A workplace page lets a player open a bench from a little
     * further than that, and any furnace of the workplace from its list, so the screen is given the room it needs.
     */
    public static final double REACH_MARGIN = 2.0;

    /** One listed bench. {@code number} counts benches of this kind from 1, in the order the workplace is read. */
    public record Row(int number, WorkplaceEquipment.Bench bench) {
        /** What the row's button sends back. */
        public String key() { return WorkplaceBenchRows.key(bench.x(), bench.y(), bench.z()); }
        public Vector3i position() { return new Vector3i(bench.x(), bench.y(), bench.z()); }
    }

    public static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    /** Every bench of this kind in the workplace, numbered in the order the workplace is read. */
    public static List<Row> rows(List<WorkplaceEquipment.Bench> benches, String benchId) {
        var rows = new ArrayList<Row>();
        for (var bench : benches) if (benchId.equals(bench.id())) rows.add(new Row(rows.size() + 1, bench));
        return rows;
    }

    /** The block a button named, or null when the text is not a block position. */
    public static Vector3i position(String key) {
        if (key == null) return null;
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new Vector3i(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException notAPosition) {
            return null;
        }
    }

    /** The row a button belongs to, or null when no bench of this kind stands at the named block any more. */
    public static Row find(List<WorkplaceEquipment.Bench> benches, String benchId, String key) {
        var at = position(key);
        if (at == null) return null;
        for (var row : rows(benches, benchId))
            if (row.bench().x() == at.x && row.bench().y() == at.y && row.bench().z() == at.z) return row;
        return null;
    }

    /**
     * How far from the player a bench screen opened from a workplace page stays open: never less than the base game
     * allows, and always enough for where the player stands right now.
     *
     * @param baseReach the base game's own limit for this screen
     * @param distance  how far the player is from the bench block's corner, as the base game measures it
     */
    public static double screenReach(double baseReach, double distance) {
        return Math.max(baseReach, distance + REACH_MARGIN);
    }
}
