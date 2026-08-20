package com.hexvane.aetherhaven.town;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Give up town affiliation: guests leave, owners pass the town on, or the town becomes unowned.
 * Mutates {@link TownRecord} only; callers persist via {@link TownManager#updateTown}.
 */
public final class TownRelinquishService {
    public enum RelinquishKind {
        LEFT_AS_MEMBER,
        TRANSFERRED,
        ORPHANED
    }

    public static final class RelinquishResult {
        @Nonnull
        public final RelinquishKind kind;
        @Nullable
        public final UUID successorUuid;
        @Nullable
        public final String successorName;

        RelinquishResult(
            @Nonnull RelinquishKind kind,
            @Nullable UUID successorUuid,
            @Nullable String successorName
        ) {
            this.kind = kind;
            this.successorUuid = successorUuid;
            this.successorName = successorName;
        }
    }

    public enum ClaimFail {
        ALREADY_OWNED,
        CLAIMANT_OWNS_TOWN
    }

    private TownRelinquishService() {}

    /**
     * @return {@code null} if {@code actor} is not the owner and not a member
     */
    @Nullable
    public static RelinquishResult relinquish(
        @Nonnull TownRecord town,
        @Nonnull UUID actor,
        @Nonnull Predicate<UUID> playerAlreadyOwnsATown,
        @Nonnull Function<UUID, String> usernameOf
    ) {
        if (town.isMemberPlayer(actor) && !town.isOwner(actor)) {
            town.removeMember(actor);
            return new RelinquishResult(RelinquishKind.LEFT_AS_MEMBER, null, null);
        }
        if (!town.isOwner(actor)) {
            return null;
        }
        UUID successor = pickSuccessor(town, playerAlreadyOwnsATown);
        if (successor != null) {
            String name = usernameOf.apply(successor);
            town.removeMember(successor);
            town.setOwner(successor, name);
            return new RelinquishResult(RelinquishKind.TRANSFERRED, successor, name);
        }
        town.clearOwner();
        town.getPendingInvites().clear();
        return new RelinquishResult(RelinquishKind.ORPHANED, null, null);
    }

    @Nullable
    public static ClaimFail tryClaim(
        @Nonnull TownRecord town,
        @Nonnull UUID claimer,
        @Nullable String username,
        boolean claimerAlreadyOwnsATown
    ) {
        if (town.hasOwner()) {
            return ClaimFail.ALREADY_OWNED;
        }
        if (claimerAlreadyOwnsATown) {
            return ClaimFail.CLAIMANT_OWNS_TOWN;
        }
        if (town.isMemberPlayer(claimer)) {
            town.removeMember(claimer);
        }
        town.setOwner(claimer, username);
        return null;
    }

    @Nullable
    static UUID pickSuccessor(@Nonnull TownRecord town, @Nonnull Predicate<UUID> playerAlreadyOwnsATown) {
        for (UUID member : town.getMemberPlayerUuids()) {
            if (playerAlreadyOwnsATown.test(member)) {
                continue;
            }
            return member;
        }
        return null;
    }
}
