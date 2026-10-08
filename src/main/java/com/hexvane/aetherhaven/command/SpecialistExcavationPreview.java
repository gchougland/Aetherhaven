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
        return create(world,owner,at,layout,fittings,enclosed,startNow,false);
    }
    static SpecialistExcavationPreview create(World world,UUID owner,Vector3d at,ExcavationPlan.Layout layout,ExcavationFittings.Mode fittings,boolean enclosed,boolean startNow,boolean railCheck) {
        return create(world,owner,at,layout,fittings,enclosed,startNow,railCheck,false);
    }
    static SpecialistExcavationPreview create(World world,UUID owner,Vector3d at,ExcavationPlan.Layout layout,ExcavationFittings.Mode fittings,boolean enclosed,boolean startNow,boolean railCheck,boolean crewCheck) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")&&!PersistentWorldSupport.shouldPersistWorldData(world),"Use an unsaved specialist preview.");
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);require(town!=null&&at!=null,"Artisan platform missing.");
        int x=(int)Math.floor(at.x),y=(int)Math.floor(at.y),z=(int)Math.floor(at.z);
        boolean deep=layout==ExcavationPlan.Layout.DEEP_STAIRS_DOWN||layout==ExcavationPlan.Layout.DEEP_STAIRS_UP;
        var area=new WorkplaceArea(x-6,y-(layout==ExcavationPlan.Layout.DEEP_STAIRS_DOWN?7:0),z-8,x+7,z+5,deep?11:layout==ExcavationPlan.Layout.STAIRS_UP||layout==ExcavationPlan.Layout.STAIRS_DOWN?6:5);
        for(int bx=area.minX()-1;bx<=area.maxX()+1;bx++)for(int bz=area.minZ();bz<area.maxZ();bz++)for(int by=y;by<y+5;by++)
            require(ChunkSectionBlockUtil.blockSectionAt(world,bx,by,bz)!=null&&ChunkSectionBlockUtil.blockId(world,bx,by,bz)==0,"Use a fresh empty Artisan platform.");
        var store=world.getEntityStore().getStore();
        var player=store.getExternalData().getRefFromUUID(owner);
        var position=player==null?null:store.getComponent(player,TransformComponent.getComponentType());
        require(position==null||!area.contains((int)Math.floor(position.getPosition().x),y,(int)Math.floor(position.getPosition().z)),"Visit another building before preparing this rock fixture.");
        var entrance=ExcavationPlan.Entrance.NORTH;var tunnel=ExcavationPlan.tunnel(area,entrance,layout);
        // This focused fixture starts with a clear stair, apart from one real work block.
        // Remove the preview platform where it crosses the descending passage too.
        if(railCheck)for(var point:tunnel)block(world,point.x(),point.y(),point.z(),"Empty");
        // Clear routes are explicit setup, with full supporting columns underneath raised floors.
        for(var point:tunnel)for(int by=area.floorY()-1;by<ExcavationPlan.floorAt(area,entrance,layout,point);by++)block(world,point.x(),by,point.z(),"Rock_Stone");
        if(layout==ExcavationPlan.Layout.STAIRS_DOWN)for(int d=1;d<=2;d++)for(int w=-1;w<=1;w++)for(int by=0;by<d;by++)
            block(world,x+w,y+by,area.minZ()+d,"Rock_Stone");
        var faces=new LinkedHashSet<WarehouseBatch.Point>();
        for(var point:tunnel) {
            int d=point.z()-area.minZ(),w=point.x()-x;
            boolean face=deep?true:layout==ExcavationPlan.Layout.BRANCHES?(w==0&&d==3||Math.abs(w)==4&&d==8)
                :w==0&&(d==3||d==7||d==11);
            if(face&&(!railCheck||faces.size()<(crewCheck?26:1)))faces.add(point);
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
        // A planned mineshaft is already its own workplace and needs no bench.
        var storage=new WarehouseBatch.Point(area.maxX()+1,y,area.minZ()+1);
        block(world,storage.x(),storage.y(),storage.z(),AetherhavenConstants.BLOCK_PRODUCTION_STORAGE);
        var mine=new PlotInstance(UUID.randomUUID(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(storage.x(),y,storage.z(),storage.x(),y+2,storage.z()),storage.x(),y,storage.z(),0);
        town.getPlotInstances().add(mine);
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));
        var sites=town.getApprenticeWorkplaces();require(sites.unlock(town,TownsfolkWorkType.MINING)==null,"Mining unlock failed.");
        require(sites.save(town,null,TownsfolkWorkType.MINING,deep?new WorkplaceArea(x-2,y,area.minZ(),x+3,area.minZ()+3):area,"Attended Mineshaft")==null,"Mining area failed.");
        var base=sites.sites().getLast();
        var site=deep?new ApprenticeWorkplace("Descending stair",TownsfolkWorkType.MINING,area):base;
        var review=NativeExcavation.review(world,town,site,entrance,layout);require(review.ready(),review.issue());
        require(review.cells().size()==faces.size(),"Unexpected reviewed rock count.");
        site.setMineshaftConfiguration(entrance,layout);
        if(startNow)require(site.excavation().start(0,owner,base.id(),area,entrance,layout,review.cells(),fittings)==null,"Mining plan failed.");
        if(deep)require(sites.addTunnel(town,owner,base.id(),site,null)==null,"Stair connection failed.");
        var materials=new LinkedHashMap<String,Integer>();
        for(var f:ExcavationFittings.plan(area,entrance,layout,fittings))materials.merge(f.item(),1,Integer::sum);
        for(var material:materials.entrySet())require(town.getWarehouse().acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(material.getKey(),material.getValue())),"Fitting material setup failed.");
        String worker=SpecialistWorkPreview.spawn(world,town,manager,new Vector3d(x+.5,y,area.minZ()+.5),TownsfolkWorkType.MINING,4010,0);
        if(crewCheck)SpecialistWorkPreview.spawn(world,town,manager,new Vector3d(x+2.5,y,area.minZ()+.5),TownsfolkWorkType.MINING,4011,0);
        TownsfolkWorkRoster.list(town,manager,store,plugin);
        world.getWorldConfig().setBlockTicking(true);world.getWorldConfig().setGameTimePaused(false);
        var time=store.getResource(WorldTimeResource.getResourceType());time.setGameTime(time.getGameTime().truncatedTo(ChronoUnit.DAYS).plus(8,ChronoUnit.HOURS),world,store);
        manager.updateTown(town);return new SpecialistExcavationPreview(site.id(),mine.getPlotId(),worker);
    }
    String status(World world,UUID owner) {
        var plugin=AetherhavenPlugin.get();var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(owner);
        var site=town.getApprenticeWorkplaces().find(siteId);var plan=site.excavation();
        return "Attended "+plan.layout().label+": "+plan.completed()+"/"+plan.total()+" blocks, cargo "+plan.cargo()+", stored cobble "+MineshaftOutput.find(town,town.getApprenticeWorkplaces().mineBase(site).id()).getAmount("Rock_Stone_Cobble")
            +", worker "+worker+" XP "+town.getTownsfolkProgress().get(worker).masteryXp()+". Fittings "+plan.fitted()+"/"+plan.fittingsTotal()
            +", beams "+town.getWarehouse().craftingStock().count(ExcavationFittings.BEAM)+", torches "+town.getWarehouse().craftingStock().count(ExcavationFittings.TORCH)
            +". Crew XP: "+town.getTownsfolkProgress().entrySet().stream().filter(e->e.getValue().priority(TownsfolkWorkType.MINING)>0).map(e->e.getKey()+"="+e.getValue().masteryXp()).toList()
            +". Carrier: "+plan.carrier()+". Rails present: "+plan.fittingsMode().rails+"/"+ExcavationFittings.plan(plan.area(),plan.entrance(),plan.layout(),ExcavationFittings.Mode.RAILS).stream().filter(f->{var block=NativeExcavation.observe(world,f.point()).block();return block!=null&&(block.equals("Rail")||block.startsWith("*Rail_"));}).count()
            +". Crew: "+crewStatus(world,site)+". Work: "+plan.work()
            +". Next: "+(plan.next()!=null?plan.next().point():plan.nextFitting()!=null?plan.nextFitting().point():"done")
            +". "+Objects.toString(NativeExcavation.issue(world,town,site,1),"Ready")+". No work or XP was granted.";
    }
    static List<String> crewStatus(World world,ApprenticeWorkplace site) {
        var result=new ArrayList<String>();var store=world.getEntityStore().getStore();
        var identity=com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding.getComponentType();
        var transform=TransformComponent.getComponentType();var work=ApprenticeWorkState.getComponentType();
        store.forEachChunk(com.hypixel.hytale.component.query.Query.and(identity,transform,work),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var state=chunk.getComponent(i,work);if(!site.id().equals(state.excavationSite))continue;
                var position=chunk.getComponent(i,transform).getPosition();
                var ref=chunk.getReferenceTo(i);
                var model=buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType());
                var visible=buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible.getComponentType());
                var network=buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType());
                result.add(chunk.getComponent(i,identity).getCharacterId()+" at "+position+" to "+state.approach
                    +" active "+state.active+" working "+state.working+" attempt "+state.routeRetries.approachAttempt(site.id(),System.currentTimeMillis())
                    +" reach "+(state.excavationTarget!=null&&NativeExcavation.clearReach(world,position,state.excavationTarget))
                    +" model "+(model==null?"MISSING":model.getModel().getModelAssetId()+" scale "+model.getModel().getScale()+" box "+model.getModel().getBoundingBox())
                    +" network "+(network==null?"MISSING":network.getId())+" viewers "+(visible==null?0:visible.visibleTo.size()));
            }
        });return result;
    }
    static SpecialistExcavationPreview network(World world,UUID owner,Vector3d at){
        var fixture=create(world,owner,at,ExcavationPlan.Layout.STRAIGHT,ExcavationFittings.Mode.NONE,false,false);
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);var town=manager.findTownForOwnerInWorld(owner);
        var site=town.getApprenticeWorkplaces().find(fixture.siteId());var old=site.area();int x=old.minX()+(old.maxX()-old.minX()-1)/2,y=old.floorY(),z=old.minZ();
        require(town.getApprenticeWorkplaces().save(town,site.id(),TownsfolkWorkType.MINING,new WorkplaceArea(x-2,y,z,x+3,z+3),"Preview mine base")==null,"Base setup failed");
        block(world,x-1,y,z,"Bench_Workbench");
        for(int by=y;by<y+3;by++)block(world,x+4,by,z+11,"Rock_Stone");
        var target=WorkplaceBenchAccess.find(world,new org.joml.Vector3i(x-1,y,z));require(target!=null&&target.site().id().equals(site.id()),"Native Workbench routing did not find the base");
        manager.updateTown(town);return fixture;
    }
    void prepareBufferCheck(World world,UUID owner){
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);var town=manager.findTownForOwnerInWorld(owner);
        var site=town.getApprenticeWorkplaces().find(siteId);require(town.getApprenticeWorkplaces().tunnels(siteId).isEmpty(),"Use a fresh base without tunnels");
        var a=site.area();int x=a.minX()+2,y=a.floorY(),z=a.minZ();
        for(int d:new int[]{3,7,11})for(int w=-1;w<=1;w++)for(int h=0;h<3;h++)block(world,x+w,y+h,z+d,"Rock_Stone");
        for(int h=0;h<3;h++)block(world,x,y+h,z+4,"Rock_Stone");
        // A second, more skilled idle worker must win the first dispatch without stealing active loads.
        String senior=SpecialistWorkPreview.spawn(world,town,manager,new Vector3d(x+1.5,y,z+.5),TownsfolkWorkType.MINING,4011,0);
        town.getTownsfolkProgress().get(senior).setSkill(TownsfolkSkill.MINING,5);
        TownsfolkWorkRoster.list(town,manager,world.getEntityStore().getStore(),plugin);
        town.getPlotInstances().removeIf(p->p.getPlotId().equals(mineId));
        manager.updateTown(town);
    }
    void bufferRoom(World world,UUID owner,int room){
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);var town=manager.findTownForOwnerInWorld(owner);
        var state=town.getOrCreatePlotProduction(mineId);var entry=com.hexvane.aetherhaven.production.ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,state);
        long maximum=com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,"Rock_Stone_Cobble");
        state.removeAmountUpTo("Rock_Stone_Cobble",1000000);state.addAmount("Rock_Stone_Cobble",Math.max(0,maximum-room),maximum);manager.updateTown(town);
    }
    String networkStatus(World world,UUID owner){
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get()).findTownForOwnerInWorld(owner);
        return "Mine network: "+town.getApprenticeWorkplaces().tunnels(siteId).stream().map(s->s.name()+" "+s.excavation().completed()+"/"+s.excavation().total()+", cargo "+s.excavation().cargo()+", carrier "+s.excavation().carrier()+", buffer "+s.excavation().output()).toList()+", delivered "+town.getOrCreatePlotProduction(mineId).getAmount("Rock_Stone_Cobble")+", workers "+town.getTownsfolkProgress().entrySet().stream().filter(e->e.getValue().priority(TownsfolkWorkType.MINING)>0).map(e->e.getKey()+" skill "+e.getValue().skill(TownsfolkSkill.MINING)+" XP "+e.getValue().masteryXp()).toList();
    }
    private static void block(World world,int x,int y,int z,String id) {
        if(id.equals(NativeExcavation.observe(world,new WarehouseBatch.Point(x,y,z)).block()))return;
        require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,id,0),"Fixture block failed: "+id);
    }
    private static void require(boolean condition,String issue){if(!condition)throw new IllegalStateException(issue);}
}
