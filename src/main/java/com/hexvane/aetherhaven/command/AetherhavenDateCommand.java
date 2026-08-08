package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.annotation.Nonnull;

/** Admin helpers for the Aetherhaven calendar date. */
public final class AetherhavenDateCommand extends AbstractCommandCollection {
    public AetherhavenDateCommand() {
        super("date", "aetherhaven_commands_help.commands.aetherhaven.date.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new SetCalendarDateCommand());
    }

    private static final class SetCalendarDateCommand extends AbstractWorldCommand {
        @Nonnull
        private final RequiredArg<String> seasonArg =
            this.withRequiredArg(
                "season",
                "aetherhaven_commands_help.commands.aetherhaven.date.set.season.desc",
                AetherhavenArgTypes.CALENDAR_SEASON
            );

        @Nonnull
        private final RequiredArg<Integer> dayArg =
            this.withRequiredArg(
                "day",
                "aetherhaven_commands_help.commands.aetherhaven.date.set.day.desc",
                AetherhavenArgTypes.CALENDAR_DAY
            );

        @Nonnull
        private final OptionalArg<Long> yearArg =
            this.withOptionalArg(
                "year",
                "aetherhaven_commands_help.commands.aetherhaven.date.set.year.desc",
                AetherhavenArgTypes.CALENDAR_YEAR
            );

        SetCalendarDateCommand() {
            super("set", "aetherhaven_commands_help.commands.aetherhaven.date.set.desc");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            if (wtr == null) {
                context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.time.noResource"));
                return;
            }
            Season season = AetherhavenCalendar.parseSeason(seasonArg.get(context));
            if (season == null) {
                context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.date.set.invalidSeason"));
                return;
            }
            int day = dayArg.get(context);
            long year = yearArg.provided(context)
                ? yearArg.get(context)
                : AetherhavenCalendar.from(wtr.getGameDateTime()).year();
            if (year < 1L) {
                context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.date.set.invalidYear"));
                return;
            }
            LocalDateTime target = AetherhavenCalendar.toLocalDateTime(season, day, year);
            Instant instant = target.atZone(ZoneOffset.UTC).toInstant();
            wtr.setGameTime(instant, world, store);
            context.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.date.set.success")
                    .param("date", AetherhavenCalendar.formatDate(target))
            );
        }
    }
}
