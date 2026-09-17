package com.hexvane.aetherhaven.economy.api;

/**
 * Where Aetherhaven rolled gold loot. Each source has its own config section, and an economy provider may serve
 * one and not the other (a mod whose own drop tables already cover dungeon chests).
 */
public enum LootSource {
    /** A chest of a dungeon or a ruin, filled as its chunk loads ({@code LootChestGoldCoin*}). */
    LOOT_CHEST,
    /** A pot or a crate a player breaks ({@code BreakableContainers.Gold}). */
    BREAKABLE_CONTAINER
}
