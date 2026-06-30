package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.ShopSpotBuyPage;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotUseInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<ShopSpotUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(ShopSpotUseInteraction.class, ShopSpotUseInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Shop spot: F opens buy UI, or lists on player spots when holding an item.")
            .build();

    private static final String MSG = "aetherhaven_shop.aetherhaven.shop";

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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.COMMERCE)) {
            return;
        }
        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ShopSpotRecord record = ShopSpotBlockInteractSupport.resolveRecord(world, plugin, targetBlock);
        if (record == null || ShopSpotBlockInteractSupport.isConfiguringPendingSpot(playerRef, commandBuffer.getStore(), record)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUIDComponent uc = commandBuffer.getComponent(playerRef, UUIDComponent.getComponentType());
        if (record.isPlayerControlled()
            && record.hasStock()
            && record.getSellerUuid() != null
            && uc != null
            && record.getSellerUuid().equals(uc.getUuid())) {
            ShopSpotPurchaseService.handleListOrRemove(playerRef, commandBuffer, context, targetBlock, false);
            return;
        }
        if (record.isPlayerControlled() && !record.hasStock()) {
            if (itemInHand != null && !itemInHand.isEmpty()) {
                ShopSpotPurchaseService.handleListOrRemove(playerRef, commandBuffer, context, targetBlock, false);
                return;
            }
            PlayerRef pr = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                TownRecord town =
                    AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(record.getTownId());
                if (town != null && uc != null && town.playerCanUseShopSpots(uc.getUuid())) {
                    pr.sendMessage(Message.translation(MSG + ".holdItemToList"));
                }
            }
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (ShopSpotBuyPage.tryOpen(playerRef, commandBuffer, world, plugin, targetBlock)) {
            context.getState().state = InteractionState.Finished;
        } else {
            context.getState().state = InteractionState.Failed;
        }
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType interactionType,
        @Nonnull InteractionContext interactionContext,
        @Nullable ItemStack itemStack,
        @Nonnull World world,
        @Nonnull Vector3i vector3i
    ) {}
}
