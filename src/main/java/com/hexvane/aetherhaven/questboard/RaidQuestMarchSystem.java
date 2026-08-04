package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.map.RaidQuestCompassCache;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Advances raid mobs toward the town charter in timed stages. Mobs walk between waypoints; stuck recovery may
 * teleport them toward the current leash when out of combat.
 */
public final class RaidQuestMarchSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    private final AetherhavenPlugin plugin;

    public RaidQuestMarchSystem(@Nonnull AetherhavenPlugin plugin) {
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
            RaidQuestMobBinding.getComponentType(),
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType(),
            UUIDComponent.getComponentType()
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
        RaidQuestMobBinding binding = chunk.getComponent(index, RaidQuestMobBinding.getComponentType());
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        UUIDComponent mobUuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (binding == null || npc == null || npc.getRole() == null || transform == null || mobUuid == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        RaidQuestReconcile.maybeReconcileWorld(world, store, plugin);
        String worldName = world.getName();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            RaidQuestCompassCache.removeMob(worldName, mobUuid.getUuid());
            return;
        }

        QuestBoardSlotRecord slot = town.findBoardSlotByInstanceId(binding.getBoardInstanceId());
        if (slot == null || !slot.isAccepted() || !slot.isRaidQuest()) {
            RaidQuestCompassCache.removeMob(worldName, mobUuid.getUuid());
            return;
        }

        Vector3d pos = transform.getPosition();
        RaidQuestCompassCache.upsert(
            worldName,
            new RaidQuestCompassCache.Entry(
                town.getTownId(),
                mobUuid.getUuid(),
                binding.getBoardInstanceId(),
                slot.getRaidTargetLabelLangKey(),
                pos.x,
                pos.y,
                pos.z
            )
        );

        Vector3d charterPos =
            binding.hasMarchTarget()
                ? binding.getMarchTarget()
                : RaidQuestSpawnService.charterMarchTargetFromTown(town);

        long nowMs = RaidQuestMarchUtil.resolveNowMs(store);
        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        if (!binding.isMarchInitialized() || !binding.hasMarchTarget()) {
            RaidQuestMarchUtil.bootstrapMarch(binding, pos, charterPos, nowMs);
            if (RaidQuestMarchUtil.isFlyingNpc(npc) && !binding.hasMarchFlyCruiseY()) {
                binding.setMarchFlyCruiseY(pos.y);
            }
            commandBuffer.putComponent(ref, RaidQuestMobBinding.getComponentType(), binding);
            RaidQuestMarchDebugLog.logBootstrap(plugin, mobUuid.getUuid(), pos, charterPos, binding.getMarchLeash(), npc);
        }

        String stateName = npc.getRole().getStateSupport().getStateName();
        boolean inCombat = RaidQuestMarchUtil.isEngagedInCombat(npc);
        RaidQuestMarchDebugLog.logMarchStatus(
            plugin,
            mobUuid.getUuid(),
            pos,
            binding.getMarchLeash(),
            charterPos,
            binding,
            npc,
            stateName,
            inCombat
        );
        if (inCombat) {
            RaidQuestMarchDebugLog.logCombatPause(plugin, mobUuid.getUuid(), stateName);
            binding.resetAutonomyStallTracking();
            return;
        }

        if (RaidQuestMarchStuckRecovery.tryRecoverIfStalled(
            plugin,
            ref,
            npc,
            binding,
            pos,
            commandBuffer,
            store,
            mobUuid.getUuid()
        )) {
            commandBuffer.putComponent(ref, RaidQuestMobBinding.getComponentType(), binding);
        }

        RaidQuestMarchUtil.ensureMarchMotion(ref, npc, binding, pos, commandBuffer);

        RaidQuestMarchAggro.tryEngageNearbyPlayers(ref, npc, pos, store, commandBuffer);

        if (!RaidQuestMarchUtil.shouldAdvanceMarch(pos, binding, nowMs)) {
            return;
        }

        RaidQuestMarchUtil.applyMarchAdvance(npc, binding, charterPos, nowMs, commandBuffer, ref, pos);
        RaidQuestMarchDebugLog.logAdvance(plugin, mobUuid.getUuid(), binding.getMarchLeash(), charterPos);
    }
}
