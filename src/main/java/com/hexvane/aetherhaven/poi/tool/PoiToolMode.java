package com.hexvane.aetherhaven.poi.tool;

import javax.annotation.Nullable;

/** POI debug staff operating mode (Q cycles between values). */
public enum PoiToolMode {
    /** Select and move existing POIs. */
    PoiEdit,
    /** Place new POI markers via configuration GUI. */
    PoiPlacement,
    /** Remove marker-backed or registry POIs near the clicked block. */
    PoiRemove,
    /** Place and remove guild hall adventurer spawn markers. */
    AdventurerSpawnMarker;

    /** Legacy serialized name from before the three-mode staff. */
    public static PoiToolMode fromSerialized(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return PoiEdit;
        }
        if ("PoiManagement".equalsIgnoreCase(raw.trim())) {
            return PoiEdit;
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return PoiEdit;
        }
    }
}
