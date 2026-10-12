package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps a profession's own prop off other residents' spots.
 *
 * <p>The building editor used to stamp the bard's lute profile on every spot given the Leisure animation. Those
 * spots were copied into building definitions and from there into saved towns, so the doctor, the alchemist and
 * others stood at work holding a lute. A profile now names the resident kinds allowed to hold it, and a spot
 * keeps such a profile only when the spot belongs to one of them.
 */
public final class PoiEquipmentProfiles {
    private PoiEquipmentProfiles() {}

    /**
     * @return the profile this spot may keep, or null when it must hold nothing of its own
     */
    @Nullable
    public static String permitted(
        @Nonnull EquipmentProfileCatalog catalog,
        @Nullable String profileId,
        @Nullable String workResidentKind,
        @Nonnull Collection<String> tags
    ) {
        if (profileId == null || profileId.isBlank()) {
            return null;
        }
        String id = profileId.trim();
        EquipmentProfileDefinition def = catalog.byId(id);
        if (def == null || !def.isResidentRestricted()) {
            return id;
        }
        if (def.permitsResidentKind(workResidentKind)) {
            return id;
        }
        // A stage spot may be marked only by its tag, as with BARD.
        for (String tag : tags) {
            if (def.permitsResidentKind(tag)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Drops restricted profiles from spots that do not belong to their owner.
     *
     * @return migrated list (the same instance if nothing changed)
     */
    @Nonnull
    public static List<PoiEntry> migrate(@Nonnull List<PoiEntry> entries, @Nonnull EquipmentProfileCatalog catalog) {
        List<PoiEntry> out = null;
        for (int i = 0; i < entries.size(); i++) {
            PoiEntry e = entries.get(i);
            String current = e.getEquipmentProfileId();
            String kept = permitted(catalog, current, e.getWorkResidentKind(), e.getTags());
            if (current == null || current.equals(kept)) {
                if (out != null) {
                    out.add(e);
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<>(entries.size());
                out.addAll(entries.subList(0, i));
            }
            out.add(e.copyWithEquipmentProfile(kept));
        }
        return out != null ? out : entries;
    }
}
