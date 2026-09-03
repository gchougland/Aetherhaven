package com.hexvane.aetherhaven.net;

import com.hexvane.aetherhaven.item.VirtualItemIdTranslator;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSelectionBoundsAdapter;
import com.hexvane.aetherhaven.rts.RtsClientMovementPacketAdapter;
import com.hexvane.aetherhaven.rts.RtsCommandHotbarSlotInboundAdapter;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolArgUpdate;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolColorAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolEntityAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolExtrudeAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolGMaskPresetLoadResponse;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolGeneralAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolLineAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolOnUseInteraction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPasteClipboard;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolRandomizeClipboard;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolResetClipboardRotation;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolRotateClipboard;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionToolAskForClipboard;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionTransform;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionUpdate;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityCollision;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityLight;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityPickupEnabled;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityScale;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityTransform;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityType;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetNPCDebug;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetTransformationModeState;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolStackArea;
import com.hypixel.hytale.protocol.packets.buildertools.PrefabSetAnchor;
import com.hypixel.hytale.protocol.packets.buildertools.PrefabUnselectPrefab;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SubPacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Every inbound packet Aetherhaven needs to see, in one place.
 *
 * <p>Each of these used to be its own {@code PacketAdapters.registerInbound} filter, which stopped working in
 * singleplayer when Update 6 moved to the Quiche transport (see {@link InboundPacketInterceptor}). They are collected
 * here because interception replaces the handler for a packet id, so two separate hooks on the same id would silently
 * cancel each other out.
 */
public final class AetherhavenInboundPackets implements SubPacketHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Every builder tool packet the server accepts. The plot creator needs all of them so that builder tool input
     * cannot reach vanilla while a player is editing plot bounds.
     */
    private static final int[] BUILDER_TOOL_PACKET_IDS = {
        BuilderToolEntityAction.PACKET_ID,
        BuilderToolSetEntityTransform.PACKET_ID,
        BuilderToolSetEntityScale.PACKET_ID,
        BuilderToolSetTransformationModeState.PACKET_ID,
        PrefabUnselectPrefab.PACKET_ID,
        PrefabSetAnchor.PACKET_ID,
        BuilderToolSetEntityPickupEnabled.PACKET_ID,
        BuilderToolSetEntityLight.PACKET_ID,
        BuilderToolSetNPCDebug.PACKET_ID,
        BuilderToolSetEntityCollision.PACKET_ID,
        BuilderToolSetEntityType.PACKET_ID,
        BuilderToolArgUpdate.PACKET_ID,
        BuilderToolSelectionUpdate.PACKET_ID,
        BuilderToolExtrudeAction.PACKET_ID,
        BuilderToolRotateClipboard.PACKET_ID,
        BuilderToolResetClipboardRotation.PACKET_ID,
        BuilderToolRandomizeClipboard.PACKET_ID,
        BuilderToolPasteClipboard.PACKET_ID,
        BuilderToolOnUseInteraction.PACKET_ID,
        BuilderToolSelectionToolAskForClipboard.PACKET_ID,
        BuilderToolLineAction.PACKET_ID,
        BuilderToolSelectionTransform.PACKET_ID,
        BuilderToolStackArea.PACKET_ID,
        BuilderToolColorAction.PACKET_ID,
        BuilderToolGeneralAction.PACKET_ID,
        BuilderToolGMaskPresetLoadResponse.PACKET_ID,
    };

    private final IPacketHandler packetHandler;

    public AetherhavenInboundPackets(@Nonnull IPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    /** Installs the hooks for every connection. Must run before players connect. */
    public static void register() {
        ServerManager serverManager = ServerManager.get();
        if (serverManager == null) {
            LOGGER.atSevere().log("ServerManager unavailable, virtual items and RTS input will not work");
            return;
        }
        serverManager.registerSubPacketHandlers(AetherhavenInboundPackets::new);
        LOGGER.atInfo().log("Inbound packet hooks installed on the game packet handler");
    }

    @Override
    public void registerHandlers() {
        InboundPacketInterceptor.intercept(packetHandler, SyncInteractionChains.PACKET_ID, this::onSyncInteractionChains);
        InboundPacketInterceptor.intercept(packetHandler, MouseInteraction.PACKET_ID, this::onMouseInteraction);
        InboundPacketInterceptor.intercept(packetHandler, SetActiveSlot.PACKET_ID, this::onSetActiveSlot);
        InboundPacketInterceptor.intercept(packetHandler, ClientMovement.PACKET_ID, this::onClientMovement);
        for (int packetId : BUILDER_TOOL_PACKET_IDS) {
            InboundPacketInterceptor.intercept(packetHandler, packetId, this::onBuilderToolPacket);
        }
    }

    private boolean onSyncInteractionChains(@Nonnull PlayerRef playerRef, @Nonnull ToServerPacket packet) {
        SyncInteractionChains sync = (SyncInteractionChains) packet;
        if (sync.updates != null) {
            for (SyncInteractionChain chain : sync.updates) {
                translateChain(chain);
            }
        }
        return RtsCommandHotbarSlotInboundAdapter.handleInboundSyncInteractionChains(playerRef, sync);
    }

    private boolean onMouseInteraction(@Nonnull PlayerRef playerRef, @Nonnull ToServerPacket packet) {
        MouseInteraction mouse = (MouseInteraction) packet;
        mouse.itemInHandId = VirtualItemIdTranslator.toBaseItemId(mouse.itemInHandId);
        return RtsCommandHotbarSlotInboundAdapter.handleInboundMouseInteraction(playerRef, mouse);
    }

    private boolean onSetActiveSlot(@Nonnull PlayerRef playerRef, @Nonnull ToServerPacket packet) {
        return RtsCommandHotbarSlotInboundAdapter.handleInboundSetActiveSlot(playerRef, (SetActiveSlot) packet);
    }

    private boolean onClientMovement(@Nonnull PlayerRef playerRef, @Nonnull ToServerPacket packet) {
        RtsClientMovementPacketAdapter.observe(playerRef, (ClientMovement) packet);
        return false;
    }

    private boolean onBuilderToolPacket(@Nonnull PlayerRef playerRef, @Nonnull ToServerPacket packet) {
        return PlotCreatorSelectionBoundsAdapter.handleInbound(playerRef, packet);
    }

    /**
     * Rewrites the client's virtual item ids back to the real ones. Without this the server sees an item id it does not
     * have in hand and cancels the chain, which is what stopped props, plot tokens, palettes and jewelry from being
     * scrolled off the hotbar or used.
     */
    private static void translateChain(SyncInteractionChain chain) {
        if (chain == null) {
            return;
        }
        chain.itemInHandId = VirtualItemIdTranslator.toBaseItemId(chain.itemInHandId);
        chain.utilityItemId = VirtualItemIdTranslator.toBaseItemId(chain.utilityItemId);
        chain.toolsItemId = VirtualItemIdTranslator.toBaseItemId(chain.toolsItemId);

        if (chain.newForks != null) {
            for (SyncInteractionChain fork : chain.newForks) {
                translateChain(fork);
            }
        }
    }
}
