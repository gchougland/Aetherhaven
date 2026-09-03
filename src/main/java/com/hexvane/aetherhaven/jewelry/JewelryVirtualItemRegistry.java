package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.item.VirtualHeldItemSanitize;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Virtual item definitions for rolled jewelry rarity borders (DTL-style {@code qualityIndex} override).
 *
 * <p>Each {@code baseId__ah_r_<RARITY>} is a cloned {@link ItemBase} sent per-player via {@code UpdateItems}; tooltip
 * text still comes from stack {@code ItemDisplay} metadata ({@link JewelryNativeTooltipManager}).</p>
 */
public final class JewelryVirtualItemRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Separator between base item id and rolled rarity key (e.g. {@code Aetherhaven_Ring_Gold_Ruby__ah_r_LEGENDARY}). */
    public static final String VIRTUAL_SEPARATOR = "__ah_r_";

    private static final int MAX_VIRTUAL_ITEMS = 2048;

    private final Map<String, ItemBase> virtualItemCache =
        Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ItemBase> eldest) {
                return size() > MAX_VIRTUAL_ITEMS;
            }
        });

    private final ConcurrentHashMap<UUID, Set<String>> sentToPlayer = new ConcurrentHashMap<>();

    @Nonnull
    public static String generateVirtualId(@Nonnull String baseItemId, @Nonnull String rarityWireName) {
        return baseItemId + VIRTUAL_SEPARATOR + rarityWireName;
    }

    public static boolean isVirtualId(@Nonnull String itemId) {
        return itemId.contains(VIRTUAL_SEPARATOR);
    }

    @Nullable
    public static String getBaseItemId(@Nonnull String virtualOrRealId) {
        int idx = virtualOrRealId.indexOf(VIRTUAL_SEPARATOR);
        return idx > 0 ? virtualOrRealId.substring(0, idx) : null;
    }

    @Nullable
    public static String getRarityWireFromVirtualId(@Nonnull String virtualId) {
        int idx = virtualId.indexOf(VIRTUAL_SEPARATOR);
        if (idx < 0 || idx + VIRTUAL_SEPARATOR.length() >= virtualId.length()) {
            return null;
        }
        return virtualId.substring(idx + VIRTUAL_SEPARATOR.length());
    }

    @Nullable
    public ItemBase getOrCreateVirtualItemBase(
        @Nonnull String baseItemId,
        @Nonnull String virtualId,
        int qualityIndex
    ) {
        ItemBase cached = virtualItemCache.get(virtualId);
        if (cached != null) {
            // Rebuild when qualities load later: an early miss falls back to the asset Quality (Rare on jewelry).
            if (cached.qualityIndex == qualityIndex) {
                return cached;
            }
            ItemBase rebuilt = buildVirtualItemBase(baseItemId, virtualId, qualityIndex);
            if (rebuilt != null) {
                virtualItemCache.put(virtualId, rebuilt);
                for (Set<String> sent : sentToPlayer.values()) {
                    sent.remove(virtualId);
                }
                return rebuilt;
            }
            return cached;
        }
        return virtualItemCache.computeIfAbsent(virtualId, id -> buildVirtualItemBase(baseItemId, virtualId, qualityIndex));
    }

    @Nullable
    private static ItemBase buildVirtualItemBase(
        @Nonnull String baseItemId,
        @Nonnull String virtualId,
        int qualityIndex
    ) {
        try {
            Item originalItem = Item.getAssetMap().getAsset(baseItemId);
            if (originalItem == null) {
                LOGGER.atWarning().log("Cannot create jewelry virtual item: base item not found: %s", baseItemId);
                return null;
            }
            ItemBase originalPacket = originalItem.toPacket();
            if (originalPacket == null) {
                LOGGER.atWarning().log("Cannot create jewelry virtual item: toPacket() null for %s", baseItemId);
                return null;
            }
            ItemBase clone = originalPacket.clone();
            clone.id = virtualId;
            clone.qualityIndex = qualityIndex;
            hideFromCreativeMenu(clone);
            VirtualHeldItemSanitize.applyHeldItemClone(originalPacket, clone);
            zeroResourceQuantities(clone);
            return clone;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to create jewelry virtual item %s: %s", virtualId, e.getMessage());
            return null;
        }
    }

    /**
     * Virtual icon clones are not pickable assets. {@code variant} alone still shows them when the player
     * displays variants; empty categories is what keeps them out of the item library.
     */
    private static void hideFromCreativeMenu(@Nonnull ItemBase clone) {
        clone.variant = true;
        clone.categories = new String[0];
        clone.subCategory = null;
    }

    private static void zeroResourceQuantities(@Nonnull ItemBase clone) {
        if (clone.resourceTypes == null) {
            return;
        }
        ItemResourceType[] types = new ItemResourceType[clone.resourceTypes.length];
        for (int i = 0; i < clone.resourceTypes.length; i++) {
            types[i] = clone.resourceTypes[i].clone();
            types[i].quantity = 0;
        }
        clone.resourceTypes = types;
    }

    @Nonnull
    public Set<String> markAndGetUnsent(@Nonnull UUID playerUuid, @Nonnull Set<String> virtualIds) {
        if (virtualIds.isEmpty()) {
            return Set.of();
        }
        Set<String> sent = sentToPlayer.computeIfAbsent(playerUuid, u -> ConcurrentHashMap.newKeySet());
        Set<String> unsent = ConcurrentHashMap.newKeySet();
        for (String virtualId : virtualIds) {
            if (sent.add(virtualId)) {
                unsent.add(virtualId);
            }
        }
        return unsent;
    }

    public void onPlayerLeave(@Nonnull UUID playerUuid) {
        sentToPlayer.remove(playerUuid);
    }
}
