package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.server.core.universe.world.World;

/** Resolve the town once per scan, then use the same ownership rule for detection, deliveries and work. */
public final class WorkplaceOwnership {
    private WorkplaceOwnership() {}
    public record Scope(TownWorkplaces workplaces,WorkplaceArea area) {
        /** Also usable just outside the band when diagnosing a bench at the wrong height. */
        public boolean available(double x,double y,double z) {
            var owner=workplaces==null?null:workplaces.ownerAtWork(x,y,z);
            return owner==null||area.equals(owner.area());
        }
        public boolean contains(double x,double y,double z) {
            return area.containsWork(x,y,z)&&available(x,y,z);
        }
    }
    public static Scope scope(World world,WorkplaceArea area) {
        var plugin=AetherhavenPlugin.get();
        var town=plugin==null?null:AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin)
            .findTownContainingBlock(world.getName(),area.minX(),area.minZ());
        return new Scope(town==null?null:town.getApprenticeWorkplaces(),area);
    }
}
