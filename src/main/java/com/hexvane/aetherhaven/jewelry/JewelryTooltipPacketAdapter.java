package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.ui.CustomUiItemStackWire;
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
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.player.JoinWorld;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.protocol.packets.window.OpenWindow;
import com.hypixel.hytale.protocol.packets.window.UpdateWindow;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/**
 * Outbound packet filter that swaps jewelry item ids for per-rarity virtual ids and sends {@link UpdateItems} clones
 * with the correct {@code qualityIndex} (same technique as DynamicTooltipsLib, jewelry-only).
 */
public final class JewelryTooltipPacketAdapter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int POST_TRANSITION_REFRESH_DELAY_SECS = 2;

    private static final JsonWriterSettings CUSTOM_UI_JSON =
        JsonWriterSettings.builder().outputMode(JsonMode.SHELL).build();

    private final JewelryVirtualItemRegistry virtualItems;
    private final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    private final Set<UUID> worldTransitioning = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, UpdatePlayerInventory> lastRawInventory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerRef> knownPlayerRefs = new ConcurrentHashMap<>();

    @Nullable
    private PacketFilter outboundFilter;
    @Nullable
    private PacketFilter inboundFilter;

    public JewelryTooltipPacketAdapter(@Nonnull JewelryVirtualItemRegistry virtualItems) {
        this.virtualItems = virtualItems;
    }

    public void register() {
        outboundFilter = PacketAdapters.registerOutbound((PlayerPacketFilter) this::onOutboundPacket);
        inboundFilter = PacketAdapters.registerInbound((PlayerPacketFilter) this::onInboundPacket);
        LOGGER.atInfo().log("Jewelry rarity borders: packet adapter registered (inventory + custom UI item grids)");
    }

    /** Sends {@link UpdateItems} for this jewelry stack's virtual id if the client has not seen it yet. */
    public void ensureVirtualItemForStack(@Nonnull PlayerRef playerRef, @Nonnull ItemStack stack) {
        if (isProcessing.get()) {
            return;
        }
        Map<String, ItemBase> defs = new LinkedHashMap<>();
        JewelryTooltipWire.collectVirtualDefinition(stack, virtualItems, defs);
        sendVirtualItemDefinitions(playerRef, defs);
    }

    public void deregister() {
        if (outboundFilter != null) {
            try {
                PacketAdapters.deregisterOutbound(outboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister jewelry outbound packet filter: %s", e.getMessage());
            }
            outboundFilter = null;
        }
        if (inboundFilter != null) {
            try {
                PacketAdapters.deregisterInbound(inboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister jewelry inbound packet filter: %s", e.getMessage());
            }
            inboundFilter = null;
        }
    }

    public void onPlayerLeave(@Nonnull UUID playerUuid) {
        worldTransitioning.remove(playerUuid);
        lastRawInventory.remove(playerUuid);
        knownPlayerRefs.remove(playerUuid);
        virtualItems.onPlayerLeave(playerUuid);
    }

    public void invalidatePlayer(@Nonnull UUID playerUuid) {
        // Keep lastRawInventory for refreshPlayer().
    }

    public boolean refreshPlayer(@Nonnull UUID playerUuid) {
        PlayerRef playerRef = knownPlayerRefs.get(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return false;
        }
        UpdatePlayerInventory raw = lastRawInventory.get(playerUuid);
        if (raw == null) {
            return false;
        }
        try {
            playerRef.getPacketHandler().writeNoCache(deepCloneInventory(raw));
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("Jewelry tooltip refresh failed for %s: %s", playerUuid, e.getMessage());
            return false;
        }
    }

    public int refreshAllPlayers() {
        int count = 0;
        for (UUID uuid : knownPlayerRefs.keySet()) {
            if (refreshPlayer(uuid)) {
                count++;
            }
        }
        return count;
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
            LOGGER.atWarning().log("Jewelry inbound packet filter error for %s: %s", playerRef.getUuid(), e.getMessage());
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
        if (itemId == null || !JewelryVirtualItemRegistry.isVirtualId(itemId)) {
            return itemId;
        }
        String base = JewelryVirtualItemRegistry.getBaseItemId(itemId);
        return base != null ? base : itemId;
    }

    private boolean onOutboundPacket(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (isProcessing.get()) {
            return false;
        }
        isProcessing.set(true);
        try {
            UUID playerUuid = playerRef.getUuid();
            knownPlayerRefs.put(playerUuid, playerRef);

            if (packet instanceof JoinWorld) {
                worldTransitioning.add(playerUuid);
            } else if (packet instanceof UpdatePlayerInventory inv) {
                lastRawInventory.put(playerUuid, deepCloneInventory(inv));
                if (worldTransitioning.remove(playerUuid)) {
                    schedulePostTransitionRefresh(playerUuid);
                } else {
                    processPlayerInventory(playerRef, inv);
                }
            } else if (packet instanceof OpenWindow open) {
                processWindowInventory(playerRef, open.inventory);
            } else if (packet instanceof UpdateWindow update) {
                processWindowInventory(playerRef, update.inventory);
            } else if (packet instanceof CustomPage customPage) {
                processCustomPage(playerRef, customPage);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Jewelry outbound packet filter error for %s: %s", playerRef.getUuid(), e.getMessage());
        } finally {
            isProcessing.set(false);
        }
        return false;
    }

    private void schedulePostTransitionRefresh(@Nonnull UUID playerUuid) {
        try {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> {
                    try {
                        refreshPlayer(playerUuid);
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Post-transition jewelry refresh failed for %s: %s", playerUuid, e.getMessage());
                    }
                },
                POST_TRANSITION_REFRESH_DELAY_SECS,
                TimeUnit.SECONDS
            );
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to schedule post-transition jewelry refresh for %s: %s", playerUuid, e.getMessage());
        }
    }

    private void processPlayerInventory(@Nonnull PlayerRef playerRef, @Nonnull UpdatePlayerInventory packet) {
        Map<String, ItemBase> newVirtual = new LinkedHashMap<>();
        try {
            processSection(packet.hotbar, newVirtual);
            processSection(packet.utility, newVirtual);
            processSection(packet.tools, newVirtual);
            processSection(packet.armor, newVirtual);
            processSection(packet.storage, newVirtual);
            processSection(packet.backpack, newVirtual);
        } finally {
            sendVirtualItemDefinitions(playerRef, newVirtual);
        }
    }

    private void processWindowInventory(@Nonnull PlayerRef playerRef, @Nullable InventorySection section) {
        if (section == null) {
            return;
        }
        Map<String, ItemBase> newVirtual = new LinkedHashMap<>();
        try {
            processSection(section, newVirtual);
        } finally {
            sendVirtualItemDefinitions(playerRef, newVirtual);
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
            applyToPacketItem(entry, newVirtual);
        }
    }

    private void applyToPacketItem(
        @Nonnull Map.Entry<Integer, ItemWithAllMetadata> entry,
        @Nonnull Map<String, ItemBase> newVirtual
    ) {
        ItemWithAllMetadata item = entry.getValue();
        String itemId = item.itemId;
        String baseId = itemId;
        if (JewelryVirtualItemRegistry.isVirtualId(itemId)) {
            String resolved = JewelryVirtualItemRegistry.getBaseItemId(itemId);
            if (resolved != null) {
                baseId = resolved;
            }
        }
        if (!JewelryItemIds.isJewelry(baseId) || !JewelryMetadata.hasJewelryMetaFromMetadataJson(item.metadata)) {
            return;
        }
        JewelryRarity rarity = JewelryMetadata.readRarityFromMetadataJson(item.metadata);
        if (rarity == null) {
            return;
        }
        String virtualId = JewelryVirtualItemRegistry.generateVirtualId(baseId, rarity.wireName());
        int qualityIndex = JewelryItemQualityIndex.forRarity(rarity, baseId);
        ItemBase virtualBase = virtualItems.getOrCreateVirtualItemBase(baseId, virtualId, qualityIndex);
        if (virtualBase == null) {
            return;
        }
        newVirtual.put(virtualId, virtualBase);
        if (!virtualId.equals(item.itemId)) {
            ItemWithAllMetadata copy = new ItemWithAllMetadata(item);
            copy.itemId = virtualId;
            entry.setValue(copy);
        }
    }

    private void processCustomPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPage customPage) {
        if (customPage.commands == null || customPage.commands.length == 0) {
            return;
        }
        Map<String, ItemBase> newVirtual = new LinkedHashMap<>();
        try {
            for (CustomUICommand command : customPage.commands) {
                if (command.data == null || command.data.isEmpty()) {
                    continue;
                }
                String modified = processCustomUiCommandData(command.data, newVirtual);
                if (modified != null) {
                    command.data = modified;
                }
            }
        } finally {
            sendVirtualItemDefinitions(playerRef, newVirtual);
        }
    }

    @Nullable
    private String processCustomUiCommandData(@Nonnull String data, @Nonnull Map<String, ItemBase> newVirtual) {
        try {
            BsonDocument doc = BsonDocument.parse(data);
            boolean modified = walkCustomUiValue(doc, newVirtual);
            return modified ? doc.toJson(CUSTOM_UI_JSON) : null;
        } catch (Exception e) {
            LOGGER.atFine().log("Jewelry custom UI command parse skipped: %s", e.getMessage());
            return null;
        }
    }

    private boolean walkCustomUiValue(@Nonnull BsonValue value, @Nonnull Map<String, ItemBase> newVirtual) {
        if (value.isDocument()) {
            return walkCustomUiDocument(value.asDocument(), newVirtual);
        }
        if (value.isArray()) {
            boolean modified = false;
            for (BsonValue element : value.asArray()) {
                modified |= walkCustomUiValue(element, newVirtual);
            }
            return modified;
        }
        return false;
    }

    private boolean walkCustomUiDocument(@Nonnull BsonDocument doc, @Nonnull Map<String, ItemBase> newVirtual) {
        boolean modified = CustomUiItemStackWire.sanitizeItemStackDocument(doc);
        BsonValue itemStackValue = doc.get("ItemStack");
        if (itemStackValue != null && itemStackValue.isDocument()) {
            modified |= JewelryTooltipWire.applyToItemStackDocument(itemStackValue.asDocument(), virtualItems, newVirtual);
        }
        for (Map.Entry<String, BsonValue> entry : doc.entrySet()) {
            if ("ItemStack".equals(entry.getKey())) {
                continue;
            }
            modified |= walkCustomUiValue(entry.getValue(), newVirtual);
        }
        return modified;
    }

    private void sendVirtualItemDefinitions(@Nonnull PlayerRef playerRef, @Nonnull Map<String, ItemBase> newVirtual) {
        if (newVirtual.isEmpty()) {
            return;
        }
        Set<String> unsent = virtualItems.markAndGetUnsent(playerRef.getUuid(), newVirtual.keySet());
        if (unsent.isEmpty()) {
            return;
        }
        Map<String, ItemBase> toSend = new LinkedHashMap<>();
        for (String virtualId : unsent) {
            ItemBase base = newVirtual.get(virtualId);
            if (base != null) {
                toSend.put(virtualId, base);
            }
        }
        if (toSend.isEmpty()) {
            return;
        }
        try {
            UpdateItems packet = new UpdateItems();
            packet.type = UpdateType.AddOrUpdate;
            packet.items = toSend;
            packet.removedItems = new String[0];
            packet.updateModels = false;
            packet.updateIcons = false;
            playerRef.getPacketHandler().writeNoCache(packet);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to send jewelry UpdateItems for %s: %s", playerRef.getUuid(), e.getMessage());
        }
    }

    @Nonnull
    private static UpdatePlayerInventory deepCloneInventory(@Nonnull UpdatePlayerInventory original) {
        UpdatePlayerInventory clone = new UpdatePlayerInventory();
        clone.hotbar = cloneSection(original.hotbar);
        clone.utility = cloneSection(original.utility);
        clone.tools = cloneSection(original.tools);
        clone.armor = cloneSection(original.armor);
        clone.storage = cloneSection(original.storage);
        clone.backpack = cloneSection(original.backpack);
        return clone;
    }

    @Nullable
    private static InventorySection cloneSection(@Nullable InventorySection section) {
        if (section == null) {
            return null;
        }
        InventorySection clone = new InventorySection();
        clone.capacity = section.capacity;
        if (section.items != null) {
            clone.items = new HashMap<>();
            for (Map.Entry<Integer, ItemWithAllMetadata> entry : section.items.entrySet()) {
                clone.items.put(entry.getKey(), entry.getValue() != null ? entry.getValue().clone() : null);
            }
        }
        return clone;
    }
}
