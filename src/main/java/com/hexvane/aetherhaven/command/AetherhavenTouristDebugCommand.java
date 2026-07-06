package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.tourist.TouristDestinationResolver;
import com.hexvane.aetherhaven.tourist.TouristPlotVisit;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownCommandResolution;
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
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class AetherhavenTouristDebugCommand extends AbstractCommandCollection {
    public AetherhavenTouristDebugCommand() {
        super("tourist", "aetherhaven_commands_help.commands.aetherhaven.tourist.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new TargetsCommand());
        this.addSubCommand(new PurgeSubCommand());
    }

    private static final class TargetsCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        TargetsCommand() {
            super("targets", "aetherhaven_commands_help.commands.aetherhaven.tourist.targets.desc");
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

            ConstructionCatalog catalog = plugin.getConstructionCatalog();
            PoiRegistry poiRegistry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            List<TouristPlotVisit> plots = TouristDestinationResolver.listVisitPlots(town, catalog, world);

            playerRef.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.tourist.targetsHeader")
                    .param("town", town.getDisplayName() != null ? town.getDisplayName() : town.getTownId().toString())
                    .param("count", String.valueOf(plots.size()))
            );
            if (plots.isEmpty()) {
                playerRef.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.tourist.targetsEmpty"));
                return;
            }

            for (TouristPlotVisit visit : plots) {
                PlotInstance plot = town.findPlotById(visit.plotId());
                ConstructionDefinition def =
                    plot != null && plot.getConstructionId() != null ? catalog.get(plot.getConstructionId()) : null;
                String building =
                    def != null && def.getId() != null ? def.getId() : (plot != null ? plot.getConstructionId() : "?");
                boolean preferred =
                    plot != null
                        && plot.getConstructionId() != null
                        && TouristDestinationResolver.isPreferredVisitPlot(town, catalog, visit.plotId());

                List<PoiEntry> pois =
                    TouristDestinationResolver.listVisitPoisOnPlot(town, poiRegistry, catalog, visit.plotId());

                playerRef.sendMessage(
                    Message.translation("aetherhaven_world_debug.aetherhaven.debug.tourist.plotLine")
                        .param("building", building)
                        .param("plot", visit.plotId().toString())
                        .param("entryX", String.format("%.1f", visit.entryX()))
                        .param("entryY", String.format("%.1f", visit.entryY()))
                        .param("entryZ", String.format("%.1f", visit.entryZ()))
                        .param("preferred", preferred ? "yes" : "no")
                        .param("poiCount", String.valueOf(pois.size()))
                );

                for (PoiEntry poi : pois) {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_world_debug.aetherhaven.debug.tourist.poiLine")
                            .param("x", String.valueOf(poi.getX()))
                            .param("y", String.valueOf(poi.getY()))
                            .param("z", String.valueOf(poi.getZ()))
                            .param("kind", String.valueOf(poi.getInteractionKind()))
                            .param("tags", poi.getTags() != null ? poi.getTags().toString() : "")
                    );
                }
            }
        }
    }

    private static final class PurgeSubCommand extends AbstractPlayerCommand {
        PurgeSubCommand() {
            super("purge", "aetherhaven_commands_help.commands.aetherhaven.tourist.purge.desc");
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
            TouristPortalTickService.TouristPurgeResult result =
                TouristPortalTickService.purgeActiveTouristsInWorld(world, plugin, store);
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_help.aetherhaven.tourist.purgeDone")
                    .param("removed", String.valueOf(result.removed()))
                    .param("skippedProtected", String.valueOf(result.skippedProtected()))
                    .param("skippedGuards", String.valueOf(result.skippedGuards()))
            );
        }
    }
}
