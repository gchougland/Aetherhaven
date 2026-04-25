package com.hexvane.aetherhaven.schedule;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/** Batch schedule application driven by {@link com.hexvane.aetherhaven.time.AetherhavenGameTimeCoordinatorSystem}. */
public final class VillagerScheduleService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    static final Query<EntityStore> SCHEDULE_ENTITY_QUERY =
        Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(), UUIDComponent.getComponentType());

    private VillagerScheduleService() {}

    /**
     * Applies weekly JSON schedules for all loaded matching NPCs in this world.
     *
     * @param timeJump if true (e.g. /time set), evaluate at current game time only — final segment/plot — and do not
     *     rely on per-minute throttle state for staleness.
     */
    public static void applyForWorld(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        boolean timeJump
    ) {
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        if (!cfg.isVillagerScheduleEnabled()) {
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        LocalDateTime gameTime = wtr.getGameDateTime();
        long epochMinute =
            gameTime.toLocalDate().toEpochDay() * 24L * 60L + gameTime.toLocalTime().toSecondOfDay() / 60L;
        long gameEpochHour = epochMinute / 60L;

        store.forEachChunk(SCHEDULE_ENTITY_QUERY, (chunk, commandBuffer) -> {
            int n = chunk.size();
            for (int index = 0; index < n; index++) {
                processEntity(
                    plugin,
                    cfg,
                    world,
                    store,
                    commandBuffer,
                    chunk,
                    index,
                    gameTime,
                    epochMinute,
                    gameEpochHour,
                    timeJump
                );
            }
        });
    }

    private static void processEntity(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull LocalDateTime gameTime,
        long epochMinute,
        long gameEpochHour,
        boolean timeJump
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        UUIDComponent uc = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (binding == null || npc == null || uc == null) {
            return;
        }
        if (TownVillagerBinding.isVisitorKind(binding.getKind())) {
            return;
        }
        String roleId = npc.getRoleName();
        if (roleId == null || roleId.isBlank()) {
            return;
        }

        VillagerScheduleTickState tickState = archetypeChunk.getComponent(index, VillagerScheduleTickState.getComponentType());
        if (tickState == null) {
            tickState = new VillagerScheduleTickState();
        }

        boolean newCalendarMinute = timeJump || tickState.getLastGameEpochMinute() != epochMinute;

        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleId.trim());
        VillagerScheduleDefinition def = plugin.getVillagerDefinitionCatalog()
            .effectiveSchedule(roleId.trim(), plugin.getVillagerScheduleRegistry());
        if (def == null) {
            if (newCalendarMinute) {
                tickState.setLastGameEpochMinute(epochMinute);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            }
            return;
        }
        String loc = VillagerScheduleResolver.activeLocationSymbol(def, gameTime);
        if (loc == null) {
            if (newCalendarMinute) {
                tickState.setLastGameEpochMinute(epochMinute);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            }
            return;
        }

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            if (newCalendarMinute) {
                tickState.setLastGameEpochMinute(epochMinute);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            }
            return;
        }

        VillagerScheduleResolveOutcome out = VillagerScheduleResolver.resolvePlot(town, binding, uc.getUuid(), loc, vdef);
        UUID targetPlot = out.plotId();
        if (targetPlot == null) {
            if (cfg.isVillagerScheduleDebugLog() && gameEpochHour != tickState.getLastUnresolvedDebugLogGameEpochHour()) {
                tickState.setLastUnresolvedDebugLogGameEpochHour(gameEpochHour);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
                String why =
                    VillagerScheduleResolver.describeSchedulePlotUnresolvedReason(town, binding, uc.getUuid(), loc, vdef);
                LOGGER.at(Level.INFO).log(
                    "[Aetherhaven schedule] unresolved plot — role=%s npc=%s segment=%s time=%s town=%s kind=%s jobPlot=%s preferredPlot=%s reason=%s (retrying)",
                    roleId,
                    uc.getUuid(),
                    loc,
                    gameTime,
                    town.getTownId(),
                    binding.getKind(),
                    binding.getJobPlotId(),
                    binding.getPreferredPlotId(),
                    why
                );
            }
            return;
        }

        UUID current = binding.getPreferredPlotId();
        boolean needsApply = timeJump || newCalendarMinute || current == null || !targetPlot.equals(current);
        if (!needsApply) {
            return;
        }

        String prevSegment = tickState.getLastAppliedScheduleSegment();
        boolean plotLocationChanged = current == null || !Objects.equals(targetPlot, current);
        boolean segmentLocationChanged = prevSegment.isEmpty() || !prevSegment.equals(loc);

        if (out.jobPlotIdToPersist() != null) {
            binding.setJobPlotId(out.jobPlotIdToPersist());
        }
        binding.setPreferredPlotId(targetPlot);
        commandBuffer.putComponent(ref, TownVillagerBinding.getComponentType(), binding);
        tickState.setLastGameEpochMinute(epochMinute);
        tickState.setLastAppliedScheduleSegment(loc);
        commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
        if (cfg.isVillagerScheduleDebugLog() && (plotLocationChanged || segmentLocationChanged)) {
            LOGGER.at(Level.INFO).log(
                "[Aetherhaven schedule] location change role=%s npc=%s time=%s segment: %s -> %s preferredPlot: %s -> %s",
                roleId,
                uc.getUuid(),
                gameTime,
                prevSegment.isEmpty() ? "(none)" : prevSegment,
                loc,
                current == null ? "(none)" : current,
                targetPlot
            );
        }
    }
}
