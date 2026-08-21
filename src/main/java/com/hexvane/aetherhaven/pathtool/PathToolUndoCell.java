package com.hexvane.aetherhaven.pathtool;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

public final class PathToolUndoCell {
    @SerializedName("x")
    public int x;
    @SerializedName("y")
    public int y;
    @SerializedName("z")
    public int z;
    /** {@link com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType#getId()} before change. */
    @SerializedName("blockId")
    public String blockId;
    @SerializedName("rotationIndex")
    public int rotationIndex;
    /**
     * Lateral index within the path width band for surface path cells. Null for foliage clears and legacy records.
     */
    @SerializedName("lat")
    @Nullable
    public Integer lateralIndex;
}
