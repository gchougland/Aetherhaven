package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/** One world chunk column owned by a town's territory claim set. */
public final class ClaimedTerritoryChunkRecord {
    @SerializedName("chunkX")
    private int chunkX;

    @SerializedName("chunkZ")
    private int chunkZ;

    public ClaimedTerritoryChunkRecord() {}

    public ClaimedTerritoryChunkRecord(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    @Nonnull
    public static ClaimedTerritoryChunkRecord of(int chunkX, int chunkZ) {
        return new ClaimedTerritoryChunkRecord(chunkX, chunkZ);
    }
}
