package com.hexvane.aetherhaven.economy.api;

/**
 * Where Aetherhaven hands out gold as items ({@link EconomyProvider#goldItems}). Each source has its own config or
 * content, and a provider may serve one and not another (a mod whose own drop tables already cover dungeon chests).
 */
public enum GoldSource {
    /** A chest of a dungeon or a ruin, filled as its chunk loads ({@code LootChestGoldCoin*}). */
    LOOT_CHEST,
    /** A pot or a crate a player breaks ({@code BreakableContainers.Gold}). */
    BREAKABLE_CONTAINER,
    /** The output of a recipe that gives coins (a plot token salvaged), rewritten when the recipes load. */
    RECIPE
}
