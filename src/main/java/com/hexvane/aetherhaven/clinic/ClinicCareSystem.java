package com.hexvane.aetherhaven.clinic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import java.lang.ref.WeakReference;

/** Loaded, attended recovery. Never spawns residents, revives them, or loads chunks. */
public final class ClinicCareSystem extends EntityTickingSystem<EntityStore> {
    public static final String KIND = "doctor", CONSTRUCTION = "plot_doctor";
    private static final Map<World, Map<UUID, Visit>> VISITS = Collections.synchronizedMap(new WeakHashMap<>());
    private final AetherhavenPlugin plugin;
    private static final class Visit {
        WeakReference<Ref<EntityStore>> patient = new WeakReference<>(null);
        double elapsed, poll;
        float lastHealth;
        long lastTick;
        Ref<EntityStore> patient() { return patient.get(); }
        void patient(Ref<EntityStore> ref) { patient = new WeakReference<>(ref); }
    }
    public ClinicCareSystem(AetherhavenPlugin plugin) { this.plugin = plugin; }
    @Override public Query<EntityStore> getQuery() {
        return Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(), UUIDComponent.getComponentType());
    }
    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (!KIND.equals(binding.getKind())) return;
        var ref = chunk.getReferenceTo(index);
        var id = chunk.getComponent(index, UUIDComponent.getComponentType()).getUuid();
        // Deferred so one transaction sees current health and current town supplies.
        buffer.run(s -> { if (ref.isValid()) update(ref, id, binding.getTownId(), Math.clamp(dt, 0, .25f), s); });
    }
    private void update(Ref<EntityStore> doctor, UUID id, UUID townId, double dt, Store<EntityStore> store) {
        var world = store.getExternalData().getWorld();
        Map<UUID, Visit> visits;
        synchronized (VISITS) { visits = VISITS.computeIfAbsent(world, w -> new HashMap<>()); }
        var visit = visits.computeIfAbsent(id, ignored -> new Visit());
        long now = System.nanoTime();
        // A course cannot bridge an unloaded Doctor or a suspended simulation.
        if (visit.lastTick != 0 && now - visit.lastTick > 2_000_000_000L) {
            visit.patient(null); visit.elapsed = visit.poll = 0;
        }
        visit.lastTick = now;
        visit.poll += dt;
        if (visit.poll < .5) return;
        double step = visit.poll; visit.poll = 0;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var town = manager.getTown(townId);
        var clinic = town == null ? null : town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), CONSTRUCTION);
        if (availability(town, clinic, doctor, store) != Availability.READY) {
            visit.patient(null); visit.elapsed = 0; return;
        }
        var patients = patients(town, clinic, doctor, store);
        if (!patients.contains(visit.patient())) {
            visit.patient(patients.isEmpty() ? null : patients.getFirst()); visit.elapsed = 0;
            visit.lastHealth = visit.patient() == null ? 0 : health(visit.patient(), store).get(DefaultEntityStatTypes.getHealth()).get();
        }
        var patient = visit.patient();
        if (patient == null) return;
        var stats = health(patient, store);
        int key = DefaultEntityStatTypes.getHealth(); var health = stats.get(key);
        // Even damage that did not put an NPC into Combat cancels the current course.
        if (health.get() < visit.lastHealth) visit.elapsed = 0;
        else visit.elapsed += step;
        visit.lastHealth = health.get();
        if (visit.elapsed < TownClinic.TREATMENT_SECONDS) return;
        float heal = town.getClinic().treat(health.get(), health.getMax());
        if (heal > 0) {
            stats.setStatValue(key, Math.min(health.getMax(), health.get() + heal));
            store.putComponent(patient, EntityStatMap.getComponentType(), stats);
            manager.updateTown(town);
        }
        visit.patient(null); visit.elapsed = 0;
    }
    public enum Availability {
        NO_CLINIC("CLINIC NEEDED", "Complete the Clinic to begin care."),
        PAUSED("CARE PAUSED", "Resume care when you are ready."),
        EMPTY("NEEDS MEDICINE", "Supply Lesser Health Potions to begin care."),
        AWAY("DOCTOR AWAY", "Elara will resume care when she returns to the clinic."),
        BUSY("DOCTOR BUSY", "Care resumes when Elara is free. End Follow before treatment."),
        BREAK("TAKING A BREAK", "Elara needs food or rest before treating patients."),
        OFFLINE("CARE ON HOLD", "A town member must be online for treatment."),
        READY("READY TO TREAT", "Injured civilians seek care after danger passes. Guards can follow you here.");
        public final String title, detail;
        Availability(String title, String detail) { this.title = title; this.detail = detail; }
    }
    /** Shared by care and its screen so the UI cannot promise treatment while the Doctor is away. */
    public static Availability availability(TownRecord town, PlotInstance clinic, Ref<EntityStore> doctor, Store<EntityStore> store) {
        if (town == null || clinic == null) return Availability.NO_CLINIC;
        if (town.getClinic().paused()) return Availability.PAUSED;
        if (town.getClinic().medicine() == 0) return Availability.EMPTY;
        if (doctor == null || !doctor.isValid() || !inside(doctor, clinic, store)) return Availability.AWAY;
        var doctorId=store.getComponent(doctor,UUIDComponent.getComponentType());
        if(doctorId==null||!TownAdmission.isMember(town,doctorId.getUuid()))return Availability.AWAY;
        if(com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem.shouldSkipAutonomy(
                store.getComponent(doctor,com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerState.getComponentType()))
            ||com.hexvane.aetherhaven.autonomy.VillagerLifeSystem.ownsActivity(doctor,store)
            ||store.getComponent(doctor,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)
            return Availability.BUSY;
        if (!safe(doctor, store)) return Availability.BUSY;
        var needs = store.getComponent(doctor, com.hexvane.aetherhaven.villager.VillagerNeeds.getComponentType());
        if (needs != null && (needs.getHunger() < 20 || needs.getEnergy() < 20)) return Availability.BREAK;
        if (Universe.get().getPlayers().stream().noneMatch(p -> town.hasMemberOrOwner(p.getUuid()))) return Availability.OFFLINE;
        return Availability.READY;
    }
    /** Read on the owning world thread; the returned progress never advances the simulation. */
    public static Ref<EntityStore> currentPatient(Ref<EntityStore> doctor, Store<EntityStore> store) {
        var id=store.getComponent(doctor,UUIDComponent.getComponentType());
        Map<UUID,Visit> visits;
        synchronized(VISITS) { visits=VISITS.get(store.getExternalData().getWorld()); }
        var visit=visits==null||id==null?null:visits.get(id.getUuid());
        var patient=visit==null?null:visit.patient();
        return patient!=null&&patient.isValid()&&System.nanoTime()-visit.lastTick<2_000_000_000L?patient:null;
    }

    /** Read on the owning world thread; the returned progress never advances the simulation. */
    public static double progress(Ref<EntityStore> doctor, Ref<EntityStore> patient, Store<EntityStore> store) {
        var id = store.getComponent(doctor, UUIDComponent.getComponentType());
        Map<UUID, Visit> visits;
        synchronized (VISITS) { visits = VISITS.get(store.getExternalData().getWorld()); }
        var visit = visits == null || id == null ? null : visits.get(id.getUuid());
        return visit != null && patient.equals(visit.patient()) && System.nanoTime() - visit.lastTick < 2_000_000_000L
            ? Math.clamp(visit.elapsed / TownClinic.TREATMENT_SECONDS, 0, 1) : 0;
    }
    public static List<Ref<EntityStore>> patients(TownRecord town, PlotInstance clinic, Ref<EntityStore> doctor, Store<EntityStore> store) {
        var found = new ArrayList<Ref<EntityStore>>();
        var position = store.getComponent(doctor, TransformComponent.getComponentType());
        if (position == null) return found;
        store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(),
                EntityStatMap.getComponentType(), UUIDComponent.getComponentType(), TransformComponent.getComponentType()), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var ref = chunk.getReferenceTo(i);
                var binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                var uuid = chunk.getComponent(i, UUIDComponent.getComponentType()).getUuid();
                var hp = chunk.getComponent(i, EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
                if (!ref.equals(doctor) && town.getTownId().equals(binding.getTownId()) && TownAdmission.isMember(town, uuid)
                        && !com.hexvane.aetherhaven.hunter.HuntService.owns(ref,store)
                        && safe(ref, store) && inside(ref, clinic, store) && hp != null && hp.getMax() - hp.get() >= 1
                        && position.getPosition().distanceSquared(chunk.getComponent(i, TransformComponent.getComponentType()).getPosition()) <= 64)
                    found.add(ref);
            }
        });
        found.sort(Comparator.comparingDouble(ref -> {
            var h = health(ref, store).get(DefaultEntityStatTypes.getHealth()); return h.get() / Math.max(1, h.getMax());
        }));
        return found;
    }
    private static EntityStatMap health(Ref<EntityStore> ref, Store<EntityStore> store) { return store.getComponent(ref, EntityStatMap.getComponentType()); }
    private static boolean safe(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store.getComponent(ref, DeathComponent.getComponentType()) != null) return false;
        var state = NpcSupportUtil.stateName(store, ref);
        var map = health(ref, store); var hp = map == null ? null : map.get(DefaultEntityStatTypes.getHealth());
        return hp != null && hp.get() > 0 && (state == null || !state.contains("Combat") && !state.contains("Interaction"));
    }
    private static boolean inside(Ref<EntityStore> ref, PlotInstance clinic, Store<EntityStore> store) {
        var p = store.getComponent(ref, TransformComponent.getComponentType());
        return p != null && clinic.containsWorldBlock((int)Math.floor(p.getPosition().x), (int)Math.floor(p.getPosition().y), (int)Math.floor(p.getPosition().z));
    }
}
