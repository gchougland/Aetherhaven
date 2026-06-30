package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.placement.PlotReconstructService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotLinkReconcileService;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownDissolutionService;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Plot instance listing and creative-only helpers (assembly skip, demolish, repair, reconstruct).
 */
public final class AetherhavenPlotsCommand extends AbstractCommandCollection {
    public AetherhavenPlotsCommand() {
        super("plots", "aetherhaven_commands_help.commands.aetherhaven.plots.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new FinishAssemblyCommand());
        this.addSubCommand(new RepairCommand());
        this.addSubCommand(new ReconstructCommand());
        this.addSubCommand(new DiagnoseCommand());
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
            this.setPermissionGroups("hytale:WorldEditor");
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
            PlotAssemblyService.InstantCompleteTownResult res =
                PlotAssemblyService.instantCompleteAllAssemblingJobsForTownDetailed(world, plugin, store, town);
            if (res.getFinished() == 0 && res.getFailed() == 0) {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.finishassemblyNone"));
                return;
            }
            playerRef.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.finishassemblySummary")
                    .param("finished", String.valueOf(res.getFinished()))
                    .param("failed", String.valueOf(res.getFailed()))
                    .param("still", String.valueOf(res.getStillAssembling()))
            );
        }
    }

    private static final class RepairCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg(
                "townName",
                "aetherhaven_commands_help.commands.aetherhaven.plots.repair.townName.desc",
                ArgTypes.GREEDY_STRING
            );

        RepairCommand() {
            super("repair", "aetherhaven_commands_help.commands.aetherhaven.plots.repair.desc");
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
            PlotLinkReconcileService.TownRepairReport report =
                PlotLinkReconcileService.repairTown(world, plugin, town, true);
            playerRef.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.repairSummary")
                    .param("town", town.getDisplayName() != null ? town.getDisplayName() : town.getTownId().toString())
                    .param("scanned", String.valueOf(report.getScanned()))
                    .param("relinked", String.valueOf(report.getRelinked()))
                    .param("ok", String.valueOf(report.getAlreadyOk()))
                    .param("skipped", String.valueOf(report.getSkippedChunkUnloaded()))
                    .param("failed", String.valueOf(report.getFailed()))
                    .param("orphans", String.valueOf(report.getOrphans()))
            );
        }
    }

    private static final class ReconstructCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> constructionIdArg =
            this.withRequiredArg(
                "constructionId",
                "aetherhaven_commands_help.commands.aetherhaven.plots.reconstruct.constructionId.desc",
                ArgTypes.STRING
            );
        @Nonnull
        private final OptionalArg<String> tailArg =
            this.withOptionalArg(
                "indexOrTown",
                "aetherhaven_commands_help.commands.aetherhaven.plots.reconstruct.tail.desc",
                ArgTypes.GREEDY_STRING
            );

        ReconstructCommand() {
            super("reconstruct", "aetherhaven_commands_help.commands.aetherhaven.plots.reconstruct.desc");
            this.setPermissionGroups("hytale:WorldEditor");
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
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            boolean admin = TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String constructionId = context.get(constructionIdArg).trim();
            Integer index = null;
            String townName = null;
            if (context.provided(tailArg)) {
                String tail = context.get(tailArg).trim();
                if (!tail.isEmpty()) {
                    try {
                        index = Integer.parseInt(tail.split("\\s+")[0]);
                        if (tail.contains(" ")) {
                            townName = tail.substring(tail.indexOf(' ') + 1).trim();
                        }
                    } catch (NumberFormatException e) {
                        townName = tail;
                    }
                }
            }
            TownCommandResolution townRes =
                TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), townName, admin);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            PlotConstructionIdResolver.ResolveResult matchRes =
                PlotConstructionIdResolver.resolve(plugin, town, constructionId, index);
            if (matchRes.error() != null) {
                playerRef.sendMessage(matchRes.error());
                return;
            }
            if (matchRes.single() == null && matchRes.ambiguous() != null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructAmbiguous").param("id", constructionId)
                );
                for (PlotConstructionIdResolver.PlotMatch m : matchRes.ambiguous()) {
                    playerRef.sendMessage(Message.raw(PlotConstructionIdResolver.formatAmbiguousLine(m)));
                }
                playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructAmbiguousUsage").param("id", constructionId)
                );
                return;
            }
            PlotInstance plot = matchRes.single().plot();
            PlotReconstructService.ReconstructResult rr =
                PlotReconstructService.reconstruct(world, plugin, town, plot, uc.getUuid(), store);
            switch (rr) {
                case OK ->
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructOk")
                            .param("construction", plot.getConstructionId() != null ? plot.getConstructionId() : "")
                            .param("index", String.valueOf(matchRes.single().index()))
                            .param("x", String.valueOf(plot.getSignX()))
                            .param("y", String.valueOf(plot.getSignY()))
                            .param("z", String.valueOf(plot.getSignZ()))
                    );
                case CHUNKS_UNLOADED ->
                    playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructChunks"));
                case UNKNOWN_CONSTRUCTION ->
                    playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructUnknown"));
                case PREFAB_MISSING ->
                    playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructPrefab"));
                case WALL_OR_DECORATION ->
                    playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructWall"));
                default ->
                    playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.reconstructFailed"));
            }
        }
    }

    private static final class DiagnoseCommand extends AbstractPlayerCommand {
        @Nonnull
        private final OptionalArg<String> townArg =
            this.withOptionalArg(
                "townName",
                "aetherhaven_commands_help.commands.aetherhaven.plots.diagnose.townName.desc",
                ArgTypes.GREEDY_STRING
            );

        DiagnoseCommand() {
            super("diagnose", "aetherhaven_commands_help.commands.aetherhaven.plots.diagnose.desc");
            this.setPermissionGroups("hytale:WorldEditor");
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
            String townOpt = context.provided(townArg) ? context.get(townArg) : null;
            TownRecord town;
            if (townOpt != null && !townOpt.isBlank()) {
                town = tm.findTownByDisplayName(townOpt.trim());
                if (town == null) {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_town.aetherhaven.town.resolve.noTownNamed").param("name", townOpt.trim())
                    );
                    return;
                }
            } else {
                town = tm.findTownForPlayerInWorld(uc.getUuid());
                if (town == null) {
                    playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld"));
                    return;
                }
            }
            List<PlotLinkReconcileService.PlotDiagnoseRow> rows =
                PlotLinkReconcileService.diagnoseTown(world, plugin, town);
            playerRef.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.diagnoseHeader")
                    .param("town", town.getDisplayName() != null ? town.getDisplayName() : town.getTownId().toString())
            );
            for (PlotLinkReconcileService.PlotDiagnoseRow row : rows) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.plots.diagnoseLine")
                        .param("construction", row.constructionId())
                        .param("state", row.state())
                        .param("link", row.linkStatus())
                        .param("x", String.valueOf(row.signX()))
                        .param("y", String.valueOf(row.signY()))
                        .param("z", String.valueOf(row.signZ()))
                );
            }
        }
    }

    /** Creative: demolish one plot (same footprint/sign cleanup as town dissolve) and remove it from town data. */
    private static final class RemoveCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> plotIdArg =
            this.withRequiredArg("plotId", "aetherhaven_commands_help.commands.aetherhaven.plots.remove.plotId.desc", ArgTypes.STRING);

        RemoveCommand() {
            super("remove", "aetherhaven_commands_help.commands.aetherhaven.plots.remove.desc");
            this.setPermissionGroups("hytale:WorldEditor");
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
