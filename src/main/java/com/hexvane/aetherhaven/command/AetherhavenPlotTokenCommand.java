package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class AetherhavenPlotTokenCommand extends AbstractCommandCollection {
    public AetherhavenPlotTokenCommand() {
        super("plottoken", "aetherhaven_commands_help.commands.aetherhaven.plottoken.desc");
        this.addSubCommand(new GiveCommand());
        this.addSubCommand(new UnlockCommand());
        this.addSubCommand(new UnlockAllCommand());
        this.addSubCommand(new BuildingsListCommand());
    }

    private static boolean requirePlotCreatorPermission(@Nonnull PlayerRef playerRef) {
        if (playerRef.hasPermission(AetherhavenConstants.PERMISSION_PLOT_CREATOR)) {
            return true;
        }
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.noPermission"));
        return false;
    }

    private static final class GiveCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> constructionIdArg =
            this.withRequiredArg("constructionId", "aetherhaven_commands_help.commands.aetherhaven.plottoken.give.constructionId", ArgTypes.STRING);
        private final OptionalArg<Integer> amountArg =
            this.withOptionalArg("amount", "aetherhaven_commands_help.commands.aetherhaven.plottoken.give.amount", ArgTypes.INTEGER);

        GiveCommand() {
            super("give", "aetherhaven_commands_help.commands.aetherhaven.plottoken.give.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!requirePlotCreatorPermission(playerRef)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            String cid = constructionIdArg.get(context).trim();
            ConstructionDefinition def = plugin.getConstructionCatalog().get(cid);
            if (def == null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.error.unknownBuilding").param("id", cid)
                );
                return;
            }
            int amount = amountArg.provided(context) ? Math.max(1, Math.min(64, amountArg.get(context))) : 1;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            PlotTokenInventory.giveToPlayer(player, cid, amount, def.getDisplayName());
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.gave")
                    .param("amount", amount)
                    .param("name", def.getDisplayName())
            );
        }
    }

    private static final class UnlockCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> constructionIdArg =
            this.withRequiredArg("constructionId", "aetherhaven_commands_help.commands.aetherhaven.plottoken.unlock.constructionId", ArgTypes.STRING);

        UnlockCommand() {
            super("unlock", "aetherhaven_commands_help.commands.aetherhaven.plottoken.unlock.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!requirePlotCreatorPermission(playerRef)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            String cid = constructionIdArg.get(context).trim();
            ConstructionDefinition def = plugin.getConstructionCatalog().get(cid);
            if (def == null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.error.unknownBuilding").param("id", cid)
                );
                return;
            }
            if (!PlotTokenUnlockService.requiresUnlock(def)) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.unlock.notLockable"));
                return;
            }
            String name = PlotTokenUnlockService.displayNameFor(cid);
            if (PlotTokenUnlockService.isUnlocked(ref, store, cid)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.unlock.alreadyKnown").param("name", name)
                );
                return;
            }
            PlotTokenUnlockService.unlock(ref, store, cid);
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.unlock.success").param("name", name)
            );
        }
    }

    private static final class UnlockAllCommand extends AbstractPlayerCommand {
        UnlockAllCommand() {
            super("unlockall", "aetherhaven_commands_help.commands.aetherhaven.plottoken.unlockall.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!requirePlotCreatorPermission(playerRef)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            int added = PlotTokenUnlockService.unlockAllLockable(ref, store, plugin.getConstructionCatalog());
            if (added == 0) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.unlockall.noneLeft"));
            } else {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.unlockall.success").param("count", added)
                );
            }
        }
    }

    private static final class BuildingsListCommand extends AbstractPlayerCommand {
        BuildingsListCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.plottoken.list.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!requirePlotCreatorPermission(playerRef)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            ConstructionCatalog catalog = plugin.getConstructionCatalog();
            StringBuilder sb = new StringBuilder();
            for (String id : catalog.ids()) {
                ConstructionDefinition d = catalog.get(id);
                if (d == null || d.isWallSegment()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(id);
                if (catalog.isCustomConstruction(id)) {
                    sb.append(" (custom)");
                }
            }
            if (sb.isEmpty()) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.list.empty"));
            } else {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plottoken.list.header"));
                playerRef.sendMessage(Message.raw(sb.toString()));
            }
        }
    }
}
