package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
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

/** Consumes a villager cosmetic unlock item for the player's active town. */
public final class VillagerCosmeticUnlockUseInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<VillagerCosmeticUnlockUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                VillagerCosmeticUnlockUseInteraction.class,
                VillagerCosmeticUnlockUseInteraction::new,
                SimpleInstantInteraction.CODEC
            )
            .documentation("Unlocks a villager cosmetic for the player's town, consuming the item on success.")
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
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || plugin.getVillagerCosmeticCatalog().byUnlockItemId(inHand.getItemId()) == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        VillagerCosmeticUnlockService.Result result =
            VillagerCosmeticUnlockService.tryUnlock(ref, commandBuffer.getStore(), pr, inHand.getItemId());
        if (result != VillagerCosmeticUnlockService.Result.UNLOCKED) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        context.getState().state = InteractionState.Finished;
    }
}
