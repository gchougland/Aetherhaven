package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.TownQuestBoardRank;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRankTierJson;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class AetherhavenTownRankDebugCommand extends AbstractCommandCollection {
    public AetherhavenTownRankDebugCommand() {
        super("townrank", "aetherhaven_commands_help.commands.aetherhaven.townrank.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new SetCommand());
    }

    private static final class SetCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> rankArg =
            this.withRequiredArg("rank", "aetherhaven_commands_help.commands.aetherhaven.townrank.rank.desc", AetherhavenArgTypes.QUEST_BOARD_RANK);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        SetCommand() {
            super("set", "aetherhaven_commands_help.commands.aetherhaven.townrank.set.desc");
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
            TownCommandResolution townRes = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            QuestBoardCatalog catalog = plugin.getQuestBoardCatalog();
            String rankInput = context.get(rankArg);
            QuestBoardRankTierJson tier = catalog.rankTier(rankInput);
            if (tier == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.townrank.unknownRank"));
                return;
            }
            int oldXp = town.getQuestBoardRankXp();
            String oldTier = TownQuestBoardRank.tierIdForXp(oldXp, catalog);
            int newXp = tier.xpRequired();
            town.setQuestBoardRankXp(newXp);
            String newTier = TownQuestBoardRank.tierIdForXp(newXp, catalog);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            tm.updateTown(town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.townrank.setDone")
                    .param("oldRank", oldTier)
                    .param("rank", newTier)
                    .param("xp", String.valueOf(newXp))
            );
        }
    }
}
