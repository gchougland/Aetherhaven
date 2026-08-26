package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hypixel.hytale.server.npc.role.support.DebugSupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavGraphService;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavTravelSupport;
import com.hexvane.aetherhaven.autonomy.pathnav.PathNavTravelWaypoints;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.festival.FestivalAttendanceService;
import com.hexvane.aetherhaven.festival.FestivalSpotService;
import com.hexvane.aetherhaven.festival.market.MarketIds;
import com.hexvane.aetherhaven.festival.market.MarketJudgeDirectorSystem;
import com.hexvane.aetherhaven.festival.market.MarketSession;
import com.hexvane.aetherhaven.festival.market.MarketSessionIndex;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.entity.EntityRotationUtil;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistState;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistSystem;
import com.hexvane.aetherhaven.clown.ClownCheerAssistState;
import com.hexvane.aetherhaven.clown.ClownCheerAssistSystem;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftSeekState;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftSeekSystem;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekState;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekSystem;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.restaurant.RestaurantBenefitService;
import com.hexvane.aetherhaven.poi.PoiEffectTable;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.shopspot.NpcShopSpotBuyerService;
import com.hexvane.aetherhaven.shopspot.ShopSpotOpenService;
import com.hexvane.aetherhaven.shopspot.ShopSpotPurchaseService;
import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.questboard.QuestBoardPoiEnsure;
import com.hexvane.aetherhaven.questboard.QuestBoardPostVisitQueue;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * POI autonomy: idle pick + leash target, travel via vanilla {@code Seek} (role JSON), use + visuals.
 * Role {@code StateTransitions} clear Status when leaving {@link AetherhavenConstants#NPC_STATE_AUTONOMY_POI} for Idle.
 */
public final class VillagerAutonomySystem extends EntityTickingSystem<EntityStore> {
    /** Keep in sync with {@code Component_Instruction_Aetherhaven_Autonomy_Poi_Travel} Leash {@code Range: 0.5}. */
    private static final double ARRIVE_HORIZONTAL_SQ = 0.5 * 0.5;
    /** Tighter leash arrival for SIT/SLEEP when there is no interaction target (leash is the POI block). */
    private static final double MOUNT_ARRIVE_HORIZONTAL_SQ = 0.88 * 0.88;
    /**
     * Horizontal distance to the entry leash (interaction target) that counts as "reached the POI entry". Seek often
     * stops 1–3 blocks short of the leash; we do not require walking to the bed after that when an interaction target
     * is set.
     */
    private static final double POI_ENTRY_ARRIVE_HORIZONTAL_SQ = 3.5 * 3.5;
    /**
     * {@link NavState#BLOCKED} and {@link NavState#DEFER}: DEFER is “delay path recompute” in vanilla Seek — it can
     * persist while an NPC is wedged against geometry (wall bug), and must not reset stuck ticks like PROGRESSING.
     */
    private static final int BLOCKED_FAIL_TICKS = 100;
    private static final int DOOR_UNJAM_STUCK_TICKS = 30;
    private static final int MOUNT_UNREACHABLE_FAIL_TICKS = 120;
    /** {@link #beginTravelToPoi} sets {@link VillagerAutonomyState#setNextDecisionEpochMs} to now + this; must be checked in {@link #tickTravel} or NPCs can follow Nav:PROGRESSING forever. */
    private static final long TRAVEL_PHASE_MAX_MS = 180_000L;
    /** How close counts as standing on a festival spot (the villager just needs to be in the right part of the square). */
    private static final double FESTIVAL_SPOT_ARRIVED_DIST_SQ = 2.5 * 2.5;
    /** How long a villager holds their festival spot before the next idle check. */
    private static final long FESTIVAL_SPOT_HOLD_MS = 5_000L;

    static void onUnloadSafetyDismount(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable NPCEntity npc,
        @Nullable VillagerAutonomyState autonomy,
        @Nullable VillagerNeeds needs,
        @Nonnull PoiRegistry reg,
        long now
    ) {
        if (autonomy != null && autonomy.getPhase() == VillagerAutonomyState.PHASE_USE) {
            abortActivePoiUseAndDismount(ref, store, commandBuffer, autonomy, needs, reg, true);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
            autonomy.setTargetPoiUuid(null);
            autonomy.setPathFailureReason("");
            autonomy.setTravelStuckTicks(0);
            autonomy.clearPendingDoorClose();
            autonomy.setNextDecisionEpochMs(now + 2500L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            if (npc != null) {
                clearAutonomyRoleState(ref, npc, commandBuffer);
            }
        } else {
            releaseBlockMountAndSnapToGround(ref, store, commandBuffer);
        }
    }

    /**
     * Interrupts {@link VillagerAutonomyState#PHASE_USE} (bed/chair/etc.), releases block mounts, and snaps to standable
     * ground. Safe from the world thread ({@code commandBuffer} null) or a system tick.
     */
    static void abortActivePoiUseAndDismount(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nullable VillagerAutonomyState autonomy,
        @Nullable VillagerNeeds needs,
        @Nullable PoiRegistry reg,
        boolean applyNeedEffects
    ) {
        if (autonomy != null && autonomy.getPhase() == VillagerAutonomyState.PHASE_USE) {
            UUID poiId = autonomy.getTargetPoiUuid();
            PoiEntry poi = poiId != null && reg != null ? reg.get(poiId) : null;
            if (poi != null) {
                PoiAutonomyVisuals.cleanupAfterPoiUse(ref, store, commandBuffer, poi, false);
                if (applyNeedEffects && needs != null) {
                    PlotRestaurantState restaurantState = RestaurantBenefitService.restaurantStateForPoi(null, poi);
                    PoiEffectTable.applyUseComplete(needs, poi, restaurantState);
                    if (commandBuffer != null) {
                        commandBuffer.putComponent(ref, VillagerNeeds.getComponentType(), needs);
                    } else {
                        store.putComponent(ref, VillagerNeeds.getComponentType(), needs);
                    }
                }
            } else {
                PoiAutonomyVisuals.forceAbortUseVisuals(ref, store, commandBuffer);
            }
        }
        releaseBlockMountAndSnapToGround(ref, store, commandBuffer);
    }

    static void releaseBlockMountAndSnapToGround(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (store.getComponent(ref, MountedComponent.getComponentType()) != null) {
            BlockMountRelease.release(ref, store, commandBuffer);
        }
        VillagerBlockUtil.snapNpcToStandY(ref, store, commandBuffer);
    }

    /**
     * Clears bed/chair USE and mounts when another system holds the villager still (snowball fight, etc.).
     * Autonomy itself is skipped afterward, so this must run on the early-out path.
     */
    private static void forceDismountForExternalHold(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable World world,
        @Nullable VillagerAutonomyState autonomy,
        @Nullable VillagerNeeds needs,
        @Nullable NPCEntity npc
    ) {
        boolean usingPoi = autonomy != null && autonomy.getPhase() == VillagerAutonomyState.PHASE_USE;
        boolean mounted = store.getComponent(ref, MountedComponent.getComponentType()) != null
            || commandBuffer.getComponent(ref, MountedComponent.getComponentType()) != null;
        if (!usingPoi && !mounted) {
            return;
        }
        PoiRegistry reg = null;
        if (usingPoi && world != null) {
            AetherhavenPlugin pluginInstance = AetherhavenPlugin.get();
            if (pluginInstance != null) {
                reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, pluginInstance);
            }
        }
        abortActivePoiUseAndDismount(ref, store, commandBuffer, autonomy, needs, reg, false);
        if (autonomy != null && autonomy.getPhase() == VillagerAutonomyState.PHASE_USE) {
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
            autonomy.clearTravelAndPoiState();
            autonomy.setFillingHunger(false);
            autonomy.setFillingEnergy(false);
            autonomy.setFillingFun(false);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        }
        if (npc != null) {
            clearAutonomyRoleState(ref, npc, commandBuffer);
        }
    }

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public VillagerAutonomySystem(@Nonnull AetherhavenPlugin plugin) {
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
            VillagerNeeds.getComponentType(),
            NPCEntity.getComponentType()
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
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null || npc.getRole() == null) {
            return;
        }

        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        VillagerNeeds needs = archetypeChunk.getComponent(index, VillagerNeeds.getComponentType());
        if (binding == null || needs == null) {
            return;
        }
        if (skipsPoiAutonomy(binding, npc)) {
            return;
        }

        BuilderConstructionAssistState assist =
            archetypeChunk.getComponent(index, BuilderConstructionAssistState.getComponentType());
        if (BuilderConstructionAssistSystem.shouldSkipAutonomy(assist)) {
            return;
        }
        ClownCheerAssistState cheer =
            archetypeChunk.getComponent(index, ClownCheerAssistState.getComponentType());
        if (ClownCheerAssistSystem.shouldSkipAutonomy(cheer)) {
            return;
        }
        UUIDComponent snowballUuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (snowballUuid != null
            && com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex.isLivingFighter(snowballUuid.getUuid())) {
            // Autonomy is paused for the fight, but bed/chair mounts must still be cleared.
            VillagerAutonomyState snowballAutonomy =
                archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());
            forceDismountForExternalHold(
                ref,
                store,
                commandBuffer,
                store.getExternalData().getWorld(),
                snowballAutonomy,
                needs,
                npc
            );
            return;
        }
        VillagerFollowPlayerState follow = store.getComponent(ref, VillagerFollowPlayerState.getComponentType());
        if (VillagerFollowPlayerSystem.shouldSkipAutonomy(follow)) {
            return;
        }
        if (WintertideGiftSeekState.isRegistered()) {
            WintertideGiftSeekState seek = store.getComponent(ref, WintertideGiftSeekState.getComponentType());
            if (WintertideGiftSeekSystem.shouldSkipAutonomy(seek)) {
                return;
            }
        }
        if (PlayerBirthdayGiftSeekState.isRegistered()) {
            PlayerBirthdayGiftSeekState birthdaySeek =
                store.getComponent(ref, PlayerBirthdayGiftSeekState.getComponentType());
            if (PlayerBirthdayGiftSeekSystem.shouldSkipAutonomy(birthdaySeek)) {
                return;
            }
        }

        long now = resolveNowMs(store);
        VillagerAutonomyState autonomy = archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());
        if (autonomy == null) {
            autonomy = VillagerAutonomyState.fresh(now);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            applyAutonomyDebugOverlay(ref, store, commandBuffer, npc, needs, autonomy);
            return;
        }

        applyAutonomyDebugOverlay(ref, store, commandBuffer, npc, needs, autonomy);

        MountedComponent mounted = store.getComponent(ref, MountedComponent.getComponentType());
        if (mounted != null && mounted.getControllerType() == MountController.BlockMount) {
            int phase = autonomy.getPhase();
            if (phase == VillagerAutonomyState.PHASE_IDLE || phase == VillagerAutonomyState.PHASE_TRAVEL) {
                releaseBlockMountAndSnapToGround(ref, store, commandBuffer);
            }
        }

        String stateName = NpcSupportUtil.stateName(store, ref);
        if (stateName.contains("Interaction")) {
            return;
        }

        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord townRecord = tm.getTown(binding.getTownId());
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        List<PoiEntry> pois = reg.listByTown(binding.getTownId());
        List<PoiEntry> poisForScoring = filterPoisForAutonomyScoring(townRecord, pois);

        switch (autonomy.getPhase()) {
            case VillagerAutonomyState.PHASE_USE ->
                tickUse(ref, store, commandBuffer, npc, reg, needs, autonomy, now, townRecord, binding, world);
            case VillagerAutonomyState.PHASE_TRAVEL ->
                tickTravel(ref, store, commandBuffer, npc, reg, needs, autonomy, now, townRecord);
            default ->
                tickIdle(
                    ref,
                    store,
                    commandBuffer,
                    world,
                    npc,
                    reg,
                    poisForScoring,
                    needs,
                    binding,
                    autonomy,
                    now,
                    this.plugin,
                    townRecord
                );
        }
    }

    @Nonnull
    private static List<PoiEntry> filterPoisForAutonomyScoring(@Nullable TownRecord town, @Nonnull List<PoiEntry> pois) {
        if (town == null) {
            List<PoiEntry> out = new ArrayList<>();
            for (PoiEntry e : pois) {
                if (!e.getTags().contains(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL)
                    && !e.getTags().contains(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL)) {
                    out.add(e);
                }
            }
            return out;
        }
        UUID allow = null;
        String gid = town.getFeastGatherPoiId();
        if (gid != null && !gid.isBlank()) {
            try {
                allow = UUID.fromString(gid.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<PoiEntry> out = new ArrayList<>();
        for (PoiEntry e : pois) {
            // Festival spots are reserved for one villager kind each and are handed out directly, not scored.
            if (e.getTags().contains(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL)) {
                continue;
            }
            if (e.getTags().contains(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL)) {
                if (allow != null && allow.equals(e.getId())) {
                    out.add(e);
                }
            } else {
                out.add(e);
            }
        }
        return out;
    }

    private static void tickIdle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord
    ) {
        boolean daytime = ShopSpotOpenService.isGameDay(store);
        if (!daytime) {
            autonomy.setFillingHunger(false);
            autonomy.setFillingFun(false);
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        // A festival spot outranks everything else: the villager is expected on the square until the festival ends.
        if (tryHoldFestivalSpot(ref, store, commandBuffer, world, npc, binding, autonomy, townRecord, now, plugin, tc)) {
            return;
        }
        // Feast / dawn quest-board posts temporarily suspend schedule commute and hunger meals.
        if (tryBeginFeastGatherTravel(ref, store, commandBuffer, world, npc, reg, binding, autonomy, townRecord, now, plugin, tc)) {
            return;
        }
        if (tryBeginQuestBoardPostTravel(
            ref, store, commandBuffer, world, npc, binding, autonomy, townRecord, now, plugin, tc
        )) {
            return;
        }
        // Urgent need breaks bypass nextDecisionEpochMs (same as feast gather) so work shifts interrupt immediately.
        if (tryBeginUrgentNeedBreak(
            ref, store, commandBuffer, world, npc, reg, pois, needs, binding, autonomy, now, plugin, townRecord, tc, daytime
        )) {
            return;
        }
        // Do not commute back to the schedule plot while seeking food, rest, or fun.
        if (!hasResolvableUrgentNeed(ref, store, pois, needs, binding, autonomy, plugin, townRecord, tc, daytime)
            && SchedulePlotCommute.tryBeginIfOffSchedulePlot(
                ref, store, commandBuffer, world, npc, binding, autonomy, now, plugin
            )) {
            return;
        }
        if (now < autonomy.getNextDecisionEpochMs()) {
            return;
        }
        Map<String, Integer> cellOcc = PoiOccupancy.cellOccupancyForTown(world, binding.getTownId(), store, reg);
        double npcX = tc != null ? tc.getPosition().x : Double.NaN;
        double npcZ = tc != null ? tc.getPosition().z : Double.NaN;
        VillagerScheduleTickState schedTick = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
        String scheduleSeg = schedTick != null ? schedTick.getLastAppliedScheduleSegment() : null;
        boolean townHasRestaurant =
            townRecord != null
                && RestaurantBenefitService.townHasCompleteRestaurant(townRecord, plugin.getConstructionCatalog());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        Set<UUID> breakPlotAllowlist =
            townRecord != null && uc != null
                ? PoiScoring.resolveUrgentBreakPlotAllowlist(
                    needs,
                    autonomy.isFillingHunger(),
                    autonomy.isFillingEnergy(),
                    autonomy.isFillingFun(),
                    daytime,
                    townRecord,
                    uc.getUuid(),
                    plugin.getConstructionCatalog()
                )
                : null;
        PoiEntry pick =
            PoiScoring.pickBest(
                pois,
                needs,
                binding,
                cellOcc,
                npcX,
                npcZ,
                scheduleSeg,
                townHasRestaurant,
                autonomy.isFillingHunger(),
                autonomy.isFillingEnergy(),
                autonomy.isFillingFun(),
                daytime,
                autonomy.getLastUsedPoiUuid(),
                breakPlotAllowlist
            );
        if (pick == null) {
            autonomy.setNextDecisionEpochMs(now + 4000L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            return;
        }
        if (!PoiOccupancy.tryClaimStand(cellOcc, pick)) {
            autonomy.setNextDecisionEpochMs(now + 1500L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            return;
        }
        if (PoiScoring.isEatPoi(pick) && PoiScoring.needsHungerBreak(needs, true, daytime)) {
            autonomy.setFillingHunger(true);
        } else if (PoiScoring.isRestPoi(pick) && PoiScoring.needsEnergyBreak(needs, true)) {
            autonomy.setFillingEnergy(true);
        } else if (PoiScoring.isFunPoi(pick) && PoiScoring.needsFunBreak(needs, true, daytime)) {
            autonomy.setFillingFun(true);
        }
        beginTravelToPoi(ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, binding.getTownId(), tc, pick);
    }

    /**
     * While a fill-hunger session is active (or hunger is below half), pick the next eat POI and travel immediately.
     * Daytime only — nights are for sleep.
     */
    private static boolean tryBeginNextHungerMeal(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nullable TransformComponent tc,
        boolean daytime
    ) {
        if (!daytime) {
            autonomy.setFillingHunger(false);
            return false;
        }
        if (!PoiScoring.isHungerNotFull(needs)) {
            autonomy.setFillingHunger(false);
            return false;
        }
        if (!PoiScoring.needsHungerBreak(needs, autonomy.isFillingHunger(), daytime)) {
            return false;
        }
        Map<String, Integer> cellOcc = PoiOccupancy.cellOccupancyForTown(world, binding.getTownId(), store, reg);
        double npcX = tc != null ? tc.getPosition().x : Double.NaN;
        double npcZ = tc != null ? tc.getPosition().z : Double.NaN;
        VillagerScheduleTickState schedTick = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
        String scheduleSeg = schedTick != null ? schedTick.getLastAppliedScheduleSegment() : null;
        boolean townHasRestaurant =
            townRecord != null
                && RestaurantBenefitService.townHasCompleteRestaurant(townRecord, plugin.getConstructionCatalog());
        PoiEntry pick =
            PoiScoring.pickBest(
                pois, needs, binding, cellOcc, npcX, npcZ, scheduleSeg, townHasRestaurant, true, daytime
            );
        if (pick == null || !PoiScoring.isEatPoi(pick)) {
            autonomy.setFillingHunger(false);
            return false;
        }
        if (!PoiOccupancy.tryClaimStand(cellOcc, pick)) {
            return false;
        }
        autonomy.setFillingHunger(true);
        beginTravelToPoi(ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, binding.getTownId(), tc, pick);
        return true;
    }

    static boolean tryBeginUrgentNeedBreak(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nullable TransformComponent tc,
        boolean daytime
    ) {
        PoiScoring.UrgentNeedKind kind =
            resolveUrgentNeedKind(ref, store, pois, needs, binding, autonomy, plugin, townRecord, tc, daytime);
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case HUNGER ->
                tryBeginNextHungerMeal(
                    ref,
                    store,
                    commandBuffer,
                    world,
                    npc,
                    reg,
                    pois,
                    needs,
                    binding,
                    autonomy,
                    now,
                    plugin,
                    townRecord,
                    tc,
                    daytime
                );
            case ENERGY ->
                tryBeginNextEnergyRest(
                    ref,
                    store,
                    commandBuffer,
                    world,
                    npc,
                    pois,
                    needs,
                    binding,
                    autonomy,
                    now,
                    plugin,
                    townRecord,
                    tc
                );
            case FUN ->
                tryBeginNextFunBreak(
                    ref,
                    store,
                    commandBuffer,
                    world,
                    npc,
                    pois,
                    needs,
                    binding,
                    autonomy,
                    now,
                    plugin,
                    townRecord,
                    tc,
                    daytime
                );
        };
    }

    @Nullable
    private static PoiScoring.UrgentNeedKind resolveUrgentNeedKind(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nullable TransformComponent tc,
        boolean daytime
    ) {
        if (townRecord == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        Map<String, Integer> cellOcc = PoiOccupancy.cellOccupancyForTown(world, binding.getTownId(), store, reg);
        double npcX = tc != null ? tc.getPosition().x : Double.NaN;
        double npcZ = tc != null ? tc.getPosition().z : Double.NaN;
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        return PoiScoring.resolveMostUrgentSatisfiableNeed(
            needs,
            autonomy.isFillingHunger(),
            autonomy.isFillingEnergy(),
            autonomy.isFillingFun(),
            daytime,
            pois,
            cellOcc,
            npcX,
            npcZ,
            uc.getUuid(),
            townRecord,
            plugin.getConstructionCatalog()
        );
    }

    static boolean hasResolvableUrgentNeed(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nullable TransformComponent tc,
        boolean daytime
    ) {
        return autonomy.isFillingAnyNeed()
            || resolveUrgentNeedKind(ref, store, pois, needs, binding, autonomy, plugin, townRecord, tc, daytime) != null;
    }

    /**
     * While a fill-energy session is active (or energy is below rest threshold), pick the next bed POI and travel
     * immediately. Prefers the assigned house bed, then inn beds.
     */
    private static boolean tryBeginNextEnergyRest(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nullable TransformComponent tc
    ) {
        if (townRecord == null) {
            autonomy.setFillingEnergy(false);
            return false;
        }
        if (!PoiScoring.isEnergyNotFull(needs)) {
            autonomy.setFillingEnergy(false);
            return false;
        }
        if (!PoiScoring.needsEnergyBreak(needs, autonomy.isFillingEnergy())) {
            return false;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            autonomy.setFillingEnergy(false);
            return false;
        }
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        Map<String, Integer> cellOcc = PoiOccupancy.cellOccupancyForTown(world, binding.getTownId(), store, reg);
        double npcX = tc != null ? tc.getPosition().x : Double.NaN;
        double npcZ = tc != null ? tc.getPosition().z : Double.NaN;
        UUID homePlotId = PoiScoring.resolveHomePlotId(townRecord, uc.getUuid(), plugin.getConstructionCatalog());
        List<UUID> innPlotIds = PoiScoring.resolveInnPlotIds(townRecord, plugin.getConstructionCatalog());
        PoiEntry pick = PoiScoring.pickEnergyRestPoi(pois, cellOcc, npcX, npcZ, homePlotId, innPlotIds);
        if (pick == null || !PoiScoring.isRestPoi(pick)) {
            autonomy.setFillingEnergy(false);
            return false;
        }
        if (!PoiOccupancy.tryClaimStand(cellOcc, pick)) {
            return false;
        }
        autonomy.setFillingEnergy(true);
        beginTravelToPoi(ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, binding.getTownId(), tc, pick);
        return true;
    }

    /**
     * While a fill-fun session is active (or fun is below break threshold), pick the nearest plot with a fun POI and
     * travel immediately. Daytime only.
     */
    private static boolean tryBeginNextFunBreak(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull List<PoiEntry> pois,
        @Nonnull VillagerNeeds needs,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nullable TransformComponent tc,
        boolean daytime
    ) {
        if (!daytime || townRecord == null) {
            autonomy.setFillingFun(false);
            return false;
        }
        if (!PoiScoring.isFunNotFull(needs)) {
            autonomy.setFillingFun(false);
            return false;
        }
        if (!PoiScoring.needsFunBreak(needs, autonomy.isFillingFun(), daytime)) {
            return false;
        }
        World w = store.getExternalData().getWorld();
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(w, plugin);
        Map<String, Integer> cellOcc = PoiOccupancy.cellOccupancyForTown(w, binding.getTownId(), store, reg);
        double npcX = tc != null ? tc.getPosition().x : Double.NaN;
        double npcZ = tc != null ? tc.getPosition().z : Double.NaN;
        PoiEntry pick =
            PoiScoring.pickFunBreakPoi(pois, cellOcc, npcX, npcZ, townRecord, plugin.getConstructionCatalog());
        if (pick == null || !PoiScoring.isFunPoi(pick)) {
            autonomy.setFillingFun(false);
            return false;
        }
        if (!PoiOccupancy.tryClaimStand(cellOcc, pick)) {
            return false;
        }
        autonomy.setFillingFun(true);
        beginTravelToPoi(ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, binding.getTownId(), tc, pick);
        return true;
    }

    /**
     * When a feast is active, residents in {@link VillagerAutonomyState#PHASE_IDLE} path to the ephemeral feast POI
     * immediately (bypasses {@link VillagerAutonomyState#getNextDecisionEpochMs}).
     */
    /**
     * Keeps a villager on their festival spot for the whole festival: walk over as soon as it opens, then stand there
     * instead of following the day's schedule. Returns true while the festival owns this villager.
     */
    private static boolean tryHoldFestivalSpot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nullable TownRecord townRecord,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TransformComponent tc
    ) {
        if (townRecord == null || TownVillagerBinding.isScheduleSuppressedKind(binding.getKind())) {
            return false;
        }
        UUID villagerUuid = null;
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc != null) {
            villagerUuid = uc.getUuid();
        }
        PoiEntry judging = MarketJudgeDirectorSystem.redirectSpot(world, plugin, townRecord, binding, villagerUuid);
        PoiEntry spot =
            judging != null
                ? judging
                : FestivalAttendanceService.findSpot(world, plugin, townRecord, binding.getKind(), villagerUuid);
        if (spot == null) {
            return false;
        }
        boolean tightStand = judging != null || isTightFestivalStandPoi(spot, townRecord);
        boolean pondering = judging != null && isJudgingPonder(townRecord);
        if (tc != null && isStandingAtFestivalSpot(tc, spot, tightStand ? MarketIds.STALL_ARRIVED_DIST_SQ : FESTIVAL_SPOT_ARRIVED_DIST_SQ)) {
            if (tightStand && judging == null) {
                snapToFestivalSpot(ref, commandBuffer, tc, spot);
            }
            if (!pondering) {
                faceFestivalSpotYaw(ref, store, commandBuffer, tc, spot);
            }
            // Stay put: clear leftover travel seek so pathing does not pull them off the marker.
            if (autonomy.getPhase() != VillagerAutonomyState.PHASE_IDLE) {
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                clearAutonomySeekState(ref, npc, commandBuffer);
            }
            autonomy.setNextDecisionEpochMs(now + FESTIVAL_SPOT_HOLD_MS);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            NpcStandStill.hold(ref, store, npc, tc.getPosition(), commandBuffer);
            return true;
        }
        // Already walking to this spot: do not restart travel every tick (that strands villagers).
        if (autonomy.getPhase() == VillagerAutonomyState.PHASE_TRAVEL
            && spot.getId().equals(autonomy.getTargetPoiUuid())) {
            return true;
        }
        beginTravelToPoi(
            ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, binding.getTownId(), tc, spot
        );
        return true;
    }

    private static boolean isStandingAtFestivalSpot(@Nonnull TransformComponent tc, @Nonnull PoiEntry spot) {
        return isStandingAtFestivalSpot(tc, spot, FESTIVAL_SPOT_ARRIVED_DIST_SQ);
    }

    private static boolean isStandingAtFestivalSpot(
        @Nonnull TransformComponent tc,
        @Nonnull PoiEntry spot,
        double limitSq
    ) {
        double tx = spot.getInteractionTargetX() != null ? spot.getInteractionTargetX() : spot.getX() + 0.5;
        double tz = spot.getInteractionTargetZ() != null ? spot.getInteractionTargetZ() : spot.getZ() + 0.5;
        double dx = tc.getPosition().x - tx;
        double dz = tc.getPosition().z - tz;
        return dx * dx + dz * dz <= limitSq;
    }

    private static boolean isJudgingPonder(@Nullable TownRecord town) {
        if (town == null) {
            return false;
        }
        MarketSession session = MarketSessionIndex.get(town.getTownId());
        return session != null && session.isJudging() && session.isPondering();
    }

    /**
     * Park on a festival watch pad, market stall, or judging stand. Stops leftover travel so they do not walk in place.
     */
    private static void parkArrivedFestivalStand(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull TransformComponent tc,
        @Nonnull PoiEntry spot,
        boolean snap
    ) {
        if (MarketIds.isStandKind(spot.getWorkResidentKind())) {
            NpcStandStill.hold(ref, store, npc, tc.getPosition(), commandBuffer);
            faceFestivalSpotYaw(ref, store, commandBuffer, tc, spot);
            return;
        }
        if (snap) {
            snapToFestivalSpot(ref, commandBuffer, tc, spot);
        }
        NpcStandStill.hold(ref, store, npc, tc.getPosition(), commandBuffer);
        faceFestivalSpotYaw(ref, store, commandBuffer, tc, spot);
    }

    /** Watch / dance markers on the square, not Market Festival judging stands, ticket stalls, or Lyren's booth. */
    private static boolean isFestivalWatchPoi(@Nullable PoiEntry poi, @Nullable TownRecord town) {
        return poi != null && PoiScoring.isFestivalPoi(poi) && !isTightFestivalStandPoi(poi, town);
    }

    private static boolean isTightFestivalStandPoi(@Nullable PoiEntry poi, @Nullable TownRecord town) {
        return FestivalSpotService.isStallPinPoi(poi, town);
    }

    private static boolean isJudgingStandPoi(@Nullable PoiEntry poi) {
        return poi != null && MarketIds.isStandKind(poi.getWorkResidentKind());
    }

    private static void snapToFestivalSpot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TransformComponent tc,
        @Nonnull PoiEntry spot
    ) {
        double tx = spot.getInteractionTargetX() != null ? spot.getInteractionTargetX() : spot.getX() + 0.5;
        double tz = spot.getInteractionTargetZ() != null ? spot.getInteractionTargetZ() : spot.getZ() + 0.5;
        Vector3d pos = tc.getPosition();
        if (Math.abs(pos.x - tx) < 0.01 && Math.abs(pos.z - tz) < 0.01) {
            return;
        }
        pos.x = tx;
        pos.z = tz;
        commandBuffer.putComponent(ref, TransformComponent.getComponentType(), tc);
    }

    private static void faceFestivalSpotYaw(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TransformComponent tc,
        @Nonnull PoiEntry spot
    ) {
        Float yaw = spot.getInteractionTargetYawRadians();
        if (yaw == null) {
            return;
        }
        EntityRotationUtil.setBodyYaw(tc.getRotation(), yaw);
        commandBuffer.putComponent(ref, TransformComponent.getComponentType(), tc);
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (head != null) {
            EntityRotationUtil.setBodyYaw(head.getRotation(), yaw);
            commandBuffer.putComponent(ref, HeadRotation.getComponentType(), head);
        }
    }

    private static boolean tryBeginFeastGatherTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nullable TownRecord townRecord,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TransformComponent tc
    ) {
        if (townRecord == null || TownVillagerBinding.isScheduleSuppressedKind(binding.getKind())) {
            return false;
        }
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_IDLE) {
            return false;
        }
        String gid = townRecord.getFeastGatherPoiId();
        if (gid == null || gid.isBlank()) {
            return false;
        }
        UUID pid;
        try {
            pid = UUID.fromString(gid.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (pid.equals(autonomy.getTargetPoiUuid())) {
            return false;
        }
        long gatherDeadline = townRecord.getFeastGatherDeadlineEpochMs();
        if (gatherDeadline > 0L && gatherDeadline == autonomy.getLastFeastGatherDeadlineAttended()) {
            return false;
        }
        PoiEntry feastPoi = reg.get(pid);
        if (feastPoi == null) {
            return false;
        }
        beginTravelToPoi(ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, binding.getTownId(), tc, feastPoi);
        return true;
    }

    /**
     * After dawn quest roll, OFFER givers temporarily leave their schedule, walk to the guild hall quest board
     * (town roads when available), and ponder. Flavor only — the board already has the offers.
     */
    public static boolean tryBeginQuestBoardPostTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerAutonomyState autonomy,
        @Nullable TownRecord townRecord,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TransformComponent tc
    ) {
        if (townRecord == null || TownVillagerBinding.isScheduleSuppressedKind(binding.getKind())) {
            return false;
        }
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_IDLE) {
            return false;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        UUID npcUuid = uc.getUuid();
        UUID townId = townRecord.getTownId();
        if (!QuestBoardPostVisitQueue.isDue(townId, npcUuid, now)) {
            return false;
        }
        PoiEntry boardPoi = QuestBoardPoiEnsure.findOrRegister(plugin, world, townRecord);
        if (boardPoi == null) {
            // Keep queued — board chunk may be unloaded, or POI staff placement not visible yet.
            autonomy.setNextDecisionEpochMs(now + 5000L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            return false;
        }
        beginTravelToPoi(
            ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, townId, tc, boardPoi, true
        );
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_TRAVEL
            && autonomy.getPhase() != VillagerAutonomyState.PHASE_USE) {
            // Travel setup failed (bad interaction target, etc.) — keep queued for a later idle tick.
            return false;
        }
        QuestBoardPostVisitQueue.consume(townId, npcUuid);
        return true;
    }

    private static boolean isQuestBoardPoi(@Nullable PoiEntry poi) {
        return poi != null && poi.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD);
    }

    private static boolean isQuestBoardPosterDue(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable TownRecord townRecord,
        long now
    ) {
        if (townRecord == null) {
            return false;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        return QuestBoardPostVisitQueue.isDue(townRecord.getTownId(), uc.getUuid(), now);
    }

    private static void beginTravelToPoi(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nonnull UUID pathNavTownId,
        @Nullable TransformComponent tc,
        @Nonnull PoiEntry pick
    ) {
        beginTravelToPoi(
            ref, store, commandBuffer, world, npc, autonomy, now, plugin, townRecord, pathNavTownId, tc, pick, false
        );
    }

    private static void beginTravelToPoi(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord townRecord,
        @Nonnull UUID pathNavTownId,
        @Nullable TransformComponent tc,
        @Nonnull PoiEntry pick,
        boolean useTownPathNav
    ) {
        autonomy.setPhase(VillagerAutonomyState.PHASE_TRAVEL);
        autonomy.resetAutonomyStallTracking();
        double tx;
        double tz;
        double leashY;
        if (pick.hasInteractionTarget()) {
            Double tpx = pick.getInteractionTargetX();
            Double tpy = pick.getInteractionTargetY();
            Double tpz = pick.getInteractionTargetZ();
            if (tpx == null || tpy == null || tpz == null) {
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                autonomy.setNextDecisionEpochMs(now + 4000L);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                return;
            }
            tx = tpx;
            tz = tpz;
            int hintY = (int) Math.floor(tpy);
            int clearY = VillagerBlockUtil.resolveClearStandFeetY(world, (int) Math.floor(tpx), hintY, (int) Math.floor(tpz));
            leashY = clearY != Integer.MIN_VALUE ? clearY + 0.02 : tpy;
        } else {
            int bx = pick.getX();
            int bz = pick.getZ();
            tx = bx + 0.5;
            tz = bz + 0.5;
            int standY =
                tc != null
                    ? AutonomyNavBounds.standBlockYForPoiWithoutTarget(
                        world,
                        plugin,
                        townRecord,
                        pick,
                        bx,
                        bz,
                        (int) Math.floor(tc.getPosition().y)
                    )
                    : Integer.MIN_VALUE;
            leashY = standY != Integer.MIN_VALUE ? standY + 0.02 : pick.getY();
        }
        autonomy.setTravelTarget(tx, leashY, tz, pick.getId());
        autonomy.setPathFailureReason("");
        autonomy.setTravelStuckTicks(0);
        Vector3d finalTarget = new Vector3d(tx, leashY, tz);
        if (tc != null) {
            double dx = tc.getPosition().x - finalTarget.x;
            double dz = tc.getPosition().z - finalTarget.z;
            double horizSq = dx * dx + dz * dz;
            double maxArriveSq = ARRIVE_HORIZONTAL_SQ;
            boolean mountKind = pick.getInteractionKind() == PoiInteractionKind.SIT || pick.getInteractionKind() == PoiInteractionKind.SLEEP;
            boolean festivalWatch = isFestivalWatchPoi(pick, townRecord);
            boolean tightStand = isTightFestivalStandPoi(pick, townRecord);
            if (festivalWatch) {
                maxArriveSq = FESTIVAL_SPOT_ARRIVED_DIST_SQ;
            } else if (tightStand) {
                maxArriveSq = MarketIds.STALL_ARRIVED_DIST_SQ;
            } else if (mountKind && pick.hasInteractionTarget()) {
                maxArriveSq = POI_ENTRY_ARRIVE_HORIZONTAL_SQ;
            } else if (mountKind) {
                maxArriveSq = MOUNT_ARRIVE_HORIZONTAL_SQ;
            }
            if (horizSq <= maxArriveSq) {
                if (mountKind
                    && !pick.hasInteractionTarget()
                    && !VillagerBlockUtil.canNpcMountBlockPoi(
                        world,
                        tc.getPosition().x,
                        tc.getPosition().y,
                        tc.getPosition().z,
                        pick.getX(),
                        pick.getY(),
                        pick.getZ()
                    )) {
                    commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                    applyAutonomyRoleState(ref, npc, commandBuffer);
                    return;
                }
                autonomy.clearTravelWaypoints();
                npc.setLeashPoint(finalTarget);
                closePendingDoorsAfterTravelArrival(ref, store, world, autonomy, tc.getPosition(), maxArriveSq);
                if (festivalWatch || tightStand) {
                    autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                    autonomy.setPathFailureReason("");
                    autonomy.setTravelStuckTicks(0);
                    autonomy.setFillingHunger(false);
                    autonomy.setFillingEnergy(false);
                    autonomy.setFillingFun(false);
                    autonomy.setNextDecisionEpochMs(now);
                    commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                    parkArrivedFestivalStand(ref, store, commandBuffer, npc, tc, pick, tightStand);
                    return;
                }
                tryEnterPoiUse(ref, store, commandBuffer, world, npc, autonomy, now, townRecord, pathNavTownId, pick);
                return;
            }
        }
        if (useTownPathNav && tc != null && townRecord != null) {
            AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
            PathNavGraphService.PathNavFindResult navResult =
                AetherhavenWorldRegistries
                    .getOrCreatePathNavGraphService(world)
                    .findRouteResult(pathNavTownId, tc.getPosition(), finalTarget, plugin.getConfig().get());
            PathNavGraphService.logPathfindingSkip(
                plugin.getConfig().get(),
                "quest_board_post",
                pathNavTownId,
                pick.getId(),
                navResult
            );
            var route = navResult.waypoints();
            if (!route.isEmpty()) {
                route =
                    PathNavTravelWaypoints.prepareForSeek(
                        world,
                        tc.getPosition(),
                        route,
                        finalTarget,
                        (int) Math.floor(tc.getPosition().y),
                        plugin.getConfig().get().getPathNavNodeSpacing()
                    );
            }
            if (!route.isEmpty()) {
                autonomy.setTravelWaypoints(route);
                Vector3d first = autonomy.getCurrentTravelWaypoint();
                npc.setLeashPoint(first != null ? first : finalTarget);
                autonomy.setNextDecisionEpochMs(now + TRAVEL_PHASE_MAX_MS);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                applyAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
        }
        // Local POI travel (wander/work/use) should not pull villagers onto road nodes; path-nav is reserved for
        // inter-location commute and dawn quest-board / similar cross-town trips.
        autonomy.clearTravelWaypoints();
        npc.setLeashPoint(finalTarget);
        autonomy.setNextDecisionEpochMs(now + TRAVEL_PHASE_MAX_MS);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void tickTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull VillagerNeeds needs,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nullable TownRecord townRecord
    ) {
        UUID poiId = autonomy.getTargetPoiUuid();
        if (poiId == null) {
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
            autonomy.setPathFailureReason("");
            autonomy.setTravelStuckTicks(0);
            autonomy.setNextDecisionEpochMs(now + 2000L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            clearAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        if (now >= autonomy.getNextDecisionEpochMs()) {
            failTravel(autonomy, now, "TRAVEL_TIMEOUT", commandBuffer, ref, npc);
            return;
        }

        PoiEntry poiEarly = poiId != null ? reg.get(poiId) : null;
        boolean daytime = ShopSpotOpenService.isGameDay(store);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        TownVillagerBinding travelBinding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        TransformComponent tcEarly = store.getComponent(ref, TransformComponent.getComponentType());
        // Festival spots outrank other trips: drop a wrong destination so idle re-routes to the square.
        // Judging walks to market stands, which are not the elder's watch marker — do not cancel those.
        boolean travelingToFestival = isFestivalWatchPoi(poiEarly, townRecord) || isTightFestivalStandPoi(poiEarly, townRecord);
        if (plugin != null && travelBinding != null && townRecord != null) {
            World festivalWorld = store.getExternalData().getWorld();
            if (festivalWorld != null) {
                UUIDComponent travelUuid = store.getComponent(ref, UUIDComponent.getComponentType());
                PoiEntry expectedFestival =
                    MarketJudgeDirectorSystem.redirectSpot(
                        festivalWorld,
                        plugin,
                        townRecord,
                        travelBinding,
                        travelUuid != null ? travelUuid.getUuid() : null
                    );
                if (expectedFestival == null) {
                    expectedFestival =
                        FestivalAttendanceService.findSpot(
                            festivalWorld,
                            plugin,
                            townRecord,
                            travelBinding.getKind(),
                            travelUuid != null ? travelUuid.getUuid() : null
                        );
                }
                if (expectedFestival != null && !expectedFestival.getId().equals(poiId)) {
                    failTravel(autonomy, now, "FESTIVAL", commandBuffer, ref, npc);
                    return;
                }
            }
        }
        // Needs / quest board / off-shift must not yank someone off a walk to their festival stand (innkeeper was
        // bouncing back to the inn whenever fun or energy dipped mid-trip).
        if (!travelingToFestival) {
            PoiScoring.UrgentNeedKind urgentKind = null;
            if (plugin != null && travelBinding != null && townRecord != null) {
                List<PoiEntry> poisForScoring =
                    filterPoisForAutonomyScoring(townRecord, reg.listByTown(travelBinding.getTownId()));
                urgentKind =
                    resolveUrgentNeedKind(
                        ref,
                        store,
                        poisForScoring,
                        needs,
                        travelBinding,
                        autonomy,
                        plugin,
                        townRecord,
                        tcEarly,
                        daytime
                    );
            }
            // Redirect travel only when a free POI exists for a higher-priority need.
            if (daytime
                && poiEarly != null
                && !PoiScoring.isEatPoi(poiEarly)
                && urgentKind == PoiScoring.UrgentNeedKind.HUNGER) {
                autonomy.setFillingHunger(true);
                failTravel(autonomy, now, "HUNGRY", commandBuffer, ref, npc);
                return;
            }
            // Do not cross-cancel an active need-fill trip (e.g. low fun must not abort travel to an inn bed).
            boolean onEnergyFillTrip =
                autonomy.isFillingEnergy() && poiEarly != null && PoiScoring.isRestPoi(poiEarly);
            boolean onFunFillTrip =
                autonomy.isFillingFun() && poiEarly != null && PoiScoring.isFunPoi(poiEarly);
            if (poiEarly != null
                && !PoiScoring.isRestPoi(poiEarly)
                && urgentKind == PoiScoring.UrgentNeedKind.ENERGY
                && !onFunFillTrip) {
                autonomy.setFillingEnergy(true);
                failTravel(autonomy, now, "TIRED", commandBuffer, ref, npc);
                return;
            }
            if (daytime
                && poiEarly != null
                && !PoiScoring.isFunPoi(poiEarly)
                && urgentKind == PoiScoring.UrgentNeedKind.FUN
                && !onEnergyFillTrip) {
                autonomy.setFillingFun(true);
                failTravel(autonomy, now, "BORED", commandBuffer, ref, npc);
                return;
            }
            // Dawn quest-board posts preempt schedule commute / POI wander (not feast gather).
            if (isQuestBoardPosterDue(ref, store, townRecord, now)
                && !isQuestBoardPoi(poiEarly)
                && (poiEarly == null || !poiEarly.getTags().contains(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL))) {
                failTravel(autonomy, now, "QUEST_BOARD", commandBuffer, ref, npc);
                return;
            }
            // Night: cancel hunger trips so they can sleep instead of arriving at the restaurant after dark.
            if (!ShopSpotOpenService.isGameDay(store)
                && poiEarly != null
                && PoiScoring.isEatPoi(poiEarly)
                && !poiEarly.getTags().contains(AetherhavenConstants.POI_TAG_FEAST)) {
                autonomy.setFillingHunger(false);
                failTravel(autonomy, now, "NIGHT", commandBuffer, ref, npc);
                return;
            }
            VillagerScheduleTickState schedTick = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
            String scheduleSeg = schedTick != null ? schedTick.getLastAppliedScheduleSegment() : null;
            if (poiEarly != null
                && PoiScoring.isWorkPoi(poiEarly)
                && !PoiScoring.isWorkScheduleSegment(scheduleSeg)) {
                failTravel(autonomy, now, "OFF_SHIFT", commandBuffer, ref, npc);
                return;
            }
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }

        if (!isJudgingStandPoi(poiEarly) && tryStallTeleportRecovery(ref, store, commandBuffer, npc, autonomy, tc.getPosition())) {
            return;
        }

        Vector3d pos = tc.getPosition();
        Vector3d leash = npc.getLeashPoint();
        double dx = pos.x - leash.x;
        double dz = pos.z - leash.z;
        double horizSq = dx * dx + dz * dz;

        double maxArriveSq = ARRIVE_HORIZONTAL_SQ;
        boolean mountKind =
            poiEarly != null
                && (poiEarly.getInteractionKind() == PoiInteractionKind.SIT
                    || poiEarly.getInteractionKind() == PoiInteractionKind.SLEEP);
        boolean tightStandTravel = isTightFestivalStandPoi(poiEarly, townRecord);
        if (travelingToFestival && isFestivalWatchPoi(poiEarly, townRecord)) {
            maxArriveSq = FESTIVAL_SPOT_ARRIVED_DIST_SQ;
        } else if (tightStandTravel) {
            maxArriveSq = MarketIds.STALL_ARRIVED_DIST_SQ;
        } else if (mountKind && poiEarly != null && poiEarly.hasInteractionTarget()) {
            maxArriveSq = POI_ENTRY_ARRIVE_HORIZONTAL_SQ;
        } else if (mountKind) {
            maxArriveSq = MOUNT_ARRIVE_HORIZONTAL_SQ;
        }

        PathNavTravelSupport.WaypointTickAction waypointAction =
            PathNavTravelSupport.tickTravelWaypoints(autonomy, pos, leash.x, leash.z, maxArriveSq, now);
        if (waypointAction == PathNavTravelSupport.WaypointTickAction.ADVANCED) {
            Vector3d nextLeash = autonomy.getCurrentTravelWaypoint();
            if (nextLeash != null) {
                npc.setLeashPoint(nextLeash);
            }
            autonomy.setTravelStuckTicks(0);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }
        if (waypointAction == PathNavTravelSupport.WaypointTickAction.CLEARED_TO_FINAL) {
            autonomy.setTravelStuckTicks(0);
            npc.setLeashPoint(new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ()));
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            applyAutonomyRoleState(ref, npc, commandBuffer);
            return;
        }

        World world = store.getExternalData().getWorld();

        VillagerDoorUtil.tryOpenDoorsTowardLeash(
            world,
            pos,
            leash,
            (x, y, z) -> autonomy.addPendingDoorOpened(x, y, z)
        );

        boolean mountLimbo =
            mountKind
                && horizSq <= maxArriveSq
                && poiEarly != null
                && !poiEarly.hasInteractionTarget()
                && !VillagerBlockUtil.canNpcMountBlockPoi(world, pos.x, pos.y, pos.z, poiEarly.getX(), poiEarly.getY(), poiEarly.getZ());

        NavState nav = NavState.INIT;
        MotionController mc = npc.getRole() != null ? npc.getRole().getActiveMotionController() : null;
        if (mc != null) {
            nav = mc.getNavState();
        }

        if (nav == NavState.ABORTED) {
            failTravel(autonomy, now, "NO_PATH", commandBuffer, ref, npc);
            return;
        }

        if (mountLimbo) {
            autonomy.setTravelStuckTicks(autonomy.getTravelStuckTicks() + 1);
            if (autonomy.getTravelStuckTicks() >= MOUNT_UNREACHABLE_FAIL_TICKS) {
                failTravel(autonomy, now, "MOUNT_UNREACHABLE", commandBuffer, ref, npc);
                return;
            }
        } else if (nav == NavState.BLOCKED || nav == NavState.DEFER) {
            autonomy.setTravelStuckTicks(autonomy.getTravelStuckTicks() + 1);
            if (autonomy.getTravelStuckTicks() >= DOOR_UNJAM_STUCK_TICKS) {
                VillagerDoorUtil.tryUnjamDoorsAlongPath(world, pos, leash);
            }
            if (autonomy.getTravelStuckTicks() >= BLOCKED_FAIL_TICKS) {
                if (autonomy.hasTravelWaypoints()) {
                    if (autonomy.advanceTravelWaypoint()) {
                        Vector3d nextLeash = autonomy.getCurrentTravelWaypoint();
                        if (nextLeash != null) {
                            npc.setLeashPoint(nextLeash);
                        }
                    } else {
                        autonomy.clearTravelWaypoints();
                        npc.setLeashPoint(new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ()));
                    }
                    autonomy.setTravelStuckTicks(0);
                    commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                    commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                    applyAutonomyRoleState(ref, npc, commandBuffer);
                    return;
                }
                Vector3d recovery = AutonomyStuckTeleportRecovery.resolveVillagerRecoveryTarget(autonomy);
                if (recovery != null && !isJudgingStandPoi(poiEarly)) {
                    AutonomyStuckTeleportRecovery.teleportNpc(ref, commandBuffer, store, recovery, npc);
                    AutonomyStuckTeleportRecovery.applyPostTeleportTravel(npc, autonomy, recovery);
                    commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                    commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                    applyAutonomyRoleState(ref, npc, commandBuffer);
                    return;
                }
                failTravel(autonomy, now, nav == NavState.DEFER ? "DEFER" : "BLOCKED", commandBuffer, ref, npc);
                return;
            }
        } else if (nav == NavState.PROGRESSING || nav == NavState.INIT) {
            autonomy.setTravelStuckTicks(0);
        }

        boolean arrived = horizSq <= maxArriveSq;
        if (arrived && !autonomy.hasTravelWaypoints()) {
            closePendingDoorsAfterTravelArrival(ref, store, world, autonomy, pos, maxArriveSq);
            if (AetherhavenConstants.isScheduleZoneCommutePoi(poiId)) {
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                autonomy.setTargetPoiUuid(null);
                autonomy.setPathFailureReason("");
                autonomy.setTravelStuckTicks(0);
                autonomy.setNextDecisionEpochMs(now);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                clearAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            PoiEntry poi = poiEarly;
            if (poi == null) {
                failTravel(autonomy, now, "POI_GONE", commandBuffer, ref, npc);
                return;
            }
            // Festival stands are for watching, not a timed POI use that can hand off to inn needs afterward.
            boolean festivalWatch = isFestivalWatchPoi(poi, townRecord);
            boolean tightStand = isTightFestivalStandPoi(poi, townRecord);
            if (festivalWatch || tightStand) {
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                autonomy.setPathFailureReason("");
                autonomy.setTravelStuckTicks(0);
                autonomy.setFillingHunger(false);
                autonomy.setFillingEnergy(false);
                autonomy.setFillingFun(false);
                autonomy.setNextDecisionEpochMs(now);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                parkArrivedFestivalStand(ref, store, commandBuffer, npc, tc, poi, tightStand);
                return;
            }
            if (mountKind
                && !poi.hasInteractionTarget()
                && !VillagerBlockUtil.canNpcMountBlockPoi(world, pos.x, pos.y, pos.z, poi.getX(), poi.getY(), poi.getZ())) {
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                applyAutonomyRoleState(ref, npc, commandBuffer);
                return;
            }
            autonomy.setTravelStuckTicks(0);
            TownVillagerBinding arriveBinding = store.getComponent(ref, TownVillagerBinding.getComponentType());
            UUID arriveTownId = arriveBinding != null ? arriveBinding.getTownId() : null;
            if (arriveTownId == null) {
                failTravel(autonomy, now, "NO_TOWN", commandBuffer, ref, npc);
                return;
            }
            tryEnterPoiUse(ref, store, commandBuffer, world, npc, autonomy, now, townRecord, arriveTownId, poi);
            return;
        }

        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        applyAutonomyRoleState(ref, npc, commandBuffer);
    }

    /**
     * Capacity recheck + seat mountability before USE. Soft claims can still race until TRAVEL lands; if the seat is
     * full, fail travel instead of mounting / overlapping Sit overlays.
     */
    private static boolean tryEnterPoiUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nullable TownRecord townRecord,
        @Nonnull UUID townId,
        @Nonnull PoiEntry poi
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PoiRegistry reg = plugin != null ? AetherhavenWorldRegistries.getPoiRegistry(world) : null;
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID selfUuid = uc != null ? uc.getUuid() : null;
        if (reg == null
            || !PoiOccupancy.canBeginUse(store, townId, reg, poi, selfUuid)) {
            failTravel(autonomy, now, "OCCUPIED", commandBuffer, ref, npc);
            return false;
        }
        // Physical seat slots (benches have 2) — independently of POI capacity / village TRAVEL counts.
        VillagerBlockUtil.FurnitureMountKind furniture =
            VillagerBlockUtil.furnitureMountKind(world, poi.getX(), poi.getY(), poi.getZ());
        if (furniture == VillagerBlockUtil.FurnitureMountKind.SEAT
            && !VillagerBlockUtil.hasAvailableSeat(world, poi.getX(), poi.getY(), poi.getZ())) {
            failTravel(autonomy, now, "MOUNT_FULL", commandBuffer, ref, npc);
            return false;
        }
        boolean mountKind =
            poi.getInteractionKind() == PoiInteractionKind.SIT || poi.getInteractionKind() == PoiInteractionKind.SLEEP;
        if (mountKind && !poi.hasInteractionTarget()) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null
                || !VillagerBlockUtil.canNpcMountBlockPoi(
                    world,
                    tc.getPosition().x,
                    tc.getPosition().y,
                    tc.getPosition().z,
                    poi.getX(),
                    poi.getY(),
                    poi.getZ()
                )) {
                failTravel(autonomy, now, "MOUNT_FULL", commandBuffer, ref, npc);
                return false;
            }
        }
        NpcAnimationPlayback.play(ref, npc, AnimationSlot.Movement, null, commandBuffer);
        float dur = PoiEffectTable.useDurationSeconds(poi, RestaurantBenefitService.restaurantStateForPoi(townRecord, poi));
        autonomy.setPhase(VillagerAutonomyState.PHASE_USE);
        autonomy.setPhaseEndEpochMs(now + (long) (dur * 1000L));
        autonomy.setLastWorkHitEpochMs(0L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        PoiAutonomyVisuals.beginPoiUse(ref, store, commandBuffer, poi);
        afterBeginPoiUseMotion(ref, store, commandBuffer, npc);
        return true;
    }

    private static boolean tryStallTeleportRecovery(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull Vector3d pos
    ) {
        if (!AutonomyStuckTeleportRecovery.shouldTrackVillagerStall(autonomy.getPhase())) {
            autonomy.resetAutonomyStallTracking();
            return false;
        }
        if (store.getComponent(ref, MountedComponent.getComponentType()) != null) {
            return false;
        }

        Vector3d leash = npc.getLeashPoint();
        AutonomyStuckTeleportRecovery.updateStall(autonomy, pos, leash.x, leash.z);
        if (!AutonomyStuckTeleportRecovery.isStallTeleportDue(autonomy)) {
            return false;
        }

        Vector3d recovery = AutonomyStuckTeleportRecovery.resolveVillagerRecoveryTarget(autonomy);
        if (recovery == null) {
            AutonomyStuckTeleportRecovery.resetAfterRecovery(autonomy);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            return false;
        }

        AutonomyStuckTeleportRecovery.teleportNpc(ref, commandBuffer, store, recovery, npc);
        AutonomyStuckTeleportRecovery.applyPostTeleportTravel(npc, autonomy, recovery);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        applyAutonomyRoleState(ref, npc, commandBuffer);
        return true;
    }

    private static void closePendingDoorsAfterTravelArrival(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull Vector3d pos,
        double arriveHorizontalSq
    ) {
        if (autonomy.getPendingOpenDoorsMutable().isEmpty()) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            autonomy.clearPendingDoorClose();
            return;
        }
        Vector3d goal = new Vector3d(autonomy.getTargetX(), autonomy.getTargetY(), autonomy.getTargetZ());
        VillagerDoorUtil.closePendingDoorsAfterGoalReached(
            world,
            store,
            uc.getUuid(),
            pos,
            goal,
            arriveHorizontalSq,
            autonomy.getPendingOpenDoorsMutable()
        );
    }

    private static void failTravel(
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nonnull String reason,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc
    ) {
        autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
        autonomy.setTargetPoiUuid(null);
        autonomy.setPathFailureReason(reason);
        autonomy.setTravelStuckTicks(0);
        autonomy.clearTravelWaypoints();
        autonomy.clearPendingDoorClose();
        autonomy.setNextDecisionEpochMs(now + 5000L);
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    private static void tickUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiRegistry reg,
        @Nonnull VillagerNeeds needs,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nullable TownRecord townRecord,
        @Nonnull TownVillagerBinding binding,
        @Nonnull World world
    ) {
        UUID poiId = autonomy.getTargetPoiUuid();
        PoiEntry poi = poiId != null ? reg.get(poiId) : null;
        boolean daytime = ShopSpotOpenService.isGameDay(store);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        TransformComponent tcUse = store.getComponent(ref, TransformComponent.getComponentType());
        UUIDComponent villagerUuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID villagerUuid = villagerUuidComponent != null ? villagerUuidComponent.getUuid() : null;
        // Leave a workplace / bed use early when this villager has a reserved festival spot.
        if (plugin != null && townRecord != null) {
            PoiEntry festivalSpot =
                FestivalAttendanceService.findSpot(world, plugin, townRecord, binding.getKind(), villagerUuid);
            if (festivalSpot != null) {
                if (poiId == null || !festivalSpot.getId().equals(poiId)) {
                    abortActivePoiUseAndDismount(ref, store, commandBuffer, autonomy, needs, reg, true);
                    autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                    autonomy.clearTravelAndPoiState();
                    autonomy.setFillingHunger(false);
                    autonomy.setFillingEnergy(false);
                    autonomy.setFillingFun(false);
                    autonomy.setNextDecisionEpochMs(now);
                    commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                    clearAutonomyRoleState(ref, npc, commandBuffer);
                    return;
                }
                // Already "using" a festival stand — drop to idle hold so needs do not pull them back to the inn.
                abortActivePoiUseAndDismount(ref, store, commandBuffer, autonomy, needs, reg, true);
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                autonomy.setPathFailureReason("");
                autonomy.setTravelStuckTicks(0);
                autonomy.setFillingHunger(false);
                autonomy.setFillingEnergy(false);
                autonomy.setFillingFun(false);
                autonomy.setNextDecisionEpochMs(now);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                clearAutonomySeekState(ref, npc, commandBuffer);
                return;
            }
        }
        PoiScoring.UrgentNeedKind urgentKind = null;
        if (plugin != null && townRecord != null) {
            List<PoiEntry> poisForScoring =
                filterPoisForAutonomyScoring(townRecord, reg.listByTown(binding.getTownId()));
            urgentKind =
                resolveUrgentNeedKind(
                    ref,
                    store,
                    poisForScoring,
                    needs,
                    binding,
                    autonomy,
                    plugin,
                    townRecord,
                    tcUse,
                    daytime
                );
        }
        VillagerScheduleTickState schedTickEarly = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
        String scheduleSegEarly = schedTickEarly != null ? schedTickEarly.getLastAppliedScheduleSegment() : null;
        boolean shopping = PoiScoring.isShopScheduleSegment(scheduleSegEarly);
        boolean leaveForQuestBoard = isQuestBoardPosterDue(ref, store, townRecord, now) && !isQuestBoardPoi(poi);
        boolean deferEnergyLeaveForFun =
            poi != null
                && PoiScoring.isFunPoi(poi)
                && daytime
                && PoiScoring.needsFunBreak(needs, autonomy.isFillingFun(), daytime)
                && (autonomy.isFillingFun() || needs.getFun() <= needs.getEnergy());
        boolean hungerLeaveNonEat =
            !leaveForQuestBoard
                && urgentKind == PoiScoring.UrgentNeedKind.HUNGER
                && (poi == null || !PoiScoring.isEatPoi(poi));
        boolean energyLeaveNonRest =
            !leaveForQuestBoard
                && !deferEnergyLeaveForFun
                && urgentKind == PoiScoring.UrgentNeedKind.ENERGY
                && (poi == null || !PoiScoring.isRestPoi(poi));
        boolean deferFunLeaveForEnergy =
            poi != null
                && PoiScoring.isRestPoi(poi)
                && PoiScoring.needsEnergyBreak(needs, autonomy.isFillingEnergy())
                && (autonomy.isFillingEnergy() || needs.getEnergy() <= needs.getFun());
        boolean funLeaveNonFun =
            !leaveForQuestBoard
                && !deferFunLeaveForEnergy
                && urgentKind == PoiScoring.UrgentNeedKind.FUN
                && (poi == null
                    || !(PoiScoring.isFunPoi(poi) || PoiScoring.isShopFunFillPoi(poi, shopping)));
        // Do not abort a need fill mid session because another meter is also low.
        if (poi != null
            && autonomy.isFillingFun()
            && (PoiScoring.isFunPoi(poi) || PoiScoring.isShopFunFillPoi(poi, shopping))) {
            hungerLeaveNonEat = false;
            energyLeaveNonRest = false;
            funLeaveNonFun = false;
        } else if (poi != null && autonomy.isFillingEnergy() && PoiScoring.isRestPoi(poi)) {
            hungerLeaveNonEat = false;
            funLeaveNonFun = false;
            energyLeaveNonRest = false;
        } else if (poi != null && autonomy.isFillingHunger() && PoiScoring.isEatPoi(poi)) {
            energyLeaveNonRest = false;
            funLeaveNonFun = false;
            hungerLeaveNonEat = false;
        }
        // Finish an eat session promptly once night falls so they can get to bed.
        boolean nightAbortEat =
            !daytime
                && poi != null
                && PoiScoring.isEatPoi(poi)
                && !poi.getTags().contains(AetherhavenConstants.POI_TAG_FEAST);
        if (nightAbortEat) {
            autonomy.setFillingHunger(false);
        }
        // Leave work stations when the schedule segment ends (house + workplace share one plot).
        boolean scheduleLeaveWork =
            poi != null
                && PoiScoring.isWorkPoi(poi)
                && !PoiScoring.isWorkScheduleSegment(scheduleSegEarly);
        boolean needFilledAtPoi =
            poi != null
                && isNeedFillMeterFullAtPoi(poi, needs, townRecord, plugin, villagerUuid, shopping);
        if (now < autonomy.getPhaseEndEpochMs()
            && !hungerLeaveNonEat
            && !energyLeaveNonRest
            && !funLeaveNonFun
            && !nightAbortEat
            && !scheduleLeaveWork
            && !leaveForQuestBoard
            && !needFilledAtPoi) {
            if (isNpcBlockMounted(store, commandBuffer, ref)) {
                stopSeekThenRestoreMountedPose(ref, store, commandBuffer, npc);
                if (poi != null
                    && VillagerWorkVisuals.tickHit(
                        ref,
                        store,
                        commandBuffer,
                        npc,
                        poi,
                        binding.getKind(),
                        now,
                        autonomy.getLastWorkHitEpochMs()
                    )) {
                    autonomy.setLastWorkHitEpochMs(now);
                    commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                }
                return;
            }
            if (poi != null && isActiveNeedFillAtPoi(autonomy, poi, shopping)) {
                holdNeedFillAtPoiPose(ref, store, commandBuffer, npc, poi);
                return;
            }
            if (poi != null
                && VillagerWorkVisuals.tickHit(
                    ref,
                    store,
                    commandBuffer,
                    npc,
                    poi,
                    binding.getKind(),
                    now,
                    autonomy.getLastWorkHitEpochMs()
                )) {
                autonomy.setLastWorkHitEpochMs(now);
                commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            }
            holdStandingPoiUse(ref, store, commandBuffer, npc);
            return;
        }
        VillagerScheduleTickState schedTick = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
        if (poi != null
            && !hungerLeaveNonEat
            && !energyLeaveNonRest
            && !funLeaveNonFun
            && !leaveForQuestBoard
            && shouldHoldWorkShiftAtStation(schedTick, binding, needs, poi, daytime)) {
            float dur =
                PoiEffectTable.useDurationSeconds(
                    poi,
                    RestaurantBenefitService.restaurantStateForPoi(townRecord, poi)
                );
            autonomy.setPhaseEndEpochMs(now + (long) (dur * 1000L));
            autonomy.setLastWorkHitEpochMs(0L);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            if (!isNpcBlockMounted(store, commandBuffer, ref)) {
                holdStandingPoiUse(ref, store, commandBuffer, npc);
            }
            return;
        }
        if (poi != null
            && !hungerLeaveNonEat
            && !energyLeaveNonRest
            && !funLeaveNonFun
            && !leaveForQuestBoard
            && !nightAbortEat
            && !scheduleLeaveWork
            && shouldHoldNeedFillAtPoi(
                needs, autonomy, poi, daytime, townRecord, plugin, villagerUuid, shopping)) {
            if (maintainNeedFillUse(
                ref,
                store,
                commandBuffer,
                npc,
                needs,
                autonomy,
                now,
                townRecord,
                poi,
                daytime,
                plugin,
                villagerUuid,
                shopping
            )) {
                return;
            }
        }
        boolean finishedEat = false;
        boolean finishedWork = false;
        boolean finishedRest = false;
        boolean finishedFun = false;
        if (poi != null) {
            finishedEat = PoiScoring.isEatPoi(poi);
            finishedWork = PoiScoring.isWorkPoi(poi);
            finishedRest = PoiScoring.isRestPoi(poi);
            finishedFun = PoiScoring.isFunPoi(poi) || PoiScoring.isShopFunFillPoi(poi, shopping);
            autonomy.setLastUsedPoiUuid(poi.getId());
            PoiAutonomyVisuals.cleanupAfterPoiUse(ref, store, commandBuffer, poi);
            if (!hungerLeaveNonEat && !energyLeaveNonRest && !funLeaveNonFun) {
                if (!isNeedFillMeterFullAtPoi(poi, needs, townRecord, plugin, villagerUuid, shopping)) {
                    applyPoiUseCompleteForVillager(
                        needs,
                        poi,
                        RestaurantBenefitService.restaurantStateForPoi(townRecord, poi),
                        townRecord,
                        plugin,
                        villagerUuid,
                        shopping
                    );
                    commandBuffer.putComponent(ref, VillagerNeeds.getComponentType(), needs);
                }
            }
            if (townRecord != null
                && poi.getTags().contains(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL)
                && townRecord.getFeastGatherDeadlineEpochMs() > 0L) {
                autonomy.setLastFeastGatherDeadlineAttended(townRecord.getFeastGatherDeadlineEpochMs());
            }
            if (!hungerLeaveNonEat && !energyLeaveNonRest && !funLeaveNonFun) {
                tryPlayerShopPurchase(ref, store, commandBuffer, npc, poi, townRecord, binding, world);
            }
        }
        boolean keepEating =
            daytime
                && finishedEat
                && !hungerLeaveNonEat
                && poi != null
                && !PoiScoring.isNeedMeterFilledForPoi(
                    poi, needs, townRecord, catalogForNeedCap(plugin), villagerUuid
                );
        boolean keepResting =
            finishedRest
                && !energyLeaveNonRest
                && poi != null
                && !PoiScoring.isNeedMeterFilledForPoi(
                    poi, needs, townRecord, catalogForNeedCap(plugin), villagerUuid
                );
        boolean keepHavingFun =
            daytime && finishedFun && !funLeaveNonFun && PoiScoring.isFunNotFull(needs);
        if (keepEating) {
            autonomy.setFillingHunger(true);
        } else if (!daytime
            || (finishedEat
                && poi != null
                && PoiScoring.isNeedMeterFilledForPoi(
                    poi, needs, townRecord, catalogForNeedCap(plugin), villagerUuid
                ))) {
            autonomy.setFillingHunger(false);
        }
        if (keepResting) {
            autonomy.setFillingEnergy(true);
        } else if (finishedRest
            && poi != null
            && PoiScoring.isNeedMeterFilledForPoi(
                poi, needs, townRecord, catalogForNeedCap(plugin), villagerUuid
            )) {
            autonomy.setFillingEnergy(false);
        } else {
            autonomy.setFillingEnergy(false);
        }
        if (keepHavingFun) {
            autonomy.setFillingFun(true);
        } else if (!daytime || (finishedFun && !PoiScoring.isFunNotFull(needs))) {
            autonomy.setFillingFun(false);
        } else {
            autonomy.setFillingFun(false);
        }
        autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
        autonomy.setTargetPoiUuid(null);
        autonomy.setPathFailureReason("");
        autonomy.setTravelStuckTicks(0);
        autonomy.clearTravelWaypoints();
        // After eating / work / rest / fun, re-decide quickly so they can chain trips or rotate work spots.
        autonomy.setNextDecisionEpochMs(
            finishedEat
                    || finishedWork
                    || finishedRest
                    || finishedFun
                    || hungerLeaveNonEat
                    || energyLeaveNonRest
                    || funLeaveNonFun
                    || keepEating
                    || keepResting
                    || keepHavingFun
                    || nightAbortEat
                    || scheduleLeaveWork
                    || leaveForQuestBoard
                ? now
                : now + 2500L
        );
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        clearAutonomyRoleState(ref, npc, commandBuffer);
        if (plugin != null) {
            TransformComponent tc = tcUse != null ? tcUse : store.getComponent(ref, TransformComponent.getComponentType());
            if (leaveForQuestBoard
                && tryBeginQuestBoardPostTravel(
                    ref, store, commandBuffer, world, npc, binding, autonomy, townRecord, now, plugin, tc
                )) {
                return;
            }
            List<PoiEntry> pois =
                filterPoisForAutonomyScoring(townRecord, reg.listByTown(binding.getTownId()));
            if (keepEating
                || keepResting
                || keepHavingFun
                || hasResolvableUrgentNeed(
                    ref, store, pois, needs, binding, autonomy, plugin, townRecord, tc, daytime
                )) {
                tryBeginUrgentNeedBreak(
                    ref,
                    store,
                    commandBuffer,
                    world,
                    npc,
                    reg,
                    pois,
                    needs,
                    binding,
                    autonomy,
                    now,
                    plugin,
                    townRecord,
                    tc,
                    daytime
                );
            }
        }
    }

    /** During a {@code work} schedule segment, stay at the role's work POI instead of cycling back to plot wander. */
    private static boolean shouldHoldWorkShiftAtStation(
        @Nullable VillagerScheduleTickState schedTick,
        @Nonnull TownVillagerBinding binding,
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        boolean daytime
    ) {
        // Field producers finish each USE so they can travel to another work spot on the same plot.
        // Desk roles stay planted for the whole work segment. (Production still accrues for every USE window.)
        if (PoiScoring.isWorkPoi(poi) && VillagerWorkActivity.rotatesWorkStations(binding.getKind())) {
            return false;
        }
        if (schedTick == null || !PoiScoring.isWorkScheduleSegment(schedTick.getLastAppliedScheduleSegment())) {
            return false;
        }
        if (PoiScoring.needsBreakForSchedule(needs, daytime)) {
            return false;
        }
        UUID preferredPlot = binding.getPreferredPlotId();
        if (preferredPlot == null || poi.getPlotId() == null || !preferredPlot.equals(poi.getPlotId())) {
            return false;
        }
        return PoiScoring.matchesWorkPoiForBindingKind(poi, binding.getKind());
    }

    /** Stay seated or in bed while hunger, energy, or fun is still being refilled (renew USE timer in place). */
    private static boolean shouldHoldNeedFillAtPoi(
        @Nonnull VillagerNeeds needs,
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull PoiEntry poi,
        boolean daytime,
        @Nullable TownRecord townRecord,
        @Nullable AetherhavenPlugin plugin,
        @Nullable UUID villagerUuid,
        boolean shopping
    ) {
        if (isNeedFillMeterFullAtPoi(poi, needs, townRecord, plugin, villagerUuid, shopping)) {
            return false;
        }
        if (PoiScoring.isEatPoi(poi)) {
            return daytime
                && (autonomy.isFillingHunger()
                    || PoiScoring.needsHungerBreak(needs, autonomy.isFillingHunger(), daytime));
        }
        if (PoiScoring.isRestPoi(poi)) {
            return autonomy.isFillingEnergy()
                || PoiScoring.needsEnergyBreak(needs, autonomy.isFillingEnergy());
        }
        if (PoiScoring.isFunPoi(poi)) {
            return daytime
                && (autonomy.isFillingFun() || PoiScoring.needsFunBreak(needs, autonomy.isFillingFun(), daytime));
        }
        if (PoiScoring.isShopFunFillPoi(poi, shopping)) {
            return daytime
                && (autonomy.isFillingFun() || PoiScoring.isFunNotFull(needs));
        }
        return false;
    }

    private static boolean maintainNeedFillUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerNeeds needs,
        @Nonnull VillagerAutonomyState autonomy,
        long now,
        @Nullable TownRecord townRecord,
        @Nonnull PoiEntry poi,
        boolean daytime,
        @Nullable AetherhavenPlugin plugin,
        @Nullable UUID villagerUuid,
        boolean shopping
    ) {
        if (now >= autonomy.getPhaseEndEpochMs()) {
            applyPoiUseCompleteForVillager(
                needs,
                poi,
                RestaurantBenefitService.restaurantStateForPoi(townRecord, poi),
                townRecord,
                plugin,
                villagerUuid,
                shopping
            );
            commandBuffer.putComponent(ref, VillagerNeeds.getComponentType(), needs);
            autonomy.setLastUsedPoiUuid(poi.getId());
        }
        if (!shouldHoldNeedFillAtPoi(
            needs, autonomy, poi, daytime, townRecord, plugin, villagerUuid, shopping
        )) {
            return false;
        }
        float dur =
            PoiEffectTable.useDurationSeconds(
                poi,
                RestaurantBenefitService.restaurantStateForPoi(townRecord, poi)
            );
        autonomy.setPhase(VillagerAutonomyState.PHASE_USE);
        autonomy.setPhaseEndEpochMs(now + (long) (dur * 1000L));
        if (PoiScoring.isEatPoi(poi)) {
            autonomy.setFillingHunger(true);
        } else if (PoiScoring.isRestPoi(poi)) {
            autonomy.setFillingEnergy(true);
        } else if (PoiScoring.isFunPoi(poi) || PoiScoring.isShopFunFillPoi(poi, shopping)) {
            autonomy.setFillingFun(true);
        }
        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        holdNeedFillAtPoiPose(ref, store, commandBuffer, npc, poi);
        return true;
    }

    /** Stop autonomy Seek during USE; mount on furniture when the POI block supports it. */
    private static void holdNeedFillAtPoiPose(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiEntry poi
    ) {
        if (isNpcBlockMounted(store, commandBuffer, ref)) {
            stopSeekThenRestoreMountedPose(ref, store, commandBuffer, npc);
            return;
        }
        if (PoiAutonomyVisuals.ensureMountedForPoi(ref, store, commandBuffer, poi)) {
            stopSeekThenRestoreMountedPose(ref, store, commandBuffer, npc);
            return;
        }
        holdStandingPoiUse(ref, store, commandBuffer, npc);
    }

    private static boolean isActiveNeedFillAtPoi(
        @Nonnull VillagerAutonomyState autonomy,
        @Nonnull PoiEntry poi,
        boolean shopping
    ) {
        return (autonomy.isFillingHunger() && PoiScoring.isEatPoi(poi))
            || (autonomy.isFillingEnergy() && PoiScoring.isRestPoi(poi))
            || (autonomy.isFillingFun()
                && (PoiScoring.isFunPoi(poi) || PoiScoring.isShopFunFillPoi(poi, shopping)));
    }

    private static boolean isNeedFillMeterFullAtPoi(
        @Nonnull PoiEntry poi,
        @Nonnull VillagerNeeds needs,
        @Nullable TownRecord townRecord,
        @Nullable AetherhavenPlugin plugin,
        @Nullable UUID villagerUuid,
        boolean shopping
    ) {
        if (PoiScoring.isShopFunFillPoi(poi, shopping)) {
            return !PoiScoring.isFunNotFull(needs);
        }
        return PoiScoring.isNeedMeterFilledForPoi(
            poi, needs, townRecord, catalogForNeedCap(plugin), villagerUuid
        );
    }

    @Nonnull
    private static ConstructionCatalog catalogForNeedCap(@Nullable AetherhavenPlugin plugin) {
        return plugin != null ? plugin.getConstructionCatalog() : ConstructionCatalog.empty();
    }

    private static void applyPoiUseCompleteForVillager(
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        @Nullable PlotRestaurantState restaurantState,
        @Nullable TownRecord townRecord,
        @Nullable AetherhavenPlugin plugin,
        @Nullable UUID villagerUuid,
        boolean shopping
    ) {
        PoiScoring.applyPoiUseComplete(
            needs,
            poi,
            restaurantState,
            townRecord,
            catalogForNeedCap(plugin),
            villagerUuid,
            shopping
        );
    }

    /** Exit autonomy Seek while keeping POI Status overlays (eat, sit, etc.). */
    private static void stopSeekDuringPoiUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String state = NpcSupportUtil.stateName(commandBuffer.getStore(), ref);
        if (state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
            NpcSupportUtil.setState(ref, "Idle", null, commandBuffer);
            NpcAnimationPlayback.play(ref, npc, AnimationSlot.Movement, null, commandBuffer);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        }
    }

    private static void tryPlayerShopPurchase(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull PoiEntry poi,
        @Nullable TownRecord townRecord,
        @Nonnull TownVillagerBinding binding,
        @Nonnull World world
    ) {
        if (townRecord == null || poi.getPlotId() == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !ShopSpotPurchaseService.isPlayerShopPlot(plugin, townRecord, poi.getPlotId())) {
            return;
        }
        PlotInstance plot = townRecord.findPlotById(poi.getPlotId());
        if (plot != null && !plot.isAllowNpcShopPurchases()) {
            return;
        }
        VillagerScheduleTickState sched = store.getComponent(ref, VillagerScheduleTickState.getComponentType());
        if (sched == null
            || !VillagerScheduleResolver.LOC_SHOP.equals(sched.getLastAppliedScheduleSegment())
            || sched.isShopSegmentPurchaseDone()
            || !poi.getTags().contains("SHOP")) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        String roleId = npc.getRoleName();
        String buyerName = roleId != null ? AetherhavenRoleLabels.displayNameForRoleId(roleId.trim()) : "A villager";
        NpcShopSpotBuyerService.scheduleBuyOneListing(
            world,
            binding.getTownId(),
            poi.getPlotId(),
            buyerName,
            uc.getUuid()
        );
        sched.setShopSegmentPurchaseDone(true);
        commandBuffer.putComponent(ref, VillagerScheduleTickState.getComponentType(), sched);
    }

    private static void applyAutonomyDebugOverlay(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull VillagerNeeds needs,
        @Nonnull VillagerAutonomyState autonomy
    ) {
        if (npc.getRole() == null) {
            return;
        }
        boolean showAhDebug = store.getComponent(ref, VillagerAutonomyDebugTag.getComponentType()) != null;
        if (!showAhDebug) {
            VillagerAutonomyDebug.clearAutonomyDebugForNpc(ref, commandBuffer, npc);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            return;
        }
        VillagerAutonomyDebug.ensureAutonomyDebugRoleFlags(npc);
        boolean daytime = ShopSpotOpenService.isGameDay(store);
        StringBuilder sb = new StringBuilder();
        sb.append("AH ");
        sb.append(switch (autonomy.getPhase()) {
            case VillagerAutonomyState.PHASE_TRAVEL -> "TR";
            case VillagerAutonomyState.PHASE_USE -> "USE";
            default -> "IDLE";
        });
        UUID tgtPoi = autonomy.getTargetPoiUuid();
        if (tgtPoi != null) {
            String u = tgtPoi.toString();
            sb.append(" POI:").append(u, 0, Math.min(8, u.length()));
        }
        if (!autonomy.getPathFailureReason().isEmpty()) {
            sb.append(" FAIL:").append(autonomy.getPathFailureReason());
        }
        if (autonomy.isFillingHunger()) {
            sb.append(" FH");
        }
        if (autonomy.isFillingEnergy()) {
            sb.append(" FE");
        }
        if (autonomy.isFillingFun()) {
            sb.append(" FF");
        }
        if (autonomy.getPhase() == VillagerAutonomyState.PHASE_IDLE
            && tgtPoi == null
            && daytime
            && PoiScoring.needsHungerBreak(needs, autonomy.isFillingHunger(), daytime)) {
            sb.append(autonomy.isFillingHunger() ? " NO_EAT" : " HUNGRY");
        }
        if (autonomy.getPhase() == VillagerAutonomyState.PHASE_IDLE
            && tgtPoi == null
            && PoiScoring.needsEnergyBreak(needs, autonomy.isFillingEnergy())) {
            sb.append(autonomy.isFillingEnergy() ? " NO_BED" : " TIRED");
        }
        if (autonomy.getPhase() == VillagerAutonomyState.PHASE_IDLE
            && tgtPoi == null
            && daytime
            && PoiScoring.needsFunBreak(needs, autonomy.isFillingFun(), daytime)) {
            sb.append(autonomy.isFillingFun() ? " NO_FUN" : " BORED");
        }
        Vector3d leash = npc.getLeashPoint();
        sb.append(" L:").append((int) leash.x).append(',').append((int) leash.z);
        NavState nav = NavState.INIT;
        MotionController mc = npc.getRole().getActiveMotionController();
        if (mc != null) {
            nav = mc.getNavState();
        }
        sb.append(" NAV:").append(nav);
        DebugSupport debugSupport = NpcSupportUtil.debugSupport(store, ref);
        if (debugSupport != null) {
            debugSupport.setDisplayCustomString(sb.toString());
        }
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeModule mod = TimeModule.get();
        if (mod != null) {
            TimeResource tr = store.getResource(mod.getTimeResourceType());
            if (tr != null) {
                return tr.getNow().toEpochMilli();
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * Current world time in ms, aligned with {@link #tick} decision scheduling.
     * Safe to use from the world thread when mutating {@link VillagerAutonomyState} outside the system tick.
     */
    public static long resolveAutonomyNowMs(@Nonnull Store<EntityStore> store) {
        return resolveNowMs(store);
    }

    /**
     * After assigning a villager to a workplace plot, reset autonomy so {@link SchedulePlotCommute} or POI scoring
     * paths them there instead of leaving them idle until the next decision window.
     */
    public static void promptWorkplaceTravel(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store, long nowMs) {
        resetAutonomyForRescue(npcRef, store, null, nowMs);
    }

    /**
     * Same as {@link #promptWorkplaceTravel(Ref, Store, long)} but routes component writes through {@code commandBuffer}
     * for use from tick/schedule systems (never write to {@link Store} directly during a tick).
     */
    public static void promptWorkplaceTravel(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        long nowMs
    ) {
        resetAutonomyForRescue(npcRef, store, commandBuffer, nowMs);
    }

    /**
     * After teleporting a town NPC to the player, drop them to idle pathing, clear travel targets, and end autonomy
     * role motion (Seek) so pathfinding can start fresh.
     */
    public static void resetAutonomyForRescue(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store, long nowMs) {
        resetAutonomyForRescue(npcRef, store, null, nowMs);
    }

    private static void resetAutonomyForRescue(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        long nowMs
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        VillagerAutonomyState aut = store.getComponent(npcRef, VillagerAutonomyState.getComponentType());
        VillagerNeeds needs = store.getComponent(npcRef, VillagerNeeds.getComponentType());
        PoiRegistry reg = null;
        if (aut != null && aut.getPhase() == VillagerAutonomyState.PHASE_USE) {
            World world = store.getExternalData().getWorld();
            AetherhavenPlugin pluginInstance = AetherhavenPlugin.get();
            if (pluginInstance != null) {
                reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, pluginInstance);
            }
        }
        abortActivePoiUseAndDismount(npcRef, store, commandBuffer, aut, needs, reg, true);
        if (aut == null) {
            aut = VillagerAutonomyState.fresh(nowMs);
        } else {
            aut = (VillagerAutonomyState) aut.clone();
        }
        aut.setPhase(VillagerAutonomyState.PHASE_IDLE);
        aut.clearTravelAndPoiState();
        aut.setPathFailureReason("");
        aut.setTravelStuckTicks(0);
        aut.setPhaseEndEpochMs(0L);
        aut.setNextDecisionEpochMs(nowMs);
        if (commandBuffer != null) {
            commandBuffer.putComponent(npcRef, VillagerAutonomyState.getComponentType(), aut);
            clearAutonomyRoleState(npcRef, npc, commandBuffer);
        } else {
            store.putComponent(npcRef, VillagerAutonomyState.getComponentType(), aut);
            clearAutonomyRoleState(npcRef, npc, store);
        }
    }

    /** Ends {@link AetherhavenConstants#NPC_STATE_AUTONOMY_POI} seek motion; used when builder assist or other systems stop travel. */
    public static void clearAutonomySeekState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        clearAutonomyRoleState(ref, npc, commandBuffer);
    }

    /** Hired guards and other roles without an {@code AetherhavenAutonomy} instruction block must not enter POI travel. */
    public static boolean skipsPoiAutonomy(@Nonnull TownVillagerBinding binding, @Nonnull NPCEntity npc) {
        if (TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
            return true;
        }
        return !supportsAutonomyPoiRoleState(npc);
    }

    public static boolean supportsAutonomyPoiRoleState(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return false;
        }
        Ref<EntityStore> npcRef = npc.getReference();
        if (npcRef == null) {
            return false;
        }
        StateSupport stateSupport = NpcSupportUtil.stateSupport(npcRef.getStore(), npcRef);
        return stateSupport != null
            && stateSupport.getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_AUTONOMY_POI) >= 0;
    }

    /**
     * After {@link PoiAutonomyVisuals#beginPoiUse}: while block-mounted, stop Seek (role Idle) so they stay in the seat,
     * then re-apply Sit/Sleep — Idle StateTransitions clear Status, so overlays must be restored.
     * Standing desks use {@link NpcStandStill} so Idle rect-wander does not pull them off the station.
     */
    public static void afterBeginPoiUseMotion(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc
    ) {
        if (isNpcBlockMounted(store, commandBuffer, ref)) {
            stopSeekThenRestoreMountedPose(ref, store, commandBuffer, npc);
            return;
        }
        holdStandingPoiUse(ref, store, commandBuffer, npc);
    }

    /** Plant at current feet during standing (non-mounted) POI USE — avoids Idle 4x4 rect wander. */
    private static void holdStandingPoiUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            stopSeekDuringPoiUse(ref, npc, commandBuffer);
            return;
        }
        NpcStandStill.hold(ref, store, npc, tc.getPosition(), commandBuffer);
    }

    public static boolean isNpcBlockMounted(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref
    ) {
        MountedComponent mounted = commandBuffer.getComponent(ref, MountedComponent.getComponentType());
        if (mounted == null) {
            mounted = store.getComponent(ref, MountedComponent.getComponentType());
        }
        if (mounted != null) {
            return mounted.getControllerType() == MountController.BlockMount;
        }
        // Same-tick SafeBlockMount has queued MountedComponent but the store has not flushed yet.
        return SafeBlockMount.isMountedOrPending(store, commandBuffer, ref);
    }

    /**
     * Exit autonomy Seek (Idle) without leaving the seat: rebuild Sit/Sleep Status after Idle clears overlays.
     */
    private static void stopSeekThenRestoreMountedPose(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc
    ) {
        if (npc.getRole() != null) {
            String state = NpcSupportUtil.stateName(commandBuffer.getStore(), ref);
            if (state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
                NpcSupportUtil.setState(ref, "Idle", null, commandBuffer);
            }
        }
        NpcAnimationPlayback.play(ref, npc, AnimationSlot.Movement, null, commandBuffer);
        reaffirmMountedPose(ref, store, commandBuffer, npc);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static void reaffirmMountedPose(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc
    ) {
        MountedComponent mounted = commandBuffer.getComponent(ref, MountedComponent.getComponentType());
        if (mounted == null) {
            mounted = store.getComponent(ref, MountedComponent.getComponentType());
        }
        String pose = "Sit";
        if (mounted != null && mounted.getBlockMountType() == com.hypixel.hytale.protocol.BlockMountType.Bed) {
            pose = "Sleep";
            ModelComponent mc = store.getComponent(ref, ModelComponent.getComponentType());
            if (mc != null
                && mc.getModel() != null
                && !mc.getModel().getAnimationSetMap().containsKey("Sleep")
                && mc.getModel().getAnimationSetMap().containsKey("Sit")) {
                pose = "Sit";
            }
        }
        NpcAnimationPlayback.play(ref, npc, AnimationSlot.Status, pose, commandBuffer);
    }

    public static void applyAutonomyRoleState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!supportsAutonomyPoiRoleState(npc)) {
            return;
        }
        NpcSupportUtil.setState(ref, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static void clearAutonomyRoleState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String state = NpcSupportUtil.stateName(commandBuffer.getStore(), ref);
        if (!state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
            return;
        }
        NpcSupportUtil.setState(ref, "Idle", null, commandBuffer);
        NpcAnimationPlayback.clearOverlaySlots(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static void clearAutonomyRoleState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store
    ) {
        if (npc.getRole() == null) {
            return;
        }
        String state = NpcSupportUtil.stateName(store, ref);
        if (!state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)) {
            return;
        }
        NpcSupportUtil.setState(ref, "Idle", null, store);
        npc.playAnimation(ref, AnimationSlot.Action, null, store);
        npc.playAnimation(ref, AnimationSlot.Emote, null, store);
        npc.playAnimation(ref, AnimationSlot.Status, null, store);
        store.putComponent(ref, NPCEntity.getComponentType(), npc);
    }
}
