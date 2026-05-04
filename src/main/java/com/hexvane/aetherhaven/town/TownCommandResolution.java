package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.server.core.Message;
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
            TownRecord t = tm.findTownForPlayerInWorld(senderUuid);
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
}
