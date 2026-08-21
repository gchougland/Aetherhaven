package com.hexvane.aetherhaven.pathtool;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** One cemented path with a sparse undo list for non-destructive remove. */
public final class PathCommitRecord {
    @SerializedName("id")
    @Nonnull
    public String id;
    @SerializedName("createdMs")
    public long createdMs;
    @SerializedName("undo")
    @Nonnull
    public List<PathToolUndoCell> undo = new ArrayList<>();
    @SerializedName("townId")
    public String townId;
    @SerializedName("navNodes")
    @Nonnull
    public List<PathNavPoint> navNodes = new ArrayList<>();
    /** When false, townsfolk ignore this path. Missing JSON field stays true. */
    @SerializedName("villagerNav")
    public boolean villagerNav = true;
    /**
     * Path width in blocks at cement time. 0 or missing means unknown (legacy commits); restyle may infer it.
     */
    @SerializedName("pathWidthBlocks")
    public int pathWidthBlocks;

    @Nonnull
    public UUID getIdUuid() {
        return UUID.fromString(id);
    }

    /** True when this commit should be added to the townsfolk walking graph. */
    public boolean includeInTownsfolkGraph() {
        return villagerNav
            && townId != null
            && !townId.isBlank()
            && navNodes != null
            && navNodes.size() >= 2;
    }
}
