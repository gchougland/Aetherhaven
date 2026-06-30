package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerPoolService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AetherhavenGuildCommand extends AbstractCommandCollection {
    public AetherhavenGuildCommand() {
        super("guild", "aetherhaven_commands_help.commands.aetherhaven.guild.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new RespawnSubCommand());
        this.addSubCommand(new ClearGuardsSubCommand());
        this.addSubCommand(new StatusSubCommand());
    }

    private static final class RespawnSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        RespawnSubCommand() {
            super("respawn", "aetherhaven_commands_help.commands.aetherhaven.guild.respawn.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                ctx.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(ctx, world, store, ref, playerRef, false);
            if (!townRes.isOk()) {
                ctx.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            if (!town.isGuildHallActive()) {
                ctx.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.guild.notActive"));
                return;
            }
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            GuildHallAdventurerPoolService.ForceRespawnResult result =
                GuildHallAdventurerPoolService.forceRespawnAdventurers(world, plugin, town, tm, store, wtr);
            ctx.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.guild.respawn.ok")
                    .param("reclaimed", result.reclaimedCheckouts())
                    .param("despawned", result.despawnedAdventurers())
                    .param("slots", result.slotsRolled())
                    .param("spawned", result.spawned())
                    .param("available", result.poolAvailable())
            );
        }
    }

    private static final class ClearGuardsSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        ClearGuardsSubCommand() {
            super("clearguards", "aetherhaven_commands_help.commands.aetherhaven.guild.clearguards.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                ctx.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(ctx, world, store, ref, playerRef, false);
            if (!townRes.isOk()) {
                ctx.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            int despawned = GuildHallAdventurerPoolService.clearHiredGuardsInTown(world, plugin, town, tm, store);
            ctx.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.guild.clearguards.ok")
                    .param("count", despawned)
            );
        }
    }

    private static final class StatusSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        StatusSubCommand() {
            super("status", "aetherhaven_commands_help.commands.aetherhaven.guild.status.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                ctx.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(ctx, world, store, ref, playerRef, false);
            if (!townRes.isOk()) {
                ctx.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            TownsfolkExistenceService.PoolSummary summary = TownsfolkExistenceService.summarizePool(world, plugin);
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
            ctx.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.guild.status.ok")
                    .param("active", town.isGuildHallActive())
                    .param("epochDay", epochDay)
                    .param("lastMorning", town.getGuildHallLastMorningEpochDay() != null ? town.getGuildHallLastMorningEpochDay() : -1)
                    .param("filledSlots", town.getGuildHallAdventurerFilledSlots().size())
                    .param("tracked", town.getGuildHallAdventurerNpcIds().size())
                    .param("hiredGuards", town.getHiredGuardRecords().size())
                    .param("poolTotal", summary.total())
                    .param("poolGuards", summary.guards())
                    .param("poolAdventurers", summary.guildAdventurers())
                    .param("poolOther", summary.other())
            );
        }
    }
}
