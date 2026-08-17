package com.hexvane.aetherhaven.clown;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.autonomy.VillagerDoorUtil;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Resident clown: during work hours, travels to the town resident with the lowest fun and cheers them up until fun is
 * full (~20 seconds). Both play the Laugh emote while cheering.
 */
public final class ClownCheerAssistSystem extends EntityTickingSystem<EntityStore> {
    private static final long EMOTE_INTERVAL_NS = 3_000_000_000L;
    private static final int DOOR_UNJAM_STUCK_TICKS = 30;
    private static final float FUN_MAX = 100f;
    /** Horizontal stand-off from the cheer target so Seek does not stack the clown on top of them. */
    private static final double STAND_OFF_DISTANCE = 2.0;

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public ClownCheerAssistSystem(@Nonnull AetherhavenPlugin plugin) {
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
        if (!AetherhavenConstants.NPC_CLOWN.equals(npc.getRoleName())) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }

        ClownCheerAssistState assist = archetypeChunk.getComponent(index, ClownCheerAssistState.getComponentType());
        if (assist == null) {
            assist = new ClownCheerAssistState();
            commandBuffer.addComponent(ref, ClownCheerAssistState.getComponentType(), assist);
        }
        if (!TownVillagerBinding.KIND_CLOWN.equals(binding.getKind())) {
            clearAssist(ref, commandBuffer, npc, assist);
            return;
        }

        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null || !town.hasQuestCompleted(AetherhavenConstants.QUEST_CLOWN_TENT)) {
            clearAssist(ref, commandBuffer, npc, assist);
            return;
        }
        if (!isOnWorkSchedule(store)) {
            clearAssist(ref, commandBuffer, npc, assist);
            return;
        }

        CheerTarget target = resolveCheerTarget(store, town, ref, assist);
        if (target == null) {
            clearAssist(ref, commandBuffer, npc, assist);
            return;
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = tc.getPosition();

        UUID targetId = target.entityUuid();
        if (!targetId.equals(assist.getTargetEntityId())) {
            assist.setTarget(targetId, target.x(), target.y(), target.z());
            assist.setPhase(ClownCheerAssistState.PHASE_TRAVEL);
        } else {
            assist.setTarget(targetId, target.x(), target.y(), target.z());
        }

        if (assist.isWithinCheerRange(pos.x, pos.y, pos.z)) {
            int prevPhase = assist.getPhase();
            if (prevPhase != ClownCheerAssistState.PHASE_CHEER) {
                assist.setPhase(ClownCheerAssistState.PHASE_CHEER);
                assist.setStartFun(target.fun());
                assist.setCheerElapsed(0f);
                assist.setLastEmoteNs(0L);
            }
            Vector3d stand = standOffLeashPoint(target.x(), target.y(), target.z(), pos);
            npc.setLeashPoint(stand);
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            tickCheer(dt, ref, store, commandBuffer, npc, assist, target);
            if (prevPhase != ClownCheerAssistState.PHASE_CHEER) {
                applyAutonomySeek(ref, npc, commandBuffer);
            }
            commandBuffer.putComponent(ref, ClownCheerAssistState.getComponentType(), assist);
            return;
        }

        assist.setPhase(ClownCheerAssistState.PHASE_TRAVEL);
        Vector3d stand = standOffLeashPoint(target.x(), target.y(), target.z(), pos);
        npc.setLeashPoint(stand);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        tickTravel(world, npc, pos, stand, assist);
        applyAutonomySeek(ref, npc, commandBuffer);
        commandBuffer.putComponent(ref, ClownCheerAssistState.getComponentType(), assist);
    }

    /**
     * Point beside the target toward the clown so Seek settles next to them instead of on the same block.
     */
    @Nonnull
    private static Vector3d standOffLeashPoint(
        double targetX,
        double targetY,
        double targetZ,
        @Nonnull Vector3d clownPos
    ) {
        double dx = clownPos.x - targetX;
        double dz = clownPos.z - targetZ;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.05) {
            return new Vector3d(targetX + STAND_OFF_DISTANCE, targetY, targetZ);
        }
        double scale = STAND_OFF_DISTANCE / horiz;
        return new Vector3d(targetX + dx * scale, targetY, targetZ + dz * scale);
    }

    private void tickCheer(
        float dt,
        @Nonnull Ref<EntityStore> clownRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity clownNpc,
        @Nonnull ClownCheerAssistState assist,
        @Nonnull CheerTarget target
    ) {
        assist.addCheerElapsed(dt);
        float t = Math.min(1f, assist.getCheerElapsed() / ClownCheerAssistState.CHEER_SECONDS);
        float fun = assist.getStartFun() + (FUN_MAX - assist.getStartFun()) * t;
        if (fun > FUN_MAX) {
            fun = FUN_MAX;
        }

        Ref<EntityStore> targetRef = store.getExternalData().getRefFromUUID(target.entityUuid());
        if (targetRef != null && targetRef.isValid()) {
            VillagerNeeds needs = store.getComponent(targetRef, VillagerNeeds.getComponentType());
            if (needs != null) {
                VillagerNeeds updated = (VillagerNeeds) needs.clone();
                updated.setFun(fun);
                commandBuffer.putComponent(targetRef, VillagerNeeds.getComponentType(), updated);
            }
            NPCEntity targetNpc = store.getComponent(targetRef, NPCEntity.getComponentType());
            maybeLaughBoth(clownRef, targetRef, commandBuffer, clownNpc, targetNpc, assist);
        }

        if (assist.getCheerElapsed() >= ClownCheerAssistState.CHEER_SECONDS || fun >= FUN_MAX - 0.01f) {
            assist.clearTarget();
            VillagerAutonomySystem.clearAutonomySeekState(clownRef, clownNpc, commandBuffer);
            commandBuffer.putComponent(clownRef, ClownCheerAssistState.getComponentType(), assist);
        }
    }

    private void maybeLaughBoth(
        @Nonnull Ref<EntityStore> clownRef,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity clownNpc,
        @Nullable NPCEntity targetNpc,
        @Nonnull ClownCheerAssistState assist
    ) {
        long now = System.nanoTime();
        if (assist.getLastEmoteNs() != 0L && now - assist.getLastEmoteNs() < EMOTE_INTERVAL_NS) {
            return;
        }
        assist.setLastEmoteNs(now);
        NpcAnimationPlayback.play(clownRef, clownNpc, AnimationSlot.Emote, "Laugh", commandBuffer);
        commandBuffer.putComponent(clownRef, NPCEntity.getComponentType(), clownNpc);
        if (targetNpc != null) {
            NpcAnimationPlayback.play(targetRef, targetNpc, AnimationSlot.Emote, "Laugh", commandBuffer);
            commandBuffer.putComponent(targetRef, NPCEntity.getComponentType(), targetNpc);
        }
    }

    private static void tickTravel(
        @Nonnull World world,
        @Nonnull NPCEntity npc,
        @Nonnull Vector3d pos,
        @Nonnull Vector3d leash,
        @Nonnull ClownCheerAssistState assist
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

    public static boolean shouldSkipAutonomy(@Nullable ClownCheerAssistState assist) {
        return assist != null && assist.isActive();
    }

    private static void applyAutonomySeek(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (npc.getRole() == null || !VillagerAutonomySystem.supportsAutonomyPoiRoleState(npc)) {
            return;
        }
        npc.getRole().getStateSupport().setState(ref, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, commandBuffer);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private void clearAssist(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull ClownCheerAssistState assist
    ) {
        if (assist.getPhase() != ClownCheerAssistState.PHASE_OFF) {
            VillagerAutonomySystem.clearAutonomySeekState(ref, npc, commandBuffer);
        }
        assist.clearTarget();
        commandBuffer.putComponent(ref, ClownCheerAssistState.getComponentType(), assist);
    }

    private boolean isOnWorkSchedule(@Nonnull Store<EntityStore> store) {
        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(AetherhavenConstants.NPC_CLOWN);
        if (vdef == null) {
            return false;
        }
        VillagerScheduleDefinition schedule =
            plugin.getVillagerDefinitionCatalog().effectiveSchedule(AetherhavenConstants.NPC_CLOWN, plugin.getVillagerScheduleRegistry());
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
    private CheerTarget resolveCheerTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Ref<EntityStore> clownRef,
        @Nonnull ClownCheerAssistState assist
    ) {
        UUID cachedId = assist.getTargetEntityId();
        if (cachedId != null) {
            CheerTarget cached = readTarget(store, town, clownRef, cachedId);
            // Stay with this resident until fun is full (or they despawn / leave town).
            if (cached != null && cached.fun() < FUN_MAX) {
                return cached;
            }
            assist.clearTarget();
        }
        return findLowestFunResident(store, town, clownRef);
    }

    @Nullable
    private CheerTarget findLowestFunResident(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Ref<EntityStore> clownRef
    ) {
        AtomicReference<CheerTarget> best = new AtomicReference<>();
        store.forEachChunk(
            Query.and(
                TownVillagerBinding.getComponentType(),
                VillagerNeeds.getComponentType(),
                TransformComponent.getComponentType(),
                UUIDComponent.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> otherRef = chunk.getReferenceTo(i);
                    if (otherRef == null || !otherRef.isValid() || otherRef.equals(clownRef)) {
                        continue;
                    }
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    VillagerNeeds needs = chunk.getComponent(i, VillagerNeeds.getComponentType());
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    UUIDComponent uuid = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (b == null || needs == null || tc == null || uuid == null) {
                        continue;
                    }
                    if (!town.getTownId().equals(b.getTownId())) {
                        continue;
                    }
                    if (TownVillagerBinding.isVisitorKind(b.getKind()) || TownVillagerBinding.isRescueKind(b.getKind())) {
                        continue;
                    }
                    float fun = needs.getFun();
                    if (fun >= FUN_MAX) {
                        continue;
                    }
                    CheerTarget current = best.get();
                    if (current != null && fun >= current.fun()) {
                        continue;
                    }
                    Vector3d pos = tc.getPosition();
                    best.set(new CheerTarget(uuid.getUuid(), pos.x, pos.y, pos.z, fun));
                }
            }
        );
        return best.get();
    }

    @Nullable
    private CheerTarget readTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Ref<EntityStore> clownRef,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> otherRef = store.getExternalData().getRefFromUUID(entityUuid);
        if (otherRef == null || !otherRef.isValid() || otherRef.equals(clownRef)) {
            return null;
        }
        TownVillagerBinding b = store.getComponent(otherRef, TownVillagerBinding.getComponentType());
        VillagerNeeds needs = store.getComponent(otherRef, VillagerNeeds.getComponentType());
        TransformComponent tc = store.getComponent(otherRef, TransformComponent.getComponentType());
        if (b == null || needs == null || tc == null || !town.getTownId().equals(b.getTownId())) {
            return null;
        }
        if (TownVillagerBinding.isVisitorKind(b.getKind()) || TownVillagerBinding.isRescueKind(b.getKind())) {
            return null;
        }
        Vector3d pos = tc.getPosition();
        return new CheerTarget(entityUuid, pos.x, pos.y, pos.z, needs.getFun());
    }

    private record CheerTarget(@Nonnull UUID entityUuid, double x, double y, double z, float fun) {}
}
