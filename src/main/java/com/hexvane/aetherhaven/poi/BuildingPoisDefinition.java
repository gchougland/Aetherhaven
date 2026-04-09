package com.hexvane.aetherhaven.poi;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * POI rows embedded under {@code "pois"} in each {@code Server/Aetherhaven/Buildings/} construction JSON file
 * (also deserializable as a standalone array root for tooling).
 */
public final class BuildingPoisDefinition {
    @SerializedName("pois")
    private List<PoiRow> pois = new ArrayList<>();

    public List<PoiRow> getPois() {
        return pois != null ? pois : List.of();
    }

    public static final class PoiRow {
        @SerializedName("localX")
        private int localX;

        @SerializedName("localY")
        private int localY;

        @SerializedName("localZ")
        private int localZ;

        @SerializedName("tags")
        private List<String> tags = new ArrayList<>();

        @SerializedName("capacity")
        private int capacity = 1;

        /** Expected block type id at the anchor cell after build; when blank, registration skips validation. */
        @Nullable
        @SerializedName("blockTypeId")
        private String blockTypeId;

        @SerializedName("interactionKind")
        private String interactionKind = "NONE";

        /**
         * Optional prefab-local cell for autonomy leash / Seek (same space as {@code localX/Y/Z}). When all three are
         * set, world position is computed at build time (with the same anchor shift delta as the POI furniture).
         */
        @Nullable
        @SerializedName("interactionTargetLocalX")
        private Integer interactionTargetLocalX;

        @Nullable
        @SerializedName("interactionTargetLocalY")
        private Integer interactionTargetLocalY;

        @Nullable
        @SerializedName("interactionTargetLocalZ")
        private Integer interactionTargetLocalZ;

        public int getLocalX() {
            return localX;
        }

        public int getLocalY() {
            return localY;
        }

        public int getLocalZ() {
            return localZ;
        }

        @Nonnull
        public Set<String> getTags() {
            Set<String> s = new HashSet<>();
            if (tags != null) {
                for (String t : tags) {
                    if (t != null && !t.isBlank()) {
                        s.add(t.trim());
                    }
                }
            }
            return s;
        }

        public int getCapacity() {
            return Math.max(1, capacity);
        }

        @Nullable
        public String getBlockTypeId() {
            return blockTypeId != null && !blockTypeId.isBlank() ? blockTypeId.trim() : null;
        }

        @Nonnull
        public PoiInteractionKind getInteractionKind() {
            return PoiInteractionKind.fromJson(interactionKind);
        }

        public boolean hasInteractionTargetLocal() {
            return interactionTargetLocalX != null && interactionTargetLocalY != null && interactionTargetLocalZ != null;
        }

        public int getInteractionTargetLocalX() {
            return interactionTargetLocalX != null ? interactionTargetLocalX : 0;
        }

        public int getInteractionTargetLocalY() {
            return interactionTargetLocalY != null ? interactionTargetLocalY : 0;
        }

        public int getInteractionTargetLocalZ() {
            return interactionTargetLocalZ != null ? interactionTargetLocalZ : 0;
        }
    }
}
