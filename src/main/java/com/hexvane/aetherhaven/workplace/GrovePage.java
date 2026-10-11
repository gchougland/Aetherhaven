package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.mastery.CoreProductionRequirements;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.ui.ProductionStoragePage;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.function.Predicate;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;

/** One screen for a lumbermill's Rootstones, opened from a stone or from the mill's stores. */
public final class GrovePage extends AetherhavenInteractiveCustomUIPage<GrovePage.Data> {
    record Context(TownManager manager,TownRecord town,PlotInstance plot,PlotProductionState production,ProductionCatalog.Entry entry,World world) {}
    private static final int MAX_SAPLINGS=12,MAX_STOCK=10;
    private final UUID townId,plotId;
    /** The Rootstone the player used, when the page was opened from one. */
    private final Position block;
    private int stone=Integer.MIN_VALUE;
    private boolean appended,confirmUproot;
    private String hint;

    public GrovePage(PlayerRef player,UUID town,UUID plot,Position block) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);
        townId=town;plotId=plot;this.block=block;
    }

    /** The lumbermill that owns this stone, or else the first one with a stone to spare. */
    public static PlotInstance millFor(AetherhavenPlugin plugin,TownRecord town,Position block) {
        PlotInstance spare=null,any=null;
        for(var plot:town.getPlotInstances()) {
            if(plot.getState()!=PlotInstanceState.COMPLETE||!plugin.getConstructionCatalog().matchesGameplayConstruction(
                plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL))continue;
            var production=town.getOrCreatePlotProduction(plot.getPlotId());
            if(production.timber().stoneAt(block)>=0)return plot;
            if(any==null)any=plot;
            if(spare==null&&production.timber().freeStone(WorkplaceProductionUpgrades.stoneCount(production))>=0)spare=plot;
        }
        return spare!=null?spare:any;
    }
    public static Predicate<Position> bounds(TownRecord town) {
        return p->p.y()>=ChunkUtil.MIN_Y
            &&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z()));
    }
    private static String otherGroveIssue(Context c,TimberPlantingReceipt tree) {
        for(var plot:c.town().getPlotInstances()) {
            if(plot.getPlotId().equals(c.plot().getPlotId()))continue;
            var other=c.town().peekPlotProduction(plot.getPlotId());
            if(other!=null&&other.groveConflictsWith(tree))return "This tree would grow into another lumbermill's tree. Move the stones further apart.";
        }
        return null;
    }
    private Context current(Ref<EntityStore> ref,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();var pr=store.getComponent(ref,PlayerRef.getComponentType());
        var pos=store.getComponent(ref,TransformComponent.getComponentType());if(plugin==null||pr==null||pos==null)return null;
        var world=store.getExternalData().getWorld();
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.getTown(townId);var p=pos.getPosition();
        if(town==null||!town.hasMemberOrOwner(pr.getUuid())||!town.playerCanManageConstructions(pr.getUuid())
            ||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),ChunkUtil.chunkCoordinate((int)Math.floor(p.z))))return null;
        var plot=town.findPlotById(plotId);
        if(plot==null||plot.getState()!=PlotInstanceState.COMPLETE
            ||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL))return null;
        var production=town.getOrCreatePlotProduction(plotId);
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL,production);
        return entry==null||entry.catalogSize()<=0?null:new Context(manager,town,plot,production,entry,world);
    }
    /** True while the stone the player used is still in the world and not yet bound to a tree. */
    private boolean pending(Context c) {
        if(block==null||c.production().timber().stoneAt(block)>=0)return false;
        var type=ChunkSectionBlockUtil.blockType(c.world(),block.x(),block.y(),block.z());
        return type!=null&&Rootstone.is(type.getId());
    }
    private static List<NativeTimberGrowth.Species> carried(Ref<EntityStore> ref,Store<EntityStore> store) {
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);var result=new ArrayList<NativeTimberGrowth.Species>();
        if(inv!=null)for(var species:NativeTimberGrowth.species()) {
            if(result.size()>=MAX_SAPLINGS)break;
            if(com.hexvane.aetherhaven.inventory.InventoryMaterials.count(inv,species.sapling())>0)result.add(species);
        }
        return result;
    }
    /** "Wood_Beech_Trunk" reads as "Beech" on a button. */
    private static String wood(String item) {
        return item.replace("Wood_","").replace("_Trunk","").replace('_',' ');
    }
    private static Message name(String item) {
        var asset=Item.getAssetMap().getAsset(item);
        return asset!=null&&asset.getTranslationKey()!=null&&!asset.getTranslationKey().isBlank()?Message.translation(asset.getTranslationKey()):Message.raw(item);
    }

    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store) {
        var c=current(ref,store);if(c==null){close();return;}
        var timber=c.production().timber();int allowed=WorkplaceProductionUpgrades.stoneCount(c.production());
        boolean pending=pending(c);int free=timber.freeStone(allowed);
        if(stone==Integer.MIN_VALUE)stone=block!=null?timber.stoneAt(block):timber.groves().keySet().stream().findFirst().orElse(-1);
        if(stone>=0&&!timber.groves().containsKey(stone))stone=-1;
        if(!appended){b.append("Aetherhaven/GrovePage.ui");appended=true;
            for(int i=0;i<TimberProductionState.MAX_STONES;i++) {
                b.append("#Stones","Aetherhaven/GroveStone.ui");
                e.addEventBinding(CustomUIEventBindingType.Activating,"#Stones["+i+"]",EventData.of("Action","Stone").append("Key",Integer.toString(i)),false);
            }
            for(String action:List.of("Bless","Uproot","Refresh","Collect","Fibre","Char"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
        }
        b.set("#Summary.Text","ROOTSTONES "+timber.groves().size()+" / "+allowed);
        for(int i=0;i<TimberProductionState.MAX_STONES;i++) {
            String path="#Stones["+i+"]";var bound=timber.groves().get(i);boolean locked=i>=allowed&&bound==null;
            boolean fresh=pending&&i==free;
            b.set(path+".Disabled",locked||bound==null&&!fresh);b.set(path+" #Selected.Visible",bound!=null?i==stone:fresh&&stone<0);
            b.set(path+" #LockedShade.Visible",locked);b.set(path+" #Lock.Visible",locked);b.set(path+" #Icon.Visible",!locked&&(bound!=null||fresh));
            b.set(path+" #Icon.ItemId",bound!=null?bound.sapling():Rootstone.BLOCK);
            b.set(path+" #Number.Text","STONE "+(i+1));
            b.set(path+".TooltipText",locked?"Lumbermill upgrades let Seren tend more Rootstones.":bound==null?(fresh?"The Rootstone you are using.":"Place another Rootstone in town to use this."):"See this Rootstone's tree.");
            b.set(path+" #Status.Text",locked?"Locked":bound==null?(fresh?"New stone":"Empty"):CoreProductionRequirements.issueForKind(c.town(),"logger",bound.output())!=null?"Needs training"
                :timber.trees().containsKey(i)?(timber.blessed(i)?"Elder tree":"Growing"):"Waiting");
        }
        var plan=stone>=0?timber.groves().get(stone):null;
        b.set("#Growth.Value",0f);b.set("#Growth.Visible",plan!=null);
        if(plan!=null) {
            b.set("#TreeIcon.ItemId",plan.sapling());
            b.set("#StoneTitle.Text","ROOTSTONE "+(stone+1)+(timber.blessed(stone)?"  |  ELDER BLESSING":""));
            b.set("#TreeName.TextSpans",name(plan.sapling()));
            b.set("#StoneStatus.Text",stoneStatus(c,stone,b));
        }else {
            b.set("#TreeIcon.ItemId",Rootstone.BLOCK);
            b.set("#StoneTitle.Text",pending?"NEW ROOTSTONE":"NO ROOTSTONE CHOSEN");
            b.set("#TreeName.TextSpans",Message.raw(pending?"Choose its tree":"Place a Rootstone"));
            b.set("#StoneStatus.Text",pending?(free<0?"Seren can tend "+allowed+" Rootstones for this lumbermill. Lumbermill upgrades let her tend more."
                :"Pick one of the saplings you carry. It takes one sapling, and the tree then regrows here for free.")
                :"Craft a Rootstone, place it on open ground anywhere in town, then use it to choose a tree.");
        }
        var saplings=pending&&plan==null&&free>=0?carried(ref,store):List.<NativeTimberGrowth.Species>of();
        boolean choosing=pending&&plan==null&&free>=0;
        b.set("#SaplingHint.Visible",choosing);b.set("#Saplings.Visible",choosing);
        // The window grows only while there are saplings to choose from, so it never shows an empty band.
        var window=new com.hypixel.hytale.server.core.ui.Anchor();
        window.setWidth(com.hypixel.hytale.server.core.ui.Value.of(900));window.setHeight(com.hypixel.hytale.server.core.ui.Value.of(choosing?796:700));
        b.setObject("#GroveWindow.Anchor",window);
        b.set("#SaplingHint.Text",saplings.isEmpty()?"CARRY A TREE SAPLING TO CHOOSE THIS STONE'S TREE":"CHOOSE A SAPLING YOU CARRY");
        b.clear("#Saplings");
        for(int i=0;i<saplings.size();i++) {
            var species=saplings.get(i);String path="#Saplings["+i+"]";b.append("#Saplings","Aetherhaven/GroveSapling.ui");
            b.set(path+" #Icon.ItemId",species.sapling());
            String need=CoreProductionRequirements.issueForKind(c.town(),"logger",species.output());
            b.set(path+".TooltipTextSpans",need==null?name(species.sapling()):Message.join(name(species.sapling()),Message.raw(". Seren needs more training first. "+need)));
            e.addEventBinding(CustomUIEventBindingType.Activating,path,EventData.of("Action","Sapling").append("Key",species.output()),false);
        }
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
        boolean canBless=plan!=null&&!timber.blessed(stone)&&inv!=null
            &&com.hexvane.aetherhaven.inventory.InventoryMaterials.count(inv,TimberProductionState.BLESSING_ITEM)>=TimberProductionState.BLESSING_COST;
        b.set("#Bless.Visible",plan!=null);b.set("#Bless.Disabled",!canBless);
        b.set("#Bless.Text",plan!=null&&timber.blessed(stone)?"Blessed. Next harvest gives double wood":"Bless with "+TimberProductionState.BLESSING_COST+" Life Essence");
        b.set("#Uproot.Visible",plan!=null);b.set("#Uproot.Text",confirmUproot?"Confirm uprooting":"Uproot this tree");
        drawStock(b,e,c);
        b.set("#Fibre.Text","Gather fibre: "+(timber.gathersFibre()?"On":"Off"));
        b.set("#Char.Text","Char spare logs: "+(timber.charWood()==null?"Off":wood(timber.charWood())));
        b.set("#GroveNote.Text",hint!=null?hint:timber.adopted()?waiting(c)
            :"This lumbermill still makes wood the old way. That stops for good once its first Rootstone has a tree, and Seren works the grove instead.");
    }
    private static String waiting(Context c) {
        var timber=c.production().timber();
        if(timber.cargo()==null)return "Seren works the grove during her work hours while someone from town is nearby.";
        int count=timber.waitingProducts().values().stream().mapToInt(Integer::intValue).sum();
        return count+" items from the last tree are on their way to the mill stores.";
    }
    private String stoneStatus(Context c,int id,UICommandBuilder b) {
        var timber=c.production().timber();var plan=timber.groves().get(id);var planted=timber.trees().get(id);
        String need=CoreProductionRequirements.issueForKind(c.town(),"logger",plan.output());
        if(need!=null)return "Seren is not ready for this tree yet. "+need;
        if(!NativeTimberProducts.hasRoom(c.production(),c.entry(),planted!=null?planted.tree():plan))
            return "The mill has no room left for anything this tree gives. Collect something so Seren can carry on here.";
        if(planted!=null) {
            double grown=NativeTimberTending.progress(c.world(),planted.tree());
            if(grown>=0){b.set("#Growth.Value",(float)grown);return "Growing. Seren sings it taller each time she visits.";}
            var ready=NativeTimberHarvest.inspect(c.world(),planted,bounds(c.town()));
            if(ready.ready()){b.set("#Growth.Value",1f);return timber.chopping(id)>0?"Seren is felling this tree.":"Fully grown. Seren will fell it soon.";}
            return ready.issue();
        }
        if(id>=WorkplaceProductionUpgrades.stoneCount(c.production()))return "This lumbermill cannot tend this Rootstone right now.";
        var prepared=NativeTimberPlanting.prepare(c.world(),c.production(),id,plan,bounds(c.town()));
        return prepared.ready()?"Waiting for Seren to plant it.":prepared.issue();
    }
    private void drawStock(UICommandBuilder b,UIEventBuilder e,Context c) {
        b.clear("#Stock");int shown=0;boolean any=false;
        var stored=new ArrayList<>(c.production().getAmounts().entrySet());
        stored.sort(Map.Entry.<String,Long>comparingByValue().reversed());
        for(var row:stored) {
            if(row.getValue()<=0||Item.getAssetMap().getAsset(row.getKey())==null)continue;
            any|=row.getValue()>c.town().getWarehouse().reservedProduction(plotId,row.getKey());
            if(shown>=MAX_STOCK)continue;
            b.append("#Stock","Aetherhaven/GroveStock.ui");String path="#Stock["+(shown++)+"] #Cell";
            b.set(path+" #Icon.ItemId",row.getKey());b.set(path+" #Quantity.Text",Long.toString(row.getValue()));
            b.set(path+".TooltipTextSpans",Message.join(name(row.getKey()),Message.raw(". "+row.getValue()+" of "
                +WorkplaceProductionUpgrades.effectiveMaxStorage(c.production(),c.entry(),row.getKey())+" stored. Select to take them.")));
            e.addEventBinding(CustomUIEventBindingType.Activating,path,EventData.of("Action","Take").append("Key",row.getKey()),false);
        }
        b.set("#Stock.Visible",shown>0);b.set("#StockEmpty.Visible",shown==0);
        b.set("#Collect.Disabled",!any);
    }

    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var c=current(ref,store);if(c==null||data.action==null){close();return;}
        var timber=c.production().timber();int allowed=WorkplaceProductionUpgrades.stoneCount(c.production());
        boolean confirming=confirmUproot;confirmUproot=false;hint=null;
        switch(data.action) {
            case "Stone" -> {
                try{int chosen=Integer.parseInt(data.key);if(timber.groves().containsKey(chosen))stone=chosen;
                    else if(pending(c)&&chosen==timber.freeStone(allowed))stone=-1;}catch(NumberFormatException ignored){}
            }
            case "Sapling" -> bind(ref,store,c,data.key);
            case "Bless" -> {
                var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
                String issue=timber.blessingIssue(stone);
                if(issue==null&&(inv==null||com.hexvane.aetherhaven.inventory.InventoryMaterials.count(inv,TimberProductionState.BLESSING_ITEM)<TimberProductionState.BLESSING_COST))
                    issue="You need "+TimberProductionState.BLESSING_COST+" Life Essence to bless a Rootstone.";
                if(issue==null&&!inv.removeItemStack(new ItemStack(TimberProductionState.BLESSING_ITEM,TimberProductionState.BLESSING_COST)).succeeded())
                    issue="The Life Essence could not be taken from your pack.";
                if(issue==null){timber.bless(stone);c.manager().updateTown(c.town());hint="The Rootstone hums. Its next tree will give double wood.";}else hint=issue;
            }
            case "Uproot" -> {
                var plan=timber.groves().get(stone);
                if(plan==null)break;
                if(!confirming){confirmUproot=true;hint=timber.trees().containsKey(stone)
                    ?"Uprooting frees this Rootstone. The tree standing here stays as it is and becomes yours to chop."
                    :"Uprooting frees this Rootstone and returns its sapling.";break;}
                boolean planted=timber.trees().containsKey(stone);
                if(timber.uproot(stone)) {
                    if(!planted){var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inv!=null)inv.addItemStack(new ItemStack(plan.sapling(),1));}
                    c.manager().updateTown(c.town());stone=Integer.MIN_VALUE;
                    hint="Rootstone freed. Use it again to choose a new tree, or pick it up and move it.";
                }
            }
            case "Fibre" -> {timber.setGathersFibre(!timber.gathersFibre());c.manager().updateTown(c.town());
                hint=timber.gathersFibre()?"Seren will gather fibre from the leaves of each tree she fells.":"Seren will leave the fibre behind.";}
            case "Char" -> {
                // Each press moves to the next wood in the stores, then back to off.
                var woods=new ArrayList<String>();
                for(var stored:c.production().getAmounts().entrySet())
                    if(stored.getValue()>0&&!TimberCharring.OUTPUT.equals(stored.getKey())&&NativeTimberGrowth.species(stored.getKey())!=null)woods.add(stored.getKey());
                Collections.sort(woods);int at=woods.indexOf(timber.charWood());
                timber.setCharWood(at+1<woods.size()?woods.get(at+1):null);c.manager().updateTown(c.town());
                String need=CoreProductionRequirements.issueForKind(c.town(),"logger",TimberCharring.OUTPUT);
                hint=timber.charWood()==null?(woods.isEmpty()?"Seren needs some wood in the mill stores before she can char any.":"Seren will keep spare logs as they are.")
                    :need!=null?"Seren needs more training before she can char wood. "+need
                    :"Seren will char "+TimberCharring.LOG_COST+" spare "+wood(timber.charWood())+" logs into one Burnt log. Keep a lit campfire within two blocks of the mill stores. Press again for another wood.";
            }
            case "Collect" -> collect(ref,store,c,null);
            case "Take" -> collect(ref,store,c,data.key);
            default -> {}
        }
        var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);
    }
    private void bind(Ref<EntityStore> ref,Store<EntityStore> store,Context c,String output) {
        var timber=c.production().timber();var species=NativeTimberGrowth.species(output);
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
        if(species==null||inv==null||!pending(c)){hint="This Rootstone cannot take that sapling right now.";return;}
        int free=timber.freeStone(WorkplaceProductionUpgrades.stoneCount(c.production()));
        if(free<0){hint="Seren cannot tend another Rootstone for this lumbermill yet.";return;}
        if(com.hexvane.aetherhaven.inventory.InventoryMaterials.count(inv,species.sapling())<1){hint="You no longer carry that sapling.";return;}
        var root=new Position(block.x(),block.y()+1,block.z());
        var captured=NativeTimberGrowth.capture(output,root,0,bounds(c.town()));
        String issue=captured.ready()?otherGroveIssue(c,captured.receipt()):captured.issue();
        if(issue==null)issue=timber.plan(free,WorkplaceProductionUpgrades.stoneCount(c.production()),timber.layoutRevision(),captured.receipt(),
            bounds(c.town()),p->NativeTimberPlanting.observe(c.world(),p));
        if(issue!=null){hint=issue;return;}
        if(!inv.removeItemStack(new ItemStack(species.sapling(),1)).succeeded()){timber.uproot(free);hint="The sapling could not be taken from your pack.";return;}
        c.manager().updateTown(c.town());stone=free;
        String need=CoreProductionRequirements.issueForKind(c.town(),"logger",output);
        hint=need!=null?"The Rootstone accepts the sapling, but Seren needs more training before she can grow it. "+need
            :"The Rootstone accepts the sapling. Seren will plant it on her next round.";
    }
    private void collect(Ref<EntityStore> ref,Store<EntityStore> store,Context c,String only) {
        var player=store.getComponent(ref,Player.getComponentType());var pr=store.getComponent(ref,PlayerRef.getComponentType());
        if(player==null||pr==null)return;
        var result=new ProductionWithdrawal.ResultSink();
        for(var row:new LinkedHashMap<>(c.production().getAmounts()).entrySet()) {
            if(row.getValue()<=0||Item.getAssetMap().getAsset(row.getKey())==null||only!=null&&!only.equals(row.getKey()))continue;
            long spare=Math.max(0,row.getValue()-c.town().getWarehouse().reservedProduction(plotId,row.getKey()));
            ProductionWithdrawal.withdrawToPlayer(ref,store,player,c.production(),c.entry(),row.getKey(),spare,result);
        }
        c.manager().updateTown(c.town());
        ProductionStoragePage.notifyWithdrawResult(pr,result);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Key",Codec.STRING),(d,v)->d.key=v,d->d.key).add().build();
        String action,key;
    }
}
