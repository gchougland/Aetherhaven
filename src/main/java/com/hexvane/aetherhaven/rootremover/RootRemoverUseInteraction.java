package com.hexvane.aetherhaven.rootremover;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Right click a tree trunk to clear buried roots with one root remover from the stack. */
public final class RootRemoverUseInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<RootRemoverUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(RootRemoverUseInteraction.class, RootRemoverUseInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Clears underground trunk and root wood from a clicked tree, consuming one root remover.")
            .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i targetBlock,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.REPUTATION_UNLOCKS)) {
            return;
        }
        if (type != InteractionType.Secondary) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null
            || itemInHand == null
            || itemInHand.isEmpty()
            || !AetherhavenConstants.ITEM_ROOT_REMOVER.equals(itemInHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        boolean cleared = RootRemoverService.clearRoots(world, commandBuffer, playerRef, targetBlock);
        if (!cleared) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!removeOneFromActiveHotbar(playerRef, store, itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        context.getState().state = InteractionState.Finished;
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType interactionType,
        @Nonnull InteractionContext interactionContext,
        @Nullable ItemStack itemStack,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock
    ) {
        if (interactionType != InteractionType.Secondary) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }
        if (itemStack == null
            || itemStack.isEmpty()
            || !AetherhavenConstants.ITEM_ROOT_REMOVER.equals(itemStack.getItemId())) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }
        BlockType clicked = ChunkSectionBlockUtil.blockType(world, targetBlock.x(), targetBlock.y(), targetBlock.z());
        interactionContext.getState().state =
            RootRemoverService.isTrunkBlock(clicked) ? InteractionState.Finished : InteractionState.Failed;
    }

    private static boolean removeOneFromActiveHotbar(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ItemStack inHand
    ) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }
        byte slot = hotbar.getActiveSlot();
        if (slot < 0) {
            return false;
        }
        ItemContainer container = hotbar.getInventory();
        int q = inHand.getQuantity();
        ItemStack replacement;
        if (q <= 1) {
            replacement = ItemStack.EMPTY;
        } else {
            ItemStack dec = inHand.withQuantity(q - 1);
            replacement = dec != null ? dec : ItemStack.EMPTY;
        }
        container.replaceItemStackInSlot(slot, inHand, replacement);
        return true;
    }
}
