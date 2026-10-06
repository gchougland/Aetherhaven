package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import java.util.Comparator;

/** The player's priorities win; equally important food sites are tended before their reserve expires. */
public final class FoodWorkSelection {
    private FoodWorkSelection() {}
    public static Comparator<ApprenticeWorkplace> order(TownsfolkProgress worker,double x,double y,double z) {
        return Comparator.comparingInt((ApprenticeWorkplace site)->worker.priority(site.type()))
            .thenComparingLong(site->site.food().minutesUntilNeglected())
            .thenComparingDouble(site->{var a=site.area();
                double dx=a.minX()-x,dy=a.floorY()-y,dz=a.minZ()-z;return dx*dx+dy*dy+dz*dz;})
            .thenComparing(ApprenticeWorkplace::id);
    }
}
