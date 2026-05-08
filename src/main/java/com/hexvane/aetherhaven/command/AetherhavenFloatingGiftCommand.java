package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftSpawnSchedule;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftSpawnService;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class AetherhavenFloatingGiftCommand extends AbstractCommandCollection {
    public AetherhavenFloatingGiftCommand() {
        super("floatinggift", "aetherhaven_commands_help.commands.aetherhaven.floatinggift.desc");
        this.addSubCommand(new SpawnSubCommand());
        this.addSubCommand(new NextSubCommand());
    }

    private static final class SpawnSubCommand extends AbstractPlayerCommand {
        SpawnSubCommand() {
            super("spawn", "aetherhaven_commands_help.commands.aetherhaven.floatinggift.spawn.desc");
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
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t == null) {
                return;
            }
            Vector3d p = t.getPosition();
            if (!FloatingGiftSpawnService.isModelAssetRegistered()) {
                playerRef.sendMessage(
                    Message.raw(
                        "Floating gift model not loaded (Floating_Gift). Reload assets or check Server/Models/Floating_Gift.json and Items/Aetherhaven/Floating_Gift/*."
                    )
                );
                return;
            }
            Ref<EntityStore> gift = FloatingGiftSpawnService.spawnAroundTarget(store, p.clone(), p.clone());
            if (gift != null && gift.isValid()) {
                playerRef.sendMessage(Message.raw("Spawned floating gift."));
            } else {
                playerRef.sendMessage(Message.raw("Failed to spawn floating gift (see server log)."));
            }
        }
    }

    private static final class NextSubCommand extends AbstractPlayerCommand {
        NextSubCommand() {
            super("next", "aetherhaven_commands_help.commands.aetherhaven.floatinggift.next.desc");
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
            AetherhavenPluginConfig cfg = plugin.getConfig().get();
            if (!cfg.isFloatingGiftEnabled()) {
                playerRef.sendMessage(Message.raw("Floating gift natural spawns are disabled (FloatingGift.Enabled)."));
                return;
            }
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            TimeResource tr = store.getResource(TimeResource.getResourceType());
            if (wtr == null || tr == null) {
                playerRef.sendMessage(Message.raw("World time resources not available."));
                return;
            }
            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp == null) {
                return;
            }
            UUID playerUuid = uuidComp.getUuid();
            Instant now = wtr.getGameTime();
            Instant scheduled = FloatingGiftSpawnSchedule.ensureAndGet(playerUuid, now, cfg);
            Duration calendarWait = FloatingGiftSpawnSchedule.calendarWait(now, scheduled);
            int cycle = Math.max(1, world.getDaytimeDurationSeconds() + world.getNighttimeDurationSeconds());
            double approxWall = FloatingGiftSpawnSchedule.approximateWallClockSeconds(calendarWait, cycle, tr.getTimeDilationModifier());
            int active = FloatingGiftSystem.countActiveGifts(store);
            int cap = cfg.getFloatingGiftMaxActivePerWorld();
            StringBuilder sb = new StringBuilder(256);
            if (active >= cap) {
                sb.append("World is at max floating gifts (")
                    .append(active)
                    .append("/")
                    .append(cap)
                    .append("); natural spawns wait until one despawns. ");
            }
            double igDays = calendarWait.toSeconds() / 86400.0 + calendarWait.getNano() * 1e-9 / 86400.0;
            if (calendarWait.isNegative() || calendarWait.isZero()) {
                sb.append(
                    "Your queue is overdue on the game's calendar clock; next scheduler check (~2s) will attempt a spawn if a slot exists."
                );
            } else {
                long totalMin = calendarWait.toMinutes();
                long calH = totalMin / 60;
                long calM = totalMin % 60;
                sb.append(
                    String.format(
                        Locale.US,
                        "Next spawn not before ~%.4f calendar in-game-days (~%dh %dm on game calendar clock). Rough real wait ~%.0fs (day cycle length %ds, time dilation %.2fx). Scheduled instant: %s.",
                        igDays,
                        calH,
                        calM,
                        approxWall,
                        cycle,
                        tr.getTimeDilationModifier(),
                        scheduled
                    )
                );
            }
            playerRef.sendMessage(Message.raw(sb.toString()));
        }
    }
}
