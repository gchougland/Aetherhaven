package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.world.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.joml.Vector3d;

/** Rock faces at layout transitions; ordinary work must excavate and deliver every drop. */
record SpecialistExcavationPreview(UUID siteId, UUID mineId, String worker) {
    static SpecialistExcavationPreview create(World world,UUID owner,Vector3d at,ExcavationPlan.Layout layout,ExcavationFittings.Mode fittings,boolean enclosed) {
        return create(world,owner,at,layout,fittings,enclosed,true);
    }
    static SpecialistExcavationPreview create(World world,UUID owner,Vector3d at,ExcavationPlan.Layout layout,ExcavationFittings.Mode fittings,boolean enclosed,boolean startNow) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")&&!PersistentWorldSupport.shouldPersistWorldData(world),"Use an unsaved specialist preview.");
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);require(town!=null&&at!=null,"Artisan platform missing.");
        int x=(int)Math.floor(at.x),y=(int)Math.floor(at.y),z=(int)Math.floor(at.z);
        var area=new WorkplaceArea(x-6,y,z-8,x+7,z+5);
        for(int bx=area.minX()-1;bx<=area.maxX()+1;bx++)for(int bz=area.minZ();bz<area.maxZ();bz++)for(int by=y;by<y+5;by++)
            require(ChunkSectionBlockUtil.blockSectionAt(world,bx,by,bz)!=null&&ChunkSectionBlockUtil.blockId(world,bx,by,bz)==0,"Use a fresh empty Artisan platform.");
        var store=world.getEntityStore().getStore();
        var player=store.getExternalData().getRefFromUUID(owner);
        var position=player==null?null:store.getComponent(player,TransformComponent.getComponentType());
        require(position==null||!area.contains((int)Math.floor(position.getPosition().x),y,(int)Math.floor(position.getPosition().z)),"Visit another building before preparing this rock fixture.");
        var entrance=ExcavationPlan.Entrance.NORTH;var tunnel=ExcavationPlan.tunnel(area,entrance,layout);
        // Clear routes are explicit setup, with full supporting columns underneath raised floors.
        for(var point:tunnel)for(int by=y;by<ExcavationPlan.floorAt(area,entrance,layout,point);by++)block(world,point.x(),by,point.z(),"Rock_Stone");
        if(layout==ExcavationPlan.Layout.STAIRS_DOWN)for(int d=1;d<=2;d++)for(int w=-1;w<=1;w++)for(int by=0;by<d;by++)
            block(world,x+w,y+by,area.minZ()+d,"Rock_Stone");
        var faces=new LinkedHashSet<WarehouseBatch.Point>();
        for(var point:tunnel) {
            int d=point.z()-area.minZ(),w=point.x()-x;
            boolean face=layout==ExcavationPlan.Layout.BRANCHES?(w==0&&d==3||Math.abs(w)==4&&d==8)
                :w==0&&(d==3||d==7||d==11);
            if(face)faces.add(point);
        }
        for(var point:faces)block(world,point.x(),point.y(),point.z(),"Rock_Stone");
        if(enclosed) {
            var passage=new HashSet<>(tunnel);
            // A solid shell prevents walking around the reviewed faces. Leave only the entrance open.
            var shell=new HashSet<WarehouseBatch.Point>();
            for(var point:tunnel)for(var offset:List.of(new int[]{-1,0,0},new int[]{1,0,0},new int[]{0,0,-1},new int[]{0,0,1},new int[]{0,1,0})) {
                var neighbor=new WarehouseBatch.Point(point.x()+offset[0],point.y()+offset[1],point.z()+offset[2]);
                if(neighbor.z()>=area.minZ()+3&&!passage.contains(neighbor))shell.add(neighbor);
            }
            for(var point:shell)block(world,point.x(),point.y(),point.z(),"Rock_Stone");
        }
        block(world,area.minX()+1,y,area.minZ()+1,"Bench_Workbench");
        var storage=new WarehouseBatch.Point(area.maxX()+1,y,area.minZ()+1);
        block(world,storage.x(),storage.y(),storage.z(),AetherhavenConstants.BLOCK_PRODUCTION_STORAGE);
        var mine=new PlotInstance(UUID.randomUUID(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(storage.x(),y,storage.z(),storage.x(),y+2,storage.z()),storage.x(),y,storage.z(),0);
        town.getPlotInstances().add(mine);
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));
        var sites=town.getApprenticeWorkplaces();require(sites.unlock(town,TownsfolkWorkType.MINING)==null,"Mining unlock failed.");
        require(sites.save(town,null,TownsfolkWorkType.MINING,area,"Attended Mineshaft")==null,"Mining area failed.");
        var site=sites.sites().getLast();var review=NativeExcavation.review(world,town,site,entrance,layout);require(review.ready(),review.issue());
        require(review.cells().size()==faces.size(),"Unexpected reviewed rock count.");
        site.setMineshaftConfiguration(entrance,layout);
        if(startNow)require(site.excavation().start(0,owner,mine.getPlotId(),area,entrance,layout,review.cells(),fittings)==null,"Mining plan failed.");
        var materials=new LinkedHashMap<String,Integer>();
        for(var f:ExcavationFittings.plan(area,entrance,layout,fittings))materials.merge(f.item(),1,Integer::sum);
        for(var material:materials.entrySet())require(town.getWarehouse().acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(material.getKey(),material.getValue())),"Fitting material setup failed.");
        String worker=SpecialistWorkPreview.spawn(world,town,manager,new Vector3d(x+.5,y,area.minZ()+.5),TownsfolkWorkType.MINING,4010,0);
        TownsfolkWorkRoster.list(town,manager,store,plugin);
        world.getWorldConfig().setBlockTicking(true);world.getWorldConfig().setGameTimePaused(false);
        var time=store.getResource(WorldTimeResource.getResourceType());time.setGameTime(time.getGameTime().truncatedTo(ChronoUnit.DAYS).plus(8,ChronoUnit.HOURS),world,store);
        manager.updateTown(town);return new SpecialistExcavationPreview(site.id(),mine.getPlotId(),worker);
    }
    String status(World world,UUID owner) {
        var plugin=AetherhavenPlugin.get();var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(owner);
        var site=town.getApprenticeWorkplaces().find(siteId);var plan=site.excavation();
        return "Attended "+plan.layout().label+": "+plan.completed()+"/"+plan.total()+" blocks, cargo "+plan.cargo()+", delivered cobble "+town.getOrCreatePlotProduction(mineId).getAmount("Rock_Stone_Cobble")
            +", worker "+worker+" XP "+town.getTownsfolkProgress().get(worker).masteryXp()+". Fittings "+plan.fitted()+"/"+plan.fittingsTotal()
            +", beams "+town.getWarehouse().craftingStock().count(ExcavationFittings.BEAM)+", torches "+town.getWarehouse().craftingStock().count(ExcavationFittings.TORCH)
            +". Next: "+(plan.next()!=null?plan.next().point():plan.nextFitting()!=null?plan.nextFitting().point():"done")
            +". "+Objects.toString(NativeExcavation.issue(world,town,site,1),"Ready")+". No work or XP was granted.";
    }
    private static void block(World world,int x,int y,int z,String id) {
        if(id.equals(NativeExcavation.observe(world,new WarehouseBatch.Point(x,y,z)).block()))return;
        require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,id,0),"Fixture block failed: "+id);
    }
    private static void require(boolean condition,String issue){if(!condition)throw new IllegalStateException(issue);}
}
