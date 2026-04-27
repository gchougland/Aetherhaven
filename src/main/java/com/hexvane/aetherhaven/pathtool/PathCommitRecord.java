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

    @Nonnull
    public UUID getIdUuid() {
        return UUID.fromString(id);
    }
}
