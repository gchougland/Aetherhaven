package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.npc.NpcSupportUtil;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Keeps guild hall display adventurers at their spawn anchor facing and out of wander states. */
public final class GuildHallDisplayAnchorSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, SteeringSystem.class),
        new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class)
    );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            GuildHallDisplayAnchor.getComponentType(),
            TownsfolkCharacterBinding.getComponentType(),
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType()
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
        TownsfolkCharacterBinding binding = archetypeChunk.getComponent(index, TownsfolkCharacterBinding.getComponentType());
        GuildHallDisplayAnchor anchor = archetypeChunk.getComponent(index, GuildHallDisplayAnchor.getComponentType());
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        TransformComponent tc = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (binding == null || anchor == null || npc == null || tc == null || npc.getRole() == null) {
            return;
        }
        if (!TownsfolkAssignmentKinds.isGuildHallAdventurer(binding.getAssignmentKind())) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        boolean anchorChanged = false;
        Vector3d spawnMarker = anchor.getSpawnMarkerPosition();
        boolean columnLoaded = VillagerBlockUtil.isGuildHallSpawnColumnLoaded(store.getExternalData().getWorld(), spawnMarker);
        boolean hasSeat = columnLoaded && GuildHallAdventurerChairMount.hasSeatNearSpawn(store, anchor);
        boolean blockMounted = GuildHallAdventurerChairMount.isBlockMounted(store, commandBuffer, ref);

        if (anchor.isChairMountFinished() && hasSeat && !blockMounted && !anchor.isSitFallbackApplied()) {
            anchor.resetChairMountForRetry();
            anchorChanged = true;
        }

        if (!anchor.isChairMountFinished() && !blockMounted) {
            if (!hasSeat) {
                if (columnLoaded) {
                    anchor.markChairMountFinished();
                    anchorChanged = true;
                }
            } else if (GuildHallAdventurerChairMount.tryMountChairBelowSpawn(ref, store, commandBuffer, anchor)) {
                anchor.markChairMountFinished();
                anchorChanged = true;
            } else {
                anchor.incrementChairMountAttempts();
                if (anchor.getChairMountAttempts() >= GuildHallDisplayAnchor.MAX_CHAIR_MOUNT_ATTEMPTS) {
                    GuildHallAdventurerChairMount.applySeatPoseFallback(ref, store, commandBuffer, anchor);
                    anchor.setSitFallbackApplied(true);
                    anchor.markChairMountFinished();
                }
                anchorChanged = true;
            }
        }

        blockMounted = GuildHallAdventurerChairMount.isBlockMounted(store, commandBuffer, ref);
        boolean seated = blockMounted || (anchor.isSitFallbackApplied() && hasSeat);

        if (seated) {
            GuildHallAdventurerChairMount.ensureSitVisuals(ref, store, anchor);
        }
        if (seated || (anchor.isChairMountFinished() && !hasSeat)) {
            anchorChanged |= applyDisplayStateIfNeeded(npc, ref, commandBuffer, anchor);
        }

        if (anchorChanged) {
            commandBuffer.putComponent(ref, GuildHallDisplayAnchor.getComponentType(), anchor);
        }

        boolean inDialogue = isInInteractionDialogue(npc);
        if (seated) {
            if (blockMounted) {
                syncSeatedHeadToMountedBody(ref, store, commandBuffer, inDialogue);
            } else {
                lockSeatedBodyFacing(ref, store, commandBuffer, anchor, inDialogue);
            }
            zeroVelocity(ref, commandBuffer);
            return;
        }
        if (!inDialogue
            && anchor.isChairMountFinished()
            && !anchor.isSitFallbackApplied()
            && !hasSeat) {
            lockDisplayTransform(ref, store, commandBuffer, anchor);
        }
    }

    /** {@link StateSupport#getStateName()} is {@code State.subState}, not the bare state id. */
    private static boolean isInInteractionDialogue(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return false;
        }
        Ref<EntityStore> npcRef = npc.getReference();
        if (npcRef == null) {
            return false;
        }
        StateSupport stateSupport = NpcSupportUtil.stateSupport(npcRef.getStore(), npcRef);
        if (stateSupport == null) {
            return false;
        }
        int interactionState = stateSupport.getStateHelper().getStateIndex("$Interaction");
        return interactionState >= 0 && stateSupport.inState(interactionState);
    }

    /** @return true when anchor transient flags changed and should be persisted on the buffer */
    private static boolean applyDisplayStateIfNeeded(
        @Nonnull NPCEntity npc,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull GuildHallDisplayAnchor anchor
    ) {
        if (isInInteractionDialogue(npc)) {
            if (anchor.isDisplayStateApplied()) {
                anchor.setDisplayStateApplied(false);
                return true;
            }
            return false;
        }
        StateSupport stateSupport = NpcSupportUtil.stateSupport(ref.getStore(), ref);
        if (stateSupport == null) {
            return false;
        }
        int displayState = stateSupport.getStateHelper().getStateIndex(AetherhavenConstants.NPC_STATE_STAND_STILL);
        if (displayState >= 0 && stateSupport.inState(displayState)) {
            if (!anchor.isDisplayStateApplied()) {
                anchor.setDisplayStateApplied(true);
                return true;
            }
            return false;
        }
        if (anchor.isDisplayStateApplied()) {
            return false;
        }
        NpcSupportUtil.setState(ref, AetherhavenConstants.NPC_STATE_STAND_STILL, null, commandBuffer);
        anchor.setDisplayStateApplied(true);
        return true;
    }

    /** Runs after {@link SteeringSystem} so separation/collision steering cannot drift display NPCs. */
    private static void lockDisplayTransform(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull GuildHallDisplayAnchor anchor
    ) {
        World world = store.getExternalData().getWorld();
        Vector3d target = VillagerBlockUtil.snapNpcFeetToStand(world, anchor.getSpawnMarkerPosition());
        TransformComponentUtil.replacePreservingChunk(
            ref,
            commandBuffer,
            target,
            new Rotation3f(0.0F, anchor.getYawRadians(), 0.0F)
        );
        syncHeadToBodyFacing(ref, store, commandBuffer, anchor.getYawRadians());
    }

    /** Block mount already applies seat facing; only align head when not in dialogue. */
    private static void syncSeatedHeadToMountedBody(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        boolean inDialogue
    ) {
        if (inDialogue) {
            return;
        }
        TransformComponent tc = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            tc = store.getComponent(ref, TransformComponent.getComponentType());
        }
        if (tc != null) {
            syncHeadToBodyFacing(ref, store, commandBuffer, tc.getRotation().yaw());
        }
    }

    /**
     * Keeps sit-fallback facing (no block mount). During dialogue only the body yaw is locked.
     */
    private static void lockSeatedBodyFacing(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull GuildHallDisplayAnchor anchor,
        boolean inDialogue
    ) {
        World world = store.getExternalData().getWorld();
        float bodyYaw =
            VillagerBlockUtil.resolveSeatedDisplayYawRadians(
                world,
                anchor.getSpawnMarkerPosition(),
                anchor.getYawRadians()
            );
        Rotation3f bodyRot = new Rotation3f(0.0F, bodyYaw, 0.0F);
        TransformComponent tc = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            tc = store.getComponent(ref, TransformComponent.getComponentType());
        }
        if (tc != null) {
            TransformComponentUtil.replacePreservingChunk(ref, commandBuffer, tc.getPosition(), bodyRot);
        }
        if (!inDialogue) {
            syncHeadToBodyFacing(ref, store, commandBuffer, bodyYaw);
        }
    }

    private static void syncHeadToBodyFacing(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        float yawRadians
    ) {
        HeadRotation head = commandBuffer.getComponent(ref, HeadRotation.getComponentType());
        if (head == null) {
            head = store.getComponent(ref, HeadRotation.getComponentType());
        }
        if (head == null) {
            return;
        }
        head.teleportRotation(new Rotation3f(0.0F, yawRadians, 0.0F));
        commandBuffer.putComponent(ref, HeadRotation.getComponentType(), head);
    }

    private static void zeroVelocity(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Velocity velocity = commandBuffer.getComponent(ref, Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), velocity);
        }
    }
}
