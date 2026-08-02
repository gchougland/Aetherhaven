package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.ui.UiSoundEffects;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import javax.annotation.Nonnull;

/** Consumes a plot blueprint page and grants one unlock point for the plot crafting bench. */
public final class PlotTokenUnlockPageUseInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PlotTokenUnlockPageUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PlotTokenUnlockPageUseInteraction.class, PlotTokenUnlockPageUseInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Grants one plot unlock point, consuming the blueprint page.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || (type != InteractionType.Primary && type != InteractionType.Secondary)) {
            if (commandBuffer != null) {
                context.getState().state = InteractionState.Failed;
            }
            return;
        }

        Ref<EntityStore> ref = context.getEntity();
        PlayerRef pr = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack inHand = context.getHeldItem();
        if (inHand == null
            || inHand.isEmpty()
            || !AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(inHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        PlotTokenUnlockService.addUnlockPoint(ref, commandBuffer);
        UiSoundEffects.play2dUi(ref, commandBuffer.getStore(), AetherhavenConstants.SFX_WORKBENCH_UPGRADE_COMPLETE);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.blueprintPointGained"),
            NotificationStyle.Success
        );
    }
}
