package com.hexvane.aetherhaven.scaffold;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.BlockRotation;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Rotation;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.interaction.BlockPlaceUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Same as vanilla {@code PlaceBlockInteraction}, but when placing wood scaffolding and a scaffold exists in the column,
 * snaps {@link BlockPlaceUtils#placeBlock} via {@link ScaffoldPlacementResolver} when aiming at scaffold;
 * otherwise uses the client placement like vanilla.
 *
 * <p>{@link com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.PlaceBlockInteraction#tick0} is
 * {@code final}; this mirrors its logic.
 */
public final class ScaffoldStackPlaceInteraction extends SimpleInteraction {
    public static final BuilderCodec<ScaffoldStackPlaceInteraction> CODEC =
        BuilderCodec.builder(ScaffoldStackPlaceInteraction.class, ScaffoldStackPlaceInteraction::new, SimpleInteraction.CODEC)
            .documentation("Places a block; scaffold stacks extend toward the top column cell.")
            .<String>append(
                new KeyedCodec<>("BlockTypeToPlace", Codec.STRING),
                (i, k) -> i.blockTypeKey = k,
                i -> i.blockTypeKey
            )
            .addValidatorLate(() -> BlockType.VALIDATOR_CACHE.getValidator().late())
            .add()
            .<Boolean>append(
                new KeyedCodec<>("RemoveItemInHand", Codec.BOOLEAN),
                (i, v) -> i.removeItemInHand = v,
                i -> i.removeItemInHand
            )
            .add()
            .<Boolean>appendInherited(
                new KeyedCodec<>("AllowDragPlacement", Codec.BOOLEAN),
                (i, v) -> i.allowDragPlacement = v,
                i -> i.allowDragPlacement,
                (i, p) -> i.allowDragPlacement = p.allowDragPlacement
            )
            .add()
            .build();

    @Nullable
    private String blockTypeKey;

    private boolean removeItemInHand = true;
    private boolean allowDragPlacement = true;

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    protected void tick0(
        boolean firstRun,
        float time,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        InteractionSyncData clientState = context.getClientState();

        assert clientState != null;

        if (!firstRun) {
            context.getState().state = clientState.state;
        } else {
            if (type != InteractionType.Secondary) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            Ref<EntityStore> ref = context.getEntity();
            CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

            assert commandBuffer != null;

            BlockPosition blockPosition = clientState.blockPosition;
            BlockRotation blockRotation = clientState.blockRotation;
            if (blockPosition != null && blockRotation != null) {
                World world = commandBuffer.getExternalData().getWorld();
                Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

                ItemStack heldItemStack = context.getHeldItem();
                if (heldItemStack == null) {
                    context.getState().state = InteractionState.Failed;
                    return;
                }

                ItemContainer heldItemContainer = context.getHeldItemContainer();
                if (heldItemContainer == null) {
                    context.getState().state = InteractionState.Failed;
                    return;
                }

                String interactionBlockTypeKey = this.blockTypeKey != null ? this.blockTypeKey : heldItemStack.getBlockKey();
                if (interactionBlockTypeKey == null) {
                    return;
                }

                TransformComponent transformForBox = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
                BoundingBox bboxComp = commandBuffer.getComponent(ref, BoundingBox.getComponentType());
                Velocity velocityComp = commandBuffer.getComponent(ref, Velocity.getComponentType());
                Box playerWorldBox = null;
                if (bboxComp != null && transformForBox != null) {
                    Vector3d pos = transformForBox.getPosition();
                    playerWorldBox = bboxComp.getBoundingBox().getBox(pos.getX(), pos.getY(), pos.getZ());
                }

                Vector3i clientPlacement = new Vector3i(blockPosition.x, blockPosition.y, blockPosition.z);
                Vector3i targetBlockPosition =
                    ScaffoldPlacementResolver.resolve(
                        world,
                        clientState,
                        clientPlacement,
                        interactionBlockTypeKey,
                        playerWorldBox,
                        velocityComp
                    );

                BlockType interactionBlockType = BlockType.getAssetMap().getAsset(interactionBlockTypeKey);

                long chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlockPosition.x, targetBlockPosition.z);
                Ref<ChunkStore> chunkReference = chunkStore.getExternalData().getChunkReference(chunkIndex);
                if (chunkReference == null || !chunkReference.isValid()) {
                    context.getState().state = InteractionState.Failed;
                    return;
                }

                TransformComponent transformComponent = transformForBox;
                Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
                if (transformComponent != null && playerComponent != null && playerComponent.getGameMode() != GameMode.Creative) {
                    Vector3d position = transformComponent.getPosition();
                    Vector3i reachProbe =
                        clientPlacement.equals(targetBlockPosition) ? targetBlockPosition : clientPlacement;
                    Vector3d blockCenter =
                        new Vector3d(reachProbe.x + 0.5, reachProbe.y + 0.5, reachProbe.z + 0.5);
                    if (position.distanceSquaredTo(blockCenter) > 49.0) {
                        context.getState().state = InteractionState.Failed;
                        return;
                    }
                }

                Inventory inventory = null;
                if (EntityUtils.getEntity(ref, commandBuffer) instanceof LivingEntity livingEntity) {
                    inventory = livingEntity.getInventory();
                }

                int clientPlacedBlockId = clientState.placedBlockId;
                String clientPlacedBlockTypeKey =
                    clientPlacedBlockId == -1 ? null : BlockType.getAssetMap().getAsset(clientPlacedBlockId).getId();
                if (clientPlacedBlockTypeKey != null
                    && !clientPlacedBlockTypeKey.equals(this.blockTypeKey)
                    && (interactionBlockType == null || !BlockPlaceUtils.canPlaceBlock(interactionBlockType, clientPlacedBlockTypeKey))) {
                    clientPlacedBlockTypeKey = null;
                }

                if (targetBlockPosition.y < 0 || targetBlockPosition.y >= 320) {
                    context.getState().state = InteractionState.Failed;
                    return;
                }

                boolean useUpNormal =
                    ScaffoldPlacementResolver.shouldUseUpPlacementNormal(
                        world,
                        clientState,
                        clientPlacement,
                        targetBlockPosition,
                        interactionBlockTypeKey,
                        velocityComp);
                Vector3i placementNormal =
                    useUpNormal
                        ? BlockFace.UP.getDirection()
                        : BlockFace.fromProtocolFace(context.getClientState().blockFace).getDirection();

                ScaffoldDebug.place(
                    "resolved=%s,%s,%s client=%s,%s,%s useUpNormal=%s placementNormal=%s,%s,%s",
                    targetBlockPosition.getX(),
                    targetBlockPosition.getY(),
                    targetBlockPosition.getZ(),
                    clientPlacement.getX(),
                    clientPlacement.getY(),
                    clientPlacement.getZ(),
                    useUpNormal,
                    placementNormal.getX(),
                    placementNormal.getY(),
                    placementNormal.getZ()
                );

                BlockPlaceUtils.placeBlock(
                    ref,
                    heldItemStack,
                    clientPlacedBlockTypeKey != null ? clientPlacedBlockTypeKey : this.blockTypeKey,
                    heldItemContainer,
                    placementNormal,
                    targetBlockPosition,
                    blockRotation,
                    inventory,
                    context.getHeldItemSlot(),
                    this.removeItemInHand,
                    chunkReference,
                    chunkStore,
                    commandBuffer,
                    false
                );

                if (AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(interactionBlockTypeKey)) {
                    world.performBlockUpdate(targetBlockPosition.x, targetBlockPosition.y, targetBlockPosition.z, false);
                    ScaffoldDebug.physics(
                        "after place performBlockUpdate at %s,%s,%s (vanilla markDeco unchanged)",
                        targetBlockPosition.x,
                        targetBlockPosition.y,
                        targetBlockPosition.z
                    );
                }
                boolean isAdventure = playerComponent == null || playerComponent.getGameMode() == GameMode.Adventure;
                if (isAdventure && heldItemStack.getQuantity() == 1 && this.removeItemInHand) {
                    context.setHeldItem(null);
                }

                BlockChunk blockChunk = chunkStore.getComponent(chunkReference, BlockChunk.getComponentType());
                BlockSection section = blockChunk.getSectionAtBlockY(targetBlockPosition.y);
                RotationTuple resultRotation = section.getRotation(targetBlockPosition.x, targetBlockPosition.y, targetBlockPosition.z);
                context.getState().blockPosition =
                    new BlockPosition(targetBlockPosition.x, targetBlockPosition.y, targetBlockPosition.z);
                context.getState().placedBlockId = section.get(targetBlockPosition.x, targetBlockPosition.y, targetBlockPosition.z);
                context.getState().blockRotation =
                    new BlockRotation(
                        resultRotation.yaw().toPacket(),
                        resultRotation.pitch().toPacket(),
                        resultRotation.roll().toPacket()
                    );
            }

            super.tick0(firstRun, time, type, context, cooldownHandler);
        }
    }

    @Override
    protected void simulateTick0(
        boolean firstRun,
        float time,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (firstRun && type != InteractionType.Secondary) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        super.simulateTick0(firstRun, time, type, context, cooldownHandler);
        if (!Interaction.failed(context.getState().state)) {
            InteractionSyncData clientState = context.getClientState();

            assert clientState != null;

            if (!firstRun) {
                context.getState().state = context.getClientState().state;
            } else {
                clientState.blockRotation = new BlockRotation(Rotation.None, Rotation.None, Rotation.None);
            }
        }
    }

    @Nonnull
    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.PlaceBlockInteraction();
    }

    @Override
    protected void configurePacket(com.hypixel.hytale.protocol.Interaction packet) {
        super.configurePacket(packet);
        com.hypixel.hytale.protocol.PlaceBlockInteraction p = (com.hypixel.hytale.protocol.PlaceBlockInteraction) packet;
        p.blockId = this.blockTypeKey == null ? -1 : BlockType.getAssetMap().getIndex(this.blockTypeKey);
        p.removeItemInHand = this.removeItemInHand;
        p.allowDragPlacement = this.allowDragPlacement;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }
}
