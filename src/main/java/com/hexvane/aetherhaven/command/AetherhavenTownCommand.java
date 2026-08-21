package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownMemberRole;
import com.hexvane.aetherhaven.town.TownSharedRecipeUnlockService;
import com.hexvane.aetherhaven.town.TownMembershipActions;
import com.hexvane.aetherhaven.town.TownRelinquishService;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hexvane.aetherhaven.ui.TownStylePickerPage;
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
import com.hypixel.hytale.protocol.GameMode;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AetherhavenTownCommand extends AbstractCommandCollection {
    public AetherhavenTownCommand() {
        super("town", "aetherhaven_commands_help.commands.aetherhaven.town.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.addSubCommand(new InviteCommand());
        this.addSubCommand(new AcceptCommand());
        this.addSubCommand(new DeclineCommand());
        this.addSubCommand(new KickCommand());
        this.addSubCommand(new RoleCommand());
        this.addSubCommand(new LeaveCommand());
        this.addSubCommand(new RelinquishCommand());
        this.addSubCommand(new StyleCommand());
    }

    private static final class InviteCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> playerArg =
            this.withRequiredArg("player", "aetherhaven_commands_help.commands.aetherhaven.town.invite.player.desc", AetherhavenArgTypes.ONLINE_PLAYER_NAME);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", AetherhavenArgTypes.TOWN_NAME);

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
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
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
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", AetherhavenArgTypes.TOWN_NAME);

        AcceptCommand() {
            super("accept", "aetherhaven_commands_help.commands.aetherhaven.town.accept.desc");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
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
            if (town.hasMemberOrOwner(self)) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.alreadyInTown"));
                return;
            }
            town.removePendingInviteForInvitee(self);
            town.putMember(self, TownMemberRole.BOTH);
            tm.updateTown(town);
            TownSharedRecipeUnlockService.tryFlushPendingCraftRecipes(store, ref, town, tm, self);
            playerRef.sendMessage(
                Message.translation("aetherhaven_town.aetherhaven.town.accept.joined").param("town", town.getDisplayName())
            );
        }
    }

    private static final class DeclineCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", AetherhavenArgTypes.TOWN_NAME);

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
            this.withRequiredArg("player", "aetherhaven_commands_help.commands.aetherhaven.town.kick.player.desc", AetherhavenArgTypes.ONLINE_PLAYER_NAME);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", AetherhavenArgTypes.TOWN_NAME);

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
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
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
            this.withRequiredArg("player", "aetherhaven_commands_help.commands.aetherhaven.town.role.player.desc", AetherhavenArgTypes.ONLINE_PLAYER_NAME);
        @Nonnull
        private final RequiredArg<String> roleArg =
            this.withRequiredArg("role", "aetherhaven_commands_help.commands.aetherhaven.town.role.role.desc", AetherhavenArgTypes.TOWN_MEMBER_ROLE);
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", AetherhavenArgTypes.TOWN_NAME);

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
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
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
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg("townName", "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc", AetherhavenArgTypes.TOWN_NAME);

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
            String townNameOpt =
                context.provided(townArg) && !context.get(townArg).trim().isEmpty() ? context.get(townArg).trim() : null;
            TownRecord town = resolveLeaveTargetTown(store, ref, playerRef, tm, self, townNameOpt);
            if (town == null) {
                return;
            }
            if (town.isOwner(self)) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.ownerCannotLeave"));
                return;
            }
            if (!town.removeMember(self)) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.notMember"));
                return;
            }
            tm.updateTown(town);
            TownPlayerResolution.clearActiveTownIdIfMatches(world, self, town.getTownId());
            playerRef.sendMessage(
                Message.translation("aetherhaven_town.aetherhaven.town.leave.left").param("town", town.getDisplayName())
            );
        }

        @Nullable
        private TownRecord resolveLeaveTargetTown(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull TownManager tm,
            @Nonnull UUID self,
            @Nullable String townDisplayName
        ) {
            if (townDisplayName != null && !townDisplayName.isEmpty()) {
                TownRecord named = tm.findTownByDisplayName(townDisplayName);
                if (named == null) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.noSuchTown"));
                    return null;
                }
                if (!named.isMemberPlayer(self)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.notMember"));
                    return null;
                }
                return named;
            }
            List<TownRecord> guestTowns = new java.util.ArrayList<>();
            for (TownRecord t : tm.findAllTownsForPlayerInWorld(self)) {
                if (!t.isOwner(self) && t.isMemberPlayer(self)) {
                    guestTowns.add(t);
                }
            }
            if (guestTowns.isEmpty()) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.notInTown"));
                return null;
            }
            if (guestTowns.size() == 1) {
                return guestTowns.get(0);
            }
            PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (journal != null) {
                UUID active = journal.getActiveTownId();
                if (active != null) {
                    for (TownRecord t : guestTowns) {
                        if (t.getTownId().equals(active)) {
                            return t;
                        }
                    }
                }
            }
            playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.leave.specifyTownName"));
            return null;
        }
    }

    private static final class RelinquishCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg(
                "townName",
                "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc",
                AetherhavenArgTypes.TOWN_NAME
            );

        RelinquishCommand() {
            super("relinquish", "aetherhaven_commands_help.commands.aetherhaven.town.relinquish.desc");
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
            String townNameOpt =
                context.provided(townArg) && !context.get(townArg).trim().isEmpty() ? context.get(townArg).trim() : null;
            TownRecord town = resolveRelinquishTargetTown(store, ref, playerRef, tm, self, townNameOpt);
            if (town == null) {
                return;
            }
            TownRelinquishService.RelinquishResult result =
                TownRelinquishService.relinquish(
                    town,
                    self,
                    uuid -> tm.findTownForOwnerInWorld(uuid) != null,
                    uuid -> TownPlayerLookup.displayNameForUuid(world, uuid)
                );
            if (result == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.relinquish.notInTown"));
                return;
            }
            tm.updateTown(town);
            TownPlayerResolution.clearActiveTownIdIfMatches(world, self, town.getTownId());
            String townName = town.getDisplayName();
            switch (result.kind) {
                case LEFT_AS_MEMBER ->
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_town.aetherhaven.town.leave.left").param("town", townName)
                    );
                case TRANSFERRED -> {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_town.aetherhaven.town.relinquish.handedOver")
                            .param("town", townName)
                            .param("name", result.successorName != null ? result.successorName : "")
                    );
                    if (result.successorUuid != null) {
                        notifySuccessor(world, result.successorUuid, townName);
                    }
                }
                case ORPHANED ->
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_town.aetherhaven.town.relinquish.townKeepsGoing")
                            .param("town", townName)
                    );
            }
        }

        private static void notifySuccessor(@Nonnull World world, @Nonnull UUID successorUuid, @Nonnull String townName) {
            for (PlayerRef pr : world.getPlayerRefs()) {
                if (successorUuid.equals(pr.getUuid())) {
                    pr.sendMessage(
                        Message.translation("aetherhaven_town.aetherhaven.town.relinquish.youAreInCharge")
                            .param("town", townName)
                    );
                    return;
                }
            }
        }

        @Nullable
        private TownRecord resolveRelinquishTargetTown(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull TownManager tm,
            @Nonnull UUID self,
            @Nullable String townDisplayName
        ) {
            if (townDisplayName != null && !townDisplayName.isEmpty()) {
                TownRecord named = tm.findTownByDisplayName(townDisplayName);
                if (named == null) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.accept.err.noSuchTown"));
                    return null;
                }
                if (!named.hasMemberOrOwner(self)) {
                    playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.relinquish.notInTown"));
                    return null;
                }
                return named;
            }
            List<TownRecord> affiliated = new java.util.ArrayList<>();
            for (TownRecord t : tm.findAllTownsForPlayerInWorld(self)) {
                if (t.hasMemberOrOwner(self)) {
                    affiliated.add(t);
                }
            }
            if (affiliated.isEmpty()) {
                playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.relinquish.notInTown"));
                return null;
            }
            if (affiliated.size() == 1) {
                return affiliated.get(0);
            }
            PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (journal != null) {
                UUID active = journal.getActiveTownId();
                if (active != null) {
                    for (TownRecord t : affiliated) {
                        if (t.getTownId().equals(active)) {
                            return t;
                        }
                    }
                }
            }
            playerRef.sendMessage(Message.translation("aetherhaven_town.aetherhaven.town.relinquish.specifyTownName"));
            return null;
        }
    }

    private static final class StyleCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg(
                "townName",
                "aetherhaven_commands_help.commands.aetherhaven.town.townName.desc",
                AetherhavenArgTypes.TOWN_NAME
            );

        StyleCommand() {
            super("style", "aetherhaven_commands_help.commands.aetherhaven.town.style.desc");
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
            if (player.getPageManager().getCustomPage() != null) {
                return;
            }
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String townOpt = context.provided(townArg) ? context.get(townArg) : null;
            TownCommandResolution res = TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), townOpt, admin);
            if (!res.isOk()) {
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            player.getPageManager().openCustomPage(ref, store, new TownStylePickerPage(playerRef, town.getTownId()));
        }
    }
}
