package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotTokenInventory {
    private PlotTokenInventory() {}

    public static boolean hasPlotToken(@Nonnull ItemContainer inv, @Nonnull ConstructionDefinition def) {
        String legacy = def.getPlotTokenItemId();
        if (legacy != null && !legacy.isBlank() && !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(legacy.trim())) {
            if (countItemId(inv, legacy.trim()) > 0) {
                return true;
            }
            return countUnifiedForConstruction(inv, def.getId()) > 0;
        }
        return countUnifiedForConstruction(inv, def.getId()) > 0;
    }

    @Nonnull
    public static List<String> listConstructionIdsWithTokens(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CombinedItemContainer inv
    ) {
        ObjectArrayList<String> ids = new ObjectArrayList<>();
        for (ConstructionDefinition d : plugin.getConstructionCatalog().list()) {
            if (d.isWallSegment()) {
                continue;
            }
            if (hasPlotToken(inv, d)) {
                ids.add(d.getId());
            }
        }
        return ids;
    }

    public static boolean consumePlotToken(@Nonnull ItemContainer inv, @Nonnull ConstructionDefinition def) {
        String legacy = def.getPlotTokenItemId();
        if (legacy != null && !legacy.isBlank() && !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(legacy.trim())) {
            if (consumeLegacyTokenByItemId(inv, legacy.trim())) {
                return true;
            }
            return consumeUnifiedForConstruction(inv, def.getId());
        }
        return consumeUnifiedForConstruction(inv, def.getId());
    }

    /** Legacy per-building tokens: match item id only (no instance metadata). */
    private static boolean consumeLegacyTokenByItemId(@Nonnull ItemContainer inv, @Nonnull String itemId) {
        for (short i = 0; i < inv.getCapacity(); i++) {
            ItemStack stack = inv.getItemStack(i);
            if (ItemStack.isEmpty(stack) || !itemId.equals(stack.getItemId())) {
                continue;
            }
            if (removeOneFromSlot(inv, i, stack)) {
                return true;
            }
        }
        return inv.removeItemStack(new ItemStack(itemId, 1)).succeeded();
    }

    /**
     * Unified plot tokens carry per-building BSON/tooltip metadata; {@link ItemContainer#removeItemStack(ItemStack)}
     * requires an exact stack match, so remove the live inventory stack by construction id instead.
     */
    private static boolean consumeUnifiedForConstruction(@Nonnull ItemContainer inv, @Nonnull String constructionId) {
        String cid = constructionId.trim();
        for (short i = 0; i < inv.getCapacity(); i++) {
            ItemStack stack = inv.getItemStack(i);
            if (ItemStack.isEmpty(stack) || !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(stack.getItemId())) {
                continue;
            }
            if (!PlotTokenMetadata.matchesConstruction(stack, cid)) {
                continue;
            }
            if (removeOneFromSlot(inv, i, stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeOneFromSlot(
        @Nonnull ItemContainer inv,
        short slot,
        @Nonnull ItemStack stack
    ) {
        if (stack.getQuantity() <= 1) {
            return inv.removeItemStack(stack).succeeded();
        }
        return inv.removeItemStackFromSlot(slot, stack, 1).succeeded();
    }

    /**
     * Plot token stack for a building definition: unified tokens carry per-building metadata; legacy tokens use a
     * dedicated item id per construction.
     */
    @Nonnull
    public static ItemStack createTokenStackForDefinition(
        @Nonnull ConstructionDefinition def,
        @Nullable String language
    ) {
        String legacy = def.getPlotTokenItemId();
        if (legacy != null && !legacy.isBlank() && !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(legacy.trim())) {
            return new ItemStack(legacy.trim(), 1);
        }
        return createTokenStack(def.getId(), 1, def.getDisplayName(), language);
    }

    @Nonnull
    public static ItemStack createTokenStack(@Nonnull String constructionId, int quantity, @Nullable String displayName) {
        return createTokenStack(constructionId, quantity, displayName, null);
    }

    @Nonnull
    public static ItemStack createTokenStack(
        @Nonnull String constructionId,
        int quantity,
        @Nullable String displayName,
        @Nullable String language
    ) {
        ItemStack base = new ItemStack(AetherhavenConstants.PLOT_TOKEN_UNIFIED, quantity);
        return PlotTokenMetadata.withConstruction(base, constructionId, displayName, language);
    }

    private static int countItemId(@Nonnull ItemContainer inv, @Nonnull String itemId) {
        return inv.countItemStacks(s -> itemId.equals(s.getItemId()));
    }

    private static int countUnifiedForConstruction(@Nonnull ItemContainer inv, @Nonnull String constructionId) {
        return inv.countItemStacks(
            s -> AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(s.getItemId())
                && PlotTokenMetadata.matchesConstruction(s, constructionId)
        );
    }

    public static void giveToPlayer(@Nonnull Player player, @Nonnull String constructionId, int amount, @Nullable String displayName) {
        Ref<EntityStore> ref = player.getReference();
        if (ref != null) {
            giveToPlayer(player, constructionId, amount, displayName, ref, ref.getStore());
            return;
        }
        player.giveItem(createTokenStack(constructionId, amount, displayName), null, null);
    }

    public static void giveToPlayer(
        @Nonnull Player player,
        @Nonnull String constructionId,
        int amount,
        @Nullable String displayName,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        String language = "en-US";
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && playerRef.getLanguage() != null && !playerRef.getLanguage().isBlank()) {
            language = playerRef.getLanguage();
        }
        ItemStack stack = createTokenStack(constructionId, amount, displayName, language);
        ItemStackTransaction tx = player.giveItem(stack, ref, store);
        if (tx.succeeded()) {
            PlotTokenIconSync.afterTokenGranted(playerRef);
        }
    }
}
