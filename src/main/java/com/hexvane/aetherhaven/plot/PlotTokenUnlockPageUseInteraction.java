package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.UiSoundEffects;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import javax.annotation.Nonnull;

/** Reads a plot blueprint page and permanently unlocks its construction variant for the player. */
public final class PlotTokenUnlockPageUseInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PlotTokenUnlockPageUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PlotTokenUnlockPageUseInteraction.class, PlotTokenUnlockPageUseInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Unlocks a plot variant for crafting at the plot crafting bench, consuming the blueprint page.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || (type != InteractionType.Primary && type != InteractionType.Secondary)) {
            if (commandBuffer != null) {
                context.getState().state = InteractionState.Failed;
            }
            return;
        }

        Ref<EntityStore> ref = context.getEntity();
        PlayerRef pr = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack inHand = context.getHeldItem();
        if (inHand == null
            || inHand.isEmpty()
            || !AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(inHand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        String constructionId = PlotTokenUnlockPageMetadata.readConstructionId(inHand);
        if (constructionId == null || constructionId.isBlank()) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.unlockInvalid"),
                NotificationStyle.Danger
            );
            context.getState().state = InteractionState.Failed;
            return;
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        ConstructionDefinition def = plugin != null ? plugin.getConstructionCatalog().get(constructionId) : null;
        if (!PlotTokenUnlockService.requiresUnlock(def)) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.unlockInvalid"),
                NotificationStyle.Danger
            );
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (PlotTokenUnlockService.isUnlocked(ref, commandBuffer.getStore(), constructionId)) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.unlockAlreadyKnown")
                    .param("building", Message.raw(PlotTokenUnlockService.displayNameFor(constructionId))),
                NotificationStyle.Warning
            );
            context.getState().state = InteractionState.Failed;
            return;
        }

        PlotTokenUnlockService.unlock(ref, commandBuffer, constructionId);
        UUIDComponent playerUuid = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin != null && playerUuid != null) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(
                commandBuffer.getStore().getExternalData().getWorld(),
                plugin
            );
            TownRecord town = tm.findTownForPlayerInWorld(playerUuid.getUuid());
            if (town != null && QuestProgressionService.onBlueprintLearned(plugin, town, constructionId)) {
                tm.updateTown(town);
            }
        }
        UiSoundEffects.play2dUi(ref, commandBuffer.getStore(), AetherhavenConstants.SFX_WORKBENCH_UPGRADE_COMPLETE);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_plot_crafting.aetherhaven.ui.plotCrafting.unlockSuccess")
                .param("building", Message.raw(PlotTokenUnlockService.displayNameFor(constructionId))),
            NotificationStyle.Success
        );
    }
}
