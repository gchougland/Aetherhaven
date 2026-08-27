package com.hexvane.aetherhaven.blockpalette;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Virtual item definitions for per-palette inventory icons. */
public final class BlockPaletteVirtualItemRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String VIRTUAL_SEPARATOR = "__ah_palette_";
    private static final int MAX_VIRTUAL_ITEMS = 2048;

    private final Map<String, ItemBase> virtualItemCache =
        Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ItemBase> eldest) {
                    return size() > MAX_VIRTUAL_ITEMS;
                }
            }
        );

    private final ConcurrentHashMap<UUID, Set<String>> sentToPlayer = new ConcurrentHashMap<>();

    @Nonnull
    public static String generateVirtualId(@Nonnull String paletteId) {
        return BlockPaletteConstants.ITEM_ID + VIRTUAL_SEPARATOR + paletteId.trim();
    }

    public static boolean isVirtualId(@Nonnull String itemId) {
        return itemId.startsWith(BlockPaletteConstants.ITEM_ID + VIRTUAL_SEPARATOR);
    }

    @Nullable
    public static String getPaletteIdFromVirtualId(@Nonnull String virtualId) {
        if (!isVirtualId(virtualId)) {
            return null;
        }
        String prefix = BlockPaletteConstants.ITEM_ID + VIRTUAL_SEPARATOR;
        if (virtualId.length() <= prefix.length()) {
            return null;
        }
        return virtualId.substring(prefix.length()).trim();
    }

    @Nonnull
    public static String getBaseItemId(@Nonnull String virtualOrRealId) {
        if (isVirtualId(virtualOrRealId)) {
            return BlockPaletteConstants.ITEM_ID;
        }
        return virtualOrRealId;
    }

    @Nullable
    public ItemBase getOrCreateVirtualItemBase(@Nonnull String virtualId, @Nonnull String paletteId) {
        ItemBase cached = virtualItemCache.get(virtualId);
        if (cached != null) {
            return cached;
        }
        return virtualItemCache.computeIfAbsent(virtualId, id -> buildVirtualItemBase(virtualId, paletteId));
    }

    @Nullable
    private static ItemBase buildVirtualItemBase(@Nonnull String virtualId, @Nonnull String paletteId) {
        try {
            String baseItemId = resolveCloneItemId(virtualId);
            Item originalItem = Item.getAssetMap().getAsset(baseItemId);
            if (originalItem == null) {
                originalItem = Item.getAssetMap().getAsset("Furniture_Village_Crate");
            }
            if (originalItem == null) {
                LOGGER.atWarning().log(
                    "Cannot create block palette virtual item for %s: base item %s not found",
                    virtualId,
                    baseItemId
                );
                return null;
            }
            ItemBase originalPacket = originalItem.toPacket();
            if (originalPacket == null) {
                return null;
            }
            String iconPath = resolveIconAssetPath(paletteId);
            ItemBase clone = originalPacket.clone();
            clone.id = virtualId;
            clone.icon = iconPath;
            hideFromCreativeMenu(clone);
            copyHeldItemInteractions(originalPacket, clone);
            return clone;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to create block palette virtual item %s: %s", virtualId, e.getMessage());
            return null;
        }
    }

    /**
     * Virtual icon clones are not pickable assets. {@code variant} alone still shows them when the player
     * displays variants; empty categories is what shop palettes use to stay out of the item library.
     */
    private static void hideFromCreativeMenu(@Nonnull ItemBase clone) {
        clone.variant = true;
        clone.categories = new String[0];
        clone.subCategory = null;
    }

    /** Keep SwapFrom on the clone so the hotbar can scroll off a virtual palette. */
    private static void copyHeldItemInteractions(@Nonnull ItemBase original, @Nonnull ItemBase clone) {
        if (clone.interactions == null || !clone.interactions.containsKey(InteractionType.SwapFrom)) {
            clone.interactions = original.interactions;
        }
    }

    @Nonnull
    private static String resolveCloneItemId(@Nonnull String itemId) {
        if (BlockPaletteVirtualItemRegistry.isVirtualId(itemId)
            || BlockPaletteConstants.ITEM_ID.equals(itemId)) {
            return BlockPaletteConstants.ITEM_ID;
        }
        if (BlockPaletteShopItemIds.paletteIdFromItemId(itemId) != null
            && Item.getAssetMap().getAsset(itemId) != null) {
            return itemId;
        }
        return BlockPaletteConstants.ITEM_ID;
    }

    @Nonnull
    private static String resolveIconAssetPath(@Nonnull String paletteId) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            BlockPaletteDefinition def = plugin.getBlockPaletteCatalog().get(paletteId.trim());
            if (def != null) {
                return BlockPaletteIconResolver.resolveIconAssetPath(def);
            }
        }
        return "Icons/ItemsGenerated/Furniture_Village_Crate.png";
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

    public void onPlayerLeave(@Nonnull UUID playerUuid) {
        sentToPlayer.remove(playerUuid);
        BlockPaletteClipboard.clear(playerUuid);
    }

    /** Clears the sent-id set so the next {@code UpdateItems} can rebuild icons for this player. */
    public void clearSent(@Nonnull UUID playerUuid) {
        sentToPlayer.remove(playerUuid);
    }
}
