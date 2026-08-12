package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Validates a prop placement before commit. Returns an error key suffix (appended to
 * {@code aetherhaven_props.aetherhaven.prop.placement.error.<suffix>} by the UI/command layer) or {@code null} when
 * the placement is valid.
 */
public final class PropPlacementValidator {
    public static final String ERROR_UNKNOWN_PROP = "unknownProp";
    public static final String ERROR_PREFAB_MISSING = "prefabMissing";
    public static final String ERROR_BLOCKED = "blocked";

    private PropPlacementValidator() {}

    @Nullable
    public static String validate(
        @Nonnull World world,
        @Nonnull PropCatalog catalog,
        @Nonnull String propId,
        @Nonnull Vector3i anchor,
        @Nonnull com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation yaw
    ) {
        PropDefinition def = catalog.get(propId);
        if (def == null) {
            return ERROR_UNKNOWN_PROP;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buffer == null) {
            return ERROR_PREFAB_MISSING;
        }
        if (!PropPrefabOps.canPlaceSolids(world, anchor, yaw, buffer)) {
            return ERROR_BLOCKED;
        }
        return null;
    }

    /** Convenience overload that also resolves the buffer for the caller (avoids resolving it twice). */
    @Nullable
    public static IPrefabBuffer resolveValidatedBuffer(
        @Nonnull World world,
        @Nonnull PropCatalog catalog,
        @Nonnull String propId,
        @Nonnull Vector3i anchor,
        @Nonnull com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation yaw
    ) {
        PropDefinition def = catalog.get(propId);
        if (def == null) {
            return null;
        }
        return PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
    }
}
