package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.EconomyProvider;
import com.hexvane.aetherhaven.economy.api.GoldSource;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Under an economy provider that is not the coin, a recipe that gives coins (the salvage of a plot token, five coins)
 * gives what the provider hands out for that many gold instead ({@link EconomyProvider#goldItems} with
 * {@link GoldSource#RECIPE}), and is hidden when the provider hands out nothing: marked as requiring knowledge, which
 * no player is ever taught. Runs on every load of the recipes, so a reload from the Asset Editor is covered. Under
 * the built-in economy every recipe stays as written. The recipe objects are edited in place, through their fields
 * (the class has no setters), so the maps, packs and paths of the asset store are untouched.
 */
public final class CoinRecipeRewriter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private CoinRecipeRewriter() {}

    public static void onRecipesLoaded(@Nonnull LoadedAssetsEvent<String, CraftingRecipe, ?> event) {
        if (AetherhavenEconomy.usesCoinItem()) {
            return;
        }
        EconomyProvider provider = AetherhavenEconomy.provider();
        String coinId = ItemCoinEconomy.coinItemId();
        int rewritten = 0;
        int hidden = 0;
        for (Map.Entry<String, CraftingRecipe> entry : event.getLoadedAssets().entrySet()) {
            switch (rewrite(entry.getValue(), provider, coinId)) {
                case REWRITTEN -> rewritten++;
                case HIDDEN -> hidden++;
                default -> { }
            }
        }
        if (rewritten > 0 || hidden > 0) {
            LOGGER.atInfo().log("Coin recipes under %s: %d rewritten, %d hidden", provider.id(), rewritten, hidden);
        }
    }

    enum Outcome { UNTOUCHED, REWRITTEN, HIDDEN }

    /**
     * Rewrites one recipe if it gives coins: its outputs become the provider's items for the coins it gave, or the
     * recipe is hidden. A coin among other outputs is dropped from them. Nothing happens to a recipe that gives no
     * coin, or when a field cannot be written (logged once per recipe).
     */
    @Nonnull
    @SuppressWarnings("deprecation")
    static Outcome rewrite(@Nonnull CraftingRecipe recipe, @Nonnull EconomyProvider provider, @Nonnull String coinId) {
        long coins = 0L;
        List<MaterialQuantity> kept = new ArrayList<>();
        MaterialQuantity[] outputs = recipe.getOutputs();
        if (outputs == null || outputs.length == 0) {
            // As decoded from JSON the outputs always hold the primary output, a recipe built in code may not.
            outputs = recipe.getPrimaryOutput() == null ? MaterialQuantity.EMPTY_ARRAY : new MaterialQuantity[] {recipe.getPrimaryOutput()};
        }
        for (MaterialQuantity output : outputs) {
            if (coinId.equals(output.getItemId())) {
                coins += output.getQuantity();
            } else {
                kept.add(output);
            }
        }
        if (coins <= 0L) {
            return Outcome.UNTOUCHED;
        }
        for (ItemStack stack : provider.goldItems(GoldSource.RECIPE, coinId, coins)) {
            kept.add(new MaterialQuantity(stack.getItemId(), null, null, stack.getQuantity(), stack.getMetadata()));
        }
        if (kept.isEmpty()) {
            return set(recipe, "knowledgeRequired", true) ? Outcome.HIDDEN : Outcome.UNTOUCHED;
        }
        MaterialQuantity[] rewritten = kept.toArray(MaterialQuantity[]::new);
        Set<String> ids = new HashSet<>();
        for (MaterialQuantity output : rewritten) {
            ids.add(output.getItemId());
        }
        boolean ok = set(recipe, "primaryOutput", rewritten[0])
            && set(recipe, "outputs", rewritten)
            && set(recipe, "outputItemIds", ids);
        return ok ? Outcome.REWRITTEN : Outcome.UNTOUCHED;
    }

    private static boolean set(@Nonnull CraftingRecipe recipe, @Nonnull String name, @Nonnull Object value) {
        try {
            Field field = CraftingRecipe.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(recipe, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Cannot write CraftingRecipe.%s, recipe %s left as written", name, recipe.getId());
            return false;
        }
    }
}
