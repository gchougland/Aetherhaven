package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.component.Store;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared implementation for world-load {@link LootChestBonusInjectSystem} and operator debug fill. */
public final class LootChestBonusApplier {
    private LootChestBonusApplier() {}

    public static void tryInjectJewelry(
        @Nonnull Store<ChunkStore> s,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        SimpleItemContainer inv = c.getItemContainer();
        if (inv == null) {
            return;
        }
        if (tryInjectJewelryToContainer(inv, cfg, rnd, force)) {
            state.markNeedsSaving(s);
        }
    }

    public static boolean tryInjectJewelryToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        if (!force) {
            if (cfg.getLootChestJewelryChance() <= 0.0) {
                return false;
            }
            if (rnd.nextDouble() >= cfg.getLootChestJewelryChance()) {
                return false;
            }
        }
        short slot = randomEmptySlot(inv, rnd);
        if (slot < 0) {
            return false;
        }
        ItemStack bonus = UnidentifiedJewelry.rollStack(rnd);
        if (ItemStack.isEmpty(bonus)) {
            return false;
        }
        ItemStackSlotTransaction tx = inv.addItemStackToSlot(slot, bonus);
        return tx.succeeded();
    }

    public static void tryInjectGoldCoins(
        @Nonnull Store<ChunkStore> s,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        SimpleItemContainer inv = c.getItemContainer();
        if (inv == null) {
            return;
        }
        if (tryInjectGoldCoinsToContainer(inv, cfg, rnd, force)) {
            state.markNeedsSaving(s);
        }
    }

    public static boolean tryInjectGoldCoinsToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        double p = cfg.getLootChestGoldCoinChance();
        if (!force) {
            if (p <= 0.0) {
                return false;
            }
            if (p < 1.0 && rnd.nextDouble() >= p) {
                return false;
            }
        }
        String coinId = cfg.getLootChestGoldCoinItemId();
        if (coinId.isEmpty()) {
            return false;
        }
        Item coin = Item.getAssetMap().getAsset(coinId);
        if (coin == null) {
            return false;
        }
        int min = cfg.getLootChestGoldCoinMin();
        int max = cfg.getLootChestGoldCoinMax();
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        if (max <= 0) {
            return false;
        }
        int q = min + (max > min ? rnd.nextInt(max - min + 1) : 0);
        if (q <= 0) {
            return false;
        }
        int itemMax = Math.max(1, coin.getMaxStack());
        return addGoldCoinsSplitAcrossRandomSlots(inv, coinId, q, itemMax, rnd);
    }

    public static void tryInjectPlotToken(
        @Nonnull Store<ChunkStore> s,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        SimpleItemContainer inv = c.getItemContainer();
        if (inv == null) {
            return;
        }
        if (tryInjectPlotTokenToContainer(inv, cfg, rnd, force)) {
            state.markNeedsSaving(s);
        }
    }

    public static boolean tryInjectPlotTokenToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        if (!force) {
            if (cfg.getLootChestPlotTokenChance() <= 0.0) {
                return false;
            }
            if (rnd.nextDouble() >= cfg.getLootChestPlotTokenChance()) {
                return false;
            }
        }
        String tokenId = cfg.getLootChestPlotTokenItemId();
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        if (Item.getAssetMap().getAsset(tokenId.trim()) == null) {
            return false;
        }
        short slot = randomEmptySlot(inv, rnd);
        if (slot < 0) {
            return false;
        }
        ItemStack token = new ItemStack(tokenId.trim(), 1);
        ItemStackSlotTransaction tx = inv.addItemStackToSlot(slot, token);
        return tx.succeeded();
    }

    public static void applyAll(
        @Nonnull Store<ChunkStore> s,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean forceJewelry,
        boolean forceGold,
        boolean forcePlot
    ) {
        tryInjectJewelry(s, state, c, cfg, rnd, forceJewelry);
        tryInjectGoldCoins(s, state, c, cfg, rnd, forceGold);
        tryInjectPlotToken(s, state, c, cfg, rnd, forcePlot);
    }

    public static boolean applyAllToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean forceJewelry,
        boolean forceGold,
        boolean forcePlot
    ) {
        boolean changed = false;
        changed |= tryInjectJewelryToContainer(inv, cfg, rnd, forceJewelry);
        changed |= tryInjectGoldCoinsToContainer(inv, cfg, rnd, forceGold);
        changed |= tryInjectPlotTokenToContainer(inv, cfg, rnd, forcePlot);
        return changed;
    }

    /**
     * @param blockTypeId from {@link com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType#getId()}; may be
     *                      null if unresolved (still allowed if include is empty and exclude is empty)
     */
    public static boolean isEligibleForBlockId(@Nullable String blockTypeId, @Nonnull AetherhavenPluginConfig cfg) {
        if (blockTypeId != null && !blockTypeId.isEmpty()) {
            for (String ex : cfg.lootChestExcludeBlockIdSubstrings()) {
                if (blockTypeId.contains(ex)) {
                    return false;
                }
            }
        }
        Set<String> mustInclude = cfg.lootChestBlockIdSubstrings();
        if (mustInclude.isEmpty()) {
            return true;
        }
        if (blockTypeId == null || blockTypeId.isEmpty()) {
            return false;
        }
        for (String sub : mustInclude) {
            if (blockTypeId.contains(sub)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to spread {@code totalCoins} into several stacks in random free slots, with random per-stack sizes, instead of
     * a single stack merged to the first slot.
     */
    private static boolean addGoldCoinsSplitAcrossRandomSlots(
        @Nonnull SimpleItemContainer inv,
        @Nonnull String coinId,
        int totalCoins,
        int itemMaxStack,
        @Nonnull ThreadLocalRandom rnd
    ) {
        int remaining = totalCoins;
        boolean any = false;
        while (remaining > 0) {
            short slot = randomEmptySlot(inv, rnd);
            if (slot < 0) {
                break;
            }
            int cap = Math.min(remaining, itemMaxStack);
            int emptySlots = countEmptySlots(inv);
            int maxForPile = cap;
            if (maxForPile > 1 && emptySlots > 1 && remaining > 1) {
                maxForPile = Math.min(maxForPile, Math.max(1, remaining - 1));
            }
            int chunk;
            if (maxForPile <= 1) {
                chunk = 1;
            } else {
                chunk = 1 + rnd.nextInt(maxForPile);
            }
            if (chunk > remaining) {
                chunk = remaining;
            }
            ItemStack stack = new ItemStack(coinId, chunk);
            ItemStackSlotTransaction tx = inv.addItemStackToSlot(slot, stack);
            if (tx.succeeded()) {
                any = true;
                remaining -= chunk;
            } else {
                break;
            }
        }
        return any;
    }

    public static short randomEmptySlot(@Nonnull SimpleItemContainer inv, @Nonnull ThreadLocalRandom rnd) {
        List<Short> empty = new ArrayList<>();
        for (short t = 0; t < inv.getCapacity(); t++) {
            ItemStack st = inv.getItemStack(t);
            if (st == null || ItemStack.isEmpty(st)) {
                empty.add(t);
            }
        }
        if (empty.isEmpty()) {
            return -1;
        }
        return empty.get(rnd.nextInt(empty.size()));
    }

    private static int countEmptySlots(@Nonnull SimpleItemContainer inv) {
        int n = 0;
        for (short t = 0; t < inv.getCapacity(); t++) {
            ItemStack st = inv.getItemStack(t);
            if (st == null || ItemStack.isEmpty(st)) {
                n++;
            }
        }
        return n;
    }
}
