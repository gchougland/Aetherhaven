package com.hexvane.aetherhaven.town;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Result of resolving which town a command targets and whether the actor may administer it. */
public final class TownCommandResolution {
    @Nullable
    private final TownRecord town;
    @Nullable
    private final String errorMessage;
    private final boolean senderIsOwner;

    private TownCommandResolution(
        @Nullable TownRecord town, @Nullable String errorMessage, boolean senderIsOwner
    ) {
        this.town = town;
        this.errorMessage = errorMessage;
        this.senderIsOwner = senderIsOwner;
    }

    @Nonnull
    public static TownCommandResolution error(@Nonnull String message) {
        return new TownCommandResolution(null, message, false);
    }

    @Nonnull
    public static TownCommandResolution ok(@Nonnull TownRecord town, boolean senderIsOwner) {
        return new TownCommandResolution(town, null, senderIsOwner);
    }

    public boolean isOk() {
        return town != null && errorMessage == null;
    }

    @Nullable
    public TownRecord townOrNull() {
        return town;
    }

    @Nonnull
    public TownRecord townOrThrow() {
        if (town == null) {
            throw new IllegalStateException(errorMessage != null ? errorMessage : "no town");
        }
        return town;
    }

    @Nullable
    public String errorMessage() {
        return errorMessage;
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
                return error("You do not own a town in this world.");
            }
            return ok(owned, true);
        }
        TownRecord named = tm.findTownByDisplayName(trimmed);
        if (named == null) {
            return error("No town named \"" + trimmed + "\" in this world.");
        }
        if (isAdmin) {
            return ok(named, named.getOwnerUuid().equals(senderUuid));
        }
        if (!named.getOwnerUuid().equals(senderUuid)) {
            return error("Only the town owner can do that (or an operator with permission).");
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
                return error("You are not in a town in this world.");
            }
            boolean owner = t.getOwnerUuid().equals(senderUuid);
            return ok(t, owner);
        }
        TownRecord named = tm.findTownByDisplayName(trimmed);
        if (named == null) {
            return error("No town named \"" + trimmed + "\" in this world.");
        }
        if (isOp) {
            boolean owner = named.getOwnerUuid().equals(senderUuid);
            return ok(named, owner);
        }
        if (!named.getOwnerUuid().equals(senderUuid)) {
            return error("Only the town owner (or an operator) can use a town name here.");
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
                return error("Specify a town name, or use this from the town you own.");
            }
            return error("Specify a town name.");
        }
        TownRecord named = tm.findTownByDisplayName(trimmed);
        if (named == null) {
            return error("No town named \"" + trimmed + "\" in this world.");
        }
        if (isOp) {
            return ok(named, named.getOwnerUuid().equals(senderUuid));
        }
        if (!named.getOwnerUuid().equals(senderUuid)) {
            return error("Only operators may target another player's town by name.");
        }
        return ok(named, true);
    }
}
