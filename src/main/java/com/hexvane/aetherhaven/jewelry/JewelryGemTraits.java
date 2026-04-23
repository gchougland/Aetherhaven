package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import javax.annotation.Nonnull;

/**
 * Three entity-stat id strings per gem (first-pass table; several gems still share stats).
 */
public final class JewelryGemTraits {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private JewelryGemTraits() {}

    @Nonnull
    public static String[] statIdsFor(@Nonnull JewelryGem gem) {
        return switch (gem) {
            case ZEPHYR -> new String[] {"Stamina", "SignatureEnergy", "Oxygen"};
            case TOPAZ -> new String[] {"Stamina", "SignatureEnergy", "Ammo"};
            case EMERALD -> new String[] {"Health", "Mana", "Oxygen"};
            case DIAMOND -> new String[] {"Health", "Stamina", "Mana"};
            case SAPPHIRE -> new String[] {"Mana", "Stamina", "Oxygen"};
            case RUBY -> new String[] {"Health", "Stamina", "Ammo"};
            case VOIDSTONE -> new String[] {"Mana", "SignatureEnergy", "Ammo"};
        };
    }

    /**
     * Logs unknown stat ids once at startup so jewelry rolls skip them safely at runtime.
     * Call from {@code JavaPlugin.start()}, not {@code setup()}, so {@link EntityStatType} assets are loaded and
     * {@code DefaultEntityStatTypes.update()} has run (same string ids as {@code DefaultEntityStatTypes}).
     */
    public static void validateStatIdsAtStartup() {
        for (JewelryGem gem : JewelryGem.values()) {
            for (String statId : statIdsFor(gem)) {
                int idx = EntityStatType.getAssetMap().getIndex(statId);
                if (idx == Integer.MIN_VALUE) {
                    LOGGER.atWarning().log("Jewelry gem %s references unknown EntityStatType id: %s", gem, statId);
                }
            }
        }
    }
}
