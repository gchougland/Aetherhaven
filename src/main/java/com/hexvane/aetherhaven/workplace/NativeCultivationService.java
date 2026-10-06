package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

/** Native plants own growth. Apprentices only sow and harvest already simulated cells. */
public final class NativeCultivationService {
    private NativeCultivationService() {}
    private record Equipment(WorkplaceArea area,long until,WorkplaceEquipment.Snapshot snapshot) {}
    private static final java.util.Map<World,java.util.Map<java.util.UUID,Equipment>> EQUIPMENT=
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static WorkplaceEquipment.Snapshot equipment(World world,ApprenticeWorkplace site) {
        java.util.Map<java.util.UUID,Equipment> cache;
        synchronized(EQUIPMENT){cache=EQUIPMENT.computeIfAbsent(world,w->new java.util.HashMap<>());}
        long now=System.currentTimeMillis();var entry=cache.get(site.id());
        if(entry!=null&&entry.until()>now&&entry.area().equals(site.area()))return entry.snapshot();
        cache.values().removeIf(v->v.until()<now-60_000);
        var snapshot=WorkplaceEquipment.scan(world,site.area());
        cache.put(site.id(),new Equipment(site.area(),now+500,snapshot));return snapshot;
    }
    private static boolean loaded(World world,WarehouseBatch.Point point) {
        return point!=null&&ChunkSectionBlockUtil.worldChunkIfTicking(world,
            ChunkUtil.indexChunkFromBlock(point.x(),point.z()))!=null;
    }
    private static BlockType block(World world,WarehouseBatch.Point p) {
        return ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());
    }
    private static boolean empty(World world,int x,int y,int z) {
        var section=ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);
        var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
        return section!=null&&section.getFiller(x,y,z)==0&&block!=null&&BlockType.EMPTY.getId().equals(block.getId());
    }
    private static boolean soil(World world,WarehouseBatch.Point p) {
        var below=ChunkSectionBlockUtil.blockType(world,p.x(),p.y()-1,p.z());
        return below!=null&&AlchemicalCrop.SOIL.equals(below.getId());
    }
    private static boolean canPlant(World world,WarehouseBatch.Point p) {
        return loaded(world,p)&&soil(world,p)&&empty(world,p.x(),p.y(),p.z())&&empty(world,p.x(),p.y()+1,p.z());
    }
    public static WarehouseBatch.Point findPlantPoint(World world,ApprenticeWorkplace site,TownWarehouse stock) {
        var a=site.area();
        for(int x=a.minX();x<a.maxX();x++)for(int z=a.minZ();z<a.maxZ();z++)
            for(int y=a.floorY();y<a.floorY()+WorkplaceArea.HEIGHT-1;y++) {
                var p=new WarehouseBatch.Point(x,y,z);
                if(!stock.destinationReserved(p)&&canPlant(world,p))return p;
            }
        return null;
    }
    public static boolean destinationReady(World world,ApprenticeWorkplace site,WorkplaceRecipe recipe,WarehouseBatch batch) {
        var p=batch.destination();var crop=AlchemicalCrop.fromBlock(recipe.output());
        if(crop==null||!loaded(world,p)||!soil(world,p)||recipe.equipmentIssue(site,equipment(world,site))!=null)return false;
        if(!batch.cropPlanted())return canPlant(world,p);
        var current=block(world,p);
        return current!=null&&crop.equals(AlchemicalCrop.fromBlock(current.getId()));
    }
    public static boolean mature(World world,WarehouseBatch batch) {
        if(!loaded(world,batch.destination())||!batch.cropPlanted()||batch.outputItems().size()!=1)return false;
        var current=block(world,batch.destination());
        return current!=null&&batch.outputItems().getFirst().getItemId().equals(current.getId());
    }
    public static boolean needsWork(World world,WarehouseBatch batch) {
        return batch.state()==WarehouseBatch.State.READY&&(!batch.cropPlanted()||mature(world,batch));
    }
    /** Missing plants release the task without producing an item or refunding a used seed. */
    public static boolean plantMissing(World world,WarehouseBatch batch) {
        if(!batch.cropPlanted()||!loaded(world,batch.destination()))return false;
        var current=block(world,batch.destination());
        if(current==null)return false;
        return !java.util.Objects.equals(AlchemicalCrop.fromBlock(current.getId()),
            AlchemicalCrop.fromBlock(batch.outputItems().getFirst().getItemId()));
    }
    /** Call outside entity iteration because block placement updates the chunk component store. */
    public static boolean plant(World world,TownWarehouse stock,WarehouseBatch batch) {
        var p=batch.destination();var crop=AlchemicalCrop.fromBlock(batch.outputItems().getFirst().getItemId());
        return crop!=null&&stock.plantCrop(batch.id(),()->canPlant(world,p)
            &&ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),crop.plantedBlock(),0));
    }
    public static boolean harvest(World world,TownWarehouse stock,WarehouseBatch batch,WorkplaceOrders orders,double seconds) {
        var p=batch.destination();
        return stock.finishCultivation(batch.id(),orders,seconds,()->mature(world,batch)
            &&ChunkSectionBlockUtil.setBlockEmpty(world,p.x(),p.y(),p.z(),0));
    }
}
