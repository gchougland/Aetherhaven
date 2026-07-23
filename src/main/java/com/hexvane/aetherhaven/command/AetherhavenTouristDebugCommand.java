package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.tourist.TouristDestinationResolver;
import com.hexvane.aetherhaven.tourist.TouristPlotVisit;
import com.hexvane.aetherhaven.tourist.TouristPortalRecord;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownCommandResolution;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AetherhavenTouristDebugCommand extends AbstractCommandCollection {
    public AetherhavenTouristDebugCommand() {
        super("tourist", "aetherhaven_commands_help.commands.aetherhaven.tourist.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new TargetsCommand());
        this.addSubCommand(new SpawnSubCommand());
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

    private static final class SpawnSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> portalArg =
            this.withOptionalArg(
                "portal",
                "aetherhaven_commands_help.commands.aetherhaven.tourist.spawn.portal.desc",
                ArgTypes.STRING
            );
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        SpawnSubCommand() {
            super("spawn", "aetherhaven_commands_help.commands.aetherhaven.tourist.spawn.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
            this.addUsageVariant(new SpawnWithCharacterCommand());
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            runSpawn(context, store, ref, playerRef, world, townTarget, portalArg, null);
        }
    }

    /** {@code /aetherhaven tourist spawn briar_mosscap} */
    private static final class SpawnWithCharacterCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> characterArg =
            this.withRequiredArg(
                "characterId",
                "aetherhaven_commands_help.commands.aetherhaven.townsfolk.id.desc",
                ArgTypes.STRING
            );
        private final OptionalArg<String> portalArg =
            this.withOptionalArg(
                "portal",
                "aetherhaven_commands_help.commands.aetherhaven.tourist.spawn.portal.desc",
                ArgTypes.STRING
            );
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        SpawnWithCharacterCommand() {
            super("aetherhaven_commands_help.commands.aetherhaven.tourist.spawn.desc");
            this.setPermissionGroups("hytale:WorldEditor");
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
            runSpawn(context, store, ref, playerRef, world, townTarget, portalArg, characterArg.get(context).trim());
        }
    }

    private static void runSpawn(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        @Nonnull DebugTownTargetArgs townTarget,
        @Nonnull OptionalArg<String> portalArg,
        @Nullable String characterId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
            return;
        }
        TownCommandResolution res = townTarget.resolve(context, world, store, ref, playerRef, false);
        if (!res.isOk()) {
            playerRef.sendMessage(res.error());
            return;
        }
        TownRecord town = res.townOrThrow();
        UUID portalUuid = parsePortalArg(context, portalArg);
        if (portalArg.provided(context) && portalUuid == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.badPortalId"));
            return;
        }
        TouristPortalRecord portal =
            TouristPortalTickService.findPortalForManualSpawn(world, plugin, town, store, ref, portalUuid);
        if (portal == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.noPortal"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TouristPortalTickService.TouristPortalManualSpawnResult result =
            TouristPortalTickService.spawnTouristAtPortalForTesting(
                world,
                plugin,
                tm,
                store,
                town,
                portal,
                characterId
            );
        playerRef.sendMessage(spawnResultMessage(result));
    }

    @Nullable
    private static UUID parsePortalArg(@Nonnull CommandContext context, @Nonnull OptionalArg<String> portalArg) {
        if (!portalArg.provided(context)) {
            return null;
        }
        String raw = portalArg.get(context).trim();
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nonnull
    private static Message spawnResultMessage(@Nonnull TouristPortalTickService.TouristPortalManualSpawnResult result) {
        String portal = result.portalId() != null ? result.portalId().toString() : "?";
        return switch (result.outcome()) {
            case SUCCESS ->
                Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.ok")
                    .param("id", result.characterId() != null ? result.characterId() : "?")
                    .param("portal", portal)
                    .param("entity", result.entityUuid() != null ? result.entityUuid().toString() : "?");
            case NO_PORTAL -> Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.noPortal");
            case PORTAL_CHUNK_UNLOADED ->
                Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.portalChunkUnloaded")
                    .param("portal", portal);
            case NO_CHARACTER_AVAILABLE ->
                Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.noCharacter");
            case CHARACTER_NOT_AVAILABLE ->
                Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.characterNotAvailable")
                    .param("id", result.characterId() != null ? result.characterId() : "?");
            case CHARACTER_ALREADY_ACTIVE ->
                Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.characterActive")
                    .param("id", result.characterId() != null ? result.characterId() : "?");
            case SPAWN_FAILED ->
                Message.translation("aetherhaven_commands_help.aetherhaven.tourist.spawn.failed")
                    .param("id", result.characterId() != null ? result.characterId() : "?");
        };
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
