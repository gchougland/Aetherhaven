package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.math.util.ChunkUtil;
import java.util.*;

/** Saved town sites and shared supervisor allowances. Mutations run on the owning world thread. */
public final class TownWorkplaces {
    private List<ApprenticeWorkplace> sites = new ArrayList<>();
    private Set<String> unlockedTypes = new LinkedHashSet<>();
    private Map<String, Integer> extraSlots = new LinkedHashMap<>();

    /** Remove retired garden sites, retaining physical cargo until couriers return it. */
    public boolean retireCultivation(com.hexvane.aetherhaven.warehouse.TownWarehouse stock) {
        var retired = new HashSet<UUID>();
        for (var site : sites) if (site.type() == TownsfolkWorkType.CULTIVATION) retired.add(site.id());
        boolean changed = false;
        for (var batch : List.copyOf(stock.batches()))
            if (batch.cultivation() || retired.contains(batch.workplaceId())) changed |= stock.cancel(batch.id());
        changed |= sites.removeIf(site -> retired.contains(site.id()));
        changed |= unlockedTypes.remove(TownsfolkWorkType.CULTIVATION.id);
        changed |= extraSlots.remove("druid") != null;
        return changed;
    }

    public List<ApprenticeWorkplace> sites() { return Collections.unmodifiableList(sites); }
    public ApprenticeWorkplace find(UUID id) { return sites.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null); }
    public ApprenticeWorkplace atGroundOrBlock(int x, int y, int z) {
        // At a shared floor boundary, the clicked ground's top belongs to the upper site.
        for (var site : sites) if (site.area().contains(x + .5, y + 1.01, z + .5)) return site;
        for (var site : sites) if (site.area().contains(x + .5, y + .01, z + .5)) return site;
        return null;
    }
    public boolean unlocked(TownsfolkWorkType type) { return unlockedTypes.contains(type.id); }
    public int slots(String supervisor) { return WorkplaceRules.initialSlots(supervisor) + Math.clamp(extraSlots.getOrDefault(supervisor, 0), 0, 32); }
    public long used(String supervisor) {
        return sites.stream().filter(s -> s.type() != null && WorkplaceRules.supervisor(s.type()).equals(supervisor)).count();
    }
    public static boolean hasSupervisor(TownRecord town, String kind) {
        // Death/unloading does not revoke residency or an existing unlock.
        return town.getResidentNpcRecords().stream().anyMatch(r -> kind.equals(r.getKind()));
    }
    public String unlock(TownRecord town, TownsfolkWorkType type) {
        if(type!=null&&!type.availableForNewWork())return "The Druid grows alchemical plants at the conservatory. Apprentice gardens are no longer created.";
        if(type==null||!type.requiresWorkplace())return "This work does not need a workplace. Use the town journal's Work page.";
        if (!hasSupervisor(town, WorkplaceRules.supervisor(type))) return "This villager must first become a resident.";
        unlockedTypes.add(type.id);
        return null;
    }
    public int extraSlots(String supervisor) { return Math.clamp(extraSlots.getOrDefault(supervisor, 0), 0, 32); }
    public long nextSlotCost(String supervisor) { return 100L * (extraSlots(supervisor) + 1); }
    public int nextSlotLevel(String supervisor) { return Math.min(6, 2 + extraSlots(supervisor)); }
    public String buySlot(TownRecord town, UUID player, String supervisor, int expectedExtraSlots) {
        if("druid".equals(supervisor))return "The Druid tends the conservatory directly. No Apprentice garden slots are needed.";
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player) || !town.playerCanSpendTreasuryGold(player)) return "You need permission to manage buildings and spend treasury gold.";
        if (!hasSupervisor(town, supervisor)) return "This villager must first become a resident.";
        if (extraSlots(supervisor) != expectedExtraSlots) return "This upgrade has changed. Review its new cost.";
        if (extraSlots(supervisor) >= 32) return "All workplace slots are unlocked.";
        if (TownPopulation.levelFor(TownPopulation.count(town)) < nextSlotLevel(supervisor)) return "Your town needs a higher level for this upgrade.";
        long cost = nextSlotCost(supervisor);
        if (town.getTreasuryGoldCoinCount() < cost) return "Your treasury needs more gold.";
        town.setTreasuryGoldCoinCount(town.getTreasuryGoldCoinCount() - cost);
        extraSlots.put(supervisor, extraSlots(supervisor) + 1);
        return null;
    }

    public String validate(TownRecord town, UUID editing, TownsfolkWorkType type, WorkplaceArea area, String name) {
        if(type!=null&&!type.availableForNewWork())
            return "The Druid grows alchemical plants at the conservatory. Choose another workplace type.";
        if(type!=null&&!type.requiresWorkplace())return "This work does not need a workplace. Use the town journal's Work page.";
        if (type == null || area == null || !area.valid()) return "Select an area from 2 by 2 to 64 by 64 blocks.";
        if (name == null || name.strip().isEmpty() || name.strip().length() > 48 || name.chars().anyMatch(Character::isISOControl)) return "Choose a name with 1 to 48 characters.";
        if (editing != null && find(editing) == null) return "This workplace no longer exists.";
        if(editing!=null&&find(editing).existingExcavation()!=null&&find(editing).existingExcavation().held()&&(find(editing).type()!=type||!find(editing).area().equals(area)))return "Let the mining Apprentice deliver collected material before changing this workplace.";
        if (!unlocked(type)) return "Unlock this workplace through its supervising villager first.";
        if (!hasSupervisor(town, WorkplaceRules.supervisor(type))) return "This villager must first become a resident.";
        var claims = TownTerritoryClaims.toChunkIndexSet(town);
        for (int x = ChunkUtil.chunkCoordinate(area.minX()); x <= ChunkUtil.chunkCoordinate(area.maxX() - 1); x++)
            for (int z = ChunkUtil.chunkCoordinate(area.minZ()); z <= ChunkUtil.chunkCoordinate(area.maxZ() - 1); z++)
                if (!claims.contains(ChunkUtil.indexChunk(x, z))) return "The whole workplace must be inside town borders.";
        String supervisor = WorkplaceRules.supervisor(type);
        long count = 0;
        for (var site : sites) {
            if (site.id().equals(editing)) continue;
            if (site.area().overlaps(area)) return "This area overlaps another workplace on this floor.";
            if (site.type() != null && WorkplaceRules.supervisor(site.type()).equals(supervisor)) count++;
        }
        return count >= slots(supervisor) ? "This villager has no free workplace slots. Upgrade their building to add one." : null;
    }

    public String save(TownRecord town, UUID editing, TownsfolkWorkType type, WorkplaceArea area, String name) {
        String error = validate(town, editing, type, area, name);
        if (error != null) return error;
        if (editing == null) sites.add(new ApprenticeWorkplace(name.strip(), type, area));
        else find(editing).configure(name.strip(), type, area);
        return null;
    }

    /** Remove the designation, never the player's blocks or supplies still travelling home. */
    public String remove(TownRecord town, UUID player, UUID id) {
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player)) return "You need permission to manage buildings.";
        var site = find(id);
        if (site == null) return "This workplace no longer exists.";
        site.excavation().pause(site.excavation().revision(), true);
        site.building().pause(site.building().revision(), true);
        for (var order : List.copyOf(site.orders().list())) site.orders().change(site.orders().revision(), order.id(), "Remove");
        for (var batch : List.copyOf(town.getWarehouse().batches()))
            if (id.equals(batch.workplaceId())) town.getWarehouse().cancel(batch.id());
        if (site.excavation().held()) return "Work stopped. Let the mining Apprentice deliver collected material, then remove this workplace again.";
        if (town.getWarehouse().batches().stream().anyMatch(batch -> id.equals(batch.workplaceId())))
            return "Work stopped. Let couriers return supplies and collect finished furnace items, then remove this workplace again.";
        sites.remove(site);
        return null;
    }
}
