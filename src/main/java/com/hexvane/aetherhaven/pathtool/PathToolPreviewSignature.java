package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Stable hash of editable path state so we can skip redundant debug redraws when nothing changed. */
public final class PathToolPreviewSignature {
    private PathToolPreviewSignature() {}

    public static long compute(@Nonnull PathToolPlayerComponent st) {
        return computeInternal(st, 0L, null, null);
    }

    public static long compute(@Nonnull PathToolPlayerComponent st, long registryRevision) {
        return computeInternal(st, registryRevision, null, null);
    }

    public static long compute(
        @Nonnull PathToolPlayerComponent st,
        long registryRevision,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        return computeInternal(st, registryRevision, ref, store);
    }

    private static long computeInternal(
        @Nonnull PathToolPlayerComponent st,
        long registryRevision,
        @javax.annotation.Nullable Ref<EntityStore> ref,
        @javax.annotation.Nullable Store<EntityStore> store
    ) {
        long h = 5381;
        h = h * 33 + st.getGizmoMode().ordinal();
        h = h * 33 + st.getPathWidthBlocks();
        h = h * 33 + st.getPathStyleIndex();
        if (ref != null && store != null) {
            h = h * 33 + PathToolReplaceFilterResolver.effectiveBlockIds(ref, store, st).hashCode();
        } else {
            h = h * 33 + st.getReplaceFilterBlockIds().hashCode();
        }
        h = h * 33 + st.getNodes().size();
        h = h * 33 + registryRevision;
        UUID sel = st.getSelectedNodeId();
        if (sel != null) {
            h = h * 33 + sel.getLeastSignificantBits();
            h = h * 33 + sel.getMostSignificantBits();
        }
        UUID removeSel = st.getSelectedRemovePathId();
        if (removeSel != null) {
            h = h * 33 + removeSel.getLeastSignificantBits();
            h = h * 33 + removeSel.getMostSignificantBits();
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
