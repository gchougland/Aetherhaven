package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownPendingInvite {
    @SerializedName("inviteeUuid")
    private String inviteeUuid;

    @SerializedName("invitedAtEpochMs")
    private long invitedAtEpochMs;

    @SerializedName("inviterUuid")
    @Nullable
    private String inviterUuid;

    public TownPendingInvite() {}

    public TownPendingInvite(@Nonnull UUID inviteeUuid, long invitedAtEpochMs, @Nullable UUID inviterUuid) {
        this.inviteeUuid = inviteeUuid.toString();
        this.invitedAtEpochMs = invitedAtEpochMs;
        this.inviterUuid = inviterUuid != null ? inviterUuid.toString() : null;
    }

    @Nonnull
    public UUID getInviteeUuid() {
        return UUID.fromString(inviteeUuid);
    }

    public long getInvitedAtEpochMs() {
        return invitedAtEpochMs;
    }

    @Nullable
    public UUID getInviterUuid() {
        return inviterUuid != null && !inviterUuid.isEmpty() ? UUID.fromString(inviterUuid) : null;
    }
}
