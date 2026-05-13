package com.hexvane.aetherhaven.scaffold;



import com.hexvane.aetherhaven.AetherhavenConstants;

import com.hypixel.hytale.codec.Codec;

import com.hypixel.hytale.codec.KeyedCodec;

import com.hypixel.hytale.codec.builder.BuilderCodec;

import com.hypixel.hytale.component.CommandBuffer;

import com.hypixel.hytale.component.Ref;

import com.hypixel.hytale.component.Store;

import com.hypixel.hytale.math.util.ChunkUtil;

import com.hypixel.hytale.math.vector.Vector3d;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.protocol.BlockMaterial;

import com.hypixel.hytale.protocol.BlockPosition;

import com.hypixel.hytale.protocol.BlockRotation;

import com.hypixel.hytale.protocol.GameMode;

import com.hypixel.hytale.protocol.Position;

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

import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

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

 * Use (F): scaffold-aware placement — tower from column sides, horizontal branch from top face. Right-click placement stays

 * vanilla {@code Block_Secondary} on the item.

 */

public final class ScaffoldUseExtendInteraction extends SimpleInteraction {

    /**
     * When the server places at a resolved cell (e.g. column top) but the client predicted {@link PlaceBlockInteraction} at
     * {@code clientPlacement}, the client can keep a ghost scaffold in the predicted air cell. That cell stays correct on
     * the server (usually still air), but viewers can desync; breaking the ghost then does not drop. Vanilla
     * {@link BlockPlaceUtils#placeBlock} only {@code invalidateBlock}s the original coordinates when the final position lands
     * in a <em>different chunk</em>, not when it is only a few blocks away in the same chunk — so we explicitly refresh the
     * predicted cell after a redirect.
     */
    private static void resyncClientPredictedCellIfRedirected(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> chunkStore,
        @Nonnull Vector3i clientPlacement,
        @Nonnull Vector3i targetBlockPosition
    ) {
        if (clientPlacement.equals(targetBlockPosition)) {
            return;
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(clientPlacement.x, clientPlacement.z);
        Ref<ChunkStore> clientChunkRef = chunkStore.getExternalData().getChunkReference(chunkIndex);
        if (clientChunkRef == null || !clientChunkRef.isValid()) {
            return;
        }
        BlockChunk clientBlockChunk = chunkStore.getComponent(clientChunkRef, BlockChunk.getComponentType());
        if (clientBlockChunk == null) {
            return;
        }
        BlockSection clientSection = clientBlockChunk.getSectionAtBlockY(clientPlacement.y);
        if (clientSection != null) {
            clientSection.invalidateBlock(clientPlacement.x, clientPlacement.y, clientPlacement.z);
        }
        world.performBlockUpdate(clientPlacement.x, clientPlacement.y, clientPlacement.z, false);
        ScaffoldDebug.resolve(
            "[UseExtend] resync client-predicted cell %s,%s,%s (placed at %s,%s,%s)",
            clientPlacement.getX(),
            clientPlacement.getY(),
            clientPlacement.getZ(),
            targetBlockPosition.getX(),
            targetBlockPosition.getY(),
            targetBlockPosition.getZ()
        );
    }

    /**
     * Client {@link com.hypixel.hytale.protocol.PlaceBlockInteraction} sync often reports {@link InteractionType#Secondary}
     * for block placement, not {@link InteractionType#Use} — vanilla {@code PlaceBlockInteraction} does not filter by type.
     */
    private static boolean isExtendTriggerType(@Nonnull InteractionType type) {
        return type == InteractionType.Use || type == InteractionType.Secondary;
    }

    private static int floorBlockCoord(double v) {
        int i = (int) v;
        if (v < 0.0 && v != i) {
            return i - 1;
        }
        return i;
    }

    private static boolean cellIsReplaceableForInference(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }
        BlockType t = world.getBlockType(x, y, z);
        if (t == null) {
            return false;
        }
        if ("Empty".equals(t.getId())) {
            return true;
        }
        return t.getMaterial() == BlockMaterial.Empty;
    }

    /**
     * Client sometimes omits {@link InteractionSyncData#blockPosition} / {@link InteractionSyncData#blockFace} when standing
     * on a block and looking straight down. Infer the air cell the player is standing in (or above a ray-hit scaffold) so
     * {@link ScaffoldPlacementResolver#resolveUseExtend} can run; set protocol face to {@code Down} when {@code None} so
     * the resolver’s Down+scaffold-below → Up remap applies for top-face horizontal branch.
     */
    @Nullable
    private static BlockPosition inferUseExtendBlockPositionWhenClientOmitsSync(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull InteractionSyncData clientState
    ) {
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        int feetX = Integer.MIN_VALUE;
        int feetZ = Integer.MIN_VALUE;
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            feetX = floorBlockCoord(pos.x);
            feetZ = floorBlockCoord(pos.z);
        }
        Position ray = clientState.raycastHit;
        if (ray != null) {
            int rx = floorBlockCoord(ray.x);
            int ry = floorBlockCoord(ray.y);
            int rz = floorBlockCoord(ray.z);
            BlockType hitType = world.getBlockType(rx, ry, rz);
            if (hitType != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(hitType.getId())) {
                int ax = rx;
                int ay = ry + 1;
                int az = rz;
                if (cellIsReplaceableForInference(world, ax, ay, az)
                    && feetX != Integer.MIN_VALUE
                    && rx == feetX
                    && rz == feetZ) {
                    return new BlockPosition(ax, ay, az);
                }
            }
            if (cellIsReplaceableForInference(world, rx, ry, rz)) {
                BlockType below = world.getBlockType(rx, ry - 1, rz);
                if (below != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(below.getId())) {
                    if (feetX == Integer.MIN_VALUE || (rx == feetX && rz == feetZ)) {
                        return new BlockPosition(rx, ry, rz);
                    }
                }
            }
        }
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            int fx = floorBlockCoord(pos.x);
            int fy = floorBlockCoord(pos.y);
            int fz = floorBlockCoord(pos.z);
            if (cellIsReplaceableForInference(world, fx, fy, fz)) {
                BlockType belowFeet = world.getBlockType(fx, fy - 1, fz);
                if (belowFeet != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(belowFeet.getId())) {
                    return new BlockPosition(fx, fy, fz);
                }
            }
        }
        return null;
    }

    private static void applyUseExtendBlockSyncInference(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull InteractionSyncData clientState
    ) {
        if (clientState.blockPosition != null) {
            return;
        }
        BlockPosition inferred = inferUseExtendBlockPositionWhenClientOmitsSync(world, commandBuffer, ref, clientState);
        if (inferred != null) {
            clientState.blockPosition = inferred;
            if (clientState.blockFace == com.hypixel.hytale.protocol.BlockFace.None) {
                clientState.blockFace = com.hypixel.hytale.protocol.BlockFace.Down;
            }
            ScaffoldDebug.resolve(
                "[UseExtend] inferred place sync pos=%s,%s,%s face=%s (client omitted blockPosition; ray/feet on scaffold)",
                inferred.x,
                inferred.y,
                inferred.z,
                clientState.blockFace
            );
        }
    }

    public static final BuilderCodec<ScaffoldUseExtendInteraction> CODEC =

        BuilderCodec.builder(ScaffoldUseExtendInteraction.class, ScaffoldUseExtendInteraction::new, SimpleInteraction.CODEC)

            .documentation("Places scaffold with extend rules when bound to Use (F).")

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

            if (!isExtendTriggerType(type)) {
                ScaffoldDebug.resolve("[UseExtend] ignored: interactionType=%s (expected Use or Secondary for place sync)", type);
                context.getState().state = InteractionState.Failed;

                return;

            }



            Ref<EntityStore> ref = context.getEntity();

            CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();



            assert commandBuffer != null;

            World world = commandBuffer.getExternalData().getWorld();
            applyUseExtendBlockSyncInference(world, commandBuffer, ref, clientState);

            BlockPosition blockPosition = clientState.blockPosition;

            if (blockPosition != null && clientState.blockRotation == null) {
                clientState.blockRotation = new BlockRotation(Rotation.None, Rotation.None, Rotation.None);
            }

            BlockRotation blockRotation = clientState.blockRotation;

            if (blockPosition != null && blockRotation != null) {

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
                    ScaffoldDebug.resolve("[UseExtend] failed: held item has no block key");
                    context.getState().state = InteractionState.Failed;
                    return;

                }



                TransformComponent transformForBox = commandBuffer.getComponent(ref, TransformComponent.getComponentType());

                Float bodyYawRadians = null;
                if (transformForBox != null) {
                    float y = transformForBox.getRotation().getYaw();
                    if (!Float.isNaN(y)) {
                        bodyYawRadians = y;
                    }
                }

                Vector3i clientPlacement = new Vector3i(blockPosition.x, blockPosition.y, blockPosition.z);

                Vector3i targetBlockPosition =

                    ScaffoldPlacementResolver.resolveUseExtend(

                        world, clientState, clientPlacement, interactionBlockTypeKey, bodyYawRadians);



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

                    // Match vanilla 7m cap (see PlaceBlockInteraction). When we redirect placement (e.g. tower above the
                    // column), measuring to the resolved cell punishes tall stacks — the player is still next to the
                    // column at the client-aimed cell, so use that for reach when it differs from the server target.
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

                String clientPlacedBlockTypeKey = null;

                if (clientPlacedBlockId != -1) {

                    BlockType clientPlacedType = BlockType.getAssetMap().getAsset(clientPlacedBlockId);

                    clientPlacedBlockTypeKey = clientPlacedType != null ? clientPlacedType.getId() : null;

                }

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

                    ScaffoldPlacementResolver.shouldUseUpPlacementNormalUseExtend(

                        world, clientState, clientPlacement, targetBlockPosition, interactionBlockTypeKey);

                Vector3i placementNormal =

                    useUpNormal

                        ? BlockFace.UP.getDirection()

                        : BlockFace.fromProtocolFace(context.getClientState().blockFace).getDirection();



                ScaffoldDebug.place(

                    "[UseExtend] resolved=%s,%s,%s client=%s,%s,%s useUpNormal=%s placementNormal=%s,%s,%s",

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

                resyncClientPredictedCellIfRedirected(world, chunkStore, clientPlacement, targetBlockPosition);

                if (AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(interactionBlockTypeKey)) {

                    world.performBlockUpdate(targetBlockPosition.x, targetBlockPosition.y, targetBlockPosition.z, false);

                    ScaffoldDebug.physics(

                        "[UseExtend] performBlockUpdate at %s,%s,%s",

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

            } else {
                ScaffoldDebug.resolve(
                    "[UseExtend] skipped: missing block sync pos=%s rot=%s face=%s",
                    blockPosition == null ? "null" : "ok",
                    blockRotation == null ? "null" : "ok",
                    clientState.blockFace
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

        if (firstRun && !isExtendTriggerType(type)) {

            context.getState().state = InteractionState.Failed;

            return;

        }

        InteractionSyncData clientState = context.getClientState();

        assert clientState != null;

        if (firstRun) {

            CommandBuffer<EntityStore> cb = context.getCommandBuffer();

            if (cb != null) {

                World w = cb.getExternalData().getWorld();

                applyUseExtendBlockSyncInference(w, cb, context.getEntity(), clientState);

            }

        }

        super.simulateTick0(firstRun, time, type, context, cooldownHandler);

        if (!Interaction.failed(context.getState().state)) {

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

