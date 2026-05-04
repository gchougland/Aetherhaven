package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Runs at the end of the potion-style {@code Charging} chain (secondary use): consumes one town charge, applies heal
 * tier, mirrors stack durability, plays consumed SFX. Charging + item {@code Consume} animation is handled by JSON.
 */
public final class GaiasDraughtConsumeInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<GaiasDraughtConsumeInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(GaiasDraughtConsumeInteraction.class, GaiasDraughtConsumeInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("After drink animation: consume one Gaia's Draught charge from town data and apply heal.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (type != InteractionType.Secondary) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || !playerRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ItemStack inHand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (inHand == null
            || inHand.isEmpty()
            || !AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(inHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        if (town == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!GaiaDraughtService.tryConsumeOneCharge(town, uc.getUuid())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        tm.updateTown(town);
        GaiaDraughtState st = town.getOrCreateGaiaDraughtState(uc.getUuid());
        GaiaDraughtService.syncDraughtStacksInInventory(playerRef, store, st);
        GaiaDraughtAmmoHudSupport.syncHeldDraughtAmmoHud(commandBuffer, playerRef, town, uc.getUuid());
        String effectId = GaiaDraughtState.instantHealEffectId(st.getHealTier());
        EntityEffect asset = EntityEffect.getAssetMap().getAsset(effectId);
        if (asset != null) {
            EffectControllerComponent ecc = commandBuffer.getComponent(playerRef, EffectControllerComponent.getComponentType());
            if (ecc != null) {
                ecc.addEffect(playerRef, asset, commandBuffer);
            }
        }
        playConsumedSound(playerRef, store);
    }

    private static void playConsumedSound(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        int idx = SoundEvent.getAssetMap().getIndex("SFX_Potion_Drink_Success");
        if (idx != 0) {
            SoundUtil.playSoundEvent2d(playerRef, idx, SoundCategory.UI, store);
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {}

    @Nonnull
    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.SimpleInteraction();
    }
}
