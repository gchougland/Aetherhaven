package com.hexvane.aetherhaven.blockpalette;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Consumes a block palette item to unlock it for the player's town. */
public final class BlockPaletteUnlockUseInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<BlockPaletteUnlockUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                BlockPaletteUnlockUseInteraction.class,
                BlockPaletteUnlockUseInteraction::new,
                SimpleInstantInteraction.CODEC
            )
            .documentation("Unlocks a block palette for the player's town, consuming the item on success.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
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
        if (inHand == null || inHand.isEmpty()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        String paletteId = BlockPaletteItemMetadata.readPaletteId(inHand);
        if (paletteId == null || paletteId.isBlank()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        BlockPaletteUnlockService.Result result =
            BlockPaletteUnlockService.tryUnlock(ref, commandBuffer.getStore(), pr, paletteId);
        if (result != BlockPaletteUnlockService.Result.UNLOCKED
            && result != BlockPaletteUnlockService.Result.REFUNDED) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        context.getState().state = InteractionState.Finished;
    }
}
