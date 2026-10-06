package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import javax.annotation.Nonnull;

/** The charged drink animation ends here. Effects never apply without consuming exactly one persisted charge. */
public final class AlchemyFlaskConsumeInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<AlchemyFlaskConsumeInteraction> CODEC = BuilderCodec
        .builder(AlchemyFlaskConsumeInteraction.class, AlchemyFlaskConsumeInteraction::new, SimpleInstantInteraction.CODEC).build();
    @Override public WaitForDataFrom getWaitForDataFrom() { return WaitForDataFrom.Server; }
    @Override public boolean needsRemoteSync() { return true; }
    @Override protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        var buffer = context.getCommandBuffer(); var ref = context.getEntity();
        if (type != InteractionType.Secondary || buffer == null || ref == null || !ref.isValid()) { context.getState().state=InteractionState.Failed; return; }
        var hotbar = buffer.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if(hotbar==null||!AlchemyFlaskService.drink(ref,buffer,hotbar.getInventory(),hotbar.getActiveSlot()))context.getState().state=InteractionState.Failed;
    }
    @Override protected void simulateFirstRun(@Nonnull InteractionType type,@Nonnull InteractionContext context,@Nonnull CooldownHandler cooldownHandler) {}
    @Override protected com.hypixel.hytale.protocol.Interaction generatePacket() { return new com.hypixel.hytale.protocol.SimpleInteraction(); }
}
