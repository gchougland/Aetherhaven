package com.hexvane.aetherhaven.plot;

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
 * Outbound packet filter that swaps unified plot token item ids for per-construction virtual ids and sends
 * {@link UpdateItems} clones with building-specific inventory icons.
 */
public final class PlotTokenIconPacketAdapter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int POST_TRANSITION_REFRESH_DELAY_SECS = 2;

    private static final JsonWriterSettings CUSTOM_UI_JSON =
        JsonWriterSettings.builder().outputMode(JsonMode.SHELL).build();

    private final PlotTokenVirtualItemRegistry virtualItems;
    private final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);
    private final Set<UUID> worldTransitioning = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, UpdatePlayerInventory> lastRawInventory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerRef> knownPlayerRefs = new ConcurrentHashMap<>();

    @Nullable
    private PacketFilter outboundFilter;
    @Nullable
    private PacketFilter inboundFilter;

    public PlotTokenIconPacketAdapter(@Nonnull PlotTokenVirtualItemRegistry virtualItems) {
        this.virtualItems = virtualItems;
    }

    public void register() {
        outboundFilter = PacketAdapters.registerOutbound((PlayerPacketFilter) this::onOutboundPacket);
        inboundFilter = PacketAdapters.registerInbound((PlayerPacketFilter) this::onInboundPacket);
        LOGGER.atInfo().log("Plot token icons: packet adapter registered (inventory + custom UI item grids)");
    }

    public void deregister() {
        if (outboundFilter != null) {
            try {
                PacketAdapters.deregisterOutbound(outboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister plot token outbound packet filter: %s", e.getMessage());
            }
            outboundFilter = null;
        }
        if (inboundFilter != null) {
            try {
                PacketAdapters.deregisterInbound(inboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister plot token inbound packet filter: %s", e.getMessage());
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

    /** Rebuild virtual item defs and re-push inventory icons after a runtime plot-creator PNG is registered. */
    public void onConstructionIconRegistered(@Nonnull String constructionId) {
        String cid = constructionId.trim();
        if (cid.isEmpty()) {
            return;
        }
        String virtualId = PlotTokenVirtualItemRegistry.generateVirtualId(cid);
        virtualItems.invalidateConstruction(cid);
        virtualItems.clearSentVirtualIdForAllPlayers(virtualId);
        for (UUID uuid : knownPlayerRefs.keySet()) {
            refreshPlayer(uuid);
        }
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
            UpdatePlayerInventory processed = deepCloneInventory(raw);
            processPlayerInventory(playerRef, processed);
            playerRef.getPacketHandler().writeNoCache(processed);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("Plot token icon refresh failed for %s: %s", playerUuid, e.getMessage());
            return false;
        }
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
            LOGGER.atWarning().log("Plot token inbound packet filter error for %s: %s", playerRef.getUuid(), e.getMessage());
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
        if (itemId == null || !PlotTokenVirtualItemRegistry.isVirtualId(itemId)) {
            return itemId;
        }
        return PlotTokenVirtualItemRegistry.getBaseItemId(itemId);
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
            LOGGER.atWarning().log("Plot token outbound packet filter error for %s: %s", playerRef.getUuid(), e.getMessage());
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
                        LOGGER.atWarning().log("Post-transition plot token refresh failed for %s: %s", playerUuid, e.getMessage());
                    }
                },
                POST_TRANSITION_REFRESH_DELAY_SECS,
                TimeUnit.SECONDS
            );
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to schedule post-transition plot token refresh for %s: %s", playerUuid, e.getMessage());
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
            ItemWithAllMetadata copy = new ItemWithAllMetadata(item);
            if (PlotTokenIconWire.applyToPacketItem(copy, virtualItems, newVirtual)) {
                entry.setValue(copy);
            }
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
            LOGGER.atFine().log("Plot token custom UI command parse skipped: %s", e.getMessage());
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
            modified |= PlotTokenIconWire.applyToItemStackDocument(itemStackValue.asDocument(), virtualItems, newVirtual);
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
            packet.updateIcons = true;
            playerRef.getPacketHandler().writeNoCache(packet);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to send plot token UpdateItems for %s: %s", playerRef.getUuid(), e.getMessage());
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
