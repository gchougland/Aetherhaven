package com.hexvane.aetherhaven.leveling;

import java.util.UUID;

/** Persisted with the town, scoped to both owner and leveling provider. */
public record OwnerLevelSnapshot(UUID owner, String provider, int level) {
    public int levelFor(UUID currentOwner, String activeProvider) {
        return owner != null && owner.equals(currentOwner) && activeProvider.equals(provider) ? Math.max(0, level) : 0;
    }
}
