package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.PropIconPath;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Virtual item definitions for {@link PropItemMetadata#PROP_ITEM_ID}: per-prop inventory icons sent via
 * {@code UpdateItems}.
 */
public final class PropVirtualItemRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String VIRTUAL_SEPARATOR = "__ah_prop_";
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
    public static String generateVirtualId(@Nonnull String propId) {
        return PropItemMetadata.PROP_ITEM_ID + VIRTUAL_SEPARATOR + propId.trim();
    }

    public static boolean isVirtualId(@Nonnull String itemId) {
        return itemId.startsWith(PropItemMetadata.PROP_ITEM_ID + VIRTUAL_SEPARATOR);
    }

    @Nullable
    public static String getPropIdFromVirtualId(@Nonnull String virtualId) {
        if (!isVirtualId(virtualId)) {
            return null;
        }
        String prefix = PropItemMetadata.PROP_ITEM_ID + VIRTUAL_SEPARATOR;
        if (virtualId.length() <= prefix.length()) {
            return null;
        }
        return virtualId.substring(prefix.length()).trim();
    }

    /** Real item id used on the server for a virtual prop stack ({@link PropItemMetadata#PROP_ITEM_ID}). */
    @Nonnull
    public static String getBaseItemId(@Nonnull String virtualOrRealId) {
        if (isVirtualId(virtualOrRealId)) {
            return PropItemMetadata.PROP_ITEM_ID;
        }
        return virtualOrRealId;
    }

    @Nullable
    public ItemBase getOrCreateVirtualItemBase(@Nonnull String virtualId, @Nonnull String propId) {
        ItemBase cached = virtualItemCache.get(virtualId);
        if (cached != null) {
            return cached;
        }
        return virtualItemCache.computeIfAbsent(virtualId, id -> buildVirtualItemBase(virtualId, propId));
    }

    @Nullable
    private static ItemBase buildVirtualItemBase(@Nonnull String virtualId, @Nonnull String propId) {
        try {
            Item originalItem = Item.getAssetMap().getAsset(PropItemMetadata.PROP_ITEM_ID);
            if (originalItem == null) {
                LOGGER.atWarning().log("Cannot create prop virtual item: base item not found");
                return null;
            }
            ItemBase originalPacket = originalItem.toPacket();
            if (originalPacket == null) {
                return null;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            Path dataDir = plugin != null ? plugin.getDataDirectory() : null;
            String iconPath = PropIconPath.forPropId(propId, dataDir);
            ItemBase clone = originalPacket.clone();
            clone.id = virtualId;
            clone.icon = iconPath;
            hideFromCreativeMenu(clone);
            copyHeldItemInteractions(originalPacket, clone);
            return clone;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to create prop virtual item %s: %s", virtualId, e.getMessage());
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

    /** Keep SwapFrom on the clone so the hotbar can scroll off a virtual prop. */
    private static void copyHeldItemInteractions(@Nonnull ItemBase original, @Nonnull ItemBase clone) {
        if (clone.interactions == null || !clone.interactions.containsKey(InteractionType.SwapFrom)) {
            clone.interactions = original.interactions;
        }
    }

    @Nonnull
    public Set<String> markAndGetUnsent(@Nonnull UUID playerUuid, @Nonnull Set<String> virtualIds) {
        if (virtualIds.isEmpty()) {
            return Set.of();
        }
        Set<String> sent = sentToPlayer.computeIfAbsent(playerUuid, u -> ConcurrentHashMap.newKeySet());
        Set<String> unsent = ConcurrentHashMap.newKeySet();
        for (String id : virtualIds) {
            if (sent.add(id)) {
                unsent.add(id);
            }
        }
        return unsent;
    }

    public void clearSentVirtualIdForAllPlayers(@Nonnull String virtualId) {
        for (Set<String> sent : sentToPlayer.values()) {
            sent.remove(virtualId);
        }
    }

    public void invalidateProp(@Nonnull String propId) {
        virtualItemCache.remove(generateVirtualId(propId.trim()));
    }

    public void onPlayerLeave(@Nonnull UUID playerUuid) {
        sentToPlayer.remove(playerUuid);
    }

    /** Clears the sent-id set so the next {@code UpdateItems} can rebuild icons for this player. */
    public void clearSent(@Nonnull UUID playerUuid) {
        sentToPlayer.remove(playerUuid);
    }
}
