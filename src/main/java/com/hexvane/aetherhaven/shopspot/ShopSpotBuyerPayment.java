package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gold sources when a player buys from a shop spot (never the visited town's treasury unless it is their town). */
public final class ShopSpotBuyerPayment {
    private ShopSpotBuyerPayment() {}

    @Nullable
    public static TownRecord buyerHomeTown(@Nonnull TownManager tm, @Nonnull UUID buyerUuid) {
        return TownPlayerResolution.resolveFallbackAffiliatedTown(tm, buyerUuid);
    }

    /** Treasury debited is always {@code buyerHomeTown}, never the shop's town unless the buyer belongs there. */
    public static boolean mayDebitBuyerTownTreasury(@Nullable TownRecord buyerHomeTown, @Nonnull UUID buyerUuid) {
        return buyerHomeTown != null && buyerHomeTown.playerCanSpendTreasuryGold(buyerUuid);
    }
}
