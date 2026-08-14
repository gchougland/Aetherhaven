package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InventorySection;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.protocol.packets.window.OpenWindow;
import com.hypixel.hytale.protocol.packets.window.UpdateWindow;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Outbound packet filter: appends shop catalog prices to item tooltips via {@code ItemDisplay.Description} with markup
 * enabled on nested vanilla description text (display-only; server stacks are not modified).
 */
public final class ShopPriceTooltipPacketAdapter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);

    @Nullable
    private PacketFilter outboundFilter;

    public void register() {
        outboundFilter = PacketAdapters.registerOutbound((PlayerPacketFilter) this::onOutboundPacket);
        LOGGER.atInfo().log("Shop price tooltips: outbound packet adapter registered");
    }

    public void deregister() {
        if (outboundFilter != null) {
            try {
                PacketAdapters.deregisterOutbound(outboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister shop price outbound packet filter: %s", e.getMessage());
            }
            outboundFilter = null;
        }
    }

    private boolean onOutboundPacket(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (isProcessing.get()) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        ShopPriceCatalog catalog = plugin.getShopPriceCatalog();
        isProcessing.set(true);
        try {
            if (packet instanceof UpdatePlayerInventory inv) {
                processPlayerInventory(inv, catalog);
            } else if (packet instanceof OpenWindow open) {
                processWindowInventory(open.inventory, catalog);
            } else if (packet instanceof UpdateWindow update) {
                processWindowInventory(update.inventory, catalog);
            }
            // Custom UI ItemGrid slots require ClientItemMetadata-shaped metadata only; price footers are inventory-only.
        } catch (Exception e) {
            LOGGER.atWarning().log("Shop price tooltip packet filter error for %s: %s", playerRef.getUuid(), e.getMessage());
        } finally {
            isProcessing.set(false);
        }
        return false;
    }

    private void processPlayerInventory(@Nonnull UpdatePlayerInventory packet, @Nonnull ShopPriceCatalog catalog) {
        processSection(packet.hotbar, catalog);
        processSection(packet.utility, catalog);
        processSection(packet.tools, catalog);
        processSection(packet.armor, catalog);
        processSection(packet.storage, catalog);
        processSection(packet.backpack, catalog);
    }

    private void processWindowInventory(@Nullable InventorySection section, @Nonnull ShopPriceCatalog catalog) {
        processSection(section, catalog);
    }

    private void processSection(@Nullable InventorySection section, @Nonnull ShopPriceCatalog catalog) {
        if (section == null || section.items == null || section.items.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, ItemWithAllMetadata> entry : section.items.entrySet()) {
            ItemWithAllMetadata item = entry.getValue();
            if (item == null) {
                continue;
            }
            ItemWithAllMetadata copy = ShopPriceTooltipWire.copyWithFooter(item, catalog);
            if (copy != null) {
                entry.setValue(copy);
            }
        }
    }

}
