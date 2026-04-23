package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.JewelryCraftingConfig;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves {@link JewelryRarity} from regular and concentrated life essence amounts using {@link JewelryCraftingConfig}.
 */
public final class JewelryCraftingRarityTable {
    private JewelryCraftingRarityTable() {}

    public static int totalPoints(int regular, int concentrated) {
        if (regular < 0) {
            regular = 0;
        }
        if (concentrated < 0) {
            concentrated = 0;
        }
        JewelryCraftingConfig c = JewelryRolling.config().getJewelry().getCrafting();
        return regular * c.getPointsPerRegular() + concentrated * c.getPointsPerConcentrated();
    }

    /**
     * @return the highest tier satisfied by the given inputs, or null if below common minimum (no craft).
     */
    @Nullable
    public static JewelryRarity resolve(int regular, int concentrated) {
        if (regular < 0) {
            regular = 0;
        }
        if (concentrated < 0) {
            concentrated = 0;
        }
        JewelryCraftingConfig c = JewelryRolling.config().getJewelry().getCrafting();
        int p = totalPoints(regular, concentrated);
        if (p < c.getMinTotalPointsCommon()) {
            return null;
        }
        if (p >= c.getMinTotalPointsLegendary() && concentrated >= c.getMinConcentratedLegendary()) {
            return JewelryRarity.LEGENDARY;
        }
        if (p >= c.getMinTotalPointsMythic() && concentrated >= c.getMinConcentratedMythic()) {
            return JewelryRarity.MYTHIC;
        }
        if (p >= c.getMinTotalPointsRare()) {
            return JewelryRarity.RARE;
        }
        if (p >= c.getMinTotalPointsUncommon()) {
            return JewelryRarity.UNCOMMON;
        }
        return JewelryRarity.COMMON;
    }
}
