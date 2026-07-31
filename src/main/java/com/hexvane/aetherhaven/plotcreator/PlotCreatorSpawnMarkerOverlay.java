package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.pathtool.PathDebugPreviewUtil;
import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

/** Per-player debug spheres for adventurer spawn locals during a plot creator session. */
public final class PlotCreatorSpawnMarkerOverlay {
    /** Short hold so removed markers fade quickly when not re-sent. */
    private static final float MARKER_HOLD_SECONDS = 4f;
    private static final Vector3f COLOR_ADVENTURER = new Vector3f(0.55f, 0.35f, 0.95f);
    private static final Vector3f COLOR_VISITOR = new Vector3f(0.25f, 0.82f, 0.72f);
    private static final double SPHERE_RADIUS = 0.32;
    private static final float OPACITY = 0.82f;

    private PlotCreatorSpawnMarkerOverlay() {}

    public static long signature(@Nonnull PlotCreatorDraft draft) {
        long h = 17L;
        for (PlotCreatorAdventurerSpawnEntry entry : draft.getAdventurerSpawns()) {
            h = 31 * h + entry.getLocalX();
            h = 31 * h + entry.getLocalY();
            h = 31 * h + entry.getLocalZ();
            h = 31 * h + Float.floatToIntBits(entry.getYawRadians());
        }
        for (int[] local : draft.getVisitorSpawnLocals()) {
            h = 31 * h + local[0];
            h = 31 * h + local[1];
            h = 31 * h + local[2];
        }
        return h;
    }

    public static void refresh(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        PlotCreatorDraft draft = session.getDraft();
        if (draft.getPlotAnchor() == null && draft.getPrefabOriginMin() == null) {
            return;
        }
        for (PlotCreatorAdventurerSpawnEntry entry : draft.getAdventurerSpawns()) {
            drawSphere(playerRef, PlotCreatorSpawnLocations.standCenterWorld(draft, entry.localArray()), COLOR_ADVENTURER);
        }
        for (int[] local : draft.getVisitorSpawnLocals()) {
            drawSphere(playerRef, PlotCreatorSpawnLocations.standCenterWorld(draft, local), COLOR_VISITOR);
        }
    }

    private static void drawSphere(@Nonnull PlayerRef playerRef, @Nonnull Vector3d center, @Nonnull Vector3f color) {
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(center.x, center.y, center.z);
        m.scale(SPHERE_RADIUS, SPHERE_RADIUS, SPHERE_RADIUS);
        DisplayDebug packet =
            new DisplayDebug(
                DebugShape.Sphere,
                Matrix4dUtil.asFloatData(m),
                color,
                MARKER_HOLD_SECONDS,
                (byte) PathDebugPreviewUtil.FLAG_SOLID_OVERLAY,
                null,
                OPACITY
            );
        playerRef.getPacketHandler().write(packet);
    }
}
