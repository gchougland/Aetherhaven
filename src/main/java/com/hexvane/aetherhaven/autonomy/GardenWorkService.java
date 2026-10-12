package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.mastery.CoreProductionRequirements;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.ProductionStorageLocator;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.joml.Vector3d;

/**
 * The Druid's rounds of the Spirit Garden: planting a chosen seed, the growing rite, the harvest and one trip home
 * with the basket. Every change to a planter needs him standing at it; between rounds he lives his ordinary day.
 */
public final class GardenWorkService {
    private GardenWorkService() {}
    private enum Action { PLANT, RITE, HARVEST, DELIVER }
    /** Planters may stand anywhere in town, so he is given a full minute to walk to one before trying another way. */
    private static final long TRAVEL_LIMIT_MS = 60_000;
    private static final Map<Ref<EntityStore>, Visit> VISITS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Visit {
        UUID plot, planter;
        int slot, attempt;
        Action action;
        Point target;
        Vector3d approach;
        long began, retry, lastTick, nextVisual;
        double attendance;
        boolean active, props;
    }

    public static boolean active(Ref<EntityStore> ref) { var v = VISITS.get(ref); return v != null && v.active; }

    public static boolean tick(float dt, Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
            NPCEntity npc, TownVillagerBinding binding, VillagerNeeds needs, VillagerAutonomyState autonomy,
            TownRecord town, TownManager manager, AetherhavenPlugin plugin) {
        var plot = town == null || binding.getJobPlotId() == null ? null : town.findPlotById(binding.getJobPlotId());
        if (!SpiritGarden.KIND.equals(binding.getKind()) || plot == null || plot.getState() != PlotInstanceState.COMPLETE
            || !plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(), SpiritGarden.CONSTRUCTION)) {
            interrupt(ref, store, npc, buffer); return false;
        }
        var production = SpiritGarden.production(town, plot);
        var garden = production.garden();
        if (garden.planters().isEmpty() && !garden.carrying()
            || store.getComponent(ref, DeathComponent.getComponentType()) != null) { interrupt(ref, store, npc, buffer); return false; }
        boolean offDuty = needs.getHunger() < 20 || needs.getEnergy() < 20 || autonomy.isFillingHunger() || autonomy.isFillingEnergy()
            || !ProductionLiveWorkDetection.isOnWorkSchedule(store, ref, npc, plugin.getVillagerDefinitionCatalog(), plugin.getVillagerScheduleRegistry())
            || !com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(store, town);
        // A harvest already in his basket is always carried home, even after his shift has ended.
        if (offDuty && !garden.carrying()) { interrupt(ref, store, npc, buffer); return false; }
        var transform = store.getComponent(ref, TransformComponent.getComponentType());
        var entry = ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(), plugin.getWorkplaceUnlockCatalog(), SpiritGarden.CONSTRUCTION, production);
        if (transform == null || entry == null) return false;
        var world = store.getExternalData().getWorld(); var position = transform.getPosition();
        var visit = VISITS.computeIfAbsent(ref, ignored -> new Visit()); long now = System.currentTimeMillis();
        double step = visit.lastTick == 0 || now - visit.lastTick > 2000 ? 0 : Math.min(1, Math.max(0, dt)); visit.lastTick = now;
        if (visit.plot != null && !visit.plot.equals(plot.getPlotId())) stop(ref, store, npc, buffer, visit);
        visit.plot = plot.getPlotId();
        var rules = SpiritGardenRules.get();
        if (!visit.active) {
            // Nothing to do right now, such as plants still resting. He goes back to his own day until the next look.
            if (now < visit.retry) return false;
            if (!choose(world, town, plot, production, entry, visit, now) || offDuty && visit.action != Action.DELIVER) {
                visit.retry = now + 3000; return false;
            }
            visit.approach = CourierWorkService.approach(world, visit.target, position, visit.attempt);
            if (visit.approach == null) { visit.attempt++; visit.retry = now + 3000; return false; }
            var registry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref, store, buffer, autonomy, needs, registry, false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE); autonomy.clearTravelAndPoiState();
            buffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            visit.active = true; visit.began = now; visit.attendance = 0; visit.nextVisual = 0;
            var bound = garden.planters().get(visit.slot);
            var plant = visit.action == Action.PLANT && bound != null ? rules.plant(bound.plant()) : null;
            String held = visit.action == Action.DELIVER ? garden.cargo().getFirst().item() : plant == null ? null : plant.seed();
            if (held != null) { visit.props = true; buffer.run(st -> { if (ref.isValid() && visit.active) VillagerLifeProps.carry(ref, held, st); }); }
            return true;
        }
        if (!valid(world, town, plot, production, visit) || !CourierWorkService.clearApproach(world, visit.approach)
            || !reached(position, visit) && now - visit.began > TRAVEL_LIMIT_MS) {
            visit.attempt++; visit.retry = now + 3000; stop(ref, store, npc, buffer, visit); return true;
        }
        if (store.getComponent(ref, com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType()) != null) return true;
        if (!reached(position, visit)) {
            WorkTravelRecovery.keepMoving(ref, store, buffer, npc, position, visit.approach, now);
            npc.setLeashPoint(new Vector3d(visit.approach)); VillagerAutonomySystem.applyAutonomyRoleState(ref, npc, buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world, position, visit.approach, null);
            buffer.putComponent(ref, NPCEntity.getComponentType(), npc); return true;
        }
        NpcStandStill.hold(ref, store, npc, visit.approach, buffer);
        PoiAutonomyVisuals.applyBodyYaw(ref, store, buffer, (float) (Math.atan2(visit.target.x() + .5 - position.x, visit.target.z() + .5 - position.z) + Math.PI));
        visit.attendance += step;
        final Action action = visit.action;
        if (action != Action.DELIVER && now >= visit.nextVisual) {
            // The rite: arms opened wide over the plant while green and gold light climbs around it.
            boolean rite = action == Action.RITE; visit.props = true; visit.nextVisual = now + (rite ? 6000 : 2500);
            var glow = new Vector3d(visit.target.x() + .5, visit.target.y(), visit.target.z() + .5);
            buffer.run(st -> { if (ref.isValid() && visit.active) {
                if (rite) VillagerLifeProps.clear(ref, st);
                AnimationUtils.playAnimation(ref, AnimationSlot.Action, NpcFaceVisuals.itemAnimationsForFaceRig(ref, "Aetherhaven_Life_Actions", st), rite ? "Stretch" : "Tend", true, st);
                if (rite) VillagerWorkVisuals.gardenRite(st, glow);
            }});
        }
        var work = rules.work();
        if (visit.attendance < switch (action) { case PLANT -> work.plantSeconds(); case RITE -> work.riteSeconds(); case HARVEST -> work.harvestSeconds(); case DELIVER -> .75; }) return true;
        // Every change to the world and the stores happens after the entity command buffer, with fresh checks.
        final int slot = visit.slot; final UUID identity = visit.planter; final Point target = visit.target;
        final Vector3d standing = new Vector3d(visit.approach);
        buffer.run(st -> {
            if (!ref.isValid() || st.getComponent(ref, DeathComponent.getComponentType()) != null) return;
            var actual = st.getComponent(ref, TransformComponent.getComponentType());
            var worker = st.getComponent(ref, TownVillagerBinding.getComponentType());
            if (actual == null || worker == null || !plot.getPlotId().equals(worker.getJobPlotId()) || !town.getTownId().equals(worker.getTownId())
                || !SpiritGarden.KIND.equals(worker.getKind()) || plot.getState() != PlotInstanceState.COMPLETE || town.findPlotById(plot.getPlotId()) != plot
                || !(CourierWorkService.arrived(actual.getPosition(), standing) || action == Action.DELIVER && beside(actual.getPosition(), target))) return;
            boolean changed = false;
            if (action == Action.DELIVER) {
                if (garden.carrying() && ProductionStorageLocator.valid(world, plot, target)) {
                    var delivered = garden.deliverAll(production, item -> GardenUpgrades.capacity(production, entry, item));
                    long xp = 0; for (var part : delivered.entrySet()) xp += (long) part.getValue() * rules.xpFor(part.getKey());
                    CoreVillagerMasteryService.progress(town, SpiritGarden.KIND).earnXp(xp);
                    changed = true;
                }
            } else {
                // Planting, the rite and the harvest all need his shift and someone from town nearby.
                if (!ProductionLiveWorkDetection.isOnWorkSchedule(st, ref, npc, plugin.getVillagerDefinitionCatalog(), plugin.getVillagerScheduleRegistry())
                    || !com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(st, town)) return;
                var planter = garden.planters().get(slot);
                var plant = planter == null || !planter.id().equals(identity) ? null : rules.plant(planter.plant());
                if (plant == null || CoreProductionRequirements.issueForKind(town, SpiritGarden.KIND, plant.plant()) != null
                    || !SpiritGardenWorld.planterStands(world, planter.block())) return;
                long clock = SpiritGardenWorld.now(world), rest = GardenUpgrades.restSeconds(production);
                var glow = new Vector3d(target.x() + .5, target.y(), target.z() + .5);
                if (action == Action.PLANT) {
                    changed = slot < GardenUpgrades.planters(production)
                        && garden.plant(slot, identity, clock, rest, () -> SpiritGardenWorld.show(world, planter, plant, 0));
                } else if (action == Action.RITE) {
                    changed = garden.rite(slot, identity, clock, rest, stage -> SpiritGardenWorld.show(world, planter, plant, stage));
                    if (changed) VillagerWorkVisuals.gardenBloom(st, glow);
                } else if (hasRoom(production, entry, plant)) {
                    var yields = SpiritGardenWorld.rollHarvest(plant, GardenUpgrades.plantsPerHarvest(production), GardenUpgrades.petalRolls(production));
                    changed = garden.harvest(slot, identity, yields, clock, rest, () -> SpiritGardenWorld.show(world, planter, plant, 0));
                    if (changed) VillagerWorkVisuals.gardenBloom(st, glow);
                }
                if (changed) garden.rotateAfter(slot);
            }
            if (changed) { town.getWarehouse().markProductionWithdrawal(); manager.updateTown(town); }
        });
        visit.attempt = 0; visit.retry = now + 1500; stop(ref, store, npc, buffer, visit); return true;
    }

    /** A harvest in the basket goes home first. Then ripe plants, then new seeds, then plants ready for their rite. */
    private static boolean choose(World world, TownRecord town, PlotInstance plot, PlotProductionState production,
            ProductionCatalog.Entry entry, Visit v, long now) {
        var garden = production.garden(); var rules = SpiritGardenRules.get();
        if (garden.carrying()) {
            v.action = Action.DELIVER; v.planter = null; v.target = ProductionStorageLocator.find(world, plot, now);
            return v.target != null;
        }
        long clock = SpiritGardenWorld.now(world), rest = GardenUpgrades.restSeconds(production);
        int allowed = GardenUpgrades.planters(production), slots = SpiritGardenState.MAX_PLANTERS;
        for (var wanted : new Action[] {Action.HARVEST, Action.PLANT, Action.RITE}) for (int offset = 0; offset < slots; offset++) {
            int slot = (garden.firstSlot() + offset) % slots;
            var planter = garden.planters().get(slot);
            var plant = planter == null ? null : rules.plant(planter.plant());
            if (plant == null || CoreProductionRequirements.issueForKind(town, SpiritGarden.KIND, plant.plant()) != null
                || !SpiritGardenWorld.planterStands(world, planter.block())) continue;
            if (planter.stage() >= 0 && SpiritGardenWorld.stageAt(world, planter, plant) == SpiritGardenWorld.NONE) {
                // Its plant was broken or cleared away. The planter keeps its seed and he sets a new sprout.
                garden.lost(slot, planter.id()); planter = garden.planters().get(slot);
            }
            boolean fits = switch (wanted) {
                case HARVEST -> planter.ripe() && hasRoom(production, entry, plant);
                case PLANT -> planter.stage() == SpiritGardenState.WAITING && slot < allowed && SpiritGardenWorld.cellFree(world, planter.cell());
                case RITE -> garden.rested(slot, clock, rest);
                case DELIVER -> false;
            };
            if (!fits) continue;
            v.action = wanted; v.slot = slot; v.planter = planter.id(); v.target = planter.cell();
            return true;
        }
        return false;
    }

    private static boolean valid(World world, TownRecord town, PlotInstance plot, PlotProductionState production, Visit v) {
        var garden = production.garden();
        if (v.action == Action.DELIVER) return garden.carrying() && ProductionStorageLocator.valid(world, plot, v.target);
        var planter = garden.planters().get(v.slot);
        if (garden.carrying() || planter == null || !planter.id().equals(v.planter) || !SpiritGardenWorld.planterStands(world, planter.block())
            || CoreProductionRequirements.issueForKind(town, SpiritGarden.KIND, planter.plant()) != null) return false;
        return switch (v.action) {
            case PLANT -> planter.stage() == SpiritGardenState.WAITING;
            case RITE -> planter.growing();
            case HARVEST -> planter.ripe();
            case DELIVER -> false;
        };
    }

    /** A harvest is worth gathering while the stores can take the plant or its petals. */
    public static boolean hasRoom(PlotProductionState production, ProductionCatalog.Entry entry, SpiritGardenRules.Plant plant) {
        return production.getAmount(plant.plant()) < GardenUpgrades.capacity(production, entry, plant.plant())
            || production.getAmount(plant.petal()) < GardenUpgrades.capacity(production, entry, plant.petal());
    }
    /** Standing at the chosen spot, or for a delivery simply close enough to reach the stores. */
    private static boolean reached(Vector3d position, Visit v) {
        return CourierWorkService.arrived(position, v.approach) || v.action == Action.DELIVER && beside(position, v.target);
    }
    private static boolean beside(Vector3d position, Point target) {
        if (target == null) return false;
        double dx = target.x() + .5 - position.x, dz = target.z() + .5 - position.z;
        return dx * dx + dz * dz <= 2.5 * 2.5 && Math.abs(target.y() - position.y) <= 1.5;
    }

    public static void interrupt(Ref<EntityStore> ref, Store<EntityStore> store, NPCEntity npc, CommandBuffer<EntityStore> buffer) {
        var visit = VISITS.get(ref); if (visit != null) { stop(ref, store, npc, buffer, visit); visit.lastTick = 0; }
    }
    private static void stop(Ref<EntityStore> ref, Store<EntityStore> store, NPCEntity npc, CommandBuffer<EntityStore> buffer, Visit v) {
        if (v.active) NpcStandStill.release(ref, npc, buffer);
        if (v.props) buffer.run(st -> { if (ref.isValid()) { VillagerLifeProps.clear(ref, st); AnimationUtils.stopAnimation(ref, AnimationSlot.Action, st); } });
        v.active = false; v.props = false; v.approach = null; v.attendance = 0;
    }
}
