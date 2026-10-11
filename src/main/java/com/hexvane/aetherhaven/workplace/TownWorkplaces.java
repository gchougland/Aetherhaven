package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.math.util.ChunkUtil;
import java.util.*;

/** Saved town sites and shared supervisor allowances. Mutations run on the owning world thread. */
public final class TownWorkplaces {
    public static final long TYPE_UNLOCK_COST = 100;
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
    public int slots(String supervisor) { return WorkplaceRules.initialSlots(supervisor) + Math.clamp(extraSlots.getOrDefault(supervisor, 0), 0, 35); }
    public long used(String supervisor) {
        return sites.stream().filter(s -> s.mineBaseId()==null && s.type() != null && WorkplaceRules.supervisor(s.type()).equals(supervisor)).count();
    }
    public static boolean hasSupervisor(TownRecord town, String kind) {
        // Death/unloading does not revoke residency or an existing unlock.
        return town.getResidentNpcRecords().stream().anyMatch(r -> kind.equals(r.getKind()));
    }
    private String unlockIssue(TownRecord town, TownsfolkWorkType type) {
        if (type == null || !type.requiresWorkplace()) return "This work does not need a workplace. Use the town journal's Work page.";
        if (!type.availableForNewWork()) return "The Druid grows alchemical plants at the conservatory. Apprentice gardens are no longer created.";
        if (!hasSupervisor(town, WorkplaceRules.supervisor(type))) return "This villager must first become a resident.";
        return null;
    }
    public String unlock(TownRecord town, TownsfolkWorkType type) {
        String error = unlockIssue(town, type);
        if (error != null) return error;
        unlockedTypes.add(type.id);
        return null;
    }
    public String buyUnlock(TownRecord town, UUID player, TownsfolkWorkType type, CombinedItemContainer inventory) {
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player)) return "You need permission to manage buildings.";
        if (type != null && unlocked(type)) return "This workplace is already unlocked.";
        String error = unlockIssue(town, type);
        if (error != null) return error;
        if (inventory == null || !GoldCoinPayment.trySpend(town, inventory, TYPE_UNLOCK_COST, town.playerCanSpendTreasuryGold(player))) return "You need 100 gold in your inventory or treasury.";
        unlockedTypes.add(type.id);
        return null;
    }
    public int extraSlots(String supervisor) { return Math.clamp(extraSlots.getOrDefault(supervisor, 0), 0, 35); }
    public long nextSlotCost(String supervisor) { return 100L * (extraSlots(supervisor) + 1); }
    public int nextSlotLevel(String supervisor) { return Math.min(6, 2 + extraSlots(supervisor)); }
    public String buySlot(TownRecord town, UUID player, String supervisor, int expectedExtraSlots, CombinedItemContainer inventory) {
        if("druid".equals(supervisor))return "The Druid tends the conservatory directly. No Apprentice garden slots are needed.";
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player)) return "You need permission to manage buildings.";
        if (!hasSupervisor(town, supervisor)) return "This villager must first become a resident.";
        if (extraSlots(supervisor) != expectedExtraSlots) return "This upgrade has changed. Review its new cost.";
        if (extraSlots(supervisor) >= 35) return "All workplace slots are unlocked.";
        if (TownPopulation.levelFor(TownPopulation.count(town)) < nextSlotLevel(supervisor)) return "Your town needs a higher level for this upgrade.";
        long cost = nextSlotCost(supervisor);
        if (inventory == null || !GoldCoinPayment.trySpend(town, inventory, cost, town.playerCanSpendTreasuryGold(player))) return "You need more gold in your inventory or treasury.";
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
        if(editing!=null&&find(editing).mineBaseId()!=null)return "Manage this tunnel from its base mineshaft planner.";
        if(editing!=null&&sites.stream().anyMatch(s->editing.equals(s.mineBaseId()))&&(type!=TownsfolkWorkType.MINING||!find(editing).area().equals(area)))return "Remove the planned tunnels before changing the base workplace area or type.";
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
            if(editing!=null&&editing.equals(site.mineBaseId()))continue;
            if (WorkplaceOverlap.workplace(area, site)) return "This area overlaps another workplace on this floor.";
            if (site.mineBaseId()==null && site.type() != null && WorkplaceRules.supervisor(site.type()).equals(supervisor)) count++;
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
        var children=sites.stream().filter(s->id.equals(s.mineBaseId())).toList();
        for(var child:children)child.excavation().pause(child.excavation().revision(),true);
        if(children.stream().anyMatch(s->s.excavation().held()))return "Tunnels paused. Let miners finish delivery and empty the mine output storage before removing the base.";
        if(site.mineBaseId()!=null&&sites.stream().anyMatch(s->id.equals(s.minePrerequisiteId())))return "Remove the branches connected to this tunnel first.";
        site.excavation().pause(site.excavation().revision(), true);
        site.building().pause(site.building().revision(), true);
        for (var order : List.copyOf(site.orders().list())) site.orders().change(site.orders().revision(), order.id(), "Remove");
        for (var batch : List.copyOf(town.getWarehouse().batches()))
            if (id.equals(batch.workplaceId())) town.getWarehouse().cancel(batch.id());
        if (site.excavation().heldCargo()) return "Tunnel paused. A miner is carrying its output. Let them deliver it, then remove the tunnel again.";
        var outputBase=site.mineBaseId()==null?null:find(site.mineBaseId());
        if (site.excavation().outputCount()>0 && outputBase==null) return "Collect the stored mining output before removing this workplace.";
        if (town.getWarehouse().batches().stream().anyMatch(batch -> id.equals(batch.workplaceId())))
            return "Work stopped. Let couriers return supplies and collect finished furnace items, then remove this workplace again.";
        if(outputBase!=null)site.excavation().moveOutputTo(outputBase.excavation());
        sites.removeAll(children);sites.remove(site);
        return null;
    }

    public ApprenticeWorkplace mineBase(ApprenticeWorkplace site){return site==null?null:site.mineBaseId()==null?site:find(site.mineBaseId());}
    public List<ApprenticeWorkplace> tunnels(UUID base){return sites.stream().filter(s->s.id().equals(base)&&s.existingExcavation()!=null&&s.existingExcavation().id()!=null||base.equals(s.mineBaseId())).toList();}
    public String addTunnel(TownRecord town,UUID player,UUID baseId,ApprenticeWorkplace tunnel,UUID prerequisite){
        var base=find(baseId);
        if(!town.hasMemberOrOwner(player)||!town.playerCanManageConstructions(player)||!town.playerCanBreakBlocks(player))return "You need permission to plan and dig tunnels.";
        if(base==null||base.type()!=TownsfolkWorkType.MINING||base.mineBaseId()!=null||!unlocked(TownsfolkWorkType.MINING))return "Choose an unlocked base Mining workplace.";
        if(tunnels(baseId).size()>=75)return "This mine already has 75 planned tunnels.";
        if(tunnel==null||tunnel.type()!=TownsfolkWorkType.MINING||!tunnel.area().valid()||tunnel.existingExcavation()==null||tunnel.excavation().id()==null||find(tunnel.id())!=null)return "Review the new tunnel before saving.";
        if(prerequisite!=null&&tunnels(baseId).stream().noneMatch(s->s.id().equals(prerequisite)))return "The connecting tunnel changed. Review again.";
        var ground=MineshaftSelection.ground(tunnel.area(),tunnel.mineshaftEntrance(),tunnel.mineshaftLayout());
        var connection=MineshaftNetwork.mapConnections(this,base).get(new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(ground.x(),ground.y()+1,ground.z()));
        if(connection==null)return "Start the tunnel on the base or an existing passage at the same height.";
        var expected=connection.equals(baseId)&&(base.existingExcavation()==null||base.excavation().id()==null)?null:connection;
        if(!Objects.equals(expected,prerequisite))return "The connecting tunnel changed. Review again.";
        var area=tunnel.area();var claims=TownTerritoryClaims.toChunkIndexSet(town);
        for(int x=ChunkUtil.chunkCoordinate(area.minX());x<=ChunkUtil.chunkCoordinate(area.maxX()-1);x++)for(int z=ChunkUtil.chunkCoordinate(area.minZ());z<=ChunkUtil.chunkCoordinate(area.maxZ()-1);z++)
            if(!claims.contains(ChunkUtil.indexChunk(x,z)))return "Keep the tunnel inside town borders.";
        String overlap=tunnelOverlapIssue(baseId,tunnel);if(overlap!=null)return overlap;
        tunnel.attachTunnel(baseId,prerequisite);sites.add(tunnel);return null;
    }
    String tunnelOverlapIssue(UUID baseId,ApprenticeWorkplace tunnel){
        for(var other:sites)if(!other.id().equals(baseId)&&!baseId.equals(other.mineBaseId())&&WorkplaceOverlap.tunnel(tunnel,other))
            return "This tunnel crosses the passage or floor of another workplace: "+other.name()+".";
        return null;
    }
}
