package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.quest.QuestPlotBlueprintOnStart;
import com.hexvane.aetherhaven.quest.QuestPlotTokenOnStart;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

public final class AetherhavenQuestDebugCommand extends AbstractCommandCollection {
    public AetherhavenQuestDebugCommand() {
        super("quest", "aetherhaven_commands_help.commands.aetherhaven.quest.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new GrantCommand());
        this.addSubCommand(new CompleteCommand());
        this.addSubCommand(new ClearCommand());
        this.addSubCommand(new StatusCommand());
    }

    @Nonnull
    private static Message questStatusMessage(
        @Nonnull AetherhavenPlugin plugin, boolean active, @Nonnull List<String> ids
    ) {
        if (ids.isEmpty()) {
            return active
                ? Message.translation("aetherhaven_quests_portals.aetherhaven.questdebug.statusActiveEmpty")
                : Message.translation("aetherhaven_quests_portals.aetherhaven.questdebug.statusCompletedEmpty");
        }
        var quests = plugin.getQuestCatalog();
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(quests.displayName(id)).append(" [").append(id).append("]");
        }
        String list = sb.toString();
        return active
            ? Message.translation("aetherhaven_quests_portals.aetherhaven.questdebug.statusActiveLine").param("list", list)
            : Message.translation("aetherhaven_quests_portals.aetherhaven.questdebug.statusCompletedLine").param("list", list);
    }

    private static final class StatusCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        StatusCommand() {
            super("status", "aetherhaven_commands_help.commands.aetherhaven.questdebug.status.desc");
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
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            TownCommandResolution res = townTarget.resolve(context, world, store, ref, playerRef, false);
            if (!res.isOk()) {
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            playerRef.sendMessage(
                questStatusMessage(plugin, true, town.getActiveQuestIdsSnapshot())
            );
            playerRef.sendMessage(
                questStatusMessage(plugin, false, town.getCompletedQuestIdsSnapshot())
            );
        }
    }

    private static final class GrantCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> idArg =
            this.withOptionalArg("questId", "aetherhaven_commands_help.commands.aetherhaven.questdebug.id.desc", AetherhavenArgTypes.QUEST_ID);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        GrantCommand() {
            super("grant", "aetherhaven_commands_help.commands.aetherhaven.questdebug.grant.desc");
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
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(context, world, store, ref, playerRef, false);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            String qid = context.provided(idArg) ? context.get(idArg) : AetherhavenConstants.QUEST_BUILD_INN;
            if (qid == null || qid.isBlank()) {
                qid = AetherhavenConstants.QUEST_BUILD_INN;
            }
            qid = qid.trim();
            town.addActiveQuest(qid);
            QuestDefinition def = plugin.getQuestCatalog().get(qid);
            if (def != null) {
                town.initQuestObjectiveProgress(qid, def.trackableObjectiveIds());
                QuestPlotTokenOnStart.grantIfConfigured(plugin, def, town, ref, store);
                QuestPlotBlueprintOnStart.grantIfConfigured(plugin, def, ref, store);
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            InnVisitorShopPromotion.tryPromoteReadyWorkplaces(world, plugin, town, tm);
            tm.updateTown(town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_quests_portals.aetherhaven.questdebug.granted")
                    .param("name", plugin.getQuestCatalog().displayName(qid))
            );
        }
    }

    private static final class CompleteCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> idArg =
            this.withOptionalArg("questId", "aetherhaven_commands_help.commands.aetherhaven.questdebug.id.desc", AetherhavenArgTypes.QUEST_ID);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        CompleteCommand() {
            super("complete", "aetherhaven_commands_help.commands.aetherhaven.questdebug.complete.desc");
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
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(context, world, store, ref, playerRef, false);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            String qid = context.provided(idArg) ? context.get(idArg) : AetherhavenConstants.QUEST_BUILD_INN;
            if (qid == null || qid.isBlank()) {
                qid = AetherhavenConstants.QUEST_BUILD_INN;
            }
            qid = qid.trim();
            town.completeQuest(qid);
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_quests_portals.aetherhaven.questdebug.completed")
                    .param("name", plugin.getQuestCatalog().displayName(qid))
            );
        }
    }

    private static final class ClearCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> idArg =
            this.withOptionalArg("questId", "aetherhaven_commands_help.commands.aetherhaven.questdebug.id.desc", AetherhavenArgTypes.QUEST_ID);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        ClearCommand() {
            super("clear", "aetherhaven_commands_help.commands.aetherhaven.questdebug.clear.desc");
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
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(context, world, store, ref, playerRef, false);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            String qid = context.provided(idArg) ? context.get(idArg) : AetherhavenConstants.QUEST_BUILD_INN;
            if (qid == null || qid.isBlank()) {
                qid = AetherhavenConstants.QUEST_BUILD_INN;
            }
            qid = qid.trim();
            town.clearActiveQuest(qid);
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_quests_portals.aetherhaven.questdebug.cleared")
                    .param("name", plugin.getQuestCatalog().displayName(qid))
            );
        }
    }
}
