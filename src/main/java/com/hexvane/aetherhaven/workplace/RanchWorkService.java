package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionConfig;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawningContext;
import java.util.*;
import org.joml.Vector3d;

/** Real, attended herd care. Breeding stock is never selected for slaughter. */
public final class RanchWorkService {
    private RanchWorkService() {}
    public record Family(String breedingRole,String youngRole,String grownRole,String grownModel) {
        /** Existing integrations keep their original model-scale policy unless they supply a grown model. */
        public Family(String breedingRole,String youngRole,String grownRole){this(breedingRole,youngRole,grownRole,null);}
    }
    private static final Map<String,Family> FAMILIES=new java.util.concurrent.ConcurrentHashMap<>();
    static {
        for(String adult:List.of("Cow","Pig","Sheep","Chicken","Goat","Mosshorn","Skrill","Tetrabird")) {
            registerFamily(new Family(adult,"Aetherhaven_Ranch_"+adult+"_Young","Aetherhaven_Ranch_"+adult,adult));
            registerFamily(new Family("Tamed_"+adult,"Aetherhaven_Ranch_"+adult+"_Young","Aetherhaven_Ranch_"+adult,adult));
        }
        registerFamily(new Family("Bluebird","Aetherhaven_Ranch_Bluebird_Young","Aetherhaven_Ranch_Bluebird","Bluebird"));
    }
    /** Integration roles must suppress item drops and passive item production. */
    public static void registerFamily(Family family) {
        if(family==null || family.breedingRole()==null || family.youngRole()==null || family.grownRole()==null
            || family.breedingRole().isBlank() || family.youngRole().isBlank() || family.grownRole().isBlank())
            throw new IllegalArgumentException("Ranch family requires three role IDs");
        FAMILIES.put(family.breedingRole(),family);
        FoodSiteService.registerLivestockRole(family.breedingRole());
    }
    public record Job(WarehouseBatch.Point target,UUID animal,boolean slaughter) {}
    /** Wild and tamed variants of the same family can share breeding stock. */
    public static Family breedingFamily(Collection<String> roles) {
        var counts=new TreeMap<String,Integer>();
        var families=new TreeMap<String,Family>();
        for(var role:roles) {
            var family=FAMILIES.get(role);if(family==null)continue;
            String key=family.youngRole()+"/"+family.grownRole();
            counts.merge(key,1,Integer::sum);families.putIfAbsent(key,family);
        }
        return counts.entrySet().stream().filter(e->e.getValue()>=2)
            .map(e->families.get(e.getKey())).findFirst().orElse(null);
    }
    public static boolean managedRole(String role) {
        return role!=null && FAMILIES.values().stream().anyMatch(f -> role.equals(f.youngRole()) || role.equals(f.grownRole()));
    }
    public static Family family(String breedingRole) { return breedingRole==null?null:FAMILIES.get(breedingRole); }
    public static boolean offspringPresent(World world,ApprenticeWorkplace site) {
        return offspring(world.getEntityStore().getStore(),site)!=null;
    }
    public static String herdStatus(World world,ApprenticeWorkplace site) {
        return "Workplace animals are provided automatically and stay near the trough. Regular tending supplies town food.";
    }
    private static Ref<EntityStore> offspring(Store<EntityStore> store,ApprenticeWorkplace site) {
        return offspring(store,site.area(),site.food().ranch());
    }
    public static Ref<EntityStore> offspring(Store<EntityStore> store,WorkplaceArea area,RanchCare herd) {
        var id=herd.offspring();if(id==null)return null;
        var ref=store.getExternalData().getRefFromUUID(id);
        if(ref==null||!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return null;
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        var family=FAMILIES.get(herd.adultRole());
        if(npc==null||transform==null||family==null)return null;
        var p=transform.getPosition();
        return area.contains(p.x,p.y,p.z)
            && (family.youngRole().equals(npc.getRoleName())||family.grownRole().equals(npc.getRoleName())) ? ref : null;
    }
    public static Job job(World world,ApprenticeWorkplace site,WarehouseBatch.Point trough) {
        return new Job(trough,null,false);
    }
    /** Called after real work and outside entity iteration. No inventory output or chunk loading. */
    public static boolean complete(World world,ApprenticeWorkplace site,Job job) {
        return job.animal()==null && !job.slaughter() && ApprenticeRanchAnimals.feed(world,site);
    }
    /** Shared native lifecycle. Core barns supply their registered breeding pair and separate inventory accounting. */
    public static boolean complete(World world,WorkplaceArea area,RanchCare herd,Job job,Family preferred,
            java.util.function.Predicate<Ref<EntityStore>> breedingFilter,java.util.function.Predicate<Vector3d> spawnAllowed,java.util.function.Consumer<String> issue) {
        var store=world.getEntityStore().getStore();
        if(job.animal()!=null) {
            var animal=offspring(store,area,herd);
            if(animal==null||!job.animal().equals(herd.offspring())||!herd.ready())return false;
            var p=store.getComponent(animal,TransformComponent.getComponentType()).getPosition();
            if(p.distanceSquared(job.target().x()+.5,job.target().y(),job.target().z()+.5)>4)return false;
            if(job.slaughter()) {
                if(!herd.grown())return false;
                // No death drops. Only the tracked, town-raised animal may be removed.
                store.removeEntity(animal,RemoveReason.REMOVE);herd.harvested();return true;
            }
            var family=FAMILIES.get(herd.adultRole());var npc=store.getComponent(animal,NPCEntity.getComponentType());
            if(family==null||npc.getRole()==null||!NPCPlugin.get().hasRoleName(family.grownRole()))return false;
            if(family.grownModel()!=null) {
                var model=ModelAsset.getAssetMap().getAsset(family.grownModel());
                if(model==null)return false;
                // Native role changes retain InitialModelScale. Maturation must adopt the adult's scale range.
                npc.setInitialModelScale(model.generateRandomScale());
            }
            RoleChangeSystem.requestRoleChange(animal,npc.getRole(),NPCPlugin.get().getIndex(family.grownRole()),true,store);
            herd.matured(herd.offspring());return true;
        }
        var animals=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Query.and(NPCEntity.getComponentType(),TransformComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var npc=chunk.getComponent(i,NPCEntity.getComponentType());var p=chunk.getComponent(i,TransformComponent.getComponentType()).getPosition();
                var family=FAMILIES.get(npc.getRoleName());
                if(family!=null&&(preferred==null||family.grownRole().equals(preferred.grownRole()))
                    &&breedingFilter.test(chunk.getReferenceTo(i))&&area.contains(p.x,p.y,p.z)
                    &&p.distanceSquared(job.target().x()+.5,job.target().y(),job.target().z()+.5)<=64
                    &&chunk.getComponent(i,DeathComponent.getComponentType())==null)animals.add(chunk.getReferenceTo(i));
            }
        });
        if(animals.size()<2){issue.accept("Keep at least two breeding animals near the trough.");return false;}
        for(var animal:animals)AnimationUtils.playAnimation(animal,AnimationSlot.Action,"Eat",true,store);
        herd.fed();
        if(herd.canBreed()) {
            var family=breedingFamily(animals.stream().map(animal->store.getComponent(animal,NPCEntity.getComponentType()).getRoleName()).toList());
            var a=area;
            if(family!=null&&NPCPlugin.get().hasRoleName(family.youngRole())) {
                int roleIndex=NPCPlugin.get().getIndex(family.youngRole());
                var builder=NPCPlugin.get().tryGetCachedValidRole(roleIndex);
                if(!(builder instanceof ISpawnableWithModel spawnable))return true;
                var context=new SpawningContext();
                try {
                    if(!context.setSpawnable(spawnable)||context.getModel()==null)return true;
                    var model=context.getModel();var box=model.getBoundingBox();
                    if(box==null)return true;
                    var occupied=new ArrayList<Box>();
                    store.forEachChunk(Query.and(TransformComponent.getComponentType(),BoundingBox.getComponentType()),(chunk,buffer)->{
                        for(int i=0;i<chunk.size();i++) {
                            var p=chunk.getComponent(i,TransformComponent.getComponentType()).getPosition();
                            if(p.distanceSquared(job.target().x()+.5,job.target().y(),job.target().z()+.5)>256)continue;
                            occupied.add(new Box(chunk.getComponent(i,BoundingBox.getComponentType()).getBoundingBox()).offset(p));
                        }
                    });
                    var clearCells=new HashSet<WarehouseBatch.Point>();
                    // Include the full feeding radius when finding connected floor space.
                    for(int x=Math.max(a.minX(),job.target().x()-8);x<Math.min(a.maxX(),job.target().x()+9);x++)
                        for(int z=Math.max(a.minZ(),job.target().z()-8);z<Math.min(a.maxZ(),job.target().z()+9);z++) {
                            int y=job.target().y();
                            var point=new Vector3d(x+.5,y,z+.5);
                            // A lamb needs its own footprint, not a three-block-wide empty pen.
                            // Keep the whole body inside the work area and support every occupied column.
                            if(point.x+box.min.x<a.minX()||point.x+box.max.x>a.maxX()
                                ||point.z+box.min.z<a.minZ()||point.z+box.max.z>a.maxZ()
                                ||point.y+box.min.y<a.floorY()||point.y+box.max.y>a.floorY()+WorkplaceArea.HEIGHT)continue;
                            boolean clear=true;
                            for(int xx=(int)Math.floor(point.x+box.min.x);xx<Math.ceil(point.x+box.max.x);xx++)
                                for(int zz=(int)Math.floor(point.z+box.min.z);zz<Math.ceil(point.z+box.max.z);zz++)
                                    clear &= VillagerBlockUtil.isNpcStandColumn(world,xx,y,zz)&&spawnAllowed.test(new Vector3d(xx+.5,y,zz+.5));
                            if(!clear)continue;
                            if(!spawnAllowed.test(point))continue;
                            if(!context.setExact(world,point.x,point.y,point.z)
                                ||!context.validatePosition(CollisionConfig.MATERIAL_SOLID|CollisionConfig.MATERIAL_DAMAGE))continue;
                            clearCells.add(new WarehouseBatch.Point(x,y,z));
                        }
                    var queue=new ArrayDeque<WarehouseBatch.Point>();
                    for(var animal:animals) {
                        var p=store.getComponent(animal,TransformComponent.getComponentType()).getPosition();
                        var cell=new WarehouseBatch.Point((int)Math.floor(p.x),job.target().y(),(int)Math.floor(p.z));
                        if(clearCells.remove(cell))queue.add(cell);
                    }
                    while(!queue.isEmpty()) {
                        var cell=queue.remove();
                        for(int[] delta:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                            var next=new WarehouseBatch.Point(cell.x()+delta[0],cell.y(),cell.z()+delta[1]);
                            if(clearCells.remove(next))queue.add(next);
                        }
                        // Births stay on floor reachable from the breeders, never across a fence or wall.
                        if(Math.abs(cell.x()-job.target().x())>3||Math.abs(cell.z()-job.target().z())>3)continue;
                        var point=new Vector3d(cell.x()+.5,cell.y(),cell.z()+.5);
                        var body=new Box(box).offset(point);
                        if(occupied.stream().anyMatch(body::isIntersecting))continue;
                        // Spawn the same model and scale that passed collision checks, including fences.
                        var pair=NPCPlugin.get().spawnEntity(store,roleIndex,point,Rotation3f.ZERO,model,null);
                        if(pair==null)return true;
                        var id=store.getComponent(pair.first(),UUIDComponent.getComponentType());
                        if(id==null){store.removeEntity(pair.first(),RemoveReason.REMOVE);return true;}
                        herd.born(id.getUuid(),family.breedingRole());return true;
                    }
                } finally {context.releaseFull();}
            }
        }
        return true;
    }
}
