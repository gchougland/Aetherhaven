package com.hexvane.aetherhaven.floatinggift;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Shared pop transition from FLOATING → FALLING while Pop plays ({@link FloatingGiftDamagePopSystem} / projectile overlap). */
public final class FloatingGiftTriggers {
    private FloatingGiftTriggers() {}

    public static void beginPop(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull FloatingGiftComponent gift,
        @Nonnull TransformComponent transform,
        @Nonnull Velocity velocity
    ) {
        if (gift.getState() != FloatingGiftState.FLOATING) {
            return;
        }
        gift.setState(FloatingGiftState.FALLING);
        gift.resetPopSeconds();
        gift.resetPopHoldClipApplied();
        gift.resetFloatClipRetriggerAccum();
        gift.resetAmbientCueAccum();
        Vector3d popPos = transform.getPosition().clone();
        FloatingGiftSounds.playPop3d(popPos, commandBuffer.getStore());
        FloatingGiftAnimationHelper.stopAnimation(commandBuffer.getStore(), ref, AnimationSlot.Action);
        FloatingGiftAnimationHelper.playAnimation(commandBuffer.getStore(), ref, AnimationSlot.Action, FloatingGiftSpawnService.POP_ANIMATION);
        velocity.setZero();
        commandBuffer.putComponent(ref, FloatingGiftComponent.getComponentType(), gift);
        commandBuffer.putComponent(ref, Velocity.getComponentType(), velocity);
    }
}
