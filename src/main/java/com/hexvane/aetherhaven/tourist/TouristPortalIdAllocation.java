package com.hexvane.aetherhaven.tourist;

import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Chooses portal UUIDs so prefab-baked or colliding ids cannot collapse the multi-town travel network.
 * Registry entries are keyed by portal id; two towns must never share one.
 */
public final class TouristPortalIdAllocation {
    private TouristPortalIdAllocation() {}

    /**
     * @param preferredRaw portal id already on the block, or null/blank to mint a new one
     * @param ownerOfPreferred lookup of whoever already owns that id in the registry (may be null)
     * @return preferred when free or already bound to {@code pos}; otherwise a fresh UUID
     */
    @Nonnull
    public static UUID allocate(
        @Nonnull Vector3i pos,
        @Nullable String preferredRaw,
        @Nonnull Function<UUID, TouristPortalRecord> ownerOfPreferred
    ) {
        if (preferredRaw == null || preferredRaw.isBlank()) {
            return UUID.randomUUID();
        }
        UUID preferred;
        try {
            preferred = UUID.fromString(preferredRaw.trim());
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
        TouristPortalRecord owner = ownerOfPreferred.apply(preferred);
        if (owner == null) {
            return preferred;
        }
        Vector3i ownerPos = owner.getBlockPosition();
        if (ownerPos.x == pos.x && ownerPos.y == pos.y && ownerPos.z == pos.z) {
            return preferred;
        }
        return UUID.randomUUID();
    }

    /**
     * Prefer an existing block portal id only when the block is already bound to a town/plot (not a prefab
     * template). Template leftovers often ship with a fixed authoring UUID.
     */
    @Nullable
    public static String preferredIdFromBlock(@Nullable TouristPortalBlock blockComp) {
        if (blockComp == null || blockComp.isTemplatePlacement() || blockComp.getPortalId().isBlank()) {
            return null;
        }
        return blockComp.getPortalId();
    }
}
