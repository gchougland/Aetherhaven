package com.hexvane.aetherhaven.npc;

import com.hexvane.aetherhaven.marker.MarkerFacingYaw;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Reputation-greeting wave for idle town NPCs ({@link #WAVE_ANIMATION_ID} on {@link AnimationSlot#Status}). */
public final class NpcReputationWaveVisuals {
    /** Model animation set on {@link com.hexvane.aetherhaven} humanoid NPC models. */
    public static final String WAVE_ANIMATION_ID = "Wave";

    private NpcReputationWaveVisuals() {}

    public static void playWave(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Vector3d playerPos,
        @Nonnull NpcReputationWaveState waveState,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!npcRef.isValid() || store.getComponent(npcRef, NetworkId.getComponentType()) == null) {
            return;
        }
        waveState.setWaveTarget(playerPos);
        faceToward(npcRef, playerPos, store, commandBuffer);
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null) {
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Status, WAVE_ANIMATION_ID, commandBuffer);
        } else {
            NpcAnimationPlayback.play(npcRef, AnimationSlot.Status, WAVE_ANIMATION_ID, commandBuffer);
        }
    }

    public static void maintainWaveFacing(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull NpcReputationWaveState waveState,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!waveState.hasWaveTarget()) {
            return;
        }
        faceToward(npcRef, waveState.waveTarget(), store, commandBuffer);
    }

    public static void stopWave(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull NpcReputationWaveState waveState,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!npcRef.isValid()) {
            return;
        }
        waveState.clearWaveTarget();
        NPCEntity npc = commandBuffer.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null) {
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Status, null, commandBuffer);
        } else {
            NpcAnimationPlayback.stop(npcRef, AnimationSlot.Status, commandBuffer);
        }
    }

    private static void faceToward(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Vector3d targetPos,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d from = tc.getPosition();
        float bodyYaw = MarkerFacingYaw.yawFacingToward(from, targetPos);
        tc.getRotation().setYaw(bodyYaw);
        commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);

        float eyeY = (float) from.y + 1.6f;
        ModelComponent modelComponent = store.getComponent(npcRef, ModelComponent.getComponentType());
        if (modelComponent != null && modelComponent.getModel() != null) {
            eyeY = (float) from.y + modelComponent.getModel().getEyeHeight(npcRef, store);
        }
        Vector3d relative = new Vector3d(targetPos.x - from.x, targetPos.y + 0.9 - eyeY, targetPos.z - from.z);
        if (relative.lengthSquared() < 1.0e-8) {
            return;
        }
        HeadRotation head = store.getComponent(npcRef, HeadRotation.getComponentType());
        if (head == null) {
            head = new HeadRotation();
        }
        head.teleportRotation(Rotation3f.lookAt(relative));
        commandBuffer.putComponent(npcRef, HeadRotation.getComponentType(), head);
    }
}
