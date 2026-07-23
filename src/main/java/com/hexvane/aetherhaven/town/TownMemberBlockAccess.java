package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gates town block interactions so only members (including the owner) of that town may proceed. */
public final class TownMemberBlockAccess {
    public static final String MEMBERS_ONLY_MESSAGE_KEY = "aetherhaven_ui_town.aetherhaven.ui.town.membersOnly";

    private TownMemberBlockAccess() {}

    @Nullable
    public static UUID parseTownId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static TownRecord townFromId(@Nonnull TownManager tm, @Nullable String townIdRaw) {
        UUID id = parseTownId(townIdRaw);
        return id != null ? tm.getTown(id) : null;
    }

    @Nullable
    public static TownRecord townFromId(@Nonnull TownManager tm, @Nullable UUID townId) {
        return townId != null ? tm.getTown(townId) : null;
    }

    /**
     * @return {@code true} if the player is not a member and a denial message was sent (caller should abort).
     */
    public static boolean denyIfNotMember(
        @Nonnull PlayerRef playerRef,
        @Nullable TownRecord town,
        @Nullable UUID playerUuid
    ) {
        if (playerUuid == null || town == null || !town.hasMemberOrOwner(playerUuid)) {
            playerRef.sendMessage(Message.translation(MEMBERS_ONLY_MESSAGE_KEY));
            return true;
        }
        return false;
    }

    public static boolean denyIfNotMember(
        @Nonnull PlayerRef playerRef,
        @Nonnull TownManager tm,
        @Nullable String townIdRaw,
        @Nullable UUID playerUuid
    ) {
        return denyIfNotMember(playerRef, townFromId(tm, townIdRaw), playerUuid);
    }

    public static boolean denyIfNotMember(
        @Nonnull PlayerRef playerRef,
        @Nonnull TownManager tm,
        @Nullable UUID townId,
        @Nullable UUID playerUuid
    ) {
        return denyIfNotMember(playerRef, townFromId(tm, townId), playerUuid);
    }
}
