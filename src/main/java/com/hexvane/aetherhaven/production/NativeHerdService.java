package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.joml.Vector3d;

/** Loaded livestock discovery and native life-cycle operations. Does not change breeding stock roles. */
public final class NativeHerdService {
    private NativeHerdService() {}
    private record Animal(UUID id,String family,Vector3d position) {}
    public record Review(List<HerdProductionState.Pair> pairs,String issue) {
        public Review {pairs=List.copyOf(pairs);}public boolean ready(){return issue==null&&!pairs.isEmpty();}
    }
    public static boolean allowed(TownRecord town,PlotInstance plot,Vector3d p) {
        return plot.containsWorldBlock((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z))
            &&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),ChunkUtil.chunkCoordinate((int)Math.floor(p.z)))
            &&town.getApprenticeWorkplaces().sites().stream().noneMatch(s->s.area().contains(p.x,p.y,p.z));
    }
    public static WorkplaceArea area(PlotInstance plot,HerdProductionState.Pair pair) {
        var f=plot.toFootprint();return new WorkplaceArea(f.getMinX(),pair.trough().y(),f.getMinZ(),f.getMaxX()+1,f.getMaxZ()+1);
    }
    public static Review review(World world,TownRecord town,PlotInstance plot) {
        if(!world.isInThread())throw new IllegalStateException("Herd review requires the world thread");
        var f=plot.toFootprint();long w=(long)f.getMaxX()-f.getMinX()+1,h=(long)f.getMaxY()-f.getMinY()+1,d=(long)f.getMaxZ()-f.getMinZ()+1;
        if(w<1||w>1024||h<1||h>320||d<1||d>1024||w*h*d>262144)return new Review(List.of(),"This barn is too large for a herd review.");
        for(int x=ChunkUtil.chunkCoordinate(f.getMinX());x<=ChunkUtil.chunkCoordinate(f.getMaxX());x++)for(int z=ChunkUtil.chunkCoordinate(f.getMinZ());z<=ChunkUtil.chunkCoordinate(f.getMaxZ());z++)
            if(ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunk(x,z))==null||!TownTerritoryClaims.contains(town,x,z))return new Review(List.of(),"Visit the whole barn inside town borders to review its herd.");
        var troughs=new ArrayList<WarehouseBatch.Point>();
        for(int x=f.getMinX();x<=f.getMaxX();x++)for(int z=f.getMinZ();z<=f.getMaxZ();z++)for(int y=f.getMinY();y<=f.getMaxY();y++) {
            var section=ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);if(section==null||section.getFiller(x,y,z)!=0)continue;
            var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
            if(block!=null&&"Bench_Trough".equals(block.getId())&&allowed(town,plot,new Vector3d(x+.5,y,z+.5)))troughs.add(new WarehouseBatch.Point(x,y,z));
        }
        if(troughs.isEmpty())return new Review(List.of(),"Place a feeding trough inside the barn, with room for breeding animals and their young.");
        var animals=new ArrayList<Animal>();var store=world.getEntityStore().getStore();
        store.forEachChunk(Query.and(NPCEntity.getComponentType(),TransformComponent.getComponentType(),UUIDComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var npc=chunk.getComponent(i,NPCEntity.getComponentType());String family=RanchProducts.family(npc.getRoleName());
                var p=chunk.getComponent(i,TransformComponent.getComponentType()).getPosition();var id=chunk.getComponent(i,UUIDComponent.getComponentType()).getUuid();
                if(family!=null&&allowed(town,plot,p)&&chunk.getComponent(i,DeathComponent.getComponentType())==null&&!claimedElsewhere(town,plot,id))animals.add(new Animal(id,family,new Vector3d(p)));
            }
        });
        var pairs=new ArrayList<HerdProductionState.Pair>();
        for(String family:RanchProducts.families())for(var trough:troughs) {
            var candidates=animals.stream().filter(a->a.family().equals(family)&&a.position().y>=trough.y()&&a.position().y<trough.y()+WorkplaceArea.HEIGHT
                &&a.position().distanceSquared(trough.x()+.5,trough.y(),trough.z()+.5)<=64).sorted(Comparator.comparing(Animal::id)).limit(2).map(Animal::id).toList();
            if(candidates.size()==2){pairs.add(new HerdProductionState.Pair(family,candidates,trough));break;}
        }
        return new Review(pairs,pairs.isEmpty()?"Keep a matching breeding pair near a trough. The herd cards show supported animals. Breeding pairs remain protected.":null);
    }
    private static boolean claimedElsewhere(TownRecord town,PlotInstance plot,UUID id) {
        for(var other:town.getPlotInstances())if(!other.getPlotId().equals(plot.getPlotId())) {
            var state=town.peekPlotProduction(other.getPlotId());if(state!=null&&state.usesPhysicalHerd())
                for(var herd:state.herd().herds().values())if(herd.pair().breeders().contains(id))return true;
        }
        return false;
    }
    public static boolean pairReady(World world,TownRecord town,PlotInstance plot,HerdProductionState.Pair pair) {
        var p=pair.trough();if(ChunkSectionBlockUtil.worldChunkIfTicking(world,p.x(),p.z())==null)return false;
        var block=ChunkSectionBlockUtil.blockType(world,p.x(),p.y(),p.z());
        if(block==null||!"Bench_Trough".equals(block.getId())||!allowed(town,plot,new Vector3d(p.x()+.5,p.y(),p.z()+.5)))return false;
        var store=world.getEntityStore().getStore();
        for(UUID id:pair.breeders()) {
            var ref=store.getExternalData().getRefFromUUID(id);if(ref==null||!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
            var npc=store.getComponent(ref,NPCEntity.getComponentType());var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(npc==null||transform==null||!pair.family().equals(RanchProducts.family(npc.getRoleName())))return false;
            var pos=transform.getPosition();
            if(!simulated(world,pos)||!area(plot,pair).contains(pos.x,pos.y,pos.z)||!allowed(town,plot,pos)||pos.distanceSquared(p.x()+.5,p.y(),p.z()+.5)>64)return false;
        }
        return true;
    }
    public static Ref<EntityStore> offspring(World world,TownRecord town,PlotInstance plot,HerdProductionState.Herd herd) {
        var store=world.getEntityStore().getStore();var ref=RanchWorkService.offspring(store,area(plot,herd.pair()),herd.care());
        if(ref==null)return null;
        var p=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        return simulated(world,p)&&allowed(town,plot,p)?ref:null;
    }
    public static boolean feed(World world,TownRecord town,PlotInstance plot,PlotProductionState state,String family) {
        var herd=state.herd().herds().get(family);if(herd==null||!pairReady(world,town,plot,herd.pair()))return false;
        var store=world.getEntityStore().getStore();var pair=herd.pair();
        return state.herd().fed(family,()->RanchWorkService.complete(world,area(plot,pair),herd.care(),new RanchWorkService.Job(pair.trough(),null,false),RanchWorkService.family(family),
            ref->{var id=store.getComponent(ref,UUIDComponent.getComponentType());return id!=null&&pair.breeders().contains(id.getUuid());},
            point->allowed(town,plot,point),issue->{}));
    }
    public static boolean mature(World world,TownRecord town,PlotInstance plot,PlotProductionState state,String family,UUID expected) {
        var herd=state.herd().herds().get(family);if(herd==null||herd.care().grown()||!herd.care().ready()||herd.sinceFeed()>=HerdProductionState.NEGLECT)return false;
        var animal=offspring(world,town,plot,herd);if(animal==null||!Objects.equals(expected,herd.care().offspring()))return false;
        var p=world.getEntityStore().getStore().getComponent(animal,TransformComponent.getComponentType()).getPosition();
        var job=new RanchWorkService.Job(point(p),expected,false);
        return RanchWorkService.complete(world,area(plot,herd.pair()),herd.care(),job,RanchWorkService.family(family),ref->false,pos->false,issue->{});
    }
    public static boolean collect(World world,TownRecord town,PlotInstance plot,PlotProductionState state,String family,UUID expected,RanchProducts.Product product) {
        if(product==null||com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"rancher",product.output())!=null)return false;
        var herd=state.herd().herds().get(family);if(herd==null||!Objects.equals(expected,herd.care().offspring()))return false;
        var animal=offspring(world,town,plot,herd);if(animal==null)return false;
        var store=world.getEntityStore().getStore();var npc=store.getComponent(animal,NPCEntity.getComponentType());
        var nativeFamily=RanchWorkService.family(herd.care().adultRole());
        if(nativeFamily==null||!nativeFamily.grownRole().equals(npc.getRoleName()))return false;
        var point=point(store.getComponent(animal,TransformComponent.getComponentType()).getPosition());
        reconcileAppearance(world,town,plot,herd);
        boolean collected=state.herd().collect(family,expected,product,()->{
            if(product.slaughter())return RanchWorkService.complete(world,area(plot,herd.pair()),herd.care(),new RanchWorkService.Job(point,expected,true),nativeFamily,ref->false,pos->false,issue->{});
            var appearance=RanchProductAppearance.get(family,product.output());
            if(appearance!=null)return RanchProductAppearance.apply(animal,store,appearance,false);
            AnimationUtils.playAnimation(animal,AnimationSlot.Action,"Eat",true,store);return true;
        });
        var appearance=product==null?null:RanchProductAppearance.get(family,product.output());
        if(collected&&appearance!=null)RanchProductAppearance.effects(animal,store,appearance);
        return collected;
    }
    /** Restores only tracked offspring, without changing breeders or producing a second item. */
    public static boolean reconcileAppearance(World world,TownRecord town,PlotInstance plot,HerdProductionState.Herd herd) {
        var appearance=RanchProductAppearance.get(herd.pair().family(),herd.recoveringOutput());
        if(appearance==null)return false;
        var animal=offspring(world,town,plot,herd);if(animal==null)return false;
        boolean ready=herd.care().grown()&&herd.care().ready();
        if(!RanchProductAppearance.apply(animal,world.getEntityStore().getStore(),appearance,ready))return false;
        return ready&&herd.recovered(herd.care().offspring());
    }
    private static boolean simulated(World world,Vector3d p){return ChunkSectionBlockUtil.worldChunkIfTicking(world,(int)Math.floor(p.x),(int)Math.floor(p.z))!=null;}
    public static WarehouseBatch.Point point(Vector3d p){return new WarehouseBatch.Point((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z));}
    public static void advance(World world,AetherhavenPlugin plugin,long minutes) {
        if(minutes<=0||Universe.get().getPlayers().isEmpty())return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        for(var town:manager.allTowns()) {
            if(!world.getName().equals(town.getWorldName())||Universe.get().getPlayers().stream().noneMatch(p->town.hasMemberOrOwner(p.getUuid())))continue;
            boolean changed=false;
            for(var plot:town.getPlotInstances()) {
                var state=town.peekPlotProduction(plot.getPlotId());if(state==null||!state.usesPhysicalHerd()||plot.getState()!=PlotInstanceState.COMPLETE)continue;
                for(var herd:state.herd().herds().values()) {
                    boolean simulated=pairReady(world,town,plot,herd.pair())&&(herd.care().offspring()==null||offspring(world,town,plot,herd)!=null);
                    if(simulated){state.herd().advance(herd.pair().family(),minutes,true);reconcileAppearance(world,town,plot,herd);changed=true;}
                }
            }
            if(changed)manager.updateTown(town);
        }
    }
}
