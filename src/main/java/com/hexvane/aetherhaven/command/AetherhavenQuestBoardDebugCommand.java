package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.QuestBoardService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Random;
import javax.annotation.Nonnull;

public final class AetherhavenQuestBoardDebugCommand extends AbstractCommandCollection {
    public AetherhavenQuestBoardDebugCommand() {
        super("questboard", "aetherhaven_commands_help.commands.aetherhaven.questboard.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new RerollCommand());
    }

    private static final class RerollCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        RerollCommand() {
            super("reroll", "aetherhaven_commands_help.commands.aetherhaven.questboard.reroll.desc");
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
            QuestBoardService.refreshUnacceptedSlots(town, store, catalog, new Random());
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            tm.updateTown(town);
            playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.questboard.rerollDone"));
        }
    }
}
