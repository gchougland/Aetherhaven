package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnPoolService;
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
import javax.annotation.Nonnull;

public final class AetherhavenInnDebugCommand extends AbstractCommandCollection {
    public AetherhavenInnDebugCommand() {
        super("inn", "aetherhaven_commands_help.commands.aetherhaven.inn.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new RerollCommand());
    }

    private static final class RerollCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        RerollCommand() {
            super("reroll", "aetherhaven_commands_help.commands.aetherhaven.inn.reroll.desc");
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
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            InnPoolService.RerollOutcome outcome =
                InnPoolService.rerollUnlockedVisitorsForTown(world, plugin, town, tm, store);
            switch (outcome) {
                case INN_NOT_READY -> playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.inn.notReady")
                );
                case INN_NOT_LOADED -> playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.inn.notLoaded")
                );
                case OK -> playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.inn.rerollDone")
                );
            }
        }
    }
}
