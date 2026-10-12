package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.equipment.ProfessionKitCatalog;
import com.hexvane.aetherhaven.patrol.GuardSpoils;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.warehouse.CourierAssignments;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.warehouse.WarehouseDeskBlock;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.BuildingOrders;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.joml.Vector3d;

/**
 * Builders tidy loose items lying on the site they are building into the town's storage.
 *
 * <p>Clearing a site breaks its blocks into ordinary item entities. A builder working the project picks up whatever
 * lies inside its footprint and {@link #MARGIN} blocks around it, as far as their hands and the town's general storage
 * have room, and the existing courier return trip puts the load away at a ledger desk.
 *
 * <p>Nothing is ever deleted or copied: an item leaves the ground only for the amount
 * {@link TownWarehouse#carrySalvage} took, and a load that no longer fits when it reaches the desk is put back on the
 * ground there. With no finished Warehouse, no ledger desk in live terrain, or full storage, nothing is picked up.
 * No chunk is loaded: only item entities the world already tracks are looked at.
 */
public final class SiteSalvage {
    private SiteSalvage() {}

    /** Loose items this many blocks outside a project's footprint still count as lying on its site. */
    public static final int MARGIN = 2;
    /** A builder this close to the item they walked to picks it up. */
    public static final double REACH = 2.5;
    /** One stop collects every loose item of the site this close to the builder. */
    public static final double SWEEP = 4.0;
    /** A builder with nothing to fetch looks again this often, between pieces. */
    public static final long SCAN_MS = 4000L;
    /** A part load stays in hand this long after the builder was last busy on the site; then it is carried home. */
    public static final long HOLD_MS = 5000L;
    /** A walk to a loose item is given up after this long. */
    public static final long WALK_MS = 20_000L;
    static final long CLAIM_MS = 3000L;
    static final long UNREACHABLE_MS = 120_000L;
    /** Gameplay id of the building whose completion means the town has a Warehouse. */
    public static final String WAREHOUSE = "plot_warehouse";

    // ---- Rules that need no world ----

    /** The site of a project: its footprint and {@link #MARGIN} blocks around it. */
    public static PlotFootprintRecord area(PlotFootprintRecord footprint) {
        return new PlotFootprintRecord(
            footprint.getMinX() - MARGIN, footprint.getMinY() - MARGIN, footprint.getMinZ() - MARGIN,
            footprint.getMaxX() + MARGIN, footprint.getMaxY() + MARGIN, footprint.getMaxZ() + MARGIN);
    }

    /** True when a point in the world lies on the site. */
    public static boolean lies(PlotFootprintRecord area, double x, double y, double z) {
        return area.containsBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static boolean liesOnAny(List<PlotFootprintRecord> areas, Vector3d point) {
        for (PlotFootprintRecord area : areas) if (lies(area, point.x, point.y, point.z)) return true;
        return false;
    }

    /**
     * Sites of the projects on a yard's board that are being raised right now. A paused yard, a plan that has not
     * started and a finished building have no site to tidy.
     *
     * @param beingBuilt whether builders can work this order at the moment
     */
    public static List<PlotFootprintRecord> areas(TownRecord town, ApprenticeWorkplace yard, Predicate<BuildingOrders.Order> beingBuilt) {
        BuildingOrders board = yard == null ? null : yard.existingBuilding();
        if (town == null || board == null || board.paused()) return List.of();
        List<PlotFootprintRecord> areas = new ArrayList<>();
        for (BuildingOrders.Order order : board.list()) {
            PlotInstance plot = town.findPlotById(order.plot());
            if (plot != null && plot.getState() == PlotInstanceState.ASSEMBLING && beingBuilt.test(order)) areas.add(area(plot.toFootprint()));
        }
        return areas;
    }

    /** True when the town has a finished Warehouse building. */
    public static boolean warehouseBuilt(TownRecord town, Predicate<String> isWarehouse) {
        if (town == null) return false;
        for (PlotInstance plot : town.getPlotInstances())
            if (plot.getState() == PlotInstanceState.COMPLETE && isWarehouse.test(plot.getConstructionId())) return true;
        return false;
    }

    /**
     * Stacks a builder carries off a site. A builder carries what a courier of the same level would, or their own
     * Logistics allowance when that is more, and always at least one stack.
     */
    public static int carryStacks(TownsfolkProgress progress, ProfessionKitCatalog kits) {
        int asBuilder = CourierAssignments.carryingStacks(progress.skill(TownsfolkSkill.BUILDING));
        int asCourier = CourierAssignments.carryingStacks(progress, kits);
        return Math.clamp(Math.max(asBuilder, asCourier), 1, WarehouseBatch.CARGO_SLOTS);
    }

    /**
     * The one place a pickup is decided.
     *
     * @param storageReady the town has a Warehouse and a ledger desk in live terrain; when false nothing is taken
     * @return how many of each offered stack went into the builder's hands, in the same order
     */
    public static int[] take(TownWarehouse stock, boolean storageReady, String worker, List<ItemStack> offered, int stackLimit) {
        if (!storageReady || stock == null) return new int[offered.size()];
        return stock.carrySalvage(worker, offered, stackLimit);
    }

    // ---- The world ----

    /** A finished Warehouse and a ledger desk of this town in terrain that is loaded right now. */
    public static boolean storageReady(World world, TownManager manager, TownRecord town, AetherhavenPlugin plugin) {
        return warehouseBuilt(town, id -> plugin.getConstructionCatalog().matchesGameplayConstruction(id, WAREHOUSE))
            && !WarehouseDeskBlock.loaded(world, manager, town).isEmpty();
    }

    /** A loose item a builder may fetch, and where it lies. */
    public record Found(Ref<EntityStore> item, Vector3d position) {
        /** The cell to stand beside. An item resting a hair under a block's top still belongs to the cell above it. */
        public WarehouseBatch.Point cell() {
            return new WarehouseBatch.Point((int) Math.floor(position.x), (int) Math.floor(position.y + 0.5), (int) Math.floor(position.z));
        }
    }

    /**
     * The stack of an item entity that anyone could pick up, or null. Shop and cargo displays, props, things with a
     * prompt, the picture of an item flying to a player, items a guard has claimed and items with their own pickup
     * effect are all left alone.
     */
    public static ItemStack pickable(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        ItemComponent item = accessor.getComponent(ref, ItemComponent.getComponentType());
        if (item == null || !item.canPickUp() || item.isRemovedByPlayerPickup()) return null;
        ItemStack stack = item.getItemStack();
        if (ItemStack.isEmpty(stack) || !stack.isValid() || GuardSpoils.owner(stack) != null) return null;
        Archetype<EntityStore> archetype = accessor.getArchetype(ref);
        if (archetype.contains(PreventPickup.getComponentType()) || archetype.contains(PickupItemComponent.getComponentType())
            || archetype.contains(PropComponent.getComponentType()) || archetype.contains(Interactable.getComponentType())) return null;
        return stack.getItem().getInteractions().get(InteractionType.Pickup) == null ? stack : null;
    }

    /**
     * The nearest loose item on one of the sites that this builder could take at least one of, that no other builder
     * is walking to, and that was not found out of reach a moment ago.
     */
    public static Found nearest(ComponentAccessor<EntityStore> accessor, World world, List<PlotFootprintRecord> areas, String worker,
                                TownWarehouse.SalvageRoom room, Vector3d from, long now) {
        if (room == null || room.handsFull() || areas.isEmpty()) return null;
        Map<Ref<EntityStore>, Mark> marks = marks(world);
        marks.entrySet().removeIf(entry -> entry.getValue().until() <= now || !entry.getKey().isValid());
        var items = accessor.getResource(EntityModule.get().getItemSpatialResourceType()).getSpatialStructure();
        List<Ref<EntityStore>> near = new ArrayList<>();
        Found best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlotFootprintRecord area : areas) {
            // One sphere round the whole site; the box test below does the exact work.
            double hx = (area.getMaxX() + 1 - area.getMinX()) / 2.0, hy = (area.getMaxY() + 1 - area.getMinY()) / 2.0, hz = (area.getMaxZ() + 1 - area.getMinZ()) / 2.0;
            near.clear();
            items.collect(new Vector3d(area.getMinX() + hx, area.getMinY() + hy, area.getMinZ() + hz), Math.sqrt(hx * hx + hy * hy + hz * hz), near);
            for (Ref<EntityStore> ref : near) {
                ItemStack stack = pickable(accessor, ref);
                TransformComponent transform = stack == null ? null : accessor.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;
                Vector3d at = transform.getPosition();
                if (!lies(area, at.x, at.y, at.z) || room.accepts(stack) == 0) continue;
                Mark mark = marks.get(ref);
                if (mark != null && !worker.equals(mark.worker())) continue;
                double distance = from.distanceSquared(at);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Found(ref, new Vector3d(at));
                }
            }
        }
        return best;
    }

    /**
     * Picks up the loose items of the sites within {@link #SWEEP} of the builder, nearest first. Each item entity
     * loses exactly what the builder's hands took: it is removed when all of it was taken and keeps the rest otherwise.
     *
     * @param remove how an emptied item entity leaves the world: the command buffer inside a tick, the store outside one
     * @return the number of items taken
     */
    public static int collect(ComponentAccessor<EntityStore> accessor, List<PlotFootprintRecord> areas, TownWarehouse stock, boolean storageReady,
                              String worker, int stackLimit, Vector3d at, Consumer<Ref<EntityStore>> remove) {
        if (!storageReady || areas.isEmpty()) return 0;
        List<Ref<EntityStore>> near = new ArrayList<>();
        accessor.getResource(EntityModule.get().getItemSpatialResourceType()).getSpatialStructure().collect(at, SWEEP, near);
        record Loose(Ref<EntityStore> ref, ItemComponent item, ItemStack stack, double distance) {}
        List<Loose> loose = new ArrayList<>();
        for (Ref<EntityStore> ref : near) {
            ItemStack stack = pickable(accessor, ref);
            TransformComponent transform = stack == null ? null : accessor.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null || !liesOnAny(areas, transform.getPosition())) continue;
            loose.add(new Loose(ref, accessor.getComponent(ref, ItemComponent.getComponentType()), stack, at.distanceSquared(transform.getPosition())));
        }
        if (loose.isEmpty()) return 0;
        loose.sort(Comparator.comparingDouble(Loose::distance));
        List<ItemStack> offered = new ArrayList<>();
        for (Loose entry : loose) offered.add(entry.stack());
        int[] taken = take(stock, true, worker, offered, stackLimit);
        int total = 0;
        for (int i = 0; i < taken.length; i++) {
            if (taken[i] <= 0) continue;
            Loose entry = loose.get(i);
            int rest = entry.stack().getQuantity() - taken[i];
            // The stack is cleared at once, so nobody else can take the same items before the entity is gone.
            entry.item().setItemStack(rest > 0 ? entry.stack().withQuantity(rest) : null);
            if (rest <= 0) remove.accept(entry.ref());
            total += taken[i];
        }
        return total;
    }

    /**
     * The end of the trip, at a ledger desk. What fits goes into general storage; what does not is put on the ground
     * at the builder's feet.
     *
     * @return false when this worker carries no salvage
     */
    public static boolean unload(TownWarehouse stock, UUID batch, String worker, Vector3d at, ComponentAccessor<EntityStore> accessor) {
        List<ItemStack> left = stock.unloadSalvage(batch, worker);
        if (left == null) return false;
        if (!left.isEmpty()) {
            Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(accessor, left, new Vector3d(at.x, at.y + 0.5, at.z), Rotation3f.ZERO);
            if (drops.length > 0) accessor.addEntities(drops, AddReason.SPAWN);
        }
        return true;
    }

    // ---- Who is walking to what. World thread only; nothing here is saved. ----

    /** A null worker marks an item nobody could reach. */
    private record Mark(String worker, long until) {}
    private static final Map<World, Map<Ref<EntityStore>, Mark>> MARKS = Collections.synchronizedMap(new WeakHashMap<>());

    private static Map<Ref<EntityStore>, Mark> marks(World world) {
        synchronized (MARKS) {
            return MARKS.computeIfAbsent(world, ignored -> new HashMap<>());
        }
    }

    /** Call every tick a builder is walking to an item, so no second builder sets off for it. */
    public static void claim(World world, Ref<EntityStore> item, String worker, long now) {
        marks(world).put(item, new Mark(worker, now + CLAIM_MS));
    }

    /** No builder walks to this item again for a while. */
    public static void unreachable(World world, Ref<EntityStore> item, long now) {
        marks(world).put(item, new Mark(null, now + UNREACHABLE_MS));
    }

    public static void release(World world, Ref<EntityStore> item, String worker) {
        Map<Ref<EntityStore>, Mark> marks = marks(world);
        Mark mark = marks.get(item);
        if (mark != null && worker.equals(mark.worker())) marks.remove(item);
    }
}
