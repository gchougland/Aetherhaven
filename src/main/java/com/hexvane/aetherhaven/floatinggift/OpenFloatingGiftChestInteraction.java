package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenContainerInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Opens a floating gift reward chest. Uses section-based block-entity lookup (U6); column
 * {@code BlockComponentSection} lookups return null and silently no-op.
 */
public final class OpenFloatingGiftChestInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<OpenFloatingGiftChestInteraction> CODEC =
        BuilderCodec
            .builder(OpenFloatingGiftChestInteraction.class, OpenFloatingGiftChestInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Opens a floating gift reward chest; removes the block when emptied and closed.")
            .build();

    private static final String OPEN_WINDOW = OpenContainerInteraction.OPEN_WINDOW;
    private static final String CLOSE_WINDOW = OpenContainerInteraction.CLOSE_WINDOW;

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i pos,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }

        PlayerRef playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return;
        }

        BlockType atPos = ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
        if (!FloatingGiftChestUtil.isGiftChestBlockType(atPos)) {
            return;
        }

        ItemContainerBlock itemContainerBlock = FloatingGiftChestUtil.ensureItemContainer(world, pos.x, pos.y, pos.z);
        if (itemContainerBlock == null) {
            return;
        }

        var chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return;
        }

        var chunkComponentStore = chunkStore.getStore();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(chunkComponentStore, sectionRef, pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return;
        }

        BlockSection section = chunkComponentStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            return;
        }

        int blockId = section.get(pos.x, pos.y, pos.z);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            return;
        }

        int rotationIndex = section.getRotationIndex(pos.x, pos.y, pos.z);

        ContainerBlockWindow window =
            new ContainerBlockWindow(pos.x, pos.y, pos.z, rotationIndex, blockType, itemContainerBlock.getItemContainer());
        Map<UUID, ContainerBlockWindow> windows = itemContainerBlock.getWindows();

        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        assert uuidComponent != null;
        UUID uuid = uuidComponent.getUuid();

        if (windows.putIfAbsent(uuid, window) == null) {
            if (playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
                window.registerCloseEvent(
                    _ ->
                        onWindowClose(
                            world,
                            ref,
                            uuid,
                            pos,
                            blockType,
                            window,
                            windows,
                            itemContainerBlock,
                            commandBuffer
                        )
                );

                if (windows.size() == 1) {
                    BlockOperations.setBlockInteractionState(
                        chunkStore, sectionRef, pos.x, pos.y, pos.z, blockType, OPEN_WINDOW, false
                    );
                }

                BlockType interactionState = blockType.getBlockForState(OPEN_WINDOW);
                if (interactionState == null) {
                    return;
                }

                int soundEventIndex = interactionState.getInteractionSoundEventIndex();
                if (soundEventIndex == SoundEvent.EMPTY_ID) {
                    return;
                }

                Vector3d soundPos = new Vector3d();
                blockType.getBlockCenter(rotationIndex, soundPos);
                soundPos.add(pos.x, pos.y, pos.z);
                SoundUtil.playSoundEvent3d(ref, soundEventIndex, soundPos, commandBuffer);
            } else {
                windows.remove(uuid, window);
            }
        }
    }

    private static void onWindowClose(
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UUID uuid,
        @Nonnull Vector3i pos,
        @Nonnull BlockType blockType,
        @Nonnull ContainerBlockWindow window,
        @Nonnull Map<UUID, ContainerBlockWindow> windows,
        @Nonnull ItemContainerBlock itemContainerBlock,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        windows.remove(uuid, window);

        var chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return;
        }

        var chunkComponentStore = chunkStore.getStore();
        BlockSection section = chunkComponentStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            return;
        }

        BlockType currentBlockType = BlockType.getAssetMap().getAsset(section.get(pos.x, pos.y, pos.z));
        if (currentBlockType == null) {
            return;
        }

        if (
            windows.isEmpty()
                && FloatingGiftChestUtil.isGiftChestBlockType(currentBlockType)
                && FloatingGiftChestUtil.isContainerEmpty(itemContainerBlock.getItemContainer())
        ) {
            FloatingGiftChestUtil.removeEmptyChest(world, pos);
            return;
        }

        if (windows.isEmpty()) {
            String defBase = Objects.requireNonNullElse(blockType.getDefaultStateKey(), blockType.getId());
            String currentBase = Objects.requireNonNullElse(currentBlockType.getDefaultStateKey(), currentBlockType.getId());
            if (Objects.equals(currentBase, defBase)) {
                BlockOperations.setBlockInteractionState(
                    chunkStore, sectionRef, pos.x, pos.y, pos.z, currentBlockType, CLOSE_WINDOW, false
                );
            }
        }

        BlockType interactionState = currentBlockType.getBlockForState(CLOSE_WINDOW);
        if (interactionState != null) {
            int soundEventIndex = interactionState.getInteractionSoundEventIndex();
            if (soundEventIndex != SoundEvent.EMPTY_ID) {
                int rotationIndex = section.getRotationIndex(pos.x, pos.y, pos.z);
                Vector3d soundPos = new Vector3d();
                blockType.getBlockCenter(rotationIndex, soundPos);
                soundPos.add(pos.x, pos.y, pos.z);
                SoundUtil.playSoundEvent3d(ref, soundEventIndex, soundPos, commandBuffer);
            }
        }
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock
    ) {}
}
