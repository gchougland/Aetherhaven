package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Debug: set reputation with milestone queue, list milestone definitions, grant one reward directly.
 * Normal command permissions apply.
 */
public final class AetherhavenReputationDebugCommand extends AbstractCommandCollection {
    public AetherhavenReputationDebugCommand() {
        super("reputation", "aetherhaven_commands_help.commands.aetherhaven.reputation.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addAliases("rep");
        this.addSubCommand(new SetSubCommand());
        this.addSubCommand(new RewardSubCommand());
    }

    private static final class SetSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> villagerArg =
            this.withRequiredArg("villager", "aetherhaven_commands_help.commands.aetherhaven.reputation.villagerTarget.desc", AetherhavenArgTypes.VILLAGER_TARGET);
        @Nonnull
        private final RequiredArg<Integer> reputationArg =
            this.withRequiredArg("value", "aetherhaven_commands_help.commands.aetherhaven.reputation.value.desc", ArgTypes.INTEGER);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        SetSubCommand() {
            super("set", "aetherhaven_commands_help.commands.aetherhaven.reputation.set.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
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
            if (plugin == null || !AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            TownVillagerTargetResolver.Outcome target =
                TownVillagerTargetResolver.resolve(town, world, store, context.get(villagerArg));
            if (!target.isOk()) {
                if (target.error() != null) {
                    playerRef.sendMessage(Message.raw(target.error()));
                } else {
                    playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.invalidVillager"));
                }
                return;
            }
            UUID villagerUuid = target.villagerUuid();
            int value = context.get(reputationArg);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            boolean changed =
                VillagerReputationService.setReputationCrossingMilestones(world, town, tm, uc.getUuid(), villagerUuid, value);
            if (!changed) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.rep.unchanged").param("value", String.valueOf(value))
                );
                return;
            }
            int clamped = Math.max(0, Math.min(100, value));
            playerRef.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.rep.set").param("value", String.valueOf(clamped))
            );
        }
    }

    private static final class RewardSubCommand extends AbstractCommandCollection {
        RewardSubCommand() {
            super("reward", "aetherhaven_commands_help.commands.aetherhaven.reputation.reward.desc");
            this.addSubCommand(new ListRewardsSubCommand());
            this.addSubCommand(new GrantRewardSubCommand());
        }
    }

    private static final class ListRewardsSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> roleFilterArg =
            this.withOptionalArg("roleId", "aetherhaven_commands_help.commands.aetherhaven.reputation.reward.roleFilter.desc", AetherhavenArgTypes.VILLAGER_NPC_ROLE);

        ListRewardsSubCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.reputation.reward.list.desc");
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
            if (plugin == null || !AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            String filter = context.provided(roleFilterArg) ? context.get(roleFilterArg).trim() : "";
            List<ReputationRewardCatalog.ReputationRewardDefinition> defs = ReputationRewardCatalog.allDefinitions();
            playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.rep.milestonesHeader"));
            for (ReputationRewardCatalog.ReputationRewardDefinition d : defs) {
                if (!filter.isEmpty() && !d.roleId().equalsIgnoreCase(filter)) {
                    continue;
                }
                String learn = d.learnRecipeItemId() != null && !d.learnRecipeItemId().isBlank()
                    ? " recipeLearn=" + d.learnRecipeItemId()
                    : "";
                String items = d.itemCount() > 0 && d.itemId() != null && !d.itemId().isBlank()
                    ? " item=" + d.itemId() + " x" + d.itemCount()
                    : "";
                playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.rep.milestoneLine")
                        .param("id", d.rewardId())
                        .param("role", d.roleId())
                        .param("min", String.valueOf(d.minReputation()))
                        .param("node", d.dialogueNodeId())
                        .param("items", items)
                        .param("learn", learn)
                );
            }
        }
    }

    private static final class GrantRewardSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> villagerArg =
            this.withRequiredArg("villager", "aetherhaven_commands_help.commands.aetherhaven.reputation.villagerTarget.desc", AetherhavenArgTypes.VILLAGER_TARGET);
        @Nonnull
        private final RequiredArg<String> rewardIdArg =
            this.withRequiredArg("rewardId", "aetherhaven_commands_help.commands.aetherhaven.reputation.rewardId.desc", AetherhavenArgTypes.REWARD_ID);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        GrantRewardSubCommand() {
            super("grant", "aetherhaven_commands_help.commands.aetherhaven.reputation.reward.grant.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
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
            if (plugin == null || !AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            TownVillagerTargetResolver.Outcome target =
                TownVillagerTargetResolver.resolve(town, world, store, context.get(villagerArg));
            if (!target.isOk()) {
                if (target.error() != null) {
                    playerRef.sendMessage(Message.raw(target.error()));
                } else {
                    playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.invalidVillager"));
                }
                return;
            }
            UUID villagerUuid = target.villagerUuid();
            String rid = context.get(rewardIdArg).trim();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String err = VillagerReputationService.grantReputationRewardDirect(
                world, town, tm, uc.getUuid(), villagerUuid, rid, ref, store
            );
            if (err != null) {
                playerRef.sendMessage(Message.raw(err));
                return;
            }
            playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.rep.granted").param("id", rid));
        }
    }
}
