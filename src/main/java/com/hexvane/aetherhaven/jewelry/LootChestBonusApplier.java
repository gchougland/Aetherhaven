package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.difficulty.LootRarityDifficulty;
import com.hexvane.aetherhaven.difficulty.TownDifficultySettings;
import com.hexvane.aetherhaven.loot.LootChestPlotBlueprintLoot;
import com.hexvane.aetherhaven.prop.PropLoot;
import com.hexvane.aetherhaven.prop.PropLootExclusions;
import com.hexvane.aetherhaven.world.WorldZoneIndex;
import com.hypixel.hytale.builtin.adventure.stash.StashGameplayConfig;
import com.hypixel.hytale.builtin.adventure.stash.StashPlugin;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
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
        int adventureZoneIndex,
        boolean force
    ) {
        SimpleItemContainer inv = c.getItemContainer();
        if (inv == null) {
            return;
        }
        if (tryInjectJewelryToContainer(inv, cfg, rnd, adventureZoneIndex, force)) {
            state.markNeedsSaving(s);
        }
    }

    public static boolean tryInjectJewelryToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        int adventureZoneIndex,
        boolean force
    ) {
        return tryInjectJewelryToContainer(
            inv, cfg, rnd, adventureZoneIndex, force, LootRarityDifficulty.currentOrNormal());
    }

    public static boolean tryInjectJewelryToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        int adventureZoneIndex,
        boolean force,
        @Nonnull TownDifficultySettings difficulty
    ) {
        short slot = randomEmptySlot(inv, rnd);
        if (slot < 0) {
            return false;
        }
        ItemStack bonus = JewelryChestLoot.rollForWorldChest(rnd, cfg, adventureZoneIndex, force, difficulty);
        if (bonus == null || ItemStack.isEmpty(bonus)) {
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
        return tryInjectGoldCoinsToContainer(inv, cfg, rnd, force, LootRarityDifficulty.currentOrNormal());
    }

    public static boolean tryInjectGoldCoinsToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force,
        @Nonnull TownDifficultySettings difficulty
    ) {
        double p = LootRarityDifficulty.scaleChance(
            cfg.getLootChestGoldCoinChance(),
            difficulty.getGoldLootRarityMultiplier()
        );
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
        q = LootRarityDifficulty.scaleQuantity(q, difficulty.getGoldLootRarityMultiplier());
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

    public static void tryInjectGaiaDraughtBonuses(
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
        if (tryInjectGaiaDraughtBonusesToContainer(inv, cfg, rnd, force)) {
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
            double chance = LootRarityDifficulty.scaleChance(
                cfg.getLootChestPlotTokenChance(),
                LootRarityDifficulty.currentOrNormal().getOtherLootRarityMultiplier()
            );
            if (chance <= 0.0) {
                return false;
            }
            if (rnd.nextDouble() >= chance) {
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

    public static void tryInjectPlotBlueprint(
        @Nonnull Store<ChunkStore> s,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        SimpleItemContainer inv = c.getItemContainer();
        if (inv == null) {
            return;
        }
        if (tryInjectPlotBlueprintToContainer(inv, cfg, catalog, rnd, force)) {
            state.markNeedsSaving(s);
        }
    }

    public static boolean tryInjectPlotBlueprintToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        if (!force) {
            double chance = LootRarityDifficulty.scaleChance(
                cfg.getLootChestPlotBlueprintChance(),
                LootRarityDifficulty.currentOrNormal().getOtherLootRarityMultiplier()
            );
            if (chance <= 0.0) {
                return false;
            }
            if (rnd.nextDouble() >= chance) {
                return false;
            }
        }
        if (!LootChestSupplementalLoot.canRollPlotBlueprint(catalog)) {
            return false;
        }
        ItemStack stack = LootChestPlotBlueprintLoot.roll(catalog, rnd);
        if (stack == null || ItemStack.isEmpty(stack)) {
            return false;
        }
        short slot = randomEmptySlot(inv, rnd);
        if (slot < 0) {
            return false;
        }
        ItemStackSlotTransaction tx = inv.addItemStackToSlot(slot, stack);
        return tx.succeeded();
    }

    public static void tryInjectProp(
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
        if (tryInjectPropToContainer(inv, cfg, rnd, force)) {
            state.markNeedsSaving(s);
        }
    }

    public static boolean tryInjectPropToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        if (!force) {
            double chance = LootRarityDifficulty.scaleChance(
                cfg.getLootChestPropChance(),
                LootRarityDifficulty.currentOrNormal().getOtherLootRarityMultiplier()
            );
            if (chance <= 0.0) {
                return false;
            }
            if (rnd.nextDouble() >= chance) {
                return false;
            }
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        ItemStack stack = PropLoot.roll(plugin.getPropCatalog(), PropLootExclusions.load(plugin), rnd);
        if (stack == null || ItemStack.isEmpty(stack)) {
            return false;
        }
        short slot = randomEmptySlot(inv, rnd);
        if (slot < 0) {
            return false;
        }
        ItemStackSlotTransaction tx = inv.addItemStackToSlot(slot, stack);
        return tx.succeeded();
    }

    public static void tryInjectBlockPalette(
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
        if (tryInjectBlockPaletteToContainer(inv, cfg, rnd, force)) {
            state.markNeedsSaving(s);
        }
    }

    public static boolean tryInjectBlockPaletteToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        if (!force) {
            double chance = LootRarityDifficulty.scaleChance(
                cfg.getLootChestBlockPaletteChance(),
                LootRarityDifficulty.currentOrNormal().getOtherLootRarityMultiplier()
            );
            if (chance <= 0.0) {
                return false;
            }
            if (rnd.nextDouble() >= chance) {
                return false;
            }
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        ItemStack stack = com.hexvane.aetherhaven.blockpalette.BlockPaletteLoot.roll(
            plugin.getBlockPaletteCatalog(), rnd);
        if (stack == null || ItemStack.isEmpty(stack)) {
            return false;
        }
        short slot = randomEmptySlot(inv, rnd);
        if (slot < 0) {
            return false;
        }
        ItemStackSlotTransaction tx = inv.addItemStackToSlot(slot, stack);
        return tx.succeeded();
    }

    public static void applyAll(
        @Nonnull Store<ChunkStore> s,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull World world,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull ThreadLocalRandom rnd,
        boolean forceJewelry,
        boolean forceGold,
        boolean forcePlot,
        boolean forcePlotBlueprint
    ) {
        int zone = resolveAdventureZoneIndex(world, s, state);
        tryInjectJewelry(s, state, c, cfg, rnd, zone, forceJewelry);
        tryInjectGoldCoins(s, state, c, cfg, rnd, forceGold);
        tryInjectPlotBlueprint(s, state, c, cfg, catalog, rnd, forcePlotBlueprint);
        tryInjectProp(s, state, c, cfg, rnd, forcePlotBlueprint);
        tryInjectBlockPalette(s, state, c, cfg, rnd, false);
        tryInjectGaiaDraughtBonuses(s, state, c, cfg, rnd, false);
    }

    public static boolean applyCoreBonusesToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        int adventureZoneIndex,
        boolean forceJewelry,
        boolean forceGold
    ) {
        boolean changed = false;
        changed |= tryInjectJewelryToContainer(inv, cfg, rnd, adventureZoneIndex, forceJewelry);
        changed |= tryInjectGoldCoinsToContainer(inv, cfg, rnd, forceGold);
        return changed;
    }

    public static boolean applySupplementalBonusesToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        boolean changed = false;
        changed |= tryInjectPlotBlueprintToContainer(inv, cfg, catalog, rnd, force);
        changed |= tryInjectPropToContainer(inv, cfg, rnd, force);
        changed |= tryInjectBlockPaletteToContainer(inv, cfg, rnd, force);
        changed |= tryInjectGaiaDraughtBonusesToContainer(inv, cfg, rnd, force);
        return changed;
    }

    public static boolean applyAllToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull ThreadLocalRandom rnd,
        int adventureZoneIndex,
        boolean forceJewelry,
        boolean forceGold,
        boolean forcePlot,
        boolean forcePlotBlueprint
    ) {
        boolean changed =
            applySupplementalBonusesToContainer(inv, cfg, catalog, rnd, forcePlotBlueprint || forcePlot);
        changed |= applyCoreBonusesToContainer(inv, cfg, rnd, adventureZoneIndex, forceJewelry, forceGold);
        return changed;
    }

    public static int resolveAdventureZoneIndex(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> store,
        @Nonnull BlockModule.BlockStateInfo bsi
    ) {
        LootChestBlockPosition.Coords coords = LootChestBlockPosition.resolve(store, bsi);
        if (coords == null) {
            return WorldZoneIndex.UNKNOWN_DEFAULT;
        }
        return WorldZoneIndex.resolveAtBlock(world, coords.blockX(), coords.blockZ());
    }

    /**
     * Supplemental then core bonus rolls for an open chest inventory (Lootr per-player or vanilla after stash).
     * Does not set chunk markers {@link LootChestBonusApplied} or {@link LootChestSupplementalBonusApplied}.
     */
    public static boolean applyOpenContainerBonuses(
        @Nonnull SimpleItemContainer inv,
        @Nonnull World world,
        @Nonnull Store<ChunkStore> chunkStore,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd
    ) {
        int zone = resolveAdventureZoneIndex(world, chunkStore, state);
        boolean changed = false;
        changed |= syncJewelryInContainerForZone(inv, zone, cfg, rnd);
        changed |= applySupplementalBonusesToContainer(inv, cfg, plugin.getConstructionCatalog(), rnd, false);
        changed |= applyCoreBonusesToContainer(inv, cfg, rnd, zone, false, false);
        return changed;
    }

    /**
     * Same rolls as natural chests on first open (config chances, zone-aware gem jewelry). Ignores block-id filters
     * and does not set chunk bonus markers.
     */
    public static boolean applyWorldChestBonusesDebug(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> s,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd
    ) {
        if (c.getDroplist() != null) {
            StashGameplayConfig sg = StashGameplayConfig.getOrDefault(world.getGameplayConfig());
            StashPlugin.stash(state, c, sg.isClearContainerDropList());
        }
        SimpleItemContainer inv = c.getItemContainer();
        boolean changed = false;
        if (inv != null) {
            changed = applyOpenContainerBonuses(inv, world, s, state, plugin, cfg, rnd);
        }
        state.markNeedsSaving(s);
        return changed;
    }

    /**
     * Jewelry, gold, and zone sync at chunk load (catalog-independent). Sets {@link LootChestBonusApplied}.
     */
    public static boolean applyWorldChestCoreBonusesOnce(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> s,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nullable String blockTypeId,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd
    ) {
        ComponentType<ChunkStore, LootChestBonusApplied> appliedType = LootChestBonusApplied.getComponentType();
        if (s.getComponent(blockRef, appliedType) != null) {
            return false;
        }
        if (!LootChestWorldGenerated.isWorldLootChest(s, blockRef)) {
            return false;
        }
        if (!isEligibleForBlockId(blockTypeId, cfg)) {
            return false;
        }
        if (c.getDroplist() != null) {
            StashGameplayConfig sg = StashGameplayConfig.getOrDefault(world.getGameplayConfig());
            StashPlugin.stash(state, c, sg.isClearContainerDropList());
        }
        int zone = resolveAdventureZoneIndex(world, s, state);
        boolean changed = false;
        SimpleItemContainer inv = c.getItemContainer();
        if (inv != null) {
            changed |= syncJewelryInContainerForZone(inv, zone, cfg, rnd);
            changed |= applyCoreBonusesToContainer(inv, cfg, rnd, zone, false, false);
        }
        s.putComponent(blockRef, appliedType, new LootChestBonusApplied());
        state.markNeedsSaving(s);
        return changed;
    }

    /**
     * Plot blueprints, props, block palettes, and Gaia materials for world loot chests. Sets
     * {@link LootChestSupplementalBonusApplied} after rolls are attempted.
     */
    public static boolean applyWorldChestSupplementalBonusesOnce(
        @Nonnull Store<ChunkStore> s,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd
    ) {
        ComponentType<ChunkStore, LootChestSupplementalBonusApplied> supplementalType =
            LootChestSupplementalBonusApplied.getComponentType();
        LootChestSupplementalBonusApplied prior = s.getComponent(blockRef, supplementalType);
        if (prior != null && prior.isCurrentPipeline()) {
            return false;
        }
        if (prior != null) {
            s.removeComponent(blockRef, supplementalType);
        }
        if (!LootChestWorldGenerated.isWorldLootChest(s, blockRef)) {
            return false;
        }
        SimpleItemContainer inv = c.getItemContainer();
        if (inv == null) {
            return false;
        }
        boolean changed = applySupplementalBonusesToContainer(inv, cfg, plugin.getConstructionCatalog(), rnd, false);
        s.putComponent(blockRef, supplementalType, new LootChestSupplementalBonusApplied());
        state.markNeedsSaving(s);
        return changed;
    }

    /**
     * @deprecated Prefer {@link #applyWorldChestCoreBonusesOnce} + {@link #applyWorldChestSupplementalBonusesOnce}.
     */
    @Deprecated
    public static boolean applyWorldChestBonusesOnce(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> s,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull BlockModule.BlockStateInfo state,
        @Nonnull ItemContainerBlock c,
        @Nullable String blockTypeId,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean forceAllRolls
    ) {
        boolean changed = applyWorldChestCoreBonusesOnce(world, s, blockRef, state, c, blockTypeId, plugin, cfg, rnd);
        if (s.getComponent(blockRef, LootChestBonusApplied.getComponentType()) == null && !forceAllRolls) {
            return changed;
        }
        changed |= applyWorldChestSupplementalBonusesOnce(s, blockRef, state, c, plugin, cfg, rnd);
        return changed;
    }

    /** Re-rolls unappraised gem jewelry and fixes common-tier mistakes in zone 4+. */
    public static boolean syncJewelryInContainerForZone(
        @Nonnull SimpleItemContainer inv,
        int adventureZoneIndex,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd
    ) {
        boolean changed = false;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack current = inv.getItemStack(slot);
            if (ItemStack.isEmpty(current) || !JewelryItemIds.isJewelry(current.getItemId())) {
                continue;
            }
            if (!JewelryPieceKind.isEnchanted(current.getItemId())) {
                continue;
            }
            if (!needsLootChestJewelryReroll(current, adventureZoneIndex)) {
                continue;
            }
            ItemStack rolled = JewelryMetadata.rerollLootChestStack(current, rnd, cfg, adventureZoneIndex);
            if (rolled.equals(current)) {
                continue;
            }
            ItemStackSlotTransaction tx = inv.replaceItemStackInSlot(slot, current, rolled);
            if (tx.succeeded()) {
                changed = true;
            }
        }
        return changed;
    }

    public static boolean needsLootChestJewelryReroll(@Nonnull ItemStack stack, int adventureZoneIndex) {
        if (!JewelryPieceKind.isEnchanted(stack.getItemId())) {
            return false;
        }
        JewelryRarity rarity = JewelryMetadata.readRarity(stack);
        if (rarity == null) {
            return true;
        }
        return adventureZoneIndex >= WorldZoneIndex.MAX && rarity == JewelryRarity.COMMON;
    }

    /**
     * Optional rolls for priestess upgrade materials. Each line is independent; both can succeed when two empty slots
     * exist.
     */
    public static boolean tryInjectGaiaDraughtBonusesToContainer(
        @Nonnull SimpleItemContainer inv,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        boolean changed = false;
        double otherMult = LootRarityDifficulty.currentOrNormal().getOtherLootRarityMultiplier();
        changed |=
            tryInjectOptionalItemRoll(
                inv,
                cfg.getLootChestGaiaShardItemId(),
                LootRarityDifficulty.scaleChance(cfg.getLootChestGaiaShardChance(), otherMult),
                rnd,
                force
            );
        changed |=
            tryInjectOptionalItemRoll(
                inv,
                cfg.getLootChestGaiaCatalystItemId(),
                LootRarityDifficulty.scaleChance(cfg.getLootChestGaiaCatalystChance(), otherMult),
                rnd,
                force
            );
        changed |=
            tryInjectOptionalItemRoll(
                inv,
                cfg.getLootChestHeartberryItemId(),
                LootRarityDifficulty.scaleChance(cfg.getLootChestHeartberryChance(), otherMult),
                rnd,
                force
            );
        return changed;
    }

    private static boolean tryInjectOptionalItemRoll(
        @Nonnull SimpleItemContainer inv,
        @Nonnull String itemId,
        double chance,
        @Nonnull ThreadLocalRandom rnd,
        boolean force
    ) {
        if (itemId.isBlank()) {
            return false;
        }
        if (!force) {
            if (chance <= 0.0) {
                return false;
            }
            if (chance < 1.0 && rnd.nextDouble() >= chance) {
                return false;
            }
        }
        if (Item.getAssetMap().getAsset(itemId) == null) {
            return false;
        }
        short slot = randomEmptySlot(inv, rnd);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = new ItemStack(itemId, 1);
        ItemStackSlotTransaction tx = inv.addItemStackToSlot(slot, stack);
        return tx.succeeded();
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
