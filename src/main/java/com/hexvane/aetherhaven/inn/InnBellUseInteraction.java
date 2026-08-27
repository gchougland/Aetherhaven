package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class InnBellUseInteraction extends SimpleBlockInteraction {
    private static final String MSG_PREFIX =
        "aetherhaven_ui_journal_items_tail.aetherhaven.ui.innBell.";

    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<InnBellUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(InnBellUseInteraction.class, InnBellUseInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Inn bell: call visitors back to guest spawn points.")
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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.COMMERCE)) {
            return;
        }
        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerEntityRef = context.getEntity();
        if (playerEntityRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!isInnBellBlock(world, targetBlock)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord blockTown = tm.findTownContainingBlock(world.getName(), targetBlock.x, targetBlock.z);
        if (blockTown == null) {
            sendFeedback(playerEntityRef, commandBuffer, "notAllowed");
            context.getState().state = InteractionState.Failed;
            return;
        }

        PlotInstance plot = blockTown.findCompletePlotContaining(targetBlock.x, targetBlock.y, targetBlock.z);
        if (plot == null
            || !plugin.getConstructionCatalog()
                .matchesGameplayConstruction(plot.getConstructionId(), AetherhavenConstants.CONSTRUCTION_PLOT_INN)) {
            sendFeedback(playerEntityRef, commandBuffer, "notAllowed");
            context.getState().state = InteractionState.Failed;
            return;
        }

        UUIDComponent uc = commandBuffer.getComponent(playerEntityRef, UUIDComponent.getComponentType());
        if (uc == null || !blockTown.hasMemberOrOwner(uc.getUuid())) {
            sendFeedback(playerEntityRef, commandBuffer, "notAllowed");
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (!blockTown.isInnActive()) {
            sendFeedback(playerEntityRef, commandBuffer, "notOpen");
            context.getState().state = InteractionState.Failed;
            return;
        }

        final UUID townId = blockTown.getTownId();
        final PlotInstance bellInnPlot = plot;
        final Vector3i bellBlock = new Vector3i(targetBlock);
        world.execute(
            () -> {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                if (entityStore == null) {
                    return;
                }
                TownRecord town = tm.getTown(townId);
                if (town == null || !town.isInnActive()) {
                    return;
                }
                InnBellService.RingOutcome outcome =
                    InnBellService.ring(world, plugin, town, tm, entityStore, bellInnPlot);
                InnBellService.playRingSound(entityStore, bellBlock);
                sendFeedback(playerEntityRef, entityStore, outcome.messageKeySuffix());
            }
        );
        context.getState().state = InteractionState.Finished;
    }

    private static void sendFeedback(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull String keySuffix
    ) {
        sendFeedback(playerEntityRef, commandBuffer.getStore(), keySuffix);
    }

    private static void sendFeedback(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String keySuffix
    ) {
        PlayerRef pr = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation(MSG_PREFIX + keySuffix));
        }
    }

    private static boolean isInnBellBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return false;
        }
        BlockType type = ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
        return type != null && AetherhavenConstants.INN_BELL_BLOCK_TYPE_ID.equals(type.getId());
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
