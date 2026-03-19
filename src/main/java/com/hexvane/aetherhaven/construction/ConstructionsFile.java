package com.hexvane.aetherhaven.construction;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

final class ConstructionsFile {
    @SerializedName("constructions")
    private List<ConstructionDefinition> constructions = Collections.emptyList();

    List<ConstructionDefinition> getConstructions() {
        return constructions != null ? constructions : Collections.emptyList();
    }
}
