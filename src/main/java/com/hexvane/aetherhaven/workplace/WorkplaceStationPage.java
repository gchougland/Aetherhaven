package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.*;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.BenchUpgradeRequirement;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import java.util.*;
import org.joml.Vector3i;

/** Native bench costs and saved tiers, with an explicit, cancellable upgrade from the task screen. */
public final class WorkplaceStationPage extends AetherhavenInteractiveCustomUIPage<WorkplaceStationPage.Data> {
    private final UUID townId,siteId;private final Vector3i pos;
    private boolean appended,refreshing;
    private String hint="Upgrade materials come from your inventory, then the town warehouse. Stay near the station while upgrading.";

    private boolean fetched(Store<EntityStore> store,com.hypixel.hytale.server.core.inventory.container.ItemContainer inventory,
            com.hypixel.hytale.server.core.inventory.MaterialQuantity[] input) {
        if(inventory.canRemoveMaterials(Arrays.asList(input)))return true;
        fetchMaterials(store,inventory,input);
        return inventory.canRemoveMaterials(Arrays.asList(input));
    }
    /** Brings missing upgrade materials out of the warehouse. Requirements given as a resource type are left to the pack. */
    private void fetchMaterials(Store<EntityStore> store,com.hypixel.hytale.server.core.inventory.container.ItemContainer inventory,
            com.hypixel.hytale.server.core.inventory.MaterialQuantity[] input) {
        if(inventory==null||input==null)return;
        var cost=new java.util.LinkedHashMap<String,Integer>();
        for(var material:input)if(material!=null&&material.getItemId()!=null)cost.merge(material.getItemId(),material.getQuantity(),Integer::sum);
        var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
        var town=plugin==null?null:com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(townId);
        if(!cost.isEmpty())com.hexvane.aetherhaven.crafter.PatternBookPayment.topUp(inventory,
            com.hexvane.aetherhaven.crafter.PatternBookPayment.stockFor(town,playerRef.getUuid()),cost);
    }
    private Ref<ChunkStore> upgradingBench;
    private BenchUpgradeRequirement upgradingRequirement;
    private int upgradingTier;
    private int renderedTier=-1;
    private long started;
    private record Station(WorkplaceBenchAccess.Target target,Ref<ChunkStore> ref,BlockType block,BenchBlock bench,BlockModule.BlockStateInfo info) {}
    public WorkplaceStationPage(PlayerRef player,UUID town,UUID site,Vector3i pos) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;siteId=site;this.pos=new Vector3i(pos);
    }
    private Station station(Ref<EntityStore> ref,Store<EntityStore> store) {
        var world=store.getExternalData().getWorld();var target=WorkplaceBenchAccess.find(world,pos);
        if(!WorkplaceBenchAccess.allowed(target,playerRef.getUuid())||!townId.equals(target.town().getTownId())
            ||!siteId.equals(target.site().id())||!WorkplaceBenchAccess.inReach(ref,store,pos))return null;
        var block=ChunkSectionBlockUtil.blockType(world,pos.x,pos.y,pos.z);
        var benchRef=ChunkSectionBlockUtil.blockEntityRefAt(world,pos.x,pos.y,pos.z);
        var cs=world.getChunkStore().getStore();
        var bench=benchRef!=null&&benchRef.isValid()?cs.getComponent(benchRef,BenchBlock.getComponentType()):null;
        var info=bench==null?null:cs.getComponent(benchRef,BlockModule.BlockStateInfo.getComponentType());
        return new Station(target,benchRef,block,bench,info);
    }
    private static BenchUpgradeRequirement requirement(Station station) {
        return station.bench()==null||station.block().getBench()==null?null:
            station.block().getBench().getUpgradeRequirement(station.bench().getTierLevel());
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        var station=station(ref,store);if(station==null){close();return;}
        if(!appended) {
            c.append("Aetherhaven/WorkplaceStationPage.ui");appended=true;
            for(String action:List.of("Back","Upgrade"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
        }
        var req=requirement(station);var item=station.block().getItem();
        c.set("#Icon.Visible",item!=null);if(item!=null)c.set("#Icon.ItemId",item.getId());
        c.set("#Name.Text",WorkplaceEquipment.label(WorkplaceEquipment.benchId(station.block())));
        int tier=station.bench()==null?1:station.bench().getTierLevel();
        c.set("#Tier.Text","TIER "+tier+(req==null?"":"   >   TIER "+(tier+1)));
        c.set("#Site.Text",station.target().site().name());
        c.set("#Summary.Text",req==null?"This station has no further upgrades.":"Improve this station to unlock recipes that require tier "+(tier+1)+". Upgrade time: "+Math.round(req.getTimeSeconds())+" seconds.");
        if(renderedTier!=tier){WorkplaceOrdersPage.materials(c,"#Materials",req==null?null:req.getInput());renderedTier=tier;}
        c.set("#Hint.Text",hint);c.set("#Upgrade.Disabled",req==null||upgradingBench!=null||station.info()==null);
        c.set("#Upgrade.Text",upgradingBench==null?"Upgrade station":"Upgrading...");
        c.set("#Progress.Value",upgradingBench==null?0f:(float)Math.clamp((System.nanoTime()-started)/1e9/Math.max(.001,upgradingRequirement.getTimeSeconds()),0,1));
        if(!refreshing){refreshing=true;schedule(store.getExternalData().getWorld());}
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var station=station(ref,store);if(station==null){close();return;}
        if("Back".equals(data.action)) {WorkplaceBenchAccess.open(ref,store,playerRef,pos);return;}
        var req=requirement(station);
        if("Upgrade".equals(data.action)&&upgradingBench==null&&req!=null&&station.info()!=null) {
            var player=store.getComponent(ref,Player.getComponentType());
            var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.BACKPACK_STORAGE_HOTBAR);
            if(req.getInput()==null||req.getInput().length==0)hint="This station's upgrade has no material requirements defined.";
            else if(!station.bench().getWindows().isEmpty())hint="Close the station's other crafting windows before upgrading.";
            else if(player.getGameMode()!=GameMode.Creative&&!fetched(store,inventory,req.getInput()))hint="You need the upgrade materials in your inventory or the town warehouse.";
            else {
                upgradingBench=station.ref();upgradingTier=station.bench().getTierLevel();upgradingRequirement=req;started=System.nanoTime();
                hint="Upgrading the station. Closing this screen cancels without spending materials.";
            }
        }
        refresh(ref,store);
    }
    private void schedule(World world) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        plugin.scheduleOnWorld(world,()->{
            if(isDismissed())return;
            var ref=playerRef.getReference();if(ref==null||!ref.isValid()||ref.getStore().getExternalData().getWorld()!=world)return;
            var store=ref.getStore();var player=store.getComponent(ref,Player.getComponentType());
            if(player==null||player.getPageManager().getCustomPage()!=this)return;
            var station=station(ref,store);if(station==null){close();return;}
            if(upgradingBench!=null) {
                if(!upgradingBench.equals(station.ref())||station.bench()==null||station.bench().getTierLevel()!=upgradingTier
                    ||requirement(station)!=upgradingRequirement||!station.bench().getWindows().isEmpty()) {
                    upgradingBench=null;hint="The station changed. No materials were spent.";
                } else if((System.nanoTime()-started)/1e9>=upgradingRequirement.getTimeSeconds())finish(ref,store,station);
                refresh(ref,store);
            }
            if(!isDismissed())schedule(world);
        },250);
    }
    private void finish(Ref<EntityStore> ref,Store<EntityStore> store,Station station) {
        upgradingBench=null;
        var world=store.getExternalData().getWorld();var cs=world.getChunkStore().getStore();
        var sectionRef=world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x,pos.y,pos.z);
        var section=ChunkSectionBlockUtil.blockSectionAt(world,pos.x,pos.y,pos.z);
        if(station.info()==null||sectionRef==null||!sectionRef.isValid()||section==null){hint="The station is no longer available.";return;}
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.BACKPACK_STORAGE_HOTBAR);
        var result=StationUpgradeCommit.apply(station.bench(),upgradingTier,upgradingRequirement.getInput(),inventory,
            store.getComponent(ref,Player.getComponentType()).getGameMode()==GameMode.Creative);
        if(result!=StationUpgradeCommit.Result.UPGRADED) {
            hint=result==StationUpgradeCommit.Result.MISSING_MATERIALS
                ?"The required materials are no longer in your inventory.":"The station changed. No materials were spent.";
            return;
        }
        station.info().markNeedsSaving();
        BlockOperations.setBlockInteractionState(world.getChunkStore(),sectionRef,pos.x,pos.y,pos.z,
            BenchBlock.getBaseBlockType(station.block()),station.bench().getTierStateName(),true);
        var processing=cs.getComponent(station.ref(),ProcessingBenchBlock.getComponentType());
        if(processing!=null)processing.setupSlots(world,station.bench(),station.info(),pos.x,pos.y,pos.z,station.block(),section.getRotationIndex(pos.x,pos.y,pos.z));
        int sound=station.block().getBench().getBenchUpgradeCompletedSoundEventIndex();
        if(sound!=SoundEvent.EMPTY_ID)SoundUtil.playSoundEvent3d(sound,SoundCategory.SFX,pos.x+.5,pos.y+.5,pos.z+.5,store);
        hint="Station upgraded to tier "+(upgradingTier+1)+". Your task list is unchanged.";
    }
    private void refresh(Ref<EntityStore> ref,Store<EntityStore> store) {
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().build();
        String action;
    }
}
