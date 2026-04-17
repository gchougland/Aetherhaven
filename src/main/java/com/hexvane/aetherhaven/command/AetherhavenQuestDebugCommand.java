package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AetherhavenQuestDebugCommand extends AbstractCommandCollection {
    public AetherhavenQuestDebugCommand() {
        super("quest", "server.commands.aetherhaven.questdebug.desc");
        this.addSubCommand(new GrantCommand());
        this.addSubCommand(new CompleteCommand());
        this.addSubCommand(new ClearCommand());
        this.addSubCommand(new StatusCommand());
    }

    @Nullable
    private static TownRecord townForPlayer(
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

    @Nonnull
    private static String questLine(@Nonnull AetherhavenPlugin plugin, @Nonnull String label, @Nonnull List<String> ids) {
        if (ids.isEmpty()) {
            return label + " (none)";
        }
        var quests = plugin.getQuestCatalog();
        List<String> parts = new ArrayList<>();
        for (String id : ids) {
            parts.add(quests.displayName(id) + " [" + id + "]");
        }
        return label + ": " + String.join(", ", parts);
    }

    private static final class StatusCommand extends AbstractPlayerCommand {
        StatusCommand() {
            super("status", "server.commands.aetherhaven.questdebug.status.desc");
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
            TownRecord town = townForPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            playerRef.sendMessage(Message.raw(questLine(plugin, "Active", town.getActiveQuestIdsSnapshot())));
            playerRef.sendMessage(Message.raw(questLine(plugin, "Completed", town.getCompletedQuestIdsSnapshot())));
        }
    }

    private static final class GrantCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> idArg =
            this.withOptionalArg("questId", "server.commands.aetherhaven.questdebug.id.desc", ArgTypes.STRING);

        GrantCommand() {
            super("grant", "server.commands.aetherhaven.questdebug.grant.desc");
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
            TownRecord town = townForPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            String qid = context.provided(idArg) ? context.get(idArg) : AetherhavenConstants.QUEST_BUILD_INN;
            if (qid == null || qid.isBlank()) {
                qid = AetherhavenConstants.QUEST_BUILD_INN;
            }
            qid = qid.trim();
            town.addActiveQuest(qid);
            QuestDefinition def = plugin.getQuestCatalog().get(qid);
            if (def != null) {
                town.initQuestObjectiveProgress(qid, def.trackableObjectiveIds());
            }
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
            playerRef.sendMessage(Message.raw("Granted active quest: " + plugin.getQuestCatalog().displayName(qid)));
        }
    }

    private static final class CompleteCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> idArg =
            this.withOptionalArg("questId", "server.commands.aetherhaven.questdebug.id.desc", ArgTypes.STRING);

        CompleteCommand() {
            super("complete", "server.commands.aetherhaven.questdebug.complete.desc");
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
            TownRecord town = townForPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            String qid = context.provided(idArg) ? context.get(idArg) : AetherhavenConstants.QUEST_BUILD_INN;
            if (qid == null || qid.isBlank()) {
                qid = AetherhavenConstants.QUEST_BUILD_INN;
            }
            qid = qid.trim();
            town.completeQuest(qid);
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
            playerRef.sendMessage(Message.raw("Completed quest: " + plugin.getQuestCatalog().displayName(qid)));
        }
    }

    private static final class ClearCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> idArg =
            this.withOptionalArg("questId", "server.commands.aetherhaven.questdebug.id.desc", ArgTypes.STRING);

        ClearCommand() {
            super("clear", "server.commands.aetherhaven.questdebug.clear.desc");
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
            TownRecord town = townForPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            String qid = context.provided(idArg) ? context.get(idArg) : AetherhavenConstants.QUEST_BUILD_INN;
            if (qid == null || qid.isBlank()) {
                qid = AetherhavenConstants.QUEST_BUILD_INN;
            }
            qid = qid.trim();
            town.clearActiveQuest(qid);
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
            playerRef.sendMessage(Message.raw("Cleared active quest: " + plugin.getQuestCatalog().displayName(qid)));
        }
    }
}
