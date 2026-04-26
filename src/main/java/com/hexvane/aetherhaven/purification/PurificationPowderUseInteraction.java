package com.hexvane.aetherhaven.purification;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.beacons.LegacySpawnBeaconEntity;
import com.hypixel.hytale.server.spawning.beacons.SpawnBeacon;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Use on a block near a spawn beacon or spawn marker to remove that spawner, consuming one purification powder.
 */
public final class PurificationPowderUseInteraction extends SimpleInstantInteraction {
    private static final double MAX_PICK_RANGE = 5.0;
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PurificationPowderUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PurificationPowderUseInteraction.class, PurificationPowderUseInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Removes a nearby Hytale spawn beacon or spawn marker entity, consuming one powder from the hand.")
            .build();
    @Nonnull
    private final List<PurificationSpawnSupport.Target> targetScratch = new ArrayList<>();

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
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = commandBuffer.getStore().getExternalData().getWorld();
        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
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
            || !AetherhavenConstants.ITEM_PURIFICATION_POWDER.equals(itemInHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        PurificationSpawnSupport.Target best = resolveTarget(store, world, context);
        if (best == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!removeOneFromActiveHotbar(playerRef, store, itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        UUID spawnId = PurificationSpawnSupport.spawnKey(store, best.ref());
        if (spawnId != null) {
            PurificationPowderPlayerComponent ppc = store.getComponent(playerRef, PurificationPowderPlayerComponent.getComponentType());
            if (ppc != null) {
                @Nullable
                UUID previewId = ppc.getSpawnEntityIdToPreviewEntityId().remove(spawnId);
                if (previewId != null) {
                    final UUID pid = previewId;
                    world.execute(() -> {
                        Ref<EntityStore> pr = world.getEntityRef(pid);
                        if (pr != null && pr.isValid()) {
                            world.getEntityStore().getStore().removeEntity(pr, RemoveReason.REMOVE);
                        }
                    });
                }
                ppc.getPendingPreviewSpawn().remove(spawnId);
            }
        }
        commandBuffer.removeEntity(best.ref(), RemoveReason.REMOVE);
        Vector3d vfxPos = new Vector3d(
            best.position().getX(),
            best.position().getY() + 0.35,
            best.position().getZ()
        );
        ParticleUtil.spawnParticleEffect(AetherhavenConstants.PURIFICATION_DESPAWN_PARTICLE_SYSTEM_ID, vfxPos, store);
        int despawnSfx = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.PURIFICATION_DESPAWN_SOUND_EVENT_ID);
        if (despawnSfx != 0) {
            SoundUtil.playSoundEvent3d(null, despawnSfx, vfxPos, store);
        }
        context.getState().state = InteractionState.Finished;
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

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        @Nullable
        ItemStack itemInHand = playerRef == null ? null : InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (itemInHand == null
            || itemInHand.isEmpty()
            || !AetherhavenConstants.ITEM_PURIFICATION_POWDER.equals(itemInHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        World world = store.getExternalData().getWorld();
        PurificationSpawnSupport.Target target = resolveTarget(store, world, context);
        context.getState().state = target != null ? InteractionState.Finished : InteractionState.Failed;
    }

    @Nullable
    private PurificationSpawnSupport.Target resolveTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull InteractionContext context
    ) {
        @Nullable
        InteractionSyncData sync = context.getClientState();
        if (sync != null && sync.entityId > 0) {
            @Nullable
            Ref<EntityStore> targetedRef = store.getExternalData().getRefFromNetworkId(sync.entityId);
            @Nullable
            PurificationSpawnSupport.Kind kind = targetedRef == null ? null : targetKind(store, targetedRef);
            if (kind != null) {
                TransformComponent tc = store.getComponent(targetedRef, TransformComponent.getComponentType());
                if (tc != null) {
                    return new PurificationSpawnSupport.Target(targetedRef, kind, new Vector3d(tc.getPosition()));
                }
            }
            @Nullable
            PurificationSpawnSupport.Target proxied = targetedRef == null ? null : resolvePreviewProxyTarget(store, world, context, targetedRef);
            if (proxied != null) {
                return proxied;
            }
        }
        @Nullable
        BlockPosition blockPosition = sync != null ? sync.blockPosition : context.getTargetBlock();
        if (blockPosition == null) {
            return null;
        }
        Vector3d center = new Vector3d(blockPosition.x + 0.5, blockPosition.y + 0.5, blockPosition.z + 0.5);
        PurificationSpawnSupport.collectAllInRange(store, center, MAX_PICK_RANGE, targetScratch);
        return PurificationSpawnSupport.findNearest(targetScratch, center, MAX_PICK_RANGE);
    }

    @Nullable
    private PurificationSpawnSupport.Target resolvePreviewProxyTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull InteractionContext context,
        @Nonnull Ref<EntityStore> targetedRef
    ) {
        if (store.getComponent(targetedRef, PurificationPreviewEntity.getComponentType()) == null) {
            return null;
        }
        UUIDComponent targetedUuid = store.getComponent(targetedRef, UUIDComponent.getComponentType());
        if (targetedUuid == null) {
            return null;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            return null;
        }
        PurificationPowderPlayerComponent ppc = store.getComponent(playerRef, PurificationPowderPlayerComponent.getComponentType());
        if (ppc == null) {
            return null;
        }
        UUID previewUuid = targetedUuid.getUuid();
        UUID spawnUuid = null;
        for (java.util.Map.Entry<UUID, UUID> e : ppc.getSpawnEntityIdToPreviewEntityId().entrySet()) {
            if (previewUuid.equals(e.getValue())) {
                spawnUuid = e.getKey();
                break;
            }
        }
        if (spawnUuid == null) {
            return null;
        }
        Ref<EntityStore> spawnRef = world.getEntityRef(spawnUuid);
        if (spawnRef == null || !spawnRef.isValid()) {
            return null;
        }
        PurificationSpawnSupport.Kind kind = targetKind(store, spawnRef);
        if (kind == null) {
            return null;
        }
        TransformComponent spawnTc = store.getComponent(spawnRef, TransformComponent.getComponentType());
        if (spawnTc == null) {
            return null;
        }
        return new PurificationSpawnSupport.Target(spawnRef, kind, new Vector3d(spawnTc.getPosition()));
    }

    @Nullable
    private static PurificationSpawnSupport.Kind targetKind(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef) {
        if (store.getComponent(targetRef, SpawnMarkerEntity.getComponentType()) != null) {
            return PurificationSpawnSupport.Kind.SPAWN_MARKER;
        }
        if (store.getComponent(targetRef, SpawnBeacon.getComponentType()) != null) {
            return PurificationSpawnSupport.Kind.MANUAL_BEACON;
        }
        if (store.getComponent(targetRef, LegacySpawnBeaconEntity.getComponentType()) != null) {
            return PurificationSpawnSupport.Kind.LEGACY_BEACON;
        }
        return null;
    }
}
