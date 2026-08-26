package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Opens the town planning desk with sorted misc-tab recipes. */
public final class OpenTownPlanningBenchInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenTownPlanningBenchInteraction> CODEC =
        BuilderCodec
            .builder(
                OpenTownPlanningBenchInteraction.class,
                OpenTownPlanningBenchInteraction::new,
                SimpleBlockInteraction.CODEC
            )
            .documentation("Opens the town planning desk crafting window.")
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
        final var ref = context.getEntity();
        final var store = ref.getStore();

        final var playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }

        final var craftingManagerComponent = commandBuffer.getComponent(ref, CraftingManager.getComponentType());
        if (craftingManagerComponent == null || craftingManagerComponent.hasBenchSet()) {
            return;
        }

        var chunkStore = world.getChunkStore();
        var chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }
        var blockEntityRef = ChunkSectionBlockUtil.blockEntityRefAt(
            world,
            targetBlock.x,
            targetBlock.y,
            targetBlock.z
        );
        if (blockEntityRef == null || !blockEntityRef.isValid()) {
            return;
        }
        var benchBlock = chunkStore.getStore().getComponent(blockEntityRef, BenchBlock.getComponentType());
        if (benchBlock == null) {
            return;
        }

        var blockType = ChunkSectionBlockUtil.blockType(world, targetBlock.x, targetBlock.y, targetBlock.z);
        if (blockType == null) {
            return;
        }
        var rotationIndex = 0;
        var sectionRef = chunkStore.getChunkSectionReferenceAtBlock(targetBlock.x, targetBlock.y, targetBlock.z);
        if (sectionRef != null && sectionRef.isValid()) {
            var section = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
            if (section != null) {
                rotationIndex = section.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
            }
        }

        final var benchWindow = new TownPlanningCraftingWindow(
            targetBlock.x,
            targetBlock.y,
            targetBlock.z,
            rotationIndex,
            blockType,
            benchBlock
        );

        final var uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        final var uuid = uuidComponent.getUuid();

        if (benchBlock.getWindows().putIfAbsent(uuid, benchWindow) == null) {
            benchWindow.registerCloseEvent(event -> benchBlock.getWindows().remove(uuid, benchWindow));
        }

        playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, benchWindow);
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
