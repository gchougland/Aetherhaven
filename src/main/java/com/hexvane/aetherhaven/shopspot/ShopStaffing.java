package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import java.util.Objects;

/** Assignment policy shared by the records shelf and physical attendance checks. */
public final class ShopStaffing {
    private ShopStaffing() {}
    public static boolean isShop(PlotInstance plot, ConstructionCatalog catalog) {
        return plot != null && plot.getState() == PlotInstanceState.COMPLETE && catalog != null
            && catalog.matchesGameplayConstruction(plot.getConstructionId(), AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP);
    }
    public static String unavailable(TownsfolkProgress progress, boolean member, boolean alive, boolean guard, boolean overdue) {
        if (!member) return "This resident is no longer part of the town.";
        if (!alive) return "This resident needs to be revived.";
        if (guard) return "This resident is on guard duty.";
        if (progress == null || progress.skill(TownsfolkSkill.COMMERCE) == 0) return "This resident cannot perform Commerce.";
        if (overdue) return "Pay this resident's overdue bill first.";
        return null;
    }
    public static boolean canWork(TownsfolkProgress progress, boolean member, boolean alive, boolean guard, boolean overdue) {
        return unavailable(progress, member, alive, guard, overdue) == null && progress.isEmployed();
    }
    public static double purchaseChance(int skill) { return skill <= 0 ? 0 : .30 + .05 * Math.min(10, skill); }
    public static boolean makesPurchase(int skill, double roll) {
        return Double.isFinite(roll) && roll >= 0 && roll < purchaseChance(skill);
    }
    /** A resident has at most one shop assignment. Moving shops does not change profession priorities. */
    public static void assign(TownRecord town, PlotInstance plot, String id) {
        id = id == null || id.isBlank() ? null : id.trim().toLowerCase(java.util.Locale.ROOT);
        if (id != null) for (var other : town.getPlotInstances())
            if (Objects.equals(other.getShopClerkId(), id)) other.setShopClerkId(null);
        plot.setShopClerkId(id);
    }
}
