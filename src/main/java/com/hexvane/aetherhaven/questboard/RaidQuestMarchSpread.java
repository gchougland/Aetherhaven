package com.hexvane.aetherhaven.questboard;

import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Spreads raid march teleports so recovery does not stack every mob on one block. */
final class RaidQuestMarchSpread {
    private static final double MIN_RADIUS = 2.0;
    private static final double MAX_RADIUS = 5.5;

    private RaidQuestMarchSpread() {}

    @Nonnull
    static Vector3d offsetAround(@Nonnull Vector3d center, @Nonnull UUID mobUuid) {
        long hash = mobUuid.getLeastSignificantBits() ^ mobUuid.getMostSignificantBits();
        double angle = (hash & 0xFFFFL) / 65536.0 * Math.PI * 2.0;
        double radiusSpan = MAX_RADIUS - MIN_RADIUS;
        double radius = MIN_RADIUS + ((hash >>> 16) & 0x7L) / 7.0 * radiusSpan;
        return new Vector3d(
            center.x + Math.cos(angle) * radius,
            center.y,
            center.z + Math.sin(angle) * radius
        );
    }
}
