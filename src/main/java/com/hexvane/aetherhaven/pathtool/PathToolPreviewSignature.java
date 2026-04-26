package com.hexvane.aetherhaven.pathtool;

import java.util.UUID;
import javax.annotation.Nonnull;

/** Stable hash of editable path state so we can skip redundant debug redraws when nothing changed. */
public final class PathToolPreviewSignature {
    private PathToolPreviewSignature() {}

    public static long compute(@Nonnull PathToolPlayerComponent st) {
        long h = 5381;
        h = h * 33 + st.getGizmoMode().ordinal();
        h = h * 33 + st.getPathWidthBlocks();
        h = h * 33 + st.getPathStyleIndex();
        h = h * 33 + st.getNodes().size();
        UUID sel = st.getSelectedNodeId();
        if (sel != null) {
            h = h * 33 + sel.getLeastSignificantBits();
            h = h * 33 + sel.getMostSignificantBits();
        }
        for (PathToolNode n : st.getNodes()) {
            h = h * 33 + n.getId().hashCode();
            h = h * 33 + Long.hashCode(Double.doubleToLongBits(n.getX()));
            h = h * 33 + Long.hashCode(Double.doubleToLongBits(n.getY()));
            h = h * 33 + Long.hashCode(Double.doubleToLongBits(n.getZ()));
            h = h * 33 + Long.hashCode(Double.doubleToLongBits(n.getYawDeg()));
        }
        return h;
    }
}
