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
        super("town", "aetherhaven_commands_help.commands.aetherhaven.town.desc");
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
            this.withRequiredArg("player", "aetherhaven_commands_help.commands.aetherhaven.town.invite.player.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", ArgTypes.GREEDY_STRING);

        InviteCommand() {
            super("invite", "aetherhaven_commands_help.commands.aetherhaven.town.invite.desc");
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
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            String targetName = context.get(playerArg).trim();
            Message err = TownMembershipActions.tryInviteMember(world, tm, town, uc.getUuid(), playerRef, targetName);
            if (err != null) {
                playerRef.sendMessage(err);
            }
        }
    }

    private static final class AcceptCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", ArgTypes.GREEDY_STRING);

        AcceptCommand() {
            super("accept", "aetherhaven_commands_help.commands.aetherhaven.town.accept.desc");
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
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.noSuchTown"));
                    return;
                }
                if (town.findPendingInvite(self) == null) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.noInvite"));
                    return;
                }
            } else {
                town = tm.findTownWithPendingInviteFor(self);
                if (town == null) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.noInvites"));
                    return;
                }
            }
            if (tm.isPlayerAffiliatedInWorld(self)) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.alreadyInTown"));
                return;
            }
            town.removePendingInviteForInvitee(self);
            town.putMember(self, TownMemberRole.BOTH);
            tm.updateTown(town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_town.aetherhaven.town.accept.joined").param("town", town.getDisplayName())
            );
        }
    }

    private static final class DeclineCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", ArgTypes.GREEDY_STRING);

        DeclineCommand() {
            super("decline", "aetherhaven_commands_help.commands.aetherhaven.town.decline.desc");
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
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.noSuchTown"));
                    return;
                }
                if (!town.removePendingInviteForInvitee(self)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.decline.noPending"));
                    return;
                }
                tm.updateTown(town);
            } else {
                TownRecord town = tm.findTownWithPendingInviteFor(self);
                if (town == null) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.decline.noInvites"));
                    return;
                }
                town.removePendingInviteForInvitee(self);
                tm.updateTown(town);
            }
            playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.decline.done"));
        }
    }

    private static final class KickCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> playerArg =
            this.withRequiredArg("player", "aetherhaven_commands_help.commands.aetherhaven.town.kick.player.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", ArgTypes.GREEDY_STRING);

        KickCommand() {
            super("kick", "aetherhaven_commands_help.commands.aetherhaven.town.kick.desc");
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
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            String targetName = context.get(playerArg).trim();
            Message err = TownMembershipActions.tryKickMember(world, tm, town, playerRef, targetName);
            if (err != null) {
                playerRef.sendMessage(err);
            }
        }
    }

    private static final class RoleCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> playerArg =
            this.withRequiredArg("player", "aetherhaven_commands_help.commands.aetherhaven.town.role.player.desc", ArgTypes.STRING);
        @Nonnull
        private final RequiredArg<String> roleArg =
            this.withRequiredArg("role", "aetherhaven_commands_help.commands.aetherhaven.town.role.role.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", ArgTypes.GREEDY_STRING);

        RoleCommand() {
            super("role", "aetherhaven_commands_help.commands.aetherhaven.town.role.desc");
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
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            PlayerRef target = TownPlayerLookup.findOnlinePlayerByUsername(world, context.get(playerArg).trim());
            if (target == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.rolecmd.playerMustBeOnline"));
                return;
            }
            TownMemberRole role;
            try {
                role = TownMemberRole.valueOf(context.get(roleArg).trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.rolecmd.roleInvalid"));
                return;
            }
            Message err = TownMembershipActions.trySetMemberRole(world, tm, town, playerRef, target.getUuid(), role);
            if (err != null) {
                playerRef.sendMessage(err);
            }
        }
    }

    private static final class LeaveCommand extends AbstractPlayerCommand {
        LeaveCommand() {
            super("leave", "aetherhaven_commands_help.commands.aetherhaven.town.leave.desc");
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
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.notInTown"));
                return;
            }
            if (town.getOwnerUuid().equals(self)) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.ownerCannotLeave"));
                return;
            }
            if (!town.removeMember(self)) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.notMember"));
                return;
            }
            tm.updateTown(town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_town.aetherhaven.town.leave.left").param("town", town.getDisplayName())
            );
        }
    }
}
