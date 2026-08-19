package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.market.MarketSession;
import com.hexvane.aetherhaven.festival.market.MarketSessionIndex;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.tourist.TouristAutonomySystem;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Villagers and tourists idle on a running festival square occasionally break into DanceBoogie or DancePop. While a
 * dance is playing they are held still so a wander step does not cancel the emote.
 */
public final class FestivalDanceSystem extends EntityTickingSystem<EntityStore> {
    /** The two player dance emotes from {@code EmotesInGame.json}. */
    static final String[] DANCE_EMOTES = {"DanceBoogie", "DancePop"};

    /** How often an eligible NPC rolls for a dance. */
    private static final long CHECK_INTERVAL_MS = 6_000L;
    /** Chance to start dancing on each check. */
    private static final double CHANCE_PER_CHECK = 0.4;
    /** How long they stay put and keep the dance emote playing. */
    private static final long DANCE_HOLD_MS = 5_500L;
    /** Wait after a dance before rolling again. */
    private static final long COOLDOWN_MS = 18_000L;

    private static final Map<UUID, Long> NEXT_CHECK_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, DanceHold> ACTIVE_DANCE = new ConcurrentHashMap<>();

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(
            new SystemDependency<>(Order.AFTER, SteeringSystem.class),
            new SystemDependency<>(Order.AFTER, VillagerAutonomySystem.class),
            new SystemDependency<>(Order.AFTER, TouristAutonomySystem.class)
        );

    public FestivalDanceSystem(@Nonnull AetherhavenPlugin plugin) {
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
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        TownVillagerBinding binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (ref == null || !ref.isValid() || npc == null || uuidComp == null || tc == null || binding == null) {
            return;
        }
        UUID entityUuid = uuidComp.getUuid();
        long nowMs = System.currentTimeMillis();
        if (com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex.isLivingFighter(entityUuid)
            || isMarketStallVendor(binding, entityUuid)
            || isJudgingElder(binding)) {
            DanceHold activeVendorDance = ACTIVE_DANCE.remove(entityUuid);
            if (activeVendorDance != null) {
                NpcAnimationPlayback.stop(ref, AnimationSlot.Emote, commandBuffer);
            }
            return;
        }

        DanceHold active = ACTIVE_DANCE.get(entityUuid);
        if (active != null) {
            if (active.untilMs() > nowMs && !NpcFaceVisuals.isInInteractionDialogue(npc)) {
                holdDance(ref, store, commandBuffer, npc, active.emote(), nowMs);
                return;
            }
            ACTIVE_DANCE.remove(entityUuid);
            // Clear the emote so a finished dance does not look like a busy overlay forever.
            NpcAnimationPlayback.stop(ref, AnimationSlot.Emote, commandBuffer);
            NpcStandStill.release(ref, npc, commandBuffer);
        }

        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }
        if (store.getComponent(ref, MountedComponent.getComponentType()) != null) {
            return;
        }
        if (hasBusyOverlay(store, ref)) {
            return;
        }

        Long cooldownUntil = COOLDOWN_UNTIL_MS.get(entityUuid);
        if (cooldownUntil != null && cooldownUntil > nowMs) {
            return;
        }
        long nextCheck = NEXT_CHECK_MS.getOrDefault(entityUuid, 0L);
        if (nextCheck > nowMs) {
            return;
        }
        NEXT_CHECK_MS.put(entityUuid, nowMs + CHECK_INTERVAL_MS);
        trimTimingMaps();

        World world = store.getExternalData().getWorld();
        if (world == null || binding.getTownId() == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null || town.getActiveFestivalId() == null) {
            return;
        }
        PoiEntry stallSpot =
            FestivalAttendanceService.findSpot(world, plugin, town, binding.getKind(), entityUuid);
        if (FestivalSpotService.isStallPinPoi(stallSpot, town)) {
            DanceHold activeStallDance = ACTIVE_DANCE.remove(entityUuid);
            if (activeStallDance != null) {
                NpcAnimationPlayback.stop(ref, AnimationSlot.Emote, commandBuffer);
            }
            return;
        }
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null || !isInsideFestivalSquare(square, tc.getPosition())) {
            return;
        }
        if (!isSettledAtFestival(store, ref, square)) {
            return;
        }
        String emote = pickSupportedDanceEmote(store, ref);
        if (emote == null) {
            // Model has no dance sets (many named townsfolk parent races without them).
            COOLDOWN_UNTIL_MS.put(entityUuid, nowMs + COOLDOWN_MS);
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= CHANCE_PER_CHECK) {
            return;
        }

        beginDance(ref, store, commandBuffer, npc, entityUuid, emote, nowMs);
    }

    /** Null when this NPC model cannot play either festival dance emote. */
    @Nullable
    private static String pickSupportedDanceEmote(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ModelComponent mc = store.getComponent(ref, ModelComponent.getComponentType());
        Model model = mc != null ? mc.getModel() : null;
        if (model == null) {
            return null;
        }
        var sets = model.getAnimationSetMap();
        List<String> supported = new ArrayList<>(2);
        for (String emote : DANCE_EMOTES) {
            if (sets.containsKey(emote)) {
                supported.add(emote);
            }
        }
        if (supported.isEmpty()) {
            return null;
        }
        return supported.get(ThreadLocalRandom.current().nextInt(supported.size()));
    }

    private static void beginDance(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull UUID entityUuid,
        @Nonnull String emote,
        long nowMs
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        // Stop walk first so Movement does not wipe the emote the moment it starts.
        NpcAnimationPlayback.stop(ref, AnimationSlot.Movement, commandBuffer);
        if (tc != null) {
            NpcStandStill.hold(ref, store, npc, tc.getPosition(), commandBuffer);
        }
        holdAutonomyStill(ref, store, commandBuffer, nowMs + DANCE_HOLD_MS);
        NpcAnimationPlayback.play(ref, npc, AnimationSlot.Emote, emote, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        ACTIVE_DANCE.put(entityUuid, new DanceHold(emote, nowMs + DANCE_HOLD_MS));
        COOLDOWN_UNTIL_MS.put(entityUuid, nowMs + COOLDOWN_MS);
    }

    private static void holdDance(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull String emote,
        long nowMs
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            NpcStandStill.hold(ref, store, npc, tc.getPosition(), commandBuffer);
        }
        // Do not replay or stop movement here. Restarting the emote every tick freezes it on the first frame.
        holdAutonomyStill(ref, store, commandBuffer, nowMs + 1_000L);
    }

    /** Push the next wander / POI pick past the dance so autonomy does not cancel the emote. */
    private static void holdAutonomyStill(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        long holdUntilMs
    ) {
        TouristAutonomyState tourist = store.getComponent(ref, TouristAutonomyState.getComponentType());
        if (tourist != null) {
            if (tourist.getNextDecisionEpochMs() < holdUntilMs) {
                tourist.setNextDecisionEpochMs(holdUntilMs);
            }
            if (tourist.getNextPoiPickEpochMs() < holdUntilMs) {
                tourist.setNextPoiPickEpochMs(holdUntilMs);
            }
            if (tourist.getPhase() == TouristAutonomyState.PHASE_POI
                && tourist.getPhaseEndEpochMs() < holdUntilMs) {
                tourist.setPhaseEndEpochMs(holdUntilMs);
            }
            commandBuffer.putComponent(ref, TouristAutonomyState.getComponentType(), tourist);
            return;
        }
        VillagerAutonomyState autonomy = store.getComponent(ref, VillagerAutonomyState.getComponentType());
        if (autonomy != null && autonomy.getNextDecisionEpochMs() < holdUntilMs) {
            autonomy.setNextDecisionEpochMs(holdUntilMs);
            commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
        }
    }

    /**
     * Tourists dance while visiting the festival square (not mid travel). Villagers dance while idle on the square.
     */
    private static boolean isSettledAtFestival(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlotInstance square
    ) {
        TouristAutonomyState tourist = store.getComponent(ref, TouristAutonomyState.getComponentType());
        if (tourist != null) {
            UUID visitPlot = tourist.getVisitPlotUuid();
            if (visitPlot == null || !visitPlot.equals(square.getPlotId())) {
                return false;
            }
            int phase = tourist.getPhase();
            return phase == TouristAutonomyState.PHASE_VISIT || phase == TouristAutonomyState.PHASE_POI;
        }
        VillagerAutonomyState autonomy = store.getComponent(ref, VillagerAutonomyState.getComponentType());
        // Anyone standing idle on the square can join in. Spot holders are there the whole time; others dance if they linger.
        return autonomy != null && autonomy.getPhase() == VillagerAutonomyState.PHASE_IDLE;
    }

    static boolean isInsideFestivalSquare(@Nonnull PlotInstance square, @Nonnull Vector3d pos) {
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);
        int z = (int) Math.floor(pos.z);
        if (square.containsWorldBlock(x, y, z)) {
            return true;
        }
        // Feet can sit a block under the solid footprint while they stand on the plaza.
        PlotFootprintRecord fp = square.toFootprint();
        return x >= fp.getMinX()
            && x <= fp.getMaxX()
            && z >= fp.getMinZ()
            && z <= fp.getMaxZ()
            && y >= fp.getMinY() - 1
            && y <= fp.getMaxY() + 4;
    }

    private static boolean hasBusyOverlay(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ActiveAnimationComponent active = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (active == null) {
            return false;
        }
        String emote = active.getActiveAnimations()[AnimationSlot.Emote.ordinal()];
        if (emote != null && !emote.isBlank()) {
            return true;
        }
        String status = active.getActiveAnimations()[AnimationSlot.Status.ordinal()];
        if (status == null || status.isBlank()) {
            return false;
        }
        // Sitting or sleeping folk stay put; dancing over a sit looks wrong.
        String lower = status.toLowerCase();
        return lower.contains("sit") || lower.contains("sleep");
    }

    private static void trimTimingMaps() {
        if (NEXT_CHECK_MS.size() < 512 && COOLDOWN_UNTIL_MS.size() < 512 && ACTIVE_DANCE.size() < 512) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 120_000L;
        NEXT_CHECK_MS.entrySet().removeIf(e -> e.getValue() != null && e.getValue() < cutoff);
        COOLDOWN_UNTIL_MS.entrySet().removeIf(e -> e.getValue() != null && e.getValue() < cutoff);
        ACTIVE_DANCE.entrySet().removeIf(e -> e.getValue() != null && e.getValue().untilMs() < cutoff);
    }

    /** Visible for tests. */
    @Nullable
    static String pickDanceEmote(int index) {
        if (index < 0 || index >= DANCE_EMOTES.length) {
            return null;
        }
        return DANCE_EMOTES[index];
    }

    private static boolean isMarketStallVendor(@Nonnull TownVillagerBinding binding, @Nonnull UUID entityUuid) {
        UUID townId = binding.getTownId();
        if (townId == null) {
            return false;
        }
        MarketSession session = MarketSessionIndex.get(townId);
        return session != null && session.isVendor(entityUuid);
    }

    private static boolean isJudgingElder(@Nonnull TownVillagerBinding binding) {
        if (!TownVillagerBinding.KIND_ELDER.equalsIgnoreCase(binding.getKind())) {
            return false;
        }
        UUID townId = binding.getTownId();
        if (townId == null) {
            return false;
        }
        MarketSession session = MarketSessionIndex.get(townId);
        return session != null && session.isJudging();
    }

    private record DanceHold(@Nonnull String emote, long untilMs) {}
}
