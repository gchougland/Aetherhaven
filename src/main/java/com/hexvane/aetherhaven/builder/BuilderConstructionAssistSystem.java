package com.hexvane.aetherhaven.builder;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.assembly.AssemblyPassiveBoostRegistry;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPhase;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.autonomy.VillagerDoorUtil;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.NavState;
import org.joml.Vector3d;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resident builder: during work hours, travels to assembling town plots and applies a passive assembly boost while
 * working with hammer visuals.
 */
public final class BuilderConstructionAssistSystem extends EntityTickingSystem<EntityStore> {
    private static final long SWING_INTERVAL_NS = 900_000_000L;
    private static final int PLOT_RESCAN_INTERVAL_TICKS = 40;
    private static final int DOOR_UNJAM_STUCK_TICKS = 30;

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public BuilderConstructionAssistSystem(@Nonnull AetherhavenPlugin plugin) {
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
        return Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (npc == null || binding == null) {
            return;
        }
        UUIDComponent fightUuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (fightUuid != null
            && com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex.isLivingFighter(fightUuid.getUuid())) {
            return;
        }
        if (!AetherhavenConstants.NPC_BUILDER.equals(npc.getRoleName())) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }

        BuilderConstructionAssistState assist =
            archetypeChunk.getComponent(index, BuilderConstructionAssistState.getComponentType());
        if (assist == null) {
            assist = new BuilderConstructionAssistState();
            commandBuffer.addComponent(ref, BuilderConstructionAssistState.getComponentType(), assist);
        }
        if (!TownVillagerBinding.KIND_BUILDER.equals(binding.getKind())) {
            clearAssist(ref, store, commandBuffer, npc, assist, null);
            return;
        }

        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null || !town.hasQuestCompleted(AetherhavenConstants.QUEST_BUILDERS_HUT)) {
            clearAssist(ref, store, commandBuffer, npc, assist, null);
            return;
        }
        if (!isOnWorkSchedule(store, npc)) {
            clearAssist(ref, store, commandBuffer, npc, assist, null);
            return;
        }

        PlotInstance targetPlot = resolveAssemblingTargetPlot(world, town, ref, store, assist);
        if (targetPlot == null) {
            clearAssist(ref, store, commandBuffer, npc, assist, null);
            return;
        }

        assist.incrementTicksSincePlotRescan();

        double tx = targetPlot.getSignX() + 0.5;
        double ty = targetPlot.getSignY() + 0.02;
        double tz = targetPlot.getSignZ() + 0.5;
        UUID plotId = targetPlot.getPlotId();

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = tc.getPosition();

        if (!plotId.equals(assist.getTargetPlotId())) {
            assist.setTargetPlot(plotId, tx, ty, tz);
            assist.setPhase(BuilderConstructionAssistState.PHASE_TRAVEL);
        }

        if (assist.isWithinAssistRange(pos.x, pos.y, pos.z)) {
            int prevPhase = assist.getPhase();
            assist.setPhase(BuilderConstructionAssistState.PHASE_ASSIST);
            npc.setLeashPoint(new Vector3d(tx, ty, tz));
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            BuilderConstructionVisuals.beginAssist(ref, store, commandBuffer, plugin, npc);
            if (prevPhase != BuilderConstructionAssistState.PHASE_ASSIST || !assist.isBoostAppliedForTarget()) {
                AssemblyPassiveBoostRegistry.setBoost(world, plotId, BuilderConstructionAssistState.BUILDER_PASSIVE_BOOST);
                PlotAssemblyService.snapPassiveDueForBoost(
                    world, plugin, plotId, BuilderConstructionAssistState.BUILDER_PASSIVE_BOOST
                );
                assist.setBoostAppliedForTarget(true);
            }
            maybeSwingHammer(ref, store, commandBuffer, npc, assist);
            if (prevPhase != BuilderConstructionAssistState.PHASE_ASSIST) {
                applyAutonomySeek(ref, npc, commandBuffer);
            }
            commandBuffer.putComponent(ref, BuilderConstructionAssistState.getComponentType(), assist);
            return;
        }

        assist.setPhase(BuilderConstructionAssistState.PHASE_TRAVEL);
        npc.setLeashPoint(new Vector3d(tx, ty, tz));
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        tickAssistTravel(world, npc, pos, npc.getLeashPoint(), assist);
        applyAutonomySeek(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, BuilderConstructionAssistState.getComponentType(), assist);
    }

    private static void tickAssistTravel(
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull Vector3d pos,
        @Nonnull Vector3d leash,
        @Nonnull BuilderConstructionAssistState assist
    ) {
        VillagerDoorUtil.tryOpenDoorsTowardLeash(world, pos, leash, null);
        NavState nav = NavState.INIT;
        if (npc.getRole() != null) {
            MotionController mc = npc.getRole().getActiveMotionController();
            if (mc != null) {
                nav = mc.getNavState();
            }
        }
        if (nav == NavState.BLOCKED || nav == NavState.DEFER) {
            assist.incrementTravelStuckTicks();
            if (assist.getTravelStuckTicks() >= DOOR_UNJAM_STUCK_TICKS) {
                VillagerDoorUtil.tryUnjamDoorsAlongPath(world, pos, leash);
            }
        } else if (nav == NavState.PROGRESSING || nav == NavState.INIT) {
            assist.resetTravelStuckTicks();
        }
    }

    public static boolean shouldSkipAutonomy(@Nullable BuilderConstructionAssistState assist) {
        return assist != null && assist.isActive();
    }

    private static void applyAutonomySeek(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null || !VillagerAutonomySystemSupports.supportsAutonomyPoiRoleState(npc)) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private void clearAssist(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull BuilderConstructionAssistState assist,
        @Nullable UUID plotId
    ) {
        World world = store.getExternalData().getWorld();
        UUID prev = assist.getTargetPlotId();
        if (prev != null) {
            AssemblyPassiveBoostRegistry.setBoost(world, prev, 0);
        }
        if (assist.getPhase() != BuilderConstructionAssistState.PHASE_OFF) {
            BuilderConstructionVisuals.endAssist(ref, store, commandBuffer, npc);
        }
        assist.clearTarget();
        VillagerAutonomySystem.clearAutonomySeekState(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, BuilderConstructionAssistState.getComponentType(), assist);
    }

    private void maybeSwingHammer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull BuilderConstructionAssistState assist
    ) {
        long now = System.nanoTime();
        if (now - assist.getLastSwingNs() < SWING_INTERVAL_NS) {
            return;
        }
        assist.setLastSwingNs(now);
        BuilderConstructionVisuals.swingHammer(ref, store, commandBuffer, npc);
    }

    private boolean isOnWorkSchedule(@Nonnull Store<EntityStore> store, @Nonnull NPCEntity npc) {
        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(AetherhavenConstants.NPC_BUILDER);
        if (vdef == null) {
            return false;
        }
        VillagerScheduleDefinition schedule =
            plugin.getVillagerDefinitionCatalog().effectiveSchedule(AetherhavenConstants.NPC_BUILDER, plugin.getVillagerScheduleRegistry());
        if (schedule == null) {
            return false;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return false;
        }
        LocalDateTime gameTime = wtr.getGameDateTime();
        String loc = VillagerScheduleResolver.activeLocationSymbol(schedule, gameTime);
        return VillagerScheduleResolver.LOC_WORK.equals(loc);
    }

    @Nullable
    private PlotInstance resolveAssemblingTargetPlot(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull BuilderConstructionAssistState assist
    ) {
        UUID cachedId = assist.getTargetPlotId();
        if (cachedId != null && assist.getTicksSincePlotRescan() < PLOT_RESCAN_INTERVAL_TICKS) {
            PlotInstance cached = town.findPlotById(cachedId);
            if (cached != null
                && cached.getState() == PlotInstanceState.ASSEMBLING
                && AssemblyWorldRegistry.get(world, cachedId) != null) {
                return cached;
            }
        }
        assist.resetTicksSincePlotRescan();
        return findBestAssemblingPlot(world, town, ref, store);
    }

    @Nullable
    private PlotInstance findBestAssemblingPlot(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return null;
        }
        Vector3d pos = tc.getPosition();
        PlotInstance bestClearing = null;
        PlotInstance bestOther = null;
        double bestClearingDistSq = Double.MAX_VALUE;
        double bestOtherDistSq = Double.MAX_VALUE;
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            UUID plotId = plot.getPlotId();
            if (AssemblyWorldRegistry.get(world, plotId) == null) {
                continue;
            }
            double dx = (plot.getSignX() + 0.5) - pos.x;
            double dz = (plot.getSignZ() + 0.5) - pos.z;
            double distSq = dx * dx + dz * dz;
            if (AssemblyWorldRegistry.phase(world, plotId) == PlotAssemblyPhase.CLEARING) {
                if (distSq < bestClearingDistSq) {
                    bestClearingDistSq = distSq;
                    bestClearing = plot;
                }
            } else if (distSq < bestOtherDistSq) {
                bestOtherDistSq = distSq;
                bestOther = plot;
            }
        }
        return bestClearing != null ? bestClearing : bestOther;
    }

    /** Package-private bridge so builder system can call autonomy helpers without circular dependency on private methods. */
    static final class VillagerAutonomySystemSupports {
        private VillagerAutonomySystemSupports() {}

        static boolean supportsAutonomyPoiRoleState(@Nonnull NPCEntity npc) {
            return com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem.supportsAutonomyPoiRoleState(npc);
        }
    }
}
