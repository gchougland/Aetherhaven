package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Plot instance listing and creative-only helpers (assembly skip, demolish). Named {@code plots} to avoid clashing with
 * {@code /aetherhaven plot} (plot sign admin UI).
 */
public final class AetherhavenPlotsCommand extends AbstractCommandCollection {
    public AetherhavenPlotsCommand() {
        super("plots", "aetherhaven_commands_help.commands.aetherhaven.plots.desc");
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new FinishAssemblyCommand());
        this.addSubCommand(new RemoveCommand());
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        ListCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.plots.list.desc");
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
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town =
                AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld"));
                return;
            }
            playerRef.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.forTown").param("id", town.getTownId().toString())
            );
            for (PlotInstance p : town.getPlotInstances()) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.line")
                        .param("plotId", p.getPlotId().toString())
                        .param("construction", p.getConstructionId() != null ? p.getConstructionId() : "")
                        .param("state", p.getState() != null ? p.getState().name() : "")
                        .param("x", String.valueOf(p.getSignX()))
                        .param("y", String.valueOf(p.getSignY()))
                        .param("z", String.valueOf(p.getSignZ()))
                );
            }
        }
    }

    /** Creative: instantly finish every assembling plot in your town that has an active assembly job (chunks loaded). */
    private static final class FinishAssemblyCommand extends AbstractPlayerCommand {
        FinishAssemblyCommand() {
            super("finishassembly", "aetherhaven_commands_help.commands.aetherhaven.plots.finishassembly.desc");
            this.setPermissionGroup(GameMode.Creative);
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
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownRecord town =
                AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld"));
                return;
            }
            int n = PlotAssemblyService.instantCompleteAllAssemblingJobsForTown(world, plugin, store, town);
            if (n == 0) {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.finishassemblyNone"));
            } else {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.finishassemblyDone").param("count", String.valueOf(n)));
            }
        }
    }

    /** Creative: demolish one plot (same footprint/sign cleanup as town dissolve) and remove it from town data. */
    private static final class RemoveCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> plotIdArg =
            this.withRequiredArg("plotId", "aetherhaven_commands_help.commands.aetherhaven.plots.remove.plotId.desc", ArgTypes.STRING);

        RemoveCommand() {
            super("remove", "aetherhaven_commands_help.commands.aetherhaven.plots.remove.desc");
            this.setPermissionGroup(GameMode.Creative);
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
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
            if (town == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld"));
                return;
            }
            UUID plotId;
            try {
                plotId = UUID.fromString(context.get(plotIdArg).trim());
            } catch (IllegalArgumentException e) {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.removeBadUuid"));
                return;
            }
            PlotInstance plot = town.findPlotById(plotId);
            if (plot == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.removeNotFound").param("plotId", plotId.toString()));
                return;
            }
            var reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            TownDissolutionService.clearPlotFromWorld(world, plugin, town, plot, store, reg);
            if (!town.removePlotInstance(plotId)) {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.removeDataFailed"));
                return;
            }
            tm.updateTown(town);
            playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.removedPlot").param("plotId", plotId.toString()));
        }
    }
}
