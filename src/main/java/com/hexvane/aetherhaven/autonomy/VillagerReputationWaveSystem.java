package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.npc.NpcReputationWaveState;
import com.hexvane.aetherhaven.npc.NpcReputationWaveVisuals;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerBefriendableResolver;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Idle befriendable villagers with high reputation occasionally wave at nearby players. */
public final class VillagerReputationWaveSystem extends EntityTickingSystem<EntityStore> {
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(new SystemDependency<>(Order.AFTER, SteeringSystem.class));

    public VillagerReputationWaveSystem(@Nonnull AetherhavenPlugin plugin) {
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
            TransformComponent.getComponentType(),
            VillagerAutonomyState.getComponentType()
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
        UUIDComponent uuidComp = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        TransformComponent tc = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        VillagerAutonomyState autonomy = archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());
        if (npc == null || uuidComp == null || tc == null || binding == null || autonomy == null) {
            return;
        }
        // Someone tied up at a rescue camp cannot wave.
        if (com.hexvane.aetherhaven.questboard.RescueCampService.isTied(uuidComp.getUuid())) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        NpcReputationWaveState waveState = archetypeChunk.getComponent(index, NpcReputationWaveState.getComponentType());
        if (waveState == null) {
            waveState = NpcReputationWaveState.fresh();
            commandBuffer.putComponent(ref, NpcReputationWaveState.getComponentType(), waveState);
        }

        if (waveState.isWaveActive(nowMs)) {
            NpcReputationWaveVisuals.maintainWaveFacing(ref, waveState, store, commandBuffer);
            commandBuffer.putComponent(ref, NpcReputationWaveState.getComponentType(), waveState);
            return;
        }
        if (waveState.hasWaveTarget() || hasStatusWave(store, ref)) {
            NpcReputationWaveVisuals.stopWave(ref, waveState, commandBuffer);
            waveState.setWaveUntilMs(0L);
            commandBuffer.putComponent(ref, NpcReputationWaveState.getComponentType(), waveState);
        }

        if (waveState.isOnCooldown(nowMs)) {
            return;
        }
        if (!VillagerBefriendableResolver.isBefriendable(store, ref, plugin)) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }
        if (autonomy.getPhase() != VillagerAutonomyState.PHASE_IDLE) {
            return;
        }
        if (store.getComponent(ref, MountedComponent.getComponentType()) != null) {
            return;
        }
        ActiveAnimationComponent active = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (active != null) {
            String emote = active.getActiveAnimations()[AnimationSlot.Emote.ordinal()];
            if (emote != null && !emote.isBlank()) {
                return;
            }
            String status = active.getActiveAnimations()[AnimationSlot.Status.ordinal()];
            if (status != null && !status.isBlank() && !NpcReputationWaveVisuals.WAVE_ANIMATION_ID.equals(status)) {
                return;
            }
        }

        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        if (waveState.getNextCheckMs() > nowMs) {
            return;
        }
        waveState.setNextCheckMs(nowMs + (long) (cfg.getReputationWaveCheckIntervalSeconds() * 1000f));
        commandBuffer.putComponent(ref, NpcReputationWaveState.getComponentType(), waveState);

        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return;
        }

        double range = cfg.getReputationWaveNearbyRangeBlocks();
        Vector3d pos = tc.getPosition();
        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
            store.getResource(EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(pos, range, nearby);

        UUID villagerUuid = uuidComp.getUuid();
        int minRep = cfg.getReputationWaveMinReputation();
        Vector3d closestPlayerPos = null;
        double closestSq = Double.MAX_VALUE;
        for (Ref<EntityStore> playerRef : nearby) {
            if (!playerRef.isValid()) {
                continue;
            }
            UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
            TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (playerUuid == null || playerTc == null) {
                continue;
            }
            int rep = VillagerReputationService.getOrCreateEntry(town, playerUuid.getUuid(), villagerUuid).getReputation();
            if (rep < minRep) {
                continue;
            }
            Vector3d playerPos = playerTc.getPosition();
            double dx = playerPos.x - pos.x;
            double dy = playerPos.y - pos.y;
            double dz = playerPos.z - pos.z;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= range * range && sq < closestSq) {
                closestSq = sq;
                closestPlayerPos = playerPos;
            }
        }
        if (closestPlayerPos == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= cfg.getReputationWaveChancePerCheck()) {
            return;
        }

        float waveDuration = cfg.getReputationWaveDurationSeconds();
        waveState.setWaveUntilMs(nowMs + (long) (waveDuration * 1000f));
        waveState.setCooldownUntilMs(nowMs + (long) (cfg.getReputationWaveCooldownSeconds() * 1000f));
        commandBuffer.putComponent(ref, NpcReputationWaveState.getComponentType(), waveState);
        NpcReputationWaveVisuals.playWave(ref, closestPlayerPos, waveState, store, commandBuffer);
    }

    private static boolean hasStatusWave(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ActiveAnimationComponent active = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (active == null) {
            return false;
        }
        return NpcReputationWaveVisuals.WAVE_ANIMATION_ID.equals(active.getActiveAnimations()[AnimationSlot.Status.ordinal()]);
    }
}
