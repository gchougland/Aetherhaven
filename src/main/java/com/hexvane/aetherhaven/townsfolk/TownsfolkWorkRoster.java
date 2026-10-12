package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

public final class TownsfolkWorkRoster {
    private TownsfolkWorkRoster() {}
    public record Worker(String characterId, String name, UUID entityId, boolean guard, boolean alive, TownsfolkProgress progress) {}

    /** Visitors and guards do not gain civilian needs. Existing needs must never be reset. */
    public static boolean initializeResidentNeeds(TownRecord town, UUID resident, String kind, boolean hasNeeds, boolean dead) {
        return !hasNeeds && !dead && town != null && resident != null
            && TownVillagerBinding.KIND_TOWNSFOLK.equals(kind) && TownAdmission.isMember(town,resident);
    }

    /**
     * A townsfolk has just been counted as a resident: they start with priorities for the work they are good at, so they
     * can be put to work without a visit to the priority grid. Safe to call again, it only ever acts once per resident.
     *
     * @return whether the town changed
     */
    public static boolean startResidentWork(TownRecord town, TownManager manager, AetherhavenPlugin plugin, String characterId) {
        if (town == null || manager == null || plugin == null || characterId == null || characterId.isBlank()) return false;
        if (!TownsfolkProgressService.getOrCreate(town, manager, plugin, characterId).applyStartingPriorities()) return false;
        manager.updateTown(town);
        return true;
    }

    /**
     * Residents of a save made before starting priorities existed get them once, when the town is loaded. Only residents
     * who already have a record are touched here; one with no record yet gets theirs the first time they are listed.
     *
     * @return whether the town changed
     */
    public static boolean applyStartingPriorities(TownRecord town) {
        boolean changed = false;
        for (var row : town.getResidentNpcRecords()) {
            var identity = row.getTownsfolkIdentity();
            if (identity == null || identity.getCharacterId() == null || !TownAdmission.isMember(town, row.getLastEntityUuid())) continue;
            var progress = town.getTownsfolkProgress().get(identity.getCharacterId().trim().toLowerCase(Locale.ROOT));
            if (progress != null) changed |= progress.applyStartingPriorities();
        }
        return changed;
    }

    /** Snapshot identity while living so missing residents can also be recovered through Gaia. */
    public static List<Worker> list(TownRecord town, TownManager manager, Store<EntityStore> store, AetherhavenPlugin plugin) {
        boolean[] changed = {false};
        store.forEachChunk(Query.and(TownsfolkCharacterBinding.getComponentType(), TownVillagerBinding.getComponentType(),
            UUIDComponent.getComponentType(), NPCEntity.getComponentType()), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                var uuid = chunk.getComponent(i, UUIDComponent.getComponentType()).getUuid();
                if (!town.getTownId().equals(binding.getTownId()) || !TownAdmission.isMember(town, uuid)) continue;
                var identity = chunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                var row = town.getResidentNpcRecords().stream().filter(r -> uuid.equals(r.getLastEntityUuid())).findFirst().orElse(null);
                if (row == null) {
                    row = new ResidentNpcRecord(chunk.getComponent(i, NPCEntity.getComponentType()).getRoleName(), binding.getKind(), binding.getJobPlotId(), uuid);
                    town.getResidentNpcRecords().add(row);
                    changed[0] = true;
                }
                if (row.getTownsfolkIdentity() == null) { row.setTownsfolkIdentity(identity); changed[0] = true; }
                for (var hired : town.getHiredGuardRecords()) if (uuid.equals(hired.getEntityUuid())
                        && !Objects.equals(row.getGuardEquipmentProfile(), hired.getEquipmentProfileId())) {
                    row.setGuardEquipmentProfile(hired.getEquipmentProfileId()); changed[0] = true;
                }
            }
        });
        if (changed[0]) manager.updateTown(town);
        Map<String, Worker> roster = new TreeMap<>();
        boolean started = false;
        for (var row : town.getResidentNpcRecords()) {
            var identity = row.getTownsfolkIdentity();
            if (identity == null || !TownAdmission.isMember(town, row.getLastEntityUuid())) continue;
            String id = identity.getCharacterId();
            var definition = plugin.getTownsfolkCharacterCatalog().byId(id);
            String name = definition != null && definition.getDisplayName() != null ? definition.getDisplayName() : id;
            var progress = TownsfolkProgressService.getOrCreate(town, manager, plugin, id);
            // Every resident the journal lists has starting priorities by now, whichever way they joined the town.
            started |= progress.applyStartingPriorities();
            roster.put(id, new Worker(id, name, row.getLastEntityUuid(), TownVillagerBinding.KIND_GUARD.equals(row.getKind()), !row.isPendingDawnRevival(), progress));
        }
        if (started) manager.updateTown(town);
        return new ArrayList<>(roster.values());
    }
}
