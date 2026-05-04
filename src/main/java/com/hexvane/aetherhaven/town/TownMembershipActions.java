package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared invite / kick / role logic for {@link com.hexvane.aetherhaven.command.AetherhavenTownCommand} and UI. */
public final class TownMembershipActions {
    private TownMembershipActions() {}

    /**
     * Sends an invite to an online player by username.
     *
     * @return {@code null} on success, otherwise an error message for the inviter.
     */
    @Nullable
    public static Message tryInviteMember(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull UUID inviterUuid,
        @Nonnull PlayerRef inviterRef,
        @Nonnull String targetUsername
    ) {
        String targetName = targetUsername.trim();
        if (targetName.isEmpty()) {
            return Message.translation("aetherhaven_town.aetherhaven.town.invite.err.emptyName");
        }
        PlayerRef target = TownPlayerLookup.findOnlinePlayerByUsername(world, targetName);
        if (target == null) {
            return Message.translation("aetherhaven_town.aetherhaven.town.invite.err.targetOffline").param("name", targetName);
        }
        if (target.getUuid().equals(inviterUuid)) {
            return Message.translation("aetherhaven_town.aetherhaven.town.invite.err.cannotInviteSelf");
        }
        if (tm.isPlayerAffiliatedInWorld(target.getUuid())) {
            return Message.translation("aetherhaven_town.aetherhaven.town.invite.err.alreadyInTown");
        }
        town.addPendingInvite(new TownPendingInvite(target.getUuid(), System.currentTimeMillis(), inviterUuid));
        tm.updateTown(town);
        String tname = town.getDisplayName();
        target.sendMessage(
            Message.translation("aetherhaven_town.aetherhaven.town.invite.toTarget")
                .param("inviter", inviterRef.getUsername())
                .param("townDisplay", tname)
        );
        inviterRef.sendMessage(
            Message.translation("aetherhaven_town.aetherhaven.town.invite.sent").param("name", target.getUsername())
        );
        return null;
    }

    /**
     * Removes a member by online username (owner cannot be removed).
     *
     * @return {@code null} on success, otherwise an error for the actor.
     */
    @Nullable
    public static Message tryKickMember(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlayerRef actorRef,
        @Nonnull String targetUsername
    ) {
        String targetName = targetUsername.trim();
        if (targetName.isEmpty()) {
            return Message.translation("aetherhaven_town.aetherhaven.town.invite.err.emptyName");
        }
        PlayerRef target = TownPlayerLookup.findOnlinePlayerByUsername(world, targetName);
        if (target == null) {
            return Message.translation("aetherhaven_town.aetherhaven.town.kick.err.mustBeOnline");
        }
        UUID tid = target.getUuid();
        if (tid.equals(town.getOwnerUuid())) {
            return Message.translation("aetherhaven_town.aetherhaven.town.kick.err.cannotRemoveOwner");
        }
        if (!town.removeMember(tid)) {
            return Message.translation("aetherhaven_town.aetherhaven.town.kick.err.notMember");
        }
        tm.updateTown(town);
        String display = town.getDisplayName();
        target.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.kick.removedYou").param("town", display));
        actorRef.sendMessage(
            Message.translation("aetherhaven_town.aetherhaven.town.kick.removed").param("name", target.getUsername())
        );
        return null;
    }

    /**
     * Sets a member's role (owner role cannot be changed).
     *
     * @return {@code null} on success, otherwise an error for the actor.
     */
    @Nullable
    public static Message trySetMemberRole(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlayerRef actorRef,
        @Nonnull UUID targetUuid,
        @Nonnull TownMemberRole role
    ) {
        PlayerRef target = null;
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr.getUuid().equals(targetUuid)) {
                target = pr;
                break;
            }
        }
        if (targetUuid.equals(town.getOwnerUuid())) {
            return Message.translation("aetherhaven_town.aetherhaven.town.role.err.ownerAlwaysFull");
        }
        if (!town.isMemberPlayer(targetUuid)) {
            return Message.translation("aetherhaven_town.aetherhaven.town.kick.err.notMember");
        }
        town.putMember(targetUuid, role);
        tm.updateTown(town);
        String who = target != null ? target.getUsername() : targetUuid.toString();
        Message roleMsg = Message.translation("aetherhaven_town.aetherhaven.town.memberRole." + role.name());
        actorRef.sendMessage(
            Message.translation("aetherhaven_town.aetherhaven.town.role.set").param("name", who).param("role", roleMsg)
        );
        return null;
    }

    /**
     * Sets granular permissions for a town member or the owner (owner entry is for testing overrides).
     *
     * @return {@code null} on success, otherwise an error for the actor.
     */
    @Nullable
    public static Message tryPutMemberPermissions(
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlayerRef actorRef,
        @Nonnull UUID actorUuid,
        @Nonnull UUID targetUuid,
        @Nonnull TownMemberPermissions permissions
    ) {
        if (!town.getOwnerUuid().equals(actorUuid)) {
            return Message.translation("aetherhaven_town.aetherhaven.town.resolve.ownerOrOpOnly");
        }
        if (!targetUuid.equals(town.getOwnerUuid()) && !town.isMemberPlayer(targetUuid)) {
            return Message.translation("aetherhaven_town.aetherhaven.town.kick.err.notMember");
        }
        town.putMemberPermissions(targetUuid, permissions);
        tm.updateTown(town);
        actorRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.permissions.updated"));
        return null;
    }

    /**
     * Kick by UUID (member must exist). Used when the target may be offline — still only works for members in data.
     *
     * @return {@code null} on success.
     */
    @Nullable
    public static Message tryKickMemberUuid(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlayerRef actorRef,
        @Nonnull UUID memberUuid
    ) {
        if (memberUuid.equals(town.getOwnerUuid())) {
            return Message.translation("aetherhaven_town.aetherhaven.town.kick.err.cannotRemoveOwner");
        }
        if (!town.removeMember(memberUuid)) {
            return Message.translation("aetherhaven_town.aetherhaven.town.kick.err.notMember");
        }
        tm.updateTown(town);
        PlayerRef online = null;
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr.getUuid().equals(memberUuid)) {
                online = pr;
                break;
            }
        }
        if (online != null) {
            online.sendMessage(
                Message.translation("aetherhaven_town.aetherhaven.town.kick.removedYou").param("town", town.getDisplayName())
            );
        }
        actorRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.kick.removedGeneric"));
        return null;
    }
}
