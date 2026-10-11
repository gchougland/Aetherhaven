package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Read only habitat checks. No chunk requests, item output, or warehouse dependency. */
public final class FoodSiteService {
    private FoodSiteService() {}
    private static final Set<String> LIVESTOCK = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> CROPS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static {
        for(String role:List.of("Cow", "Sheep", "Pig", "Chicken", "Goat")) {
            LIVESTOCK.add(role);LIVESTOCK.add("Tamed_"+role);
        }
    }
    /** Integrations can opt their own crops and animal roles into food sites. */
    public static void registerLivestockRole(String role) { if (role != null && !role.isBlank()) LIVESTOCK.add(role); }
    public static void registerCropBlock(String block) { if (block != null && !block.isBlank()) CROPS.add(block); }
    public static boolean isFoodCrop(String base, String current) {
        if (base == null || current == null) return false;
        if (AlchemicalCrop.fromBlock(base) != null || AlchemicalCrop.fromBlock(current) != null) return false;
        String state = current.toLowerCase(Locale.ROOT);
        if (state.contains("burnt") || state.contains("wilted") || state.contains("dead")) return false;
        return CROPS.contains(base) || CROPS.contains(current) || base.startsWith("Plant_Crop_")
            && !base.startsWith("Plant_Crop_Cotton_") && !base.endsWith("_Item");
    }
    public record Habitat(boolean simulated, String issue, List<WarehouseBatch.Point> targets) {
        public Habitat { targets = List.copyOf(targets); }
        public boolean ready() { return simulated && issue == null && !targets.isEmpty(); }
    }
    private record Cached(WorkplaceArea area, TownsfolkWorkType type, long until, Habitat habitat) {}
    private static final Map<World, Map<UUID, Cached>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    public static Habitat inspect(World world, ApprenticeWorkplace site) {
        var area = site.area();
        if (!WorkplaceFood.supports(site.type()) || area == null || !area.valid()) return new Habitat(false, "Not a food workplace.", List.of());
        // Read chunk simulation on every use so the short equipment cache cannot keep an unloaded job working.
        for (int x = ChunkUtil.chunkCoordinate(area.minX()); x <= ChunkUtil.chunkCoordinate(area.maxX() - 1); x++)
            for (int z = ChunkUtil.chunkCoordinate(area.minZ()); z <= ChunkUtil.chunkCoordinate(area.maxZ() - 1); z++)
                if (ChunkSectionBlockUtil.worldChunkIfTicking(world, ChunkUtil.indexChunk(x, z)) == null)
                    return new Habitat(false, "Visit the whole workplace to inspect it.", List.of());
        Map<UUID, Cached> cached;
        synchronized (CACHE) { cached = CACHE.computeIfAbsent(world, w -> new HashMap<>()); }
        long now = System.currentTimeMillis(); var old = cached.get(site.id());
        if (old != null && old.until > now && old.area.equals(area) && old.type == site.type()) return old.habitat;
        var result = scan(world, site);
        cached.values().removeIf(c -> c.until < now - 60_000);
        cached.put(site.id(), new Cached(area, site.type(), now + 1000, result));
        return result;
    }

    private static Habitat scan(World world, ApprenticeWorkplace site) {
        var area = site.area(); boolean bench = false, trough = false;
        var crops = new ArrayList<WarehouseBatch.Point>();
        WarehouseBatch.Point feedPoint = null;
        for (int x = area.minX(); x < area.maxX(); x++) for (int z = area.minZ(); z < area.maxZ(); z++)
            for (int y = area.floorY(); y < area.floorY() + WorkplaceArea.HEIGHT; y++) {
                var section = ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
                if (section == null || section.getFiller(x,y,z) != 0) continue;
                var block = ChunkSectionBlockUtil.blockType(world,x,y,z); if (block == null) continue;
                if (block.getBench() != null && "Farmingbench".equals(block.getBench().getId())) bench = true;
                if ("Bench_Trough".equals(block.getId())) { trough = true; feedPoint = new WarehouseBatch.Point(x,y,z); }
                String base = block.getDefaultStateKey();
                if (isFoodCrop(base == null ? block.getId() : base, block.getId())) crops.add(new WarehouseBatch.Point(x,y,z));
            }
        if (site.type() == TownsfolkWorkType.FARMING) {
            String issue = !bench ? "Needs a Farmer's Workbench inside the workplace." : crops.size() < 4 ? "Plant at least four crops inside this workplace." : null;
            return new Habitat(true, issue, crops);
        }
        if (!trough) return new Habitat(true, "Needs a feeding trough inside the workplace.", List.of());
        return new Habitat(true, null, List.of(feedPoint));
    }

    public static void advance(World world, AetherhavenPlugin plugin, long minutes) {
        if (minutes <= 0) return;
        var online = TownEconomyPresence.onlinePlayers(world.getEntityStore().getStore()); if (online.isEmpty()) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (var town : manager.allTowns()) {
            if (!TownEconomyPresence.hasMember(town,online)) continue;
            boolean changed = false;
            for (var site : town.getApprenticeWorkplaces().sites()) {
                if (!WorkplaceFood.supports(site.type())) continue;
                var habitat = inspect(world,site);
                if (!habitat.simulated()) continue;
                boolean offspringPresent=site.type()!=TownsfolkWorkType.RANCHING;
                site.food().advance(minutes,true,true,habitat.ready(),offspringPresent); changed = true;
            }
            if (changed) manager.updateTown(town);
        }
    }

    /** Mature food is used by the town. It never creates item drops or enters warehouse storage. */
    public static boolean readyToHarvest(World world, WarehouseBatch.Point crop) {
        var block=ChunkSectionBlockUtil.blockType(world,crop.x(),crop.y(),crop.z());
        if(block==null||block.getGathering()==null||block.getGathering().getHarvest()==null)return false;
        String base=block.getDefaultStateKey();
        return isFoodCrop(base==null?block.getId():base,block.getId()) && block.getFarming()!=null;
    }

    /** Follow native regrowth stages, or sow the same crop again when it normally disappears on harvest. */
    public static boolean harvestForTown(World world, WarehouseBatch.Point crop) {
        if(!readyToHarvest(world,crop))return false;
        var block=ChunkSectionBlockUtil.blockType(world,crop.x(),crop.y(),crop.z());
        return regrowCrop(world,crop,block.getDefaultStateKey()==null?block.getId():block.getDefaultStateKey());
    }

    /** Native crop transition only. Caller decides whether harvest feeds the town or becomes real cargo. */
    public static boolean regrowCrop(World world, WarehouseBatch.Point crop,String expectedBase) {
        int x=crop.x(),y=crop.y(),z=crop.z();
        if(ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunkFromBlock(x,z))==null)return false;
        var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
        if(block==null||block.getGathering()==null||block.getGathering().getHarvest()==null||block.getFarming()==null
            ||!expectedBase.equals(block.getDefaultStateKey()==null?block.getId():block.getDefaultStateKey()))return false;
        var config=block.getFarming();if(config.getStages()==null)return false;
        var sectionRef=world.getChunkStore().getChunkSectionReferenceAtBlock(x,y,z);
        var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,x,y,z);
        var section=ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);
        var components=ChunkSectionBlockUtil.blockComponentSectionAt(world,x,y,z);
        if(sectionRef==null||!sectionRef.isValid()||section==null||components==null)return false;
        var store=world.getChunkStore().getStore();
        var farming=ref!=null&&ref.isValid()?store.getComponent(ref,com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock.getComponentType()):null;
        String next=config.getStageSetAfterHarvest()!=null?config.getStageSetAfterHarvest():config.getStartingStageSet();
        var stages=config.getStages().get(next);
        var previous=config.getStages().get(farming!=null?farming.getCurrentStageSet():config.getStartingStageSet());
        if(stages==null||stages.length==0||previous==null||previous.length==0)return false;
        var last=previous[farming==null?previous.length-1:Math.min(previous.length-1,Math.max(0,(int)farming.getGrowthProgress()))];
        var now=world.getEntityStore().getStore().getResource(com.hypixel.hytale.server.core.modules.time.WorldTimeResource.getResourceType()).getGameTime();
        // The native growth system removes this component at maturity. Recreate it as native harvest does.
        if(ref==null||!ref.isValid()){
            var holder=com.hypixel.hytale.server.core.universe.world.storage.ChunkStore.REGISTRY.newHolder();
            holder.putComponent(com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo.getComponentType(),
                new com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo(ChunkUtil.indexBlock(x,y,z),sectionRef));
            farming=new com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock();
            farming.setLastTickGameTime(now);farming.setCurrentStageSet(next);
            holder.addComponent(com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock.getComponentType(),farming);
            ref=store.addEntity(holder,com.hypixel.hytale.component.AddReason.SPAWN);
        }else if(farming==null)farming=store.ensureAndGetComponent(ref,com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock.getComponentType());
        if(ref==null)return false;
        if(!stages[0].canApply(store,sectionRef,ref,ChunkUtil.localCoordinate(x),ChunkUtil.localCoordinate(y),ChunkUtil.localCoordinate(z)))return false;
        farming.setCurrentStageSet(next);farming.setGrowthProgress(0);farming.setExecutions(0);
        farming.setGeneration(farming.getGeneration()+1);farming.setLastTickGameTime(now);
        stages[0].apply(store,sectionRef,ref,ChunkUtil.localCoordinate(x),ChunkUtil.localCoordinate(y),ChunkUtil.localCoordinate(z),last);
        components.markBlockNeedsSaving(ChunkUtil.indexBlock(x,y,z));section.setTicking(x,y,z,true);section.scheduleTick(ChunkUtil.indexBlock(x,y,z),now);
        return true;
    }

    /** Refresh native cultivated soil after real attendance. Never converts arbitrary player blocks. */
    public static boolean waterSoil(World world, WarehouseBatch.Point crop) {
        int x=crop.x(),y=crop.y()-1,z=crop.z();
        if(ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunkFromBlock(x,z))==null)return false;
        var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,x,y,z);
        var section=ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);
        var components=ChunkSectionBlockUtil.blockComponentSectionAt(world,x,y,z);
        if(ref==null||!ref.isValid()||section==null||components==null)return false;
        var soil=world.getChunkStore().getStore().getComponent(ref,com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock.getComponentType());
        if(soil==null)return false;
        var now=world.getEntityStore().getStore().getResource(com.hypixel.hytale.server.core.modules.time.WorldTimeResource.getResourceType()).getGameTime();
        var until=now.plusSeconds(24*60*60);
        if(soil.getWateredUntil()!=null&&soil.getWateredUntil().isAfter(until))until=soil.getWateredUntil();
        soil.setWateredUntil(until);int index=ChunkUtil.indexBlock(x,y,z);
        components.markBlockNeedsSaving(index);section.setTicking(x,y,z,true);section.scheduleTick(index,until);
        var above=ChunkSectionBlockUtil.blockSectionAt(world,x,y+1,z);if(above!=null)above.setTicking(x,y+1,z,true);
        return true;
    }
}
