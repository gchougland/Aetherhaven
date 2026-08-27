package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.ui.ConstructionTokenIconPath;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.AssetIconProperties;
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
 * Virtual item definitions for unified plot tokens ({@link AetherhavenConstants#PLOT_TOKEN_UNIFIED}): per-construction
 * inventory icons sent per-player via {@code UpdateItems}.
 */
public final class PlotTokenVirtualItemRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Separator between base item id and construction id (e.g. {@code Aetherhaven_Plot_Token__ah_plot_plot_park}). */
    public static final String VIRTUAL_SEPARATOR = "__ah_plot_";

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
    public static String generateVirtualId(@Nonnull String constructionId) {
        return AetherhavenConstants.PLOT_TOKEN_UNIFIED + VIRTUAL_SEPARATOR + constructionId.trim();
    }

    public static boolean isVirtualId(@Nonnull String itemId) {
        return itemId.startsWith(AetherhavenConstants.PLOT_TOKEN_UNIFIED + VIRTUAL_SEPARATOR);
    }

    @Nullable
    public static String getBaseItemId(@Nonnull String virtualOrRealId) {
        if (!isVirtualId(virtualOrRealId)) {
            return null;
        }
        return AetherhavenConstants.PLOT_TOKEN_UNIFIED;
    }

    @Nullable
    public static String getConstructionIdFromVirtualId(@Nonnull String virtualId) {
        if (!isVirtualId(virtualId)) {
            return null;
        }
        String prefix = AetherhavenConstants.PLOT_TOKEN_UNIFIED + VIRTUAL_SEPARATOR;
        if (virtualId.length() <= prefix.length()) {
            return null;
        }
        return virtualId.substring(prefix.length()).trim();
    }

    @Nullable
    public ItemBase getOrCreateVirtualItemBase(@Nonnull String virtualId, @Nonnull String constructionId) {
        ItemBase cached = virtualItemCache.get(virtualId);
        if (cached != null) {
            return cached;
        }
        return virtualItemCache.computeIfAbsent(virtualId, id -> buildVirtualItemBase(virtualId, constructionId));
    }

    @Nullable
    private static ItemBase buildVirtualItemBase(@Nonnull String virtualId, @Nonnull String constructionId) {
        try {
            Item originalItem = Item.getAssetMap().getAsset(AetherhavenConstants.PLOT_TOKEN_UNIFIED);
            if (originalItem == null) {
                LOGGER.atWarning().log("Cannot create plot token virtual item: base item not found");
                return null;
            }
            ItemBase originalPacket = originalItem.toPacket();
            if (originalPacket == null) {
                LOGGER.atWarning().log("Cannot create plot token virtual item: toPacket() null for unified token");
                return null;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            Path dataDir = plugin != null ? plugin.getDataDirectory() : null;
            String iconPath =
                ConstructionTokenIconPath.isIconAvailable(constructionId, dataDir)
                    ? ConstructionTokenIconPath.forConstructionId(constructionId, dataDir)
                    : ConstructionTokenIconPath.unifiedPlotTokenFallbackIconPath();

            ItemBase clone = originalPacket.clone();
            clone.id = virtualId;
            clone.icon = iconPath;
            hideFromCreativeMenu(clone);
            copyHeldItemInteractions(originalPacket, clone);
            applyLegacyIconProperties(clone, constructionId, plugin);
            zeroResourceQuantities(clone);
            return clone;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to create plot token virtual item %s: %s", virtualId, e.getMessage());
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

    /** Keep SwapFrom on the clone so the hotbar can scroll off a virtual plot token. */
    private static void copyHeldItemInteractions(@Nonnull ItemBase original, @Nonnull ItemBase clone) {
        if (clone.interactions == null || !clone.interactions.containsKey(InteractionType.SwapFrom)) {
            clone.interactions = original.interactions;
        }
    }

    private static void applyLegacyIconProperties(
        @Nonnull ItemBase clone,
        @Nonnull String constructionId,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (plugin == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(constructionId.trim());
        if (def == null) {
            return;
        }
        String tokenItemId = def.getPlotTokenItemId();
        if (tokenItemId == null
            || tokenItemId.isBlank()
            || AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(tokenItemId.trim())) {
            return;
        }
        Item legacy = Item.getAssetMap().getAsset(tokenItemId.trim());
        if (legacy == null) {
            return;
        }
        AssetIconProperties iconProperties = legacy.getIconProperties();
        if (iconProperties != null) {
            clone.iconProperties = iconProperties.toPacket();
        }
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

    public void invalidateConstruction(@Nonnull String constructionId) {
        virtualItemCache.remove(generateVirtualId(constructionId.trim()));
    }

    public void clearSentVirtualIdForAllPlayers(@Nonnull String virtualId) {
        for (Set<String> sent : sentToPlayer.values()) {
            sent.remove(virtualId);
        }
    }
}
