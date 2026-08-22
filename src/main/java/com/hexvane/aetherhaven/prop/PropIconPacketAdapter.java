package com.hexvane.aetherhaven.prop;

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
import com.hypixel.hytale.protocol.packets.window.OpenWindow;
import com.hypixel.hytale.protocol.packets.window.UpdateWindow;
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

/**
 * Outbound: swaps generic {@link PropItemMetadata#PROP_ITEM_ID} stacks for per-prop virtual ids + icons.
 * Inbound: maps those virtual ids back to the real item id so use / hotbar / placement keep working
 * (same pattern as plot-token icons).
 */
public final class PropIconPacketAdapter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PropVirtualItemRegistry virtualItems;
    private final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    private final ConcurrentHashMap<UUID, PlayerRef> knownPlayerRefs = new ConcurrentHashMap<>();

    @Nullable
    private PacketFilter outboundFilter;
    @Nullable
    private PacketFilter inboundFilter;

    public PropIconPacketAdapter(@Nonnull PropVirtualItemRegistry virtualItems) {
        this.virtualItems = virtualItems;
    }

    public void register() {
        outboundFilter = PacketAdapters.registerOutbound((PlayerPacketFilter) this::onOutboundPacket);
        inboundFilter = PacketAdapters.registerInbound((PlayerPacketFilter) this::onInboundPacket);
        LOGGER.atInfo().log("Prop icons: packet adapter registered (outbound icons + inbound id remap)");
    }

    public void deregister() {
        if (outboundFilter != null) {
            try {
                PacketAdapters.deregisterOutbound(outboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister prop icon outbound filter: %s", e.getMessage());
            }
            outboundFilter = null;
        }
        if (inboundFilter != null) {
            try {
                PacketAdapters.deregisterInbound(inboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister prop icon inbound filter: %s", e.getMessage());
            }
            inboundFilter = null;
        }
    }

    public void onPlayerLeave(@Nonnull UUID playerUuid) {
        knownPlayerRefs.remove(playerUuid);
        virtualItems.onPlayerLeave(playerUuid);
    }

    public void onPropIconRegistered(@Nonnull String propId) {
        String virtualId = PropVirtualItemRegistry.generateVirtualId(propId.trim());
        virtualItems.invalidateProp(propId);
        virtualItems.clearSentVirtualIdForAllPlayers(virtualId);
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
            LOGGER.atWarning().log("Prop icon inbound packet filter error for %s: %s", playerRef.getUuid(), e.getMessage());
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
        if (itemId == null || !PropVirtualItemRegistry.isVirtualId(itemId)) {
            return itemId;
        }
        return PropVirtualItemRegistry.getBaseItemId(itemId);
    }

    private boolean onOutboundPacket(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (Boolean.TRUE.equals(isProcessing.get())) {
            return false;
        }
        knownPlayerRefs.put(playerRef.getUuid(), playerRef);
        isProcessing.set(true);
        try {
            if (packet instanceof UpdatePlayerInventory inventory) {
                Map<String, ItemBase> newVirtual = new LinkedHashMap<>();
                processSection(inventory.hotbar, newVirtual);
                processSection(inventory.utility, newVirtual);
                processSection(inventory.tools, newVirtual);
                processSection(inventory.armor, newVirtual);
                processSection(inventory.storage, newVirtual);
                processSection(inventory.backpack, newVirtual);
                sendVirtualItemDefinitions(playerRef, newVirtual);
            } else if (packet instanceof OpenWindow open) {
                processWindowInventory(playerRef, open.inventory);
            } else if (packet instanceof UpdateWindow update) {
                processWindowInventory(playerRef, update.inventory);
            }
            return false;
        } finally {
            isProcessing.set(false);
        }
    }

    private void processWindowInventory(@Nonnull PlayerRef playerRef, @Nullable InventorySection section) {
        if (section == null) {
            return;
        }
        Map<String, ItemBase> newVirtual = new LinkedHashMap<>();
        processSection(section, newVirtual);
        sendVirtualItemDefinitions(playerRef, newVirtual);
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
            if (applyVirtualPropId(copy, newVirtual)) {
                entry.setValue(copy);
            }
        }
    }

    private boolean applyVirtualPropId(@Nonnull ItemWithAllMetadata item, @Nonnull Map<String, ItemBase> newVirtual) {
        String itemId = item.itemId;
        if (PropVirtualItemRegistry.isVirtualId(itemId)) {
            String propId = PropVirtualItemRegistry.getPropIdFromVirtualId(itemId);
            if (propId == null) {
                return false;
            }
            ItemBase base = virtualItems.getOrCreateVirtualItemBase(itemId, propId);
            if (base != null) {
                newVirtual.put(itemId, base);
            }
            return false;
        }
        if (!PropItemMetadata.PROP_ITEM_ID.equals(itemId)) {
            return false;
        }
        String propId = readPropIdFromMetadata(item.metadata);
        if (propId == null || propId.isBlank()) {
            return false;
        }
        String virtualId = PropVirtualItemRegistry.generateVirtualId(propId);
        ItemBase base = virtualItems.getOrCreateVirtualItemBase(virtualId, propId);
        if (base == null) {
            return false;
        }
        item.itemId = virtualId;
        newVirtual.put(virtualId, base);
        return true;
    }

    @Nullable
    private static String readPropIdFromMetadata(@Nullable String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            BsonDocument doc = BsonDocument.parse(metadataJson);
            BsonValue root = doc.get(PropItemMetadata.BSON_KEY);
            if (root == null || !root.isDocument()) {
                return null;
            }
            BsonValue id = root.asDocument().get(PropItemMetadata.FIELD_PROP_ID);
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
            LOGGER.atWarning().log("Failed to send prop UpdateItems for %s: %s", playerRef.getUuid(), e.getMessage());
        }
    }
}
