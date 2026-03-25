package com.hexvane.aetherhaven.poi;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/** Gson root for {@code Server/Buildings/<constructionId>.json}. */
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
    }
}
