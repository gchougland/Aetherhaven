package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves {@link PropInstance}s against the block they occupy. */
public final class PropLookupUtil {
    private PropLookupUtil() {}

    /** Prop (if any) whose current placement covers this exact block. Slow path: resolves every candidate's prefab. */
    @Nullable
    public static PropInstance findPropAtBlock(
        @Nonnull World world,
        @Nonnull PropRegistry registry,
        @Nonnull PropCatalog catalog,
        int x,
        int y,
        int z
    ) {
        for (PropInstance instance : registry.all()) {
            IPrefabBuffer buffer = resolveBuffer(catalog, instance);
            if (buffer == null) {
                continue;
            }
            if (PropPrefabOps.blockBelongsToProp(instance.getAnchor(), instance.getYaw(), buffer, x, y, z)) {
                return instance;
            }
        }
        return null;
    }

    @Nullable
    static IPrefabBuffer resolveBuffer(@Nonnull PropCatalog catalog, @Nonnull PropInstance instance) {
        PropDefinition def = catalog.get(instance.getPropId());
        if (def == null || def.getPrefabPath().isEmpty()) {
            return null;
        }
        return PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
    }
}
