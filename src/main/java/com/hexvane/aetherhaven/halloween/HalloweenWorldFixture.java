package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3d;

/** Called only by the opt-in command in a newly created disposable world. */
public final class HalloweenWorldFixture {
    private HalloweenWorldFixture() {}
    public static CompletableFuture<Void> run(World world) {
        return world.getChunkStore().getChunkSectionReferenceAsync(0,4,0,GetChunkFlags.SET_TICKING)
            .thenRunAsync(() -> exercise(world),world);
    }
    private static void exercise(World world) {
        var plugin=AetherhavenPlugin.get(); var store=world.getEntityStore().getStore();
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var owner=UUID.randomUUID();
        var town=new TownRecord(UUID.randomUUID(),owner,world.getName(),1,129,1,1,1,0);
        var plot=new PlotInstance(UUID.randomUUID(),"plot_halloween",PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(3,128,3,11,132,11),3,129,3,0);
        town.getPlotInstances().add(plot);manager.markDirty(town);
        for(int x=1;x<=13;x++)for(int z=1;z<=13;z++)put(world,x,128,z,"Wood_Hardwood_Planks");
        for(int x=3;x<=11;x++)for(int z=3;z<=11;z++) {
            put(world,x,132,z,"Wood_Hardwood_Planks");
            if(x==3||x==11||z==3||z==11)for(int y=129;y<=131;y++)put(world,x,y,z,"Wood_Hardwood_Planks");
        }
        require(HalloweenGameService.chooseSpot(world,town,manager)==null,"Sealed native room accepted");
        put(world,3,129,7,"Empty");put(world,3,130,7,"Empty");
        var open=HalloweenGameService.chooseSpot(world,town,manager);
        require(open!=null,"Open native room has no hiding spot");
        put(world,3,129,7,"Furniture_Village_Door");
        var spot=HalloweenGameService.chooseSpot(world,town,manager);
        require(spot!=null,"Operable native door rejected");
        var spawn=NPCPlugin.get().spawnNPC(store,HalloweenResident.MODEL,null,new Vector3d(6.5,129,6.5),Rotation3f.ZERO);
        require(spawn!=null,"Jack's resident role failed to spawn");var jack=spawn.first();
        try {
            store.putComponent(jack,TownVillagerBinding.getComponentType(),new TownVillagerBinding(town.getTownId(),HalloweenResident.KIND,null));
            town.getResidentNpcRecords().add(new ResidentNpcRecord(HalloweenResident.MODEL,HalloweenResident.KIND,null,
                store.getComponent(jack,UUIDComponent.getComponentType()).getUuid()));
            var state=town.halloweenGame();
            require(state.start(owner,40,spot)!=null,"Fixture game failed");
            put(world,spot.x(),spot.y(),spot.z(),HalloweenGameService.BLOCK);
            require(HalloweenResident.setHeadWorn(jack,store,false),"Head removal failed");
            require(HalloweenResident.HEADLESS_MODEL.equals(store.getComponent(jack,ModelComponent.getComponentType()).getModel().getModelAssetId()),"Headless model not applied");
            HalloweenGameService.beforeBuildingMove(world,store,manager,town,plot.getPlotId());
            require(state.active()==null&&state.cleanup().isEmpty(),"Move left game or cleanup pending");
            require("Empty".equals(ChunkSectionBlockUtil.blockType(world,spot.x(),spot.y(),spot.z()).getId()),"Move left hidden head in old building");
            require(HalloweenResident.MODEL.equals(store.getComponent(jack,ModelComponent.getComponentType()).getModel().getModelAssetId()),"Move did not restore Jack's head");
            var paid=state.start(owner,40,spot);state.pickup(owner,paid.id());state.reward(owner,paid.id(),40);
            var next=state.start(UUID.randomUUID(),40,spot);require(next!=null,"Second player's fixture game failed");
            put(world,spot.x(),spot.y(),spot.z(),HalloweenGameService.BLOCK);
            require(manager.removeTown(town.getTownId()),"Town removal failed");
            require(manager.retiredHalloweenGames().rewarded(owner,40),"Town removal reset daily reward");
            HalloweenGameService.reconcileRetired(world,manager);
            require(manager.retiredHalloweenGames().cleanup().isEmpty(),"Removed town cleanup retained loaded head");
            require("Empty".equals(ChunkSectionBlockUtil.blockType(world,spot.x(),spot.y(),spot.z()).getId()),"Removed town left its hidden head");
        } finally {
            if(jack.isValid())store.removeEntity(jack,RemoveReason.REMOVE);
            manager.removeTown(town.getTownId());
        }
    }
    private static void put(World world,int x,int y,int z,String id) {
        require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,id,0),"Could not place fixture block "+id);
    }
    private static void require(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
}
