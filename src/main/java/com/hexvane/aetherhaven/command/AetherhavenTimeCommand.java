package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;

/**
 * World clock helpers aligned with villager schedule JSON (wall clock 0:00–23:59 on {@link WorldTimeResource#getGameDateTime}).
 * Vanilla {@code /time set dawn} uses a different preset (~4:48); use {@code /aetherhaven time dawn} for 06:00 on that clock.
 */
public final class AetherhavenTimeCommand extends AbstractCommandCollection {
    public AetherhavenTimeCommand() {
        super("time", "aetherhaven_commands_help.commands.aetherhaven.time.desc");
        this.setPermissionGroup(GameMode.Creative);
        this.addUsageVariant(new SetScheduleHourCommand());
        this.addSubCommand(new ScheduleDawnCommand());
    }

    private static void applyHourMinute(
        @Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        int hour,
        int minute,
        @Nonnull String successLangKey
    ) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.time.noResource"));
            return;
        }
        int h = Math.max(0, Math.min(23, hour));
        int mi = Math.max(0, Math.min(59, minute));
        double dayFraction = (h + mi / 60.0) / (double) WorldTimeResource.HOURS_PER_DAY;
        wtr.setDayTime(dayFraction, world, store);
        LocalDateTime dt = wtr.getGameDateTime();
        context.sendMessage(Message.translation(successLangKey).param("gameTime", dt.toString()));
    }

    private static final class SetScheduleHourCommand extends AbstractWorldCommand {
        @Nonnull
        private final RequiredArg<Integer> hourArg =
            this.withRequiredArg("hour", "aetherhaven_commands_help.commands.aetherhaven.time.hour.desc", ArgTypes.INTEGER)
                .addValidator(Validators.range(0, 23));

        SetScheduleHourCommand() {
            super("aetherhaven_commands_help.commands.aetherhaven.time.set.desc");
            this.setPermissionGroup(null);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            int hour = hourArg.get(context);
            applyHourMinute(context, world, store, hour, 0, "aetherhaven_commands_help.commands.aetherhaven.time.set.success");
        }
    }

    private static final class ScheduleDawnCommand extends AbstractWorldCommand {
        ScheduleDawnCommand() {
            super("dawn", "aetherhaven_commands_help.commands.aetherhaven.time.dawn.desc");
            this.setPermissionGroup(null);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            applyHourMinute(context, world, store, 6, 0, "aetherhaven_commands_help.commands.aetherhaven.time.dawn.success");
        }
    }
}
