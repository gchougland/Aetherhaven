package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldNpcRouteRecord {
    @SerializedName("routeId")
    @Nullable
    private String routeId;

    @SerializedName("nodes")
    @Nullable
    private List<WorldNpcRouteNodeRecord> nodes;

    @Nonnull
    public String routeIdOrEmpty() {
        return routeId != null ? routeId.trim() : "";
    }

    public void setRouteId(@Nullable String routeId) {
        this.routeId = routeId;
    }

    @Nonnull
    public List<WorldNpcRouteNodeRecord> nodesOrEmpty() {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        return nodes;
    }
}
