package com.hexvane.aetherhaven.town;

import org.joml.Vector3d;

/** A shared ring for residents and any inn visitors added during the same reset. */
public final class VillagerResetLayout {
    private VillagerResetLayout() {}

    public static Vector3d position(Vector3d center, int slot, int count) {
        int slots = Math.max(1, count);
        // Grow the ring for larger towns while keeping adjacent villagers at least 1.75 blocks apart.
        double radius = slots <= 1 ? 3.0 : Math.max(3.0, 1.75 / (2.0 * Math.sin(Math.PI / slots)));
        double angle = 2.0 * Math.PI * Math.floorMod(slot, slots) / slots;
        return new Vector3d(center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius);
    }
}
