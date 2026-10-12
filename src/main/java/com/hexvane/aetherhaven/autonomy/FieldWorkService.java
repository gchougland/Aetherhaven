package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.production.FarmFieldWork.Action;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.ProductionStorageLocator;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3d;

/**
 * The Farmer's rounds of the Scarecrow Fields: she follows the crop plan, tilling and sowing empty cells, watering,
 * gathering what is ripe into her basket, working compost into tired soil and carrying the basket home. Every change
 * to a field needs her standing at it. Between rounds she lives her ordinary day.
 */
public final class FieldWorkService {
    private FieldWorkService() {}
    /** Fields may lie anywhere in town, so she is given a full minute to walk to a cell before trying another way. */
    private static final long TRAVEL_LIMIT_MS = 60_000;
    private static final Map<Ref<EntityStore>, Visit> VISITS = Collections.synchronizedMap(new WeakHashMap<>());
    /** What each farm's Farmer is doing right now, for the field screen. */
    private static final Map<UUID, Action> DOING = new ConcurrentHashMap<>();
    private static final class Visit {
        UUID plot;
        int slot, attempt;
        Action action;
        Point target;
        String crop;
        Vector3d approach;
        long began, retry, lastTick, nextVisual;
        double attendance;
        boolean active, props;
        final Map<Point, Long> failed = new HashMap<>();
    }

    public static boolean active(Ref<EntityStore> ref) { var v = VISITS.get(ref); return v != null && v.active; }
    /** What this farm's Farmer is busy with, or null while she is between jobs. */
    public static Action doing(UUID plot) { return plot == null ? null : DOING.get(plot); }

    public static boolean tick(float dt, Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
            NPCEntity npc, TownVillagerBinding binding, VillagerNeeds needs, VillagerAutonomyState autonomy,
            TownRecord town, TownManager manager, AetherhavenPlugin plugin) {
        var plot = town == null || binding.getJobPlotId() == null ? null : town.findPlotById(binding.getJobPlotId());
        if (!TownVillagerBinding.KIND_FARMER.equals(binding.getKind()) || plot == null || plot.getState() != PlotInstanceState.COMPLETE
            || !plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(), FarmFields.CONSTRUCTION)) {
            interrupt(ref, store, npc, buffer); return false;
        }
        var production = FarmFields.production(town, plot);
        var farm = production.farm();
        // A farm with no crop plan still runs on automatic production, and its Farmer keeps her ordinary day.
        if (!production.usesFarmFields() || store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            interrupt(ref, store, npc, buffer); return false;
        }
        boolean offDuty = needs.getHunger() < 20 || needs.getEnergy() < 20 || autonomy.isFillingHunger() || autonomy.isFillingEnergy()
            || !ProductionLiveWorkDetection.isOnWorkSchedule(store, ref, npc, plugin.getVillagerDefinitionCatalog(), plugin.getVillagerScheduleRegistry())
            || !com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(store, town);
        // A harvest already in her basket is always carried home, even after her shift has ended.
        if (offDuty && !farm.carrying()) { interrupt(ref, store, npc, buffer); return false; }
        var transform = store.getComponent(ref, TransformComponent.getComponentType());
        var entry = ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(), plugin.getWorkplaceUnlockCatalog(), FarmFields.CONSTRUCTION, production);
        if (transform == null || entry == null) return false;
        var world = store.getExternalData().getWorld(); var position = transform.getPosition();
        var visit = VISITS.computeIfAbsent(ref, ignored -> new Visit()); long now = System.currentTimeMillis();
        double step = visit.lastTick == 0 || now - visit.lastTick > 2000 ? 0 : Math.min(1, Math.max(0, dt)); visit.lastTick = now;
        if (visit.plot != null && !visit.plot.equals(plot.getPlotId())) { stop(ref, store, npc, buffer, visit); visit.failed.clear(); }
        visit.plot = plot.getPlotId();
        var rules = FarmFieldRules.get();
        if (!visit.active) {
            // Nothing to do right now, such as crops still growing. She goes back to her own day until the next look.
            if (now < visit.retry) return false;
            if (!choose(world, town, plot, production, entry, visit, position, !offDuty, now)) { visit.retry = now + 3000; return false; }
            visit.approach = CourierWorkService.approach(world, visit.target, position, visit.attempt);
            if (visit.approach == null) {
                visit.failed.put(visit.target, now + 15_000); visit.attempt++; visit.retry = now + 1000; return false;
            }
            var registry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref, store, buffer, autonomy, needs, registry, false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE); autonomy.clearTravelAndPoiState();
            buffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
            visit.active = true; visit.began = now; visit.attendance = 0; visit.nextVisual = 0;
            DOING.put(plot.getPlotId(), visit.action);
            // The basket on the way home, a seed bag on the way to an empty cell.
            String held = visit.action == Action.DELIVER ? farm.basket().keySet().iterator().next()
                : visit.action == Action.PLANT ? NativeFarmCrops.seedFor(visit.crop) : null;
            if (held != null) { visit.props = true; buffer.run(st -> { if (ref.isValid() && visit.active) VillagerLifeProps.carry(ref, held, st); }); }
            return true;
        }
        if (!valid(world, town, plot, production, visit) || !CourierWorkService.clearApproach(world, visit.approach)
            || !reached(position, visit) && now - visit.began > TRAVEL_LIMIT_MS) {
            visit.failed.put(visit.target, now + 15_000); visit.attempt++; visit.retry = now + 1000;
            stop(ref, store, npc, buffer, visit); return true;
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
        // Speed bought with ingots before the farm had fields still makes her hands quicker.
        visit.attendance += step * WorkplaceProductionUpgrades.speedMultiplier(production);
        final Action action = visit.action;
        if (action != Action.DELIVER && now >= visit.nextVisual) {
            boolean watering = action == Action.WATER; visit.props = true; visit.nextVisual = now + 2500;
            buffer.run(st -> { if (ref.isValid() && visit.active) {
                if (watering) VillagerLifeProps.equip(ref, "WaterCrops", st);
                else if (action != Action.PLANT) VillagerLifeProps.clear(ref, st);
                AnimationUtils.playAnimation(ref, AnimationSlot.Action,
                    watering ? "Aetherhaven_Work_Actions" : NpcFaceVisuals.itemAnimationsForFaceRig(ref, "Aetherhaven_Life_Actions", st),
                    watering ? "WaterCrops" : "Tend", true, st);
            }});
        }
        var work = rules.work();
        if (visit.attendance < switch (action) {
            // A harvest is two jobs at one cell: she cuts the crop, then sows the next one.
            case HARVEST -> work.harvestSeconds() + work.plantSeconds(); case PLANT -> work.plantSeconds(); case WATER -> work.waterSeconds();
            case COMPOST -> work.compostSeconds(); case DELIVER -> work.deliverSeconds();
        }) return true;
        // Every change to the world and the stores happens after the entity command buffer, with fresh checks.
        final int slot = visit.slot; final Point target = visit.target; final Vector3d standing = new Vector3d(visit.approach);
        buffer.run(st -> {
            if (!ref.isValid() || st.getComponent(ref, DeathComponent.getComponentType()) != null) return;
            var actual = st.getComponent(ref, TransformComponent.getComponentType());
            var worker = st.getComponent(ref, TownVillagerBinding.getComponentType());
            if (actual == null || worker == null || !plot.getPlotId().equals(worker.getJobPlotId()) || !town.getTownId().equals(worker.getTownId())
                || !TownVillagerBinding.KIND_FARMER.equals(worker.getKind()) || plot.getState() != PlotInstanceState.COMPLETE
                || town.findPlotById(plot.getPlotId()) != plot
                || !(CourierWorkService.arrived(actual.getPosition(), standing) || action == Action.DELIVER && beside(actual.getPosition(), target))) return;
            boolean changed = false;
            if (action == Action.DELIVER) {
                if (farm.carrying() && ProductionStorageLocator.valid(world, plot, target)) {
                    FarmFieldJobs.deliver(town, production, entry);
                    town.getWarehouse().markProductionWithdrawal(); changed = true;
                }
            } else {
                // Work in the fields needs her shift and someone from town nearby.
                if (!ProductionLiveWorkDetection.isOnWorkSchedule(st, ref, npc, plugin.getVillagerDefinitionCatalog(), plugin.getVillagerScheduleRegistry())
                    || !com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(st, town)) return;
                var favoured = FarmFields.favoured(town, entry, production, FarmAlmanac.week(FarmFieldWorld.dateTime(world)));
                switch (action) {
                    case HARVEST -> {
                        var gathered = FarmFieldJobs.harvest(world, town, plot, production, entry, slot, target.x(), target.z(), favoured,
                            () -> ThreadLocalRandom.current().nextDouble());
                        changed = gathered != null;
                        if (gathered != null) harvestSound(st, target, gathered.golden());
                        // Between ticks, so reading every player never meets a store that is busy.
                        if (gathered != null && gathered.golden()) world.execute(() -> tellTown(world.getEntityStore().getStore(), town));
                    }
                    case PLANT -> changed = FarmFieldJobs.plant(world, town, plot, production, entry, slot, target.x(), target.z(), favoured);
                    case WATER -> changed = FarmFieldJobs.water(world, town, production, target.x(), target.y(), target.z()) > 0;
                    case COMPOST -> changed = FarmFieldJobs.compost(world, town, production, slot, target.x(), target.z());
                    default -> {}
                }
            }
            if (changed) manager.updateTown(town);
        });
        visit.attempt = 0; visit.retry = now + 600; stop(ref, store, npc, buffer, visit); return true;
    }

    /** The game's own crop sounds: a plain rustle for a harvest, the ripening chime for a Golden Harvest. */
    private static void harvestSound(Store<EntityStore> store, Point cell, boolean golden) {
        int index = SoundEvent.getAssetMap().getIndex(golden ? "SFX_Crops_Grow_Stage_Complete" : "SFX_Crops_Grow");
        if (index < 0) return;
        try {
            SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, cell.x() + .5, cell.y() + .5, cell.z() + .5, golden ? 1.0F : 0.6F, golden ? 1.3F : 1.0F, store);
        } catch (RuntimeException ignored) {
            // A sound is never worth losing a harvest over.
        }
    }

    /** A Golden Harvest is rare enough to be worth a word to everyone from town who is in this world. */
    private static void tellTown(Store<EntityStore> store, TownRecord town) {
        var message = com.hypixel.hytale.server.core.Message.translation(FarmFields.LANG + "notify.golden");
        var players = com.hypixel.hytale.component.query.Query.and(com.hypixel.hytale.server.core.entity.entities.Player.getComponentType(),
            com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType(), com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
        store.forEachChunk(players, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> ignored) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var id = chunk.getComponent(i, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                var player = chunk.getComponent(i, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                if (id != null && player != null && town.hasMemberOrOwner(id.getUuid()))
                    com.hypixel.hytale.server.core.util.NotificationUtil.sendNotification(player.getPacketHandler(), message,
                        com.hypixel.hytale.protocol.packets.interface_.NotificationStyle.Success);
            }
        });
    }

    private static boolean choose(World world, TownRecord town, PlotInstance plot, PlotProductionState production,
            ProductionCatalog.Entry entry, Visit v, Vector3d position, boolean onDuty, long now) {
        v.failed.values().removeIf(until -> until <= now);
        var farm = production.farm(); var rules = FarmFieldRules.get();
        var job = FarmFieldWork.next(farm, (slot, cell) -> FarmFieldWorld.view(world, town, cell), new FarmFieldWork.Needs(onDuty, rules.work().basket(),
            crop -> FarmFields.cropIssue(town, entry, crop) == null,
            crop -> FarmFieldJobs.seedReady(town, plot, production, crop),
            crop -> FarmFieldJobs.harvestWanted(production, entry, crop), FarmUpgrades.compostEnriches(production),
            cell -> v.failed.containsKey(cell.point()),
            cell -> position.distanceSquared(cell.x() + .5, cell.y(), cell.z() + .5)));
        if (job == null) return false;
        v.action = job.action(); v.slot = job.slot(); v.crop = job.crop();
        v.target = job.action() == Action.DELIVER ? ProductionStorageLocator.find(world, plot, now) : job.cell().point();
        return v.target != null && !v.failed.containsKey(v.target);
    }

    private static boolean valid(World world, TownRecord town, PlotInstance plot, PlotProductionState production, Visit v) {
        var farm = production.farm();
        if (v.action == Action.DELIVER) return farm.carrying() && ProductionStorageLocator.valid(world, plot, v.target);
        var cell = farm.cell(v.slot, v.target.x(), v.target.z());
        if (cell == null || !cell.planned() || cell.y() != v.target.y()) return false;
        var view = FarmFieldWorld.view(world, town, cell);
        if (!view.workable()) return false;
        return switch (v.action) {
            case HARVEST -> view.ripe();
            case PLANT -> view.standing() == null && view.plantable() && Objects.equals(v.crop, cell.crop());
            case WATER -> view.standing() != null && cell.soil() != FarmFieldsState.WORN;
            case COMPOST -> farm.compost() > 0 && FarmFieldsState.takesCompost(cell.soil(), FarmUpgrades.compostEnriches(production));
            case DELIVER -> false;
        };
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
        if (v.plot != null) DOING.remove(v.plot);
        v.active = false; v.props = false; v.approach = null; v.attendance = 0;
    }
}
