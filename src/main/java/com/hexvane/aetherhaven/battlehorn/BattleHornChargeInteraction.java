package com.hexvane.aetherhaven.battlehorn;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Charging hold for the battle horn. Client effects only play for the user; this broadcasts the world sound to nearby
 * players once the charge actually starts.
 */
public final class BattleHornChargeInteraction extends ChargingInteraction {
    private static final float CHARGING_HELD = -1.0F;
    private static final MetaKey<Boolean> WORLD_SOUND_PLAYED =
        Interaction.META_REGISTRY.registerMetaObject(i -> null);

    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<BattleHornChargeInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                BattleHornChargeInteraction.class,
                BattleHornChargeInteraction::new,
                ChargingInteraction.CODEC
            )
            .documentation("Battle horn secondary hold; broadcasts horn audio to nearby players.")
            .build();

    @Override
    protected void tick0(
        boolean firstRun,
        float time,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        maybeBroadcastWorldSound(time, context);
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }

    private void maybeBroadcastWorldSound(float time, @Nonnull InteractionContext context) {
        if (context.getClientState().chargeValue != CHARGING_HELD) {
            return;
        }
        if (time < getEffects().getStartDelay()) {
            return;
        }
        if (context.getInstanceStore().getIfPresentMetaObject(WORLD_SOUND_PLAYED) != null) {
            return;
        }
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.REPUTATION_UNLOCKS)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> playerRef = context.getEntity();
        if (commandBuffer == null || playerRef == null) {
            return;
        }
        context.getInstanceStore().putMetaObject(WORLD_SOUND_PLAYED, Boolean.TRUE);
        BattleHornSounds.playWorldHoldAt(playerRef, commandBuffer);
    }
}
