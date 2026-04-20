package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
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
import javax.annotation.Nullable;

/**
 * Debug: set reputation with milestone queue, list milestone definitions, grant one reward directly.
 * Requires {@link AetherhavenPlugin#getConfig()}{@code .isDebugCommandsEnabled()}.
 */
public final class AetherhavenReputationDebugCommand extends AbstractCommandCollection {
    public AetherhavenReputationDebugCommand() {
        super("reputation", "server.commands.aetherhaven.reputation.desc");
        this.addAliases("rep");
        this.addSubCommand(new SetSubCommand());
        this.addSubCommand(new RewardSubCommand());
    }

    @Nullable
    private static TownRecord townForQuestPlayer(
        @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull World world
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        return AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
    }

    private static final class SetSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> villagerArg =
            this.withRequiredArg("villager", "server.commands.aetherhaven.reputation.villagerTarget.desc", ArgTypes.STRING);
        @Nonnull
        private final RequiredArg<Integer> reputationArg =
            this.withRequiredArg("value", "server.commands.aetherhaven.reputation.value.desc", ArgTypes.INTEGER);

        SetSubCommand() {
            super("set", "server.commands.aetherhaven.reputation.set.desc");
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
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.raw("You do not have quest permission in this town."));
                return;
            }
            TownVillagerTargetResolver.Outcome target =
                TownVillagerTargetResolver.resolve(town, world, store, context.get(villagerArg));
            if (!target.isOk()) {
                playerRef.sendMessage(Message.raw(target.error() != null ? target.error() : "Invalid villager."));
                return;
            }
            UUID villagerUuid = target.villagerUuid();
            int value = context.get(reputationArg);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            boolean changed =
                VillagerReputationService.setReputationCrossingMilestones(world, town, tm, uc.getUuid(), villagerUuid, value);
            if (!changed) {
                playerRef.sendMessage(Message.raw("Reputation unchanged (already " + value + ")."));
                return;
            }
            playerRef.sendMessage(Message.raw("Set reputation to " + Math.max(0, Math.min(100, value))
                + " with crossed milestones queued for dialogue (if role resolved)."));
        }
    }

    private static final class RewardSubCommand extends AbstractCommandCollection {
        RewardSubCommand() {
            super("reward", "server.commands.aetherhaven.reputation.reward.desc");
            this.addSubCommand(new ListRewardsSubCommand());
            this.addSubCommand(new GrantRewardSubCommand());
        }
    }

    private static final class ListRewardsSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> roleFilterArg =
            this.withOptionalArg("roleId", "server.commands.aetherhaven.reputation.reward.roleFilter.desc", ArgTypes.STRING);

        ListRewardsSubCommand() {
            super("list", "server.commands.aetherhaven.reputation.reward.list.desc");
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
            playerRef.sendMessage(Message.raw("Reputation milestones (reward id — role — min rep — dialogue node):"));
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
                playerRef.sendMessage(Message.raw(
                    "  " + d.rewardId() + " — " + d.roleId() + " — " + d.minReputation() + " — " + d.dialogueNodeId()
                        + items + learn
                ));
            }
        }
    }

    private static final class GrantRewardSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> villagerArg =
            this.withRequiredArg("villager", "server.commands.aetherhaven.reputation.villagerTarget.desc", ArgTypes.STRING);
        @Nonnull
        private final RequiredArg<String> rewardIdArg =
            this.withRequiredArg("rewardId", "server.commands.aetherhaven.reputation.rewardId.desc", ArgTypes.STRING);

        GrantRewardSubCommand() {
            super("grant", "server.commands.aetherhaven.reputation.reward.grant.desc");
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
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.raw("You do not have quest permission in this town."));
                return;
            }
            TownVillagerTargetResolver.Outcome target =
                TownVillagerTargetResolver.resolve(town, world, store, context.get(villagerArg));
            if (!target.isOk()) {
                playerRef.sendMessage(Message.raw(target.error() != null ? target.error() : "Invalid villager."));
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
            playerRef.sendMessage(Message.raw("Granted reward " + rid + "."));
        }
    }
}
