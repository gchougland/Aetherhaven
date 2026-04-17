package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberRole;
import com.hexvane.aetherhaven.town.TownMembershipActions;
import com.hexvane.aetherhaven.town.TownPendingInvite;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class AetherhavenTownCommand extends AbstractCommandCollection {
    public AetherhavenTownCommand() {
        super("town", "server.commands.aetherhaven.town.desc");
        this.addSubCommand(new InviteCommand());
        this.addSubCommand(new AcceptCommand());
        this.addSubCommand(new DeclineCommand());
        this.addSubCommand(new KickCommand());
        this.addSubCommand(new RoleCommand());
        this.addSubCommand(new LeaveCommand());
    }

    private static final class InviteCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> playerArg =
            this.withRequiredArg("player", "server.commands.aetherhaven.town.invite.player.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "server.commands.aetherhaven.town.townName.desc", ArgTypes.STRING);

        InviteCommand() {
            super("invite", "server.commands.aetherhaven.town.invite.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (player == null || uc == null) {
                return;
            }
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String townOpt = context.provided(townArg) ? context.get(townArg) : null;
            TownCommandResolution res = TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), townOpt, admin);
            if (!res.isOk()) {
                playerRef.sendMessage(Message.raw(res.errorMessage()));
                return;
            }
            TownRecord town = res.townOrThrow();
            String targetName = context.get(playerArg).trim();
            String err = TownMembershipActions.tryInviteMember(world, tm, town, uc.getUuid(), playerRef, targetName);
            if (err != null) {
                playerRef.sendMessage(Message.raw(err));
            }
        }
    }

    private static final class AcceptCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "server.commands.aetherhaven.town.townName.desc", ArgTypes.STRING);

        AcceptCommand() {
            super("accept", "server.commands.aetherhaven.town.accept.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            UUID self = uc.getUuid();
            TownRecord town;
            if (context.provided(townArg) && !context.get(townArg).trim().isEmpty()) {
                town = tm.findTownByDisplayName(context.get(townArg).trim());
                if (town == null) {
                    playerRef.sendMessage(Message.raw("No town with that name."));
                    return;
                }
                if (town.findPendingInvite(self) == null) {
                    playerRef.sendMessage(Message.raw("You have no pending invite for that town."));
                    return;
                }
            } else {
                town = tm.findTownWithPendingInviteFor(self);
                if (town == null) {
                    playerRef.sendMessage(Message.raw("You have no pending town invites."));
                    return;
                }
            }
            if (tm.isPlayerAffiliatedInWorld(self)) {
                playerRef.sendMessage(Message.raw("You are already in a town."));
                return;
            }
            town.removePendingInviteForInvitee(self);
            town.putMember(self, TownMemberRole.BOTH);
            tm.updateTown(town);
            playerRef.sendMessage(Message.raw("You joined \"" + town.getDisplayName() + "\"."));
        }
    }

    private static final class DeclineCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "server.commands.aetherhaven.town.townName.desc", ArgTypes.STRING);

        DeclineCommand() {
            super("decline", "server.commands.aetherhaven.town.decline.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            UUID self = uc.getUuid();
            if (context.provided(townArg) && !context.get(townArg).trim().isEmpty()) {
                TownRecord town = tm.findTownByDisplayName(context.get(townArg).trim());
                if (town == null) {
                    playerRef.sendMessage(Message.raw("No town with that name."));
                    return;
                }
                if (!town.removePendingInviteForInvitee(self)) {
                    playerRef.sendMessage(Message.raw("No pending invite for that town."));
                    return;
                }
                tm.updateTown(town);
            } else {
                TownRecord town = tm.findTownWithPendingInviteFor(self);
                if (town == null) {
                    playerRef.sendMessage(Message.raw("You have no pending town invites."));
                    return;
                }
                town.removePendingInviteForInvitee(self);
                tm.updateTown(town);
            }
            playerRef.sendMessage(Message.raw("Invite declined."));
        }
    }

    private static final class KickCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> playerArg =
            this.withRequiredArg("player", "server.commands.aetherhaven.town.kick.player.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "server.commands.aetherhaven.town.townName.desc", ArgTypes.STRING);

        KickCommand() {
            super("kick", "server.commands.aetherhaven.town.kick.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (player == null || uc == null) {
                return;
            }
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String townOpt = context.provided(townArg) ? context.get(townArg) : null;
            TownCommandResolution res = TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), townOpt, admin);
            if (!res.isOk()) {
                playerRef.sendMessage(Message.raw(res.errorMessage()));
                return;
            }
            TownRecord town = res.townOrThrow();
            String targetName = context.get(playerArg).trim();
            String err = TownMembershipActions.tryKickMember(world, tm, town, playerRef, targetName);
            if (err != null) {
                playerRef.sendMessage(Message.raw(err));
            }
        }
    }

    private static final class RoleCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> playerArg =
            this.withRequiredArg("player", "server.commands.aetherhaven.town.role.player.desc", ArgTypes.STRING);
        @Nonnull
        private final RequiredArg<String> roleArg =
            this.withRequiredArg("role", "server.commands.aetherhaven.town.role.role.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "server.commands.aetherhaven.town.townName.desc", ArgTypes.STRING);

        RoleCommand() {
            super("role", "server.commands.aetherhaven.town.role.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (player == null || uc == null) {
                return;
            }
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String townOpt = context.provided(townArg) ? context.get(townArg) : null;
            TownCommandResolution res = TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), townOpt, admin);
            if (!res.isOk()) {
                playerRef.sendMessage(Message.raw(res.errorMessage()));
                return;
            }
            TownRecord town = res.townOrThrow();
            PlayerRef target = TownPlayerLookup.findOnlinePlayerByUsername(world, context.get(playerArg).trim());
            if (target == null) {
                playerRef.sendMessage(Message.raw("Player must be online."));
                return;
            }
            TownMemberRole role;
            try {
                role = TownMemberRole.valueOf(context.get(roleArg).trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                playerRef.sendMessage(Message.raw("Role must be BUILD, QUEST, or BOTH."));
                return;
            }
            String err = TownMembershipActions.trySetMemberRole(world, tm, town, playerRef, target.getUuid(), role);
            if (err != null) {
                playerRef.sendMessage(Message.raw(err));
            }
        }
    }

    private static final class LeaveCommand extends AbstractPlayerCommand {
        LeaveCommand() {
            super("leave", "server.commands.aetherhaven.town.leave.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            UUID self = uc.getUuid();
            TownRecord town = tm.findTownForPlayerInWorld(self);
            if (town == null) {
                playerRef.sendMessage(Message.raw("You are not in a town."));
                return;
            }
            if (town.getOwnerUuid().equals(self)) {
                playerRef.sendMessage(Message.raw("Owners cannot leave; transfer ownership is not implemented yet."));
                return;
            }
            if (!town.removeMember(self)) {
                playerRef.sendMessage(Message.raw("You are not a member of this town."));
                return;
            }
            tm.updateTown(town);
            playerRef.sendMessage(Message.raw("You left \"" + town.getDisplayName() + "\"."));
        }
    }
}
