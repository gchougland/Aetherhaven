package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Lays a band of integer cells along each sample. {@link CellRole#Center} = pathway in the inner strips; {@link
 * CellRole#Outline} = grass on the outer left and right strips (one block on each side when width is 3+). If two
 * samples claim the same cell, path (Center) wins over grass. For width 1-2, all cells are path (no flanking grass).
 */
public final class PathPlannedCell {
    public enum CellRole {
        /** Pathway / mud mix ({@code Soil_Pathway} etc. in cement). */
        Center,
        /** Grass strip on the outer edge of the width band. */
        Outline
    }

    public static final class Planned {
        @Nonnull
        public final Vector3i pos;
        @Nonnull
        public final CellRole role;

        public Planned(@Nonnull Vector3i pos, @Nonnull CellRole role) {
            this.pos = pos;
            this.role = role;
        }
    }

    private PathPlannedCell() {}

    @Nonnull
    public static List<Planned> build(
        @Nonnull World world,
        @Nonnull List<PathSplineUtil.PathSample> samples,
        int pathWidthBlocks,
        int rayStartAboveY,
        int maxRayDown
    ) {
        if (samples.isEmpty()) {
            return List.of();
        }
        int w = Math.max(1, Math.min(8, pathWidthBlocks));
        Map<String, CellRole> best = new HashMap<>();
        for (PathSplineUtil.PathSample s : samples) {
            for (int i = 0; i < w; i++) {
                // Symmetric band for any W (odd or even): e.g. W=8 uses offsets -3.5..+3.5
                double lateral = i - 0.5 * (w - 1);
                double wx = s.position.getX() + s.right.getX() * lateral;
                double wz = s.position.getZ() + s.right.getZ() * lateral;
                int bx = (int) Math.floor(wx);
                int bz = (int) Math.floor(wz);
                int startY = Math.min(319, (int) Math.floor(s.position.getY()) + rayStartAboveY);
                Integer y = PathGrounding.findSupportY(world, bx, bz, startY, maxRayDown, 1);
                if (y == null) {
                    continue;
                }
                String key = bx + ":" + y + ":" + bz;
                // Outermost lateral indices = grass; inner = pathway. For w<3, everything is path (no two grass flanks).
                CellRole want;
                if (w < 3) {
                    want = CellRole.Center;
                } else {
                    want = (i == 0 || i == w - 1) ? CellRole.Outline : CellRole.Center;
                }
                best.compute(
                    key,
                    (k2, old) -> {
                        if (old == null) {
                            return want;
                        }
                        if (old == CellRole.Center) {
                            return old;
                        }
                        return want == CellRole.Center ? want : old;
                    }
                );
            }
        }
        List<Planned> out = new ArrayList<>();
        for (Map.Entry<String, CellRole> e : best.entrySet()) {
            String[] p = e.getKey().split(":");
            if (p.length == 3) {
                int x = Integer.parseInt(p[0]);
                int y = Integer.parseInt(p[1]);
                int z = Integer.parseInt(p[2]);
                out.add(new Planned(new Vector3i(x, y, z), e.getValue()));
            }
        }
        out.sort(
            (a, b) ->
                a.pos.getY() == b.pos.getY()
                    ? a.pos.getX() == b.pos.getX() ? Integer.compare(a.pos.getZ(), b.pos.getZ()) : Integer.compare(a.pos.getX(), b.pos.getX())
                    : Integer.compare(a.pos.getY(), b.pos.getY())
        );
        return Collections.unmodifiableList(out);
    }
}
