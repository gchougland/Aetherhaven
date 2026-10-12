package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.List;
import java.util.UUID;

/**
 * Puts a construction project on a Builder's yard without the player opening the project board.
 *
 * <p>A project is on at most one yard. With several yards it goes to the one with the fewest open projects, counting
 * running yards before paused ones; equally busy yards are split by distance to the building site, then by the order
 * the yards were made. A project is offered once: an order the player takes off the board is not put back.
 */
public final class BuildingProjectAutoQueue {
    private BuildingProjectAutoQueue() {}

    /**
     * @param author the player who placed or started the project; orders only run for a member who may manage
     *     buildings
     * @return the yard holding the project afterwards, or null when it is on no yard
     */
    public static ApprenticeWorkplace offer(TownRecord town, PlotInstance plot, UUID author) {
        if (town == null || plot == null || plot.getState() == PlotInstanceState.COMPLETE) return null;
        UUID project = plot.getPlotId();
        ApprenticeWorkplace holder = holder(town, project);
        if (holder != null) {
            plot.setBuilderYardOffered(true);
            return holder;
        }
        if (plot.isBuilderYardOffered() || author == null
            || !town.hasMemberOrOwner(author) || !town.playerCanManageConstructions(author)) return null;
        ApprenticeWorkplace yard = choose(town, plot);
        if (yard == null) return null;
        BuildingOrders plan = yard.building();
        // A project that no longer exists can never be worked; it must not keep a live one off a full board.
        plan.makeRoom(gone -> town.findPlotById(gone) == null);
        if (plan.add(project, author) != null) return null;
        // A yard that was turned into another workplace keeps its old board. Clear this project from those, so making
        // it a yard again cannot produce a second order.
        for (var site : town.getApprenticeWorkplaces().sites()) {
            BuildingOrders dormant = site == yard ? null : site.existingBuilding();
            if (dormant == null) continue;
            for (var order : List.copyOf(dormant.list()))
                if (order.plot().equals(project)) dormant.remove(order.id());
        }
        plot.setBuilderYardOffered(true);
        return yard;
    }

    /**
     * Offers every started project that has not been offered yet, such as one begun while every yard was full or
     * before the town had a yard.
     *
     * @return true when the town changed and needs saving
     */
    public static boolean offerWaiting(TownRecord town) {
        if (town == null || yards(town).isEmpty()) return false;
        boolean changed = false;
        for (PlotInstance plot : List.copyOf(town.getPlotInstances())) {
            if (plot.getState() != PlotInstanceState.ASSEMBLING || plot.isBuilderYardOffered()) continue;
            UUID author = plot.getAssemblyOwnerUuid();
            if (author == null || !town.hasMemberOrOwner(author) || !town.playerCanManageConstructions(author))
                author = town.getOwnerUuid();
            offer(town, plot, author);
            changed |= plot.isBuilderYardOffered();
        }
        return changed;
    }

    /** The yard whose board lists this project, if any. */
    public static ApprenticeWorkplace holder(TownRecord town, UUID project) {
        for (var site : yards(town)) {
            BuildingOrders plan = site.existingBuilding();
            if (plan != null && plan.list().stream().anyMatch(order -> order.plot().equals(project))) return site;
        }
        return null;
    }

    static ApprenticeWorkplace choose(TownRecord town, PlotInstance plot) {
        ApprenticeWorkplace best = null;
        long bestKey = Long.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (var site : yards(town)) {
            BuildingOrders plan = site.existingBuilding();
            int open = 0;
            if (plan != null) for (var order : plan.list()) if (exists(town, order.plot())) open++;
            if (open >= BuildingOrders.CAPACITY) continue;
            // Paused yards only when nothing is running; then the least busy.
            long key = (plan != null && plan.paused() ? 1_000L : 0L) + open;
            double distance = distanceSquared(site.area(), plot);
            if (key < bestKey || key == bestKey && distance < bestDistance) {
                best = site;
                bestKey = key;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static List<ApprenticeWorkplace> yards(TownRecord town) {
        return town.getApprenticeWorkplaces().sites().stream().filter(site -> site.type() == TownsfolkWorkType.BUILDING).toList();
    }

    /** Unfinished plots only. A finished project is retired by {@link BuildingProjectCleanup}. */
    private static boolean exists(TownRecord town, UUID project) {
        PlotInstance plot = town.findPlotById(project);
        return plot != null && plot.getState() != PlotInstanceState.COMPLETE;
    }

    private static double distanceSquared(WorkplaceArea yard, PlotInstance plot) {
        var site = plot.toFootprint();
        double dx = (yard.minX() + yard.maxX()) / 2.0 - (site.getMinX() + site.getMaxX() + 1) / 2.0;
        double dz = (yard.minZ() + yard.maxZ()) / 2.0 - (site.getMinZ() + site.getMaxZ() + 1) / 2.0;
        return dx * dx + dz * dz;
    }
}
