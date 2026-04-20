package com.hexvane.aetherhaven.schedule;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Applies weekly JSON schedules by updating {@link TownVillagerBinding#setPreferredPlotId}. Runs before
 * {@link com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem}. Inn visitors are excluded.
 *
 * <p><b>Server logs only</b> — nothing here is sent to players. When {@code VillagerScheduleDebugLog} is enabled
 * in {@code config.json}, diagnostics are emitted at INFO. Unresolved-plot lines are throttled to at most once per
 * in-game hour per NPC so repeated retries do not flood the console.
 */
public final class VillagerScheduleSystem extends EntityTickingSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public VillagerScheduleSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TownVillagerBinding.getComponentType(),
            NPCEntity.getComponentType(),
            UUIDComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
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
        long epochMinute = gameTime.toLocalDate().toEpochDay() * 24L * 60L + gameTime.toLocalTime().toSecondOfDay() / 60L;
        long gameEpochHour = epochMinute / 60L;

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

        VillagerScheduleDefinition def = plugin.getVillagerScheduleRegistry().getOrLoad(roleId);
        if (def == null) {
            if (tickState.getLastGameEpochMinute() != epochMinute) {
                tickState.setLastGameEpochMinute(epochMinute);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            }
            return;
        }
        String loc = VillagerScheduleResolver.activeLocationSymbol(def, gameTime);
        if (loc == null) {
            if (tickState.getLastGameEpochMinute() != epochMinute) {
                tickState.setLastGameEpochMinute(epochMinute);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            }
            return;
        }

        var world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            if (tickState.getLastGameEpochMinute() != epochMinute) {
                tickState.setLastGameEpochMinute(epochMinute);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            }
            return;
        }

        boolean newCalendarMinute = tickState.getLastGameEpochMinute() != epochMinute;
        VillagerScheduleResolveOutcome out = VillagerScheduleResolver.resolvePlot(town, binding, uc.getUuid(), loc);
        UUID targetPlot = out.plotId();
        if (targetPlot == null) {
            if (cfg.isVillagerScheduleDebugLog() && gameEpochHour != tickState.getLastUnresolvedDebugLogGameEpochHour()) {
                tickState.setLastUnresolvedDebugLogGameEpochHour(gameEpochHour);
                commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
                String why =
                    VillagerScheduleResolver.describeSchedulePlotUnresolvedReason(town, binding, uc.getUuid(), loc);
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
            // Do not advance LastGameEpochMinute — resolution can fail if town/plot data loads late; retry every tick.
            return;
        }

        UUID current = binding.getPreferredPlotId();
        boolean needsApply = newCalendarMinute || current == null || !targetPlot.equals(current);
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
