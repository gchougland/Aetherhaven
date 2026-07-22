package com.hexvane.aetherhaven.heartberry;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Use on a befriendable villager to gain one heart of friendship, consuming one Heartberry. */
public final class HeartberryUseInteraction extends SimpleInstantInteraction {
    private static final float TARGET_RANGE = 5.0F;

    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<HeartberryUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(HeartberryUseInteraction.class, HeartberryUseInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Grants +10 reputation with a targeted befriendable villager, consuming one Heartberry.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.REPUTATION_UNLOCKS)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || !isAllowedType(type)) {
            if (commandBuffer != null) {
                context.getState().state = InteractionState.Failed;
            }
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        ItemStack itemInHand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (itemInHand == null
            || itemInHand.isEmpty()
            || !AetherhavenConstants.ITEM_HEARTBERRY.equals(itemInHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> targetRef = resolveTargetRef(playerRef, store, context);
        if (targetRef == null || !canApply(playerRef, store, targetRef, itemInHand, plugin)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (HeartberryService.tryApply(playerRef, store, targetRef, plugin) == null) {
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
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || !isAllowedType(type)) {
            if (commandBuffer != null) {
                context.getState().state = InteractionState.Failed;
            }
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        @Nullable
        ItemStack itemInHand = playerRef == null ? null : InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (itemInHand == null
            || itemInHand.isEmpty()
            || !AetherhavenConstants.ITEM_HEARTBERRY.equals(itemInHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        @Nullable
        Ref<EntityStore> targetRef = playerRef == null ? null : resolveTargetRef(playerRef, store, context);
        context.getState().state =
            plugin != null
                && targetRef != null
                && canApply(playerRef, store, targetRef, itemInHand, plugin)
                && HeartberryService.wouldGainReputation(playerRef, store, targetRef, plugin)
                ? InteractionState.Finished
                : InteractionState.Failed;
    }

    private static boolean isAllowedType(@Nonnull InteractionType type) {
        return type == InteractionType.Use || type == InteractionType.Secondary;
    }

    @Nullable
    private static Ref<EntityStore> resolveTargetRef(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionContext context
    ) {
        @Nullable
        Ref<EntityStore> targeted = context.getTargetEntity();
        if (targeted != null && targeted.isValid()) {
            return targeted;
        }
        @Nullable
        InteractionSyncData sync = context.getClientState();
        if (sync != null && sync.entityId > 0) {
            @Nullable
            Ref<EntityStore> fromSync = store.getExternalData().getRefFromNetworkId(sync.entityId);
            if (fromSync != null && fromSync.isValid()) {
                return fromSync;
            }
        }
        return TargetUtil.getTargetEntity(playerRef, TARGET_RANGE, store);
    }

    private static boolean canApply(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull ItemStack itemInHand,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (!InteractionValidation.canPlayerInteractWithEntity(playerRef, store, itemInHand, targetRef)) {
            return false;
        }
        return HeartberryService.wouldGainReputation(playerRef, store, targetRef, plugin);
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
