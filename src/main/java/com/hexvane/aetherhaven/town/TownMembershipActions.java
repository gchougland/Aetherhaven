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
    public static String tryInviteMember(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull UUID inviterUuid,
        @Nonnull PlayerRef inviterRef,
        @Nonnull String targetUsername
    ) {
        String targetName = targetUsername.trim();
        if (targetName.isEmpty()) {
            return "Enter a player name.";
        }
        PlayerRef target = TownPlayerLookup.findOnlinePlayerByUsername(world, targetName);
        if (target == null) {
            return "Player \"" + targetName + "\" is not online in this world.";
        }
        if (target.getUuid().equals(inviterUuid)) {
            return "You cannot invite yourself.";
        }
        if (tm.isPlayerAffiliatedInWorld(target.getUuid())) {
            return "That player is already in a town in this world.";
        }
        town.addPendingInvite(new TownPendingInvite(target.getUuid(), System.currentTimeMillis(), inviterUuid));
        tm.updateTown(town);
        target.sendMessage(
            Message.raw(
                inviterRef.getUsername()
                    + " invited you to join the town \""
                    + town.getDisplayName()
                    + "\". Type /aetherhaven town accept "
                    + town.getDisplayName()
                    + "  or  /aetherhaven town decline "
                    + town.getDisplayName()
            )
        );
        inviterRef.sendMessage(Message.raw("Invite sent to " + target.getUsername() + "."));
        return null;
    }

    /**
     * Removes a member by online username (owner cannot be removed).
     *
     * @return {@code null} on success, otherwise an error for the actor.
     */
    @Nullable
    public static String tryKickMember(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlayerRef actorRef,
        @Nonnull String targetUsername
    ) {
        String targetName = targetUsername.trim();
        if (targetName.isEmpty()) {
            return "Enter a player name.";
        }
        PlayerRef target = TownPlayerLookup.findOnlinePlayerByUsername(world, targetName);
        if (target == null) {
            return "Player must be online to kick by name.";
        }
        UUID tid = target.getUuid();
        if (tid.equals(town.getOwnerUuid())) {
            return "Cannot remove the town owner.";
        }
        if (!town.removeMember(tid)) {
            return "That player is not a member of this town.";
        }
        tm.updateTown(town);
        target.sendMessage(Message.raw("You were removed from the town \"" + town.getDisplayName() + "\"."));
        actorRef.sendMessage(Message.raw("Removed " + target.getUsername() + " from the town."));
        return null;
    }

    /**
     * Sets a member's role (owner role cannot be changed).
     *
     * @return {@code null} on success, otherwise an error for the actor.
     */
    @Nullable
    public static String trySetMemberRole(
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
            return "The owner always has full permissions.";
        }
        if (!town.isMemberPlayer(targetUuid)) {
            return "That player is not a member of this town.";
        }
        town.putMember(targetUuid, role);
        tm.updateTown(town);
        String who = target != null ? target.getUsername() : targetUuid.toString();
        actorRef.sendMessage(Message.raw("Set " + who + "'s role to " + role.name() + "."));
        return null;
    }

    /**
     * Kick by UUID (member must exist). Used when the target may be offline — still only works for members in data.
     *
     * @return {@code null} on success.
     */
    @Nullable
    public static String tryKickMemberUuid(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlayerRef actorRef,
        @Nonnull UUID memberUuid
    ) {
        if (memberUuid.equals(town.getOwnerUuid())) {
            return "Cannot remove the town owner.";
        }
        if (!town.removeMember(memberUuid)) {
            return "That player is not a member of this town.";
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
            online.sendMessage(Message.raw("You were removed from the town \"" + town.getDisplayName() + "\"."));
        }
        actorRef.sendMessage(Message.raw("Removed member from the town."));
        return null;
    }
}
