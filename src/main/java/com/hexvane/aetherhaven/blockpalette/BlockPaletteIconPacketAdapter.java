package com.hexvane.aetherhaven.blockpalette;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InventorySection;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/** Outbound virtual icons + inbound id remap for block palette stacks. */
public final class BlockPaletteIconPacketAdapter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final BlockPaletteVirtualItemRegistry virtualItems;
    private final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    private final ConcurrentHashMap<UUID, PlayerRef> knownPlayerRefs = new ConcurrentHashMap<>();

    @Nullable
    private PacketFilter outboundFilter;
    @Nullable
    private PacketFilter inboundFilter;

    public BlockPaletteIconPacketAdapter(@Nonnull BlockPaletteVirtualItemRegistry virtualItems) {
        this.virtualItems = virtualItems;
    }

    public void register() {
        outboundFilter = PacketAdapters.registerOutbound((PlayerPacketFilter) this::onOutboundPacket);
        inboundFilter = PacketAdapters.registerInbound((PlayerPacketFilter) this::onInboundPacket);
    }

    public void deregister() {
        if (outboundFilter != null) {
            try {
                PacketAdapters.deregisterOutbound(outboundFilter);
            } catch (Exception ignored) {
            }
            outboundFilter = null;
        }
        if (inboundFilter != null) {
            try {
                PacketAdapters.deregisterInbound(inboundFilter);
            } catch (Exception ignored) {
            }
            inboundFilter = null;
        }
    }

    public void onPlayerLeave(@Nonnull UUID playerUuid) {
        knownPlayerRefs.remove(playerUuid);
        virtualItems.onPlayerLeave(playerUuid);
    }

    private boolean onInboundPacket(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        try {
            if (packet instanceof MouseInteraction mouse) {
                mouse.itemInHandId = translateVirtualToBase(mouse.itemInHandId);
            } else if (packet instanceof SyncInteractionChains sync) {
                if (sync.updates != null) {
                    for (SyncInteractionChain chain : sync.updates) {
                        translateChain(chain);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Block palette icon inbound error for %s: %s", playerRef.getUuid(), e.getMessage());
        }
        return false;
    }

    private static void translateChain(@Nonnull SyncInteractionChain chain) {
        chain.itemInHandId = translateVirtualToBase(chain.itemInHandId);
        chain.utilityItemId = translateVirtualToBase(chain.utilityItemId);
        chain.toolsItemId = translateVirtualToBase(chain.toolsItemId);
        if (chain.newForks != null) {
            for (SyncInteractionChain fork : chain.newForks) {
                if (fork != null) {
                    translateChain(fork);
                }
            }
        }
    }

    @Nullable
    private static String translateVirtualToBase(@Nullable String itemId) {
        if (itemId == null || !BlockPaletteVirtualItemRegistry.isVirtualId(itemId)) {
            return itemId;
        }
        return BlockPaletteVirtualItemRegistry.getBaseItemId(itemId);
    }

    private boolean onOutboundPacket(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (Boolean.TRUE.equals(isProcessing.get())) {
            return false;
        }
        knownPlayerRefs.put(playerRef.getUuid(), playerRef);
        if (!(packet instanceof UpdatePlayerInventory inventory)) {
            return false;
        }
        isProcessing.set(true);
        try {
            Map<String, ItemBase> newVirtual = new LinkedHashMap<>();
            processSection(inventory.hotbar, newVirtual);
            processSection(inventory.utility, newVirtual);
            processSection(inventory.tools, newVirtual);
            processSection(inventory.armor, newVirtual);
            processSection(inventory.storage, newVirtual);
            processSection(inventory.backpack, newVirtual);
            sendVirtualItemDefinitions(playerRef, newVirtual);
            return false;
        } finally {
            isProcessing.set(false);
        }
    }

    private void processSection(@Nullable InventorySection section, @Nonnull Map<String, ItemBase> newVirtual) {
        if (section == null || section.items == null || section.items.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, ItemWithAllMetadata> entry : section.items.entrySet()) {
            ItemWithAllMetadata item = entry.getValue();
            if (item == null || item.itemId == null || item.itemId.isEmpty()) {
                continue;
            }
            ItemWithAllMetadata copy = new ItemWithAllMetadata(item);
            if (applyVirtualId(copy, newVirtual)) {
                entry.setValue(copy);
            }
        }
    }

    private boolean applyVirtualId(@Nonnull ItemWithAllMetadata item, @Nonnull Map<String, ItemBase> newVirtual) {
        String itemId = item.itemId;
        if (BlockPaletteVirtualItemRegistry.isVirtualId(itemId)) {
            String paletteId = BlockPaletteVirtualItemRegistry.getPaletteIdFromVirtualId(itemId);
            if (paletteId == null) {
                return false;
            }
            ItemBase base = virtualItems.getOrCreateVirtualItemBase(itemId, paletteId);
            if (base != null) {
                newVirtual.put(itemId, base);
            }
            return false;
        }
        if (!BlockPaletteConstants.ITEM_ID.equals(itemId)) {
            return false;
        }
        String paletteId = readPaletteIdFromMetadata(item.metadata);
        if (paletteId == null || paletteId.isBlank()) {
            return false;
        }
        String virtualId = BlockPaletteVirtualItemRegistry.generateVirtualId(paletteId);
        ItemBase base = virtualItems.getOrCreateVirtualItemBase(virtualId, paletteId);
        if (base == null) {
            return false;
        }
        item.itemId = virtualId;
        newVirtual.put(virtualId, base);
        return true;
    }

    @Nullable
    private static String readPaletteIdFromMetadata(@Nullable String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            BsonDocument doc = BsonDocument.parse(metadataJson);
            BsonValue root = doc.get(BlockPaletteItemMetadata.BSON_KEY);
            if (root == null || !root.isDocument()) {
                return null;
            }
            BsonValue id = root.asDocument().get(BlockPaletteItemMetadata.FIELD_PALETTE_ID);
            if (id == null || !id.isString()) {
                return null;
            }
            String s = id.asString().getValue();
            return s != null && !s.isBlank() ? s.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void sendVirtualItemDefinitions(@Nonnull PlayerRef playerRef, @Nonnull Map<String, ItemBase> newVirtual) {
        if (newVirtual.isEmpty()) {
            return;
        }
        Set<String> unsent = virtualItems.markAndGetUnsent(playerRef.getUuid(), newVirtual.keySet());
        if (unsent.isEmpty()) {
            return;
        }
        try {
            Map<String, ItemBase> toSend = new LinkedHashMap<>();
            for (String id : unsent) {
                ItemBase base = newVirtual.get(id);
                if (base != null) {
                    toSend.put(id, base);
                }
            }
            if (toSend.isEmpty()) {
                return;
            }
            UpdateItems packet = new UpdateItems();
            packet.type = UpdateType.AddOrUpdate;
            packet.items = toSend;
            packet.removedItems = new String[0];
            packet.updateModels = false;
            packet.updateIcons = true;
            playerRef.getPacketHandler().writeNoCache(packet);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to send block palette UpdateItems for %s: %s", playerRef.getUuid(), e.getMessage());
        }
    }
}
