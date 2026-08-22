package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.world.WorldZoneIndex;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Chest / gift jewelry rolls. Glow rings keep a fixed drop rate. Procedural enchanted jewelry is gated by
 * {@code LootChest.JewelryChance} on world chests (glow rings are not).
 */
public final class JewelryChestLoot {
    /** Historical share of jewelry fills that were large glow rings (legendary weight 1 of 97). */
    private static final double LARGE_GLOW_SHARE = 1.0 / 97.0;
    /** Historical share of jewelry fills that were glow rings (mythic weight 4 of 97). */
    private static final double GLOW_SHARE = 4.0 / 97.0;
    /**
     * Absolute per eligible world chest for a large glow ring (old JewelryChance 0.2 × legendary share).
     */
    private static final double LARGE_GLOW_ABS = 0.2 * LARGE_GLOW_SHARE;
    /** Absolute per eligible world chest for a glow ring. */
    private static final double GLOW_ABS = 0.2 * GLOW_SHARE;

    private JewelryChestLoot() {}

    /**
     * Gift / force rolls: rare glow rings first (same relative share as before), otherwise one procedural piece.
     */
    @Nonnull
    public static ItemStack rollForChest(@Nonnull ThreadLocalRandom rnd, @Nonnull AetherhavenPluginConfig cfg) {
        return rollForChest(rnd, cfg, WorldZoneIndex.UNKNOWN_DEFAULT);
    }

    @Nonnull
    public static ItemStack rollForChest(
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg,
        int adventureZoneIndex
    ) {
        ItemStack artifact = tryRollGlowRing(rnd, LARGE_GLOW_SHARE, GLOW_SHARE);
        if (artifact != null) {
            return artifact;
        }
        return rollProcedural(rnd, cfg, adventureZoneIndex);
    }

    /**
     * World-chest injection: glow rings at fixed absolute rates, then procedural jewelry using JewelryChance.
     *
     * @return stack to place, or null when nothing should be added
     */
    @Nullable
    public static ItemStack rollForWorldChest(
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg,
        int adventureZoneIndex,
        boolean force
    ) {
        if (force) {
            return rollForChest(rnd, cfg, adventureZoneIndex);
        }
        ItemStack artifact = tryRollGlowRing(rnd, LARGE_GLOW_ABS, GLOW_ABS);
        if (artifact != null) {
            return artifact;
        }
        double proceduralChance = cfg.getLootChestJewelryChance();
        if (proceduralChance <= 0.0 || rnd.nextDouble() >= proceduralChance) {
            return null;
        }
        return rollProcedural(rnd, cfg, adventureZoneIndex);
    }

    @Nullable
    private static ItemStack tryRollGlowRing(
        @Nonnull ThreadLocalRandom rnd,
        double largeGlowChance,
        double glowChance
    ) {
        double p = rnd.nextDouble();
        if (p < largeGlowChance) {
            return artifactStack(AetherhavenConstants.ITEM_RING_LARGE_GLOW);
        }
        if (p < largeGlowChance + glowChance) {
            return artifactStack(AetherhavenConstants.ITEM_RING_GLOW);
        }
        return null;
    }

    @Nonnull
    private static ItemStack rollProcedural(
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg,
        int adventureZoneIndex
    ) {
        ItemStack stack = UnidentifiedJewelry.rollEnchantedStack(rnd);
        return JewelryMetadata.ensureRolledForLootChest(stack, rnd, cfg, adventureZoneIndex);
    }

    @Nonnull
    private static ItemStack artifactStack(@Nonnull String itemId) {
        return new ItemStack(itemId, 1);
    }
}
