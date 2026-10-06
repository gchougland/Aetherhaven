package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkState;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3d;

/** Disposable representations of saved cargo. Props never own stock or become collectible drops. */
public final class ProductionCargoDisplays {
    private ProductionCargoDisplays() {}
    public record Supply(String key,String item,Point point,int count) {}
    public static List<Supply> waiting(TownRecord town,Set<UUID> carriedSites) {
        var result=new ArrayList<Supply>();String prefix=town.getTownId()+"/";
        for(var plot:town.getPlotInstances()) {
            var production=town.peekPlotProduction(plot.getPlotId());if(production==null)continue;
            var cargo=production.waitingTimberCargo();if(cargo==null||cargo.source()==null||cargo.remaining()<=cargo.carried())continue;
            var p=cargo.source();result.add(new Supply(prefix+"tree/"+cargo.tree(),cargo.output(),new Point(p.x(),p.y(),p.z()),cargo.remaining()-cargo.carried()));
        }
        for(var site:town.getApprenticeWorkplaces().sites()) {
            var plan=site.existingExcavation();if(plan==null||plan.cargoPoint()==null||carriedSites.contains(site.id()))continue;
            for(var e:plan.cargo().entrySet())if(e.getValue()>0)result.add(new Supply(prefix+"mine/"+site.id()+"/"+e.getKey(),e.getKey(),plan.cargoPoint(),e.getValue()));
        }
        return List.copyOf(result);
    }
    public static final class Marker implements Component<EntityStore> {
        private static ComponentType<EntityStore,Marker> type;
        public static ComponentType<EntityStore,Marker> getComponentType(){return type;}
        private Supply supply;private int ordinal;
        public Marker() {}
        private Marker(Supply supply,int ordinal){this.supply=supply;this.ordinal=ordinal;}
        @Override public Marker clone(){return new Marker(supply,ordinal);}
    }
    public static void register(ComponentRegistryProxy<EntityStore> registry,AetherhavenPlugin plugin) {
        Marker.type=registry.registerComponent(Marker.class,Marker::new);registry.registerSystem(new Tick(plugin));
    }
    private static final class Tick extends TickingSystem<EntityStore> {
        private final AetherhavenPlugin plugin;
        private final Map<World,Long> next=Collections.synchronizedMap(new WeakHashMap<>());
        private Tick(AetherhavenPlugin plugin){this.plugin=plugin;}
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var world=store.getExternalData().getWorld();long now=System.currentTimeMillis();if(now<next.getOrDefault(world,0L))return;next.put(world,now+1000);
            world.execute(()->{if(world.isAlive())sync(world,AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).allTowns());});
        }
    }
    /** Must run on the world thread outside ECS processing. Also used by isolated native fixtures. */
    public static void sync(World world,Collection<TownRecord> towns) {
        if(!world.isInThread())throw new IllegalStateException("Cargo display updates require the world thread");
        var store=world.getEntityStore().getStore();var carried=new HashSet<UUID>();
        store.forEachChunk(Query.and(ApprenticeWorkState.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var state=chunk.getComponent(i,ApprenticeWorkState.getComponentType());
                if(state.active&&state.excavationCarrying&&state.excavationSite!=null&&chunk.getComponent(i,DeathComponent.getComponentType())==null)carried.add(state.excavationSite);
            }
        });
        var desired=new LinkedHashMap<String,Supply>();
        for(var town:towns)if(world.getName().equals(town.getWorldName()))for(var supply:waiting(town,carried)) {
            var p=supply.point();
            if(com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.worldChunkIfTicking(world,p.x(),p.z())!=null
                &&VillagerBlockUtil.isNpcStandColumn(world,p.x(),p.y(),p.z())&&Item.getAssetMap().getAsset(supply.item())!=null)desired.put(supply.key(),supply);
        }
        var stale=new ArrayList<Ref<EntityStore>>();var retained=new HashSet<String>();
        store.forEachChunk(Marker.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var marker=chunk.getComponent(i,Marker.getComponentType());var actual=marker.supply;
                var want=actual==null?null:desired.get(actual.key());
                if(want==null||!want.equals(actual)||!retained.add(actual.key()))stale.add(chunk.getReferenceTo(i));
            }
        });
        for(var ref:stale)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
        var perPoint=new HashMap<Point,Integer>();
        for(var supply:desired.values()) {
            int ordinal=perPoint.merge(supply.point(),1,Integer::sum)-1;
            if(!retained.contains(supply.key()))spawn(world,store,supply,ordinal);
        }
    }
    private static void spawn(World world,Store<EntityStore> store,Supply supply,int ordinal) {
        var p=supply.point();double feet=VillagerBlockUtil.resolveFeetYForStandCell(world,p.x(),p.y(),p.z());
        // A compact fan of the actual native resources, separated so different materials do not overlap.
        double angle=ordinal*2.4,radius=ordinal==0?0:.28;
        var position=new Vector3d(p.x()+.5+Math.cos(angle)*radius,feet+.18+ordinal*.045,p.z()+.5+Math.sin(angle)*radius);
        var rotation=new Rotation3f(0,(float)(ordinal*.8),0);var holder=store.getRegistry().newHolder();
        holder.addComponent(Marker.getComponentType(),new Marker(supply,ordinal));
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(),NonSerialized.get());
        holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(position,rotation));
        holder.addComponent(HeadRotation.getComponentType(),new HeadRotation(rotation));
        holder.addComponent(EntityScaleComponent.getComponentType(),new EntityScaleComponent(.65f));
        holder.addComponent(Intangible.getComponentType(),Intangible.INSTANCE);
        holder.addComponent(PropComponent.getComponentType(),PropComponent.get());
        holder.addComponent(ItemComponent.getComponentType(),new ItemComponent(new ItemStack(supply.item(),1)));
        holder.addComponent(PreventPickup.getComponentType(),PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(),PreventItemMerging.INSTANCE);
        store.addEntity(holder,AddReason.SPAWN);
    }
}
