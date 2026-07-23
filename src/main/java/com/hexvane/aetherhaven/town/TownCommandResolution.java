package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Result of resolving which town a command targets and whether the actor may administer it. */
public final class TownCommandResolution {
    @Nullable
    private final TownRecord town;
    @Nullable
    private final Message error;
    private final boolean senderIsOwner;

    private TownCommandResolution(@Nullable TownRecord town, @Nullable Message error, boolean senderIsOwner) {
        this.town = town;
        this.error = error;
        this.senderIsOwner = senderIsOwner;
    }

    @Nonnull
    public static TownCommandResolution error(@Nonnull Message message) {
        return new TownCommandResolution(null, message, false);
    }

    @Nonnull
    public static TownCommandResolution ok(@Nonnull TownRecord town, boolean senderIsOwner) {
        return new TownCommandResolution(town, null, senderIsOwner);
    }

    public boolean isOk() {
        return town != null && error == null;
    }

    @Nullable
    public TownRecord townOrNull() {
        return town;
    }

    @Nonnull
    public TownRecord townOrThrow() {
        if (town == null) {
            throw new IllegalStateException("no town");
        }
        return town;
    }

    @Nullable
    public Message error() {
        return error;
    }

    public boolean senderIsOwner() {
        return senderIsOwner;
    }

    /**
     * Commands that require the sender to be the town owner (invite, kick, role), unless {@code isAdmin}.
     * @param townDisplayName optional; if null/blank, uses the town the sender owns in this world.
     */
    @Nonnull
    public static TownCommandResolution resolveForOwnerAction(
        @Nonnull TownManager tm,
        @Nonnull UUID senderUuid,
        @Nullable String townDisplayName,
        boolean isAdmin
    ) {
        String trimmed = townDisplayName != null ? townDisplayName.trim() : "";
        if (trimmed.isEmpty()) {
            TownRecord owned = tm.findTownForOwnerInWorld(senderUuid);
            if (owned == null) {
                return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.notOwnerInWorld"));
            }
            return ok(owned, true);
        }
        TownRecord named = tm.findTownByDisplayName(trimmed);
        if (named == null) {
            return error(
                Message.translation("aetherhaven_town.aetherhaven.town.resolve.noTownNamed").param("name", trimmed)
            );
        }
        if (isAdmin) {
            return ok(named, named.getOwnerUuid().equals(senderUuid));
        }
        if (!named.getOwnerUuid().equals(senderUuid)) {
            return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.ownerOrOpOnly"));
        }
        return ok(named, true);
    }

    @Nonnull
    public static TownCommandResolution resolve(
        @Nonnull TownManager tm,
        @Nonnull UUID senderUuid,
        @Nullable String townDisplayName,
        boolean isOp
    ) {
        String trimmed = townDisplayName != null ? townDisplayName.trim() : "";
        if (trimmed.isEmpty()) {
            TownRecord t = TownPlayerResolution.resolveFallbackAffiliatedTown(tm, senderUuid);
            if (t == null) {
                return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.notInTown"));
            }
            boolean owner = t.getOwnerUuid().equals(senderUuid);
            return ok(t, owner);
        }
        TownRecord named = tm.findTownByDisplayName(trimmed);
        if (named == null) {
            return error(
                Message.translation("aetherhaven_town.aetherhaven.town.resolve.noTownNamed").param("name", trimmed)
            );
        }
        if (isOp) {
            boolean owner = named.getOwnerUuid().equals(senderUuid);
            return ok(named, owner);
        }
        if (!named.getOwnerUuid().equals(senderUuid)) {
            return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.nameOnlyForOwner"));
        }
        return ok(named, true);
    }

    /**
     * Admin-style resolution: operator may target by name; non-OP owner may only target own town.
     */
    @Nonnull
    public static TownCommandResolution resolveForAdmin(
        @Nonnull TownManager tm, @Nonnull UUID senderUuid, @Nullable String townDisplayName, boolean isOp
    ) {
        String trimmed = townDisplayName != null ? townDisplayName.trim() : "";
        if (trimmed.isEmpty()) {
            if (!isOp) {
                return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.specifyTownName"));
            }
            return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.specifyTown"));
        }
        TownRecord named = tm.findTownByDisplayName(trimmed);
        if (named == null) {
            return error(
                Message.translation("aetherhaven_town.aetherhaven.town.resolve.noTownNamed").param("name", trimmed)
            );
        }
        if (isOp) {
            return ok(named, named.getOwnerUuid().equals(senderUuid));
        }
        if (!named.getOwnerUuid().equals(senderUuid)) {
            return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.adminOtherTown"));
        }
        return ok(named, true);
    }

    /**
     * Debug commands: default to sender's town; {@code --town} or {@code --player} require
     * {@code canAdministerForeign} and skip quest-permission checks.
     */
    @Nonnull
    public static TownCommandResolution resolveDebugTarget(
        @Nonnull TownManager tm,
        @Nonnull World world,
        @Nonnull UUID senderUuid,
        boolean canAdministerForeign,
        boolean requireQuestPermissionWhenImplicit,
        @Nullable String townFlag,
        @Nullable String playerFlag
    ) {
        boolean hasTown = townFlag != null && !townFlag.isBlank();
        boolean hasPlayer = playerFlag != null && !playerFlag.isBlank();
        if (hasTown && hasPlayer) {
            return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.bothTownAndPlayer"));
        }
        if (hasPlayer) {
            if (!canAdministerForeign) {
                return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.debugAdminOnly"));
            }
            UUID playerUuid = TownPlayerLookup.resolvePlayerUuid(world, playerFlag);
            if (playerUuid == null) {
                return error(
                    Message.translation("aetherhaven_town.aetherhaven.town.resolve.playerNotFound")
                        .param("name", playerFlag.trim())
                );
            }
            TownRecord town = TownPlayerResolution.resolveFallbackAffiliatedTown(tm, playerUuid);
            if (town == null) {
                return error(
                    Message.translation("aetherhaven_town.aetherhaven.town.resolve.playerNoTown")
                        .param("name", playerFlag.trim())
                );
            }
            return ok(town, town.getOwnerUuid().equals(senderUuid));
        }
        if (hasTown) {
            if (!canAdministerForeign) {
                return error(Message.translation("aetherhaven_town.aetherhaven.town.resolve.debugAdminOnly"));
            }
            TownRecord town = tm.findTownByIdOrDisplayName(townFlag);
            if (town == null) {
                return error(
                    Message.translation("aetherhaven_town.aetherhaven.town.resolve.noTownNamed").param("name", townFlag.trim())
                );
            }
            return ok(town, town.getOwnerUuid().equals(senderUuid));
        }
        TownRecord town = TownPlayerResolution.resolveFallbackAffiliatedTown(tm, senderUuid);
        if (town == null) {
            return error(Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld"));
        }
        if (requireQuestPermissionWhenImplicit && !town.playerHasQuestPermission(senderUuid)) {
            return error(Message.translation("aetherhaven_common.aetherhaven.common.noQuestPermission"));
        }
        return ok(town, town.getOwnerUuid().equals(senderUuid));
    }
}
