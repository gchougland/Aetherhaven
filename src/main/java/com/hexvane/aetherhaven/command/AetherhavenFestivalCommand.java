package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Admin helpers for testing festivals without waiting for the calendar. */
public final class AetherhavenFestivalCommand extends AbstractCommandCollection {
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.command.";

    public AetherhavenFestivalCommand() {
        super("festival", "aetherhaven_commands_help.commands.aetherhaven.festival.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new StartCommand());
        this.addSubCommand(new EndCommand());
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        ListCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.festival.list.desc");
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
            var all = plugin.getFestivalCatalog().list();
            if (all.isEmpty()) {
                playerRef.sendMessage(Message.translation(LANG + "list.empty"));
                return;
            }
            playerRef.sendMessage(Message.translation(LANG + "list.header"));
            for (FestivalDefinition def : all) {
                playerRef.sendMessage(
                    Message.translation(LANG + "list.row")
                        .param("id", def.getId())
                        .param("name", FestivalService.festivalName(def))
                        .param("season", def.getSeason().displayName())
                        .param("day", String.valueOf(def.getDayOfSeason()))
                );
            }
        }
    }

    private static final class StartCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg(
                "id",
                "aetherhaven_commands_help.commands.aetherhaven.festival.start.id.desc",
                ArgTypes.STRING
            );

        StartCommand() {
            super("start", "aetherhaven_commands_help.commands.aetherhaven.festival.start.desc");
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
            String id = context.get(idArg).trim();
            FestivalDefinition def = plugin.getFestivalCatalog().get(id);
            if (def == null) {
                playerRef.sendMessage(Message.translation(LANG + "unknown").param("id", id));
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(() -> {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = resolveTown(world, es, ref, tm, playerRef);
                if (town == null) {
                    return;
                }
                PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
                if (square == null) {
                    playerRef.sendMessage(Message.translation(LANG + "noSquare"));
                    return;
                }
                if (town.getActiveFestivalId() != null) {
                    FestivalService.endFestival(world, es, plugin, tm, town);
                }
                LocalDateTime gameTime = gameTime(es);
                long epochMinute = FestivalService.toEpochMinute(gameTime);
                if (FestivalService.startFestival(world, es, plugin, tm, town, def, gameTime, epochMinute)) {
                    playerRef.sendMessage(
                        Message.translation(LANG + "started").param("name", FestivalService.festivalName(def))
                    );
                } else {
                    playerRef.sendMessage(Message.translation(LANG + "noSquare"));
                }
            });
        }
    }

    private static final class EndCommand extends AbstractPlayerCommand {
        EndCommand() {
            super("end", "aetherhaven_commands_help.commands.aetherhaven.festival.end.desc");
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
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(() -> {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = resolveTown(world, es, ref, tm, playerRef);
                if (town == null) {
                    return;
                }
                if (town.getActiveFestivalId() == null) {
                    playerRef.sendMessage(Message.translation(LANG + "noneRunning"));
                    return;
                }
                FestivalService.endFestival(world, es, plugin, tm, town);
                playerRef.sendMessage(Message.translation(LANG + "ended"));
            });
        }
    }

    @Nullable
    private static TownRecord resolveTown(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TownManager tm,
        @Nonnull PlayerRef playerRef
    ) {
        TownRecord town = TownPlayerResolution.resolveActiveTown(world, store, ref, tm);
        if (town == null) {
            playerRef.sendMessage(Message.translation(LANG + "noTown"));
        }
        return town;
    }

    @Nonnull
    private static LocalDateTime gameTime(@Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        return wtr != null ? wtr.getGameDateTime() : LocalDateTime.now();
    }
}
