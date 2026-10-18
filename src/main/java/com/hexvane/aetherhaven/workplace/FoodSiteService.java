package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.production.FarmFieldRules;
import com.hexvane.aetherhaven.production.FarmFieldWorld;
import com.hexvane.aetherhaven.production.NativeFarmCrops;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** Read only habitat checks. No chunk requests, item output, or warehouse dependency. */
public final class FoodSiteService {
    private FoodSiteService() {}
    public static final int MIN_GROWING_SPACES = 4;
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
        if (current == null) return false;
        base = cropId(base); current = cropId(current);
        if (AlchemicalCrop.fromBlock(base) != null || AlchemicalCrop.fromBlock(current) != null) return false;
        String state = (base+" "+current).toLowerCase(Locale.ROOT);
        if (state.contains("burnt") || state.contains("wilted") || state.contains("dead")) return false;
        if (base.startsWith("Plant_Crop_Cotton_") || current.startsWith("Plant_Crop_Cotton_")) return false;
        return CROPS.contains(base) || CROPS.contains(current) || nativeFoodCrop(base) || nativeFoodCrop(current);
    }
    private static String cropId(String id) { return id==null?"":id.startsWith("*")?id.substring(1):id; }
    private static boolean nativeFoodCrop(String id) {
        return id.startsWith("Plant_Crop_") && !id.endsWith("_Item") && !id.contains("_Item_");
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
        return scan(site, (x,y,z) -> {
            var section = ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);
            if (section == null || section.getFiller(x,y,z) != 0) return null;
            var block = ChunkSectionBlockUtil.blockType(world,x,y,z);
            if (block == null) return null;
            var station=WorkplaceQuestSteps.standing(block);
            return new Cell(WorkplaceQuestSteps.isStation(TownsfolkWorkType.FARMING,null,station),
                WorkplaceQuestSteps.isStation(TownsfolkWorkType.RANCHING,null,station), isFoodCrop(block.getDefaultStateKey(),block.getId()),
                site.type()==TownsfolkWorkType.FARMING && plantable(world,new WarehouseBatch.Point(x,y,z)));
        });
    }

    record Cell(boolean bench, boolean trough, boolean crop, boolean plantable) {}
    @FunctionalInterface interface Blocks { Cell at(int x,int y,int z); }

    /** Shared by the loaded-world adapter and geometry regression tests. */
    static Habitat scan(ApprenticeWorkplace site, Blocks blocks) {
        var area = site.area(); boolean bench = false, trough = false;
        var crops = new ArrayList<WarehouseBatch.Point>();
        var emptyBeds = new ArrayList<WarehouseBatch.Point>();
        WarehouseBatch.Point feedPoint = null;
        for (int x = area.minX(); x < area.maxX(); x++) for (int z = area.minZ(); z < area.maxZ(); z++)
            for (int y = area.floorY(); y < area.floorY() + area.height(); y++) {
                var cell = blocks.at(x,y,z); if (cell == null) continue;
                if (cell.bench()) bench = true;
                if (cell.trough()) { trough = true; feedPoint = new WarehouseBatch.Point(x,y,z); }
                if (cell.crop()) crops.add(new WarehouseBatch.Point(x,y,z));
                else if (site.type() == TownsfolkWorkType.FARMING && cell.plantable())
                    emptyBeds.add(new WarehouseBatch.Point(x,y,z));
            }
        if (site.type() == TownsfolkWorkType.FARMING) {
            String issue = farmingIssue(bench, crops.size(), emptyBeds.size(), area);
            if (issue != null) issue += nearbyHeightIssue(area,blocks,!bench,crops.size()+emptyBeds.size()<MIN_GROWING_SPACES);
            // Establish a small patch before the first tending visit. Existing crops remain usable.
            return new Habitat(true, issue, crops.size() < MIN_GROWING_SPACES && !emptyBeds.isEmpty() ? emptyBeds : crops);
        }
        if (!trough) return new Habitat(true, "Needs a feeding trough inside the workplace.", List.of());
        return new Habitat(true, null, List.of(feedPoint));
    }

    /** Diagnose old selections made by clicking a plant or bench instead of the ground, without expanding ownership. */
    private static String nearbyHeightIssue(WorkplaceArea area,Blocks blocks,boolean needBench,boolean needSpace) {
        WarehouseBatch.Point bench=null;int crops=0,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        for(int x=area.minX();x<area.maxX();x++)for(int z=area.minZ();z<area.maxZ();z++)
            for(int y=Math.max(0,area.floorY()-3);y<Math.min(320,area.floorY()+area.height()+3);y++) {
                if(area.contains(x,y,z))continue;
                var cell=blocks.at(x,y,z);if(cell==null)continue;
                if(needBench&&cell.bench()&&bench==null)bench=new WarehouseBatch.Point(x,y,z);
                if(needSpace&&cell.crop()){crops++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            }
        if(bench==null&&crops==0)return "";
        String detail=bench==null?"":" Bench found at ("+bench.x()+", "+bench.y()+", "+bench.z()+") outside that height.";
        if(crops>0)detail+=" "+crops+" crops found outside that height at Y="+minY+(minY==maxY?"":" to "+maxY)+".";
        return detail+" Resize the workplace from the ground beneath them.";
    }

    public static String farmingIssue(boolean bench, int crops, int emptyBeds, WorkplaceArea area) {
        var missing = new ArrayList<String>();
        if (!bench) missing.add("Missing Farmer's Workbench inside the marked area.");
        int spaces = crops + emptyBeds;
        if (spaces < MIN_GROWING_SPACES) missing.add("Needs 4 growing spaces; found " + spaces + " (" + crops
            + " crops, " + emptyBeds + " clear soil spaces). Leave clear dirt, grass or tilled soil with room above for wheat.");
        if (missing.isEmpty()) return null;
        return String.join(" ", missing) + " Included height: Y="
            + area.floorY() + " to " + (area.floorY() + area.height() - 1) + ". No seeds or planted crops are required.";
    }

    /** Only empty, dry air above soil a native hoe can till. Never replace builds, decorations or other plants. */
    public static boolean plantable(World world, WarehouseBatch.Point at) {
        var soil = ChunkSectionBlockUtil.blockType(world, at.x(), at.y()-1, at.z());
        var rules = FarmFieldRules.get();
        String base = soil == null ? null : soil.getDefaultStateKey() == null ? soil.getId() : soil.getDefaultStateKey();
        return (rules.tilled(base) || rules.tillable(base))
            && ChunkSectionBlockUtil.filler(world, at.x(), at.y()-1, at.z()) == 0
            && FarmFieldWorld.free(world, at)
            && FarmFieldWorld.free(world, new WarehouseBatch.Point(at.x(), at.y()+1, at.z()));
    }

    /** Called after attendance, on the world thread. Seeds are part of this food-only workplace's care. */
    public static boolean plantWheat(World world, ApprenticeWorkplace site, WarehouseBatch.Point at) {
        if (site.type() != TownsfolkWorkType.FARMING || !site.area().contains(at.x(),at.y(),at.z()) || !plantable(world,at)) return false;
        boolean planted = FarmFieldWorld.till(world,at)
            && FarmFieldWorld.plant(world,at,NativeFarmCrops.byOutput("Plant_Crop_Wheat_Item"));
        if (planted) {
            synchronized (CACHE) { var sites = CACHE.get(world); if (sites != null) sites.remove(site.id()); }
        }
        return planted;
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
