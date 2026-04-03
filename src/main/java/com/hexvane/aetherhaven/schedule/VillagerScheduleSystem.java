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
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Applies weekly JSON schedules by updating {@link TownVillagerBinding#setPreferredPlotId}. Runs before
 * {@link com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem}. Inn visitors are excluded.
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
        if (tickState.getLastGameEpochMinute() == epochMinute) {
            return;
        }

        VillagerScheduleDefinition def = plugin.getVillagerScheduleRegistry().getOrLoad(roleId);
        if (def == null) {
            tickState.setLastGameEpochMinute(epochMinute);
            commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            return;
        }
        String loc = VillagerScheduleResolver.activeLocationSymbol(def, gameTime);
        if (loc == null) {
            tickState.setLastGameEpochMinute(epochMinute);
            commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            return;
        }

        var world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            tickState.setLastGameEpochMinute(epochMinute);
            commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);
            return;
        }

        tickState.setLastGameEpochMinute(epochMinute);
        commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), tickState);

        VillagerScheduleResolveOutcome out = VillagerScheduleResolver.resolvePlot(town, binding, uc.getUuid(), loc);
        UUID targetPlot = out.plotId();
        if (targetPlot == null) {
            if (cfg.isVillagerScheduleDebugLog()) {
                LOGGER.atInfo().log(
                    "Schedule skip %s segment=%s (unresolved)",
                    roleId,
                    loc
                );
            }
            return;
        }
        if (out.jobPlotIdToPersist() != null) {
            binding.setJobPlotId(out.jobPlotIdToPersist());
        }
        UUID current = binding.getPreferredPlotId();
        if (targetPlot.equals(current)) {
            return;
        }
        binding.setPreferredPlotId(targetPlot);
        commandBuffer.putComponent(ref, TownVillagerBinding.getComponentType(), binding);
        if (cfg.isVillagerScheduleDebugLog()) {
            LOGGER.atInfo().log("Schedule %s -> plot %s (%s)", roleId, targetPlot, loc);
        }
    }
}
