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
    private final GroveSelection selection;
    /** Both targets are refreshed together from the selected physical Rootstone. */
    private Position block;
    private int stone=-1;
    private boolean appended,confirmUproot;
    private String hint;
    private TimberPlantingReceipt previewTree;

    public GrovePage(PlayerRef player,UUID town,UUID plot,Position block) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);
        townId=town;plotId=plot;selection=new GroveSelection(block);
    }

    /** No saved production or bound Rootstone is needed to open a mill's setup screen. */
    public static GrovePage forMill(com.hexvane.aetherhaven.construction.ConstructionCatalog catalog,
            PlayerRef player,UUID town,PlotInstance plot) {
        return plot!=null&&catalog.matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL)
            ?new GrovePage(player,town,plot.getPlotId(),null):null;
    }

    public static boolean redirectToMill(Ref<EntityStore> ref,Store<EntityStore> store,PlayerRef playerRef,
            com.hexvane.aetherhaven.construction.ConstructionCatalog catalog,UUID town,PlotInstance plot) {
        var page=forMill(catalog,playerRef,town,plot);if(page==null)return false;
        store.getExternalData().getWorld().execute(()->{
            if(ref.isValid()) {var player=store.getComponent(ref,Player.getComponentType());
                if(player!=null)player.getPageManager().openCustomPage(ref,store,page);}
        });
        return true;
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
    private void syncSelection(Context c) {
        var positions=new TreeMap<Integer,Position>();
        c.production().timber().groves().forEach((id,tree)->{
            var root=tree.root();positions.put(id,new Position(root.x(),root.y()-1,root.z()));
        });
        selection.refresh(positions);
        applySelection();
    }
    private void applySelection() {
        if(!Objects.equals(block,selection.block())||stone!=selection.slot()) {
            previewTree=null;confirmUproot=false;selection.cancelConfirmation();
        }
        block=selection.block();stone=selection.slot();
    }
    /** True while the selected stone is still in the world and not yet bound to a tree. */
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
        syncSelection(c);
        boolean pending=pending(c);int free=timber.freeStone(allowed);
        if(previewTree==null&&pending)previewTree=RootstoneGrowingSpaceOverlay.selected(c.world(),store.getComponent(ref,PlayerRef.getComponentType()).getUuid(),plotId,block);
        if(!appended){b.append("Aetherhaven/GrovePage.ui");appended=true;
            for(int i=0;i<TimberProductionState.MAX_STONES;i++) {
                b.append("#Stones","Aetherhaven/GroveStone.ui");
                e.addEventBinding(CustomUIEventBindingType.Activating,"#Stones["+i+"]",EventData.of("Action","Stone").append("Key",Integer.toString(i)),false);
            }
            for(String action:List.of("Previous","Next","Bind","Bless","Uproot","Preview","Refresh","Collect","Fibre","Char"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
        }
        b.set("#Previous.Disabled",selection.count()<2);b.set("#Next.Disabled",selection.count()<2);
        b.set("#Selection.Text",block==null?"No Rootstone selected":"Showing "+selection.number()+" of "+selection.count()+"   |   X: "+block.x()+"  Y: "+block.y()+"  Z: "+block.z());
        b.set("#Summary.Text","ROOTSTONES "+timber.groves().size()+" / "+allowed);
        for(int i=0;i<TimberProductionState.MAX_STONES;i++) {
            String path="#Stones["+i+"]";var bound=timber.groves().get(i);boolean locked=i>=allowed&&bound==null;
            boolean fresh=pending&&i==free;
            b.set(path+".Disabled",locked||bound==null&&!fresh);b.set(path+" #Selected.Visible",bound!=null?i==stone:fresh&&stone<0);
            b.set(path+" #LockedShade.Visible",locked);b.set(path+" #Lock.Visible",locked);b.set(path+" #Icon.Visible",!locked&&(bound!=null||fresh));
            b.set(path+" #Icon.ItemId",bound!=null?bound.sapling():Rootstone.BLOCK);
            b.set(path+" #Number.Text","STONE "+(i+1));
            b.set(path+".TooltipText",locked?"Lumbermill upgrades let Seren tend more Rootstones.":bound==null?(fresh?"The selected Rootstone is ready for a sapling.":"Place another Rootstone in town to use this."):"Select this Rootstone to manage its tree from here.");
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
            b.set("#TreeIcon.ItemId",previewTree!=null?previewTree.sapling():Rootstone.BLOCK);
            b.set("#StoneTitle.Text",pending?"NEW ROOTSTONE":"NO ROOTSTONE CHOSEN");
            b.set("#TreeName.TextSpans",pending&&previewTree!=null?name(previewTree.sapling()):Message.raw(pending?"Choose its tree":"Place a Rootstone"));
            b.set("#StoneStatus.Text",pending?(free<0?"Seren can tend "+allowed+" Rootstones for this lumbermill. Lumbermill upgrades let her tend more."
                :"Choose a sapling below, check its growing space, then press Bind this tree. It takes one sapling and regrows here for free.")
                :block!=null?"This Rootstone is missing or its area is not loaded. Move closer and press Refresh, or choose another Rootstone."
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
        b.set("#Bind.Visible",choosing);b.set("#Bind.Disabled",previewTree==null);
        b.set("#Bless.Text",plan!=null&&timber.blessed(stone)?"Blessed: double wood":"Bless: "+TimberProductionState.BLESSING_COST+" Life Essence");
        b.set("#Uproot.Visible",plan!=null);b.set("#Uproot.Text",confirmUproot?"Confirm uprooting":"Uproot this tree");
        var pr=store.getComponent(ref,PlayerRef.getComponentType());
        b.set("#Preview.Disabled",plan==null&&!pending);
        b.set("#Preview.Text",RootstoneGrowingSpaceOverlay.enabled(c.world(),pr.getUuid())?"Hide growing space":"Show growing space");
        b.set("#Preview.TooltipText","Close this window to see the cyan growing-space outline, yellow Rootstone and red blocked spaces. Markers update as you clear blocks. Large obstructions show the nearest 256 blocks first.");
        drawStock(b,e,c);
        b.set("#Fibre.Text","Gather fibre: "+(timber.gathersFibre()?"On":"Off"));
        b.set("#Char.Text","Char spare logs: "+(timber.charWood()==null?"Off":wood(timber.charWood())));
        b.set("#GroveNote.Text",hint!=null?hint:!timber.groves().isEmpty()?waiting(c)
            :"Place a Rootstone and bind a sapling to begin. The mill only receives wood from trees Seren chops. No separate wood unlocks are needed.");
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
        var timber=c.production().timber();
        syncSelection(c);
        var selectedPlan=timber.groves().get(stone);
        boolean sameTree=selection.takeUprootConfirmation(selectedPlan==null?null:selectedPlan.id());
        boolean confirming=confirmUproot&&sameTree;confirmUproot=false;hint=null;
        switch(data.action) {
            case "Stone" -> {
                try{selection.selectSlot(Integer.parseInt(data.key));applySelection();}catch(NumberFormatException ignored){}
                previewTree=null;refreshPreview(ref,store,c);
            }
            case "Previous", "Next" -> {
                selection.move("Next".equals(data.action)?1:-1);applySelection();
                previewTree=null;refreshPreview(ref,store,c);
            }
            case "Sapling" -> {
                if(!pending(c)||NativeTimberGrowth.species(data.key)==null)break;
                var captured=NativeTimberGrowth.capture(data.key,new Position(block.x(),block.y()+1,block.z()),0,p->true);
                if(captured.ready()) {
                    previewTree=captured.receipt();refreshPreview(ref,store,c);
                    hint="Selected "+wood(data.key)+". Show growing space to check for room, then press Bind this tree to use one sapling.";
                }else hint=captured.issue();
            }
            case "Bind" -> {if(previewTree!=null)bind(ref,store,c,previewTree.output());}
            case "Preview" -> {
                var pr=store.getComponent(ref,PlayerRef.getComponentType());
                if(RootstoneGrowingSpaceOverlay.enabled(c.world(),pr.getUuid()))RootstoneGrowingSpaceOverlay.hide(c.world(),pr.getUuid());
                else {
                    var selected=stone>=0?timber.groves().get(stone):previewTree;
                    if(selected==null&&pending(c)) {
                        var saplings=carried(ref,store);
                        if(!saplings.isEmpty()) {
                            var captured=NativeTimberGrowth.capture(saplings.getFirst().output(),new Position(block.x(),block.y()+1,block.z()),0,p->true);
                            if(captured.ready())selected=previewTree=captured.receipt();else hint=captured.issue();
                        }
                    }
                    if(selected!=null) {
                        RootstoneGrowingSpaceOverlay.show(c.world(),pr.getUuid(),townId,plotId,selected);
                        hint="Close this window to see the space for "+wood(selected.output())+". Cyan: growing space. Yellow: Rootstone. Red: blocked spaces. Choose a sapling to preview another tree.";
                    }else if(hint==null)hint="Carry a tree sapling, then choose it to see the growing space it needs.";
                }
            }
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
                if(!confirming){confirmUproot=true;selection.confirmUproot(plan.id());hint=timber.trees().containsKey(stone)
                    ?"Uprooting frees this Rootstone. The tree standing here stays as it is and becomes yours to chop."
                    :"Uprooting frees this Rootstone and returns its sapling.";break;}
                boolean planted=timber.trees().containsKey(stone);
                String uprootIssue=NativeTimberUprooting.uproot(c.world(),timber,stone);
                if(uprootIssue==null) {
                    if(!planted){var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inv!=null)inv.addItemStack(new ItemStack(plan.sapling(),1));}
                    selection.rememberSelected();syncSelection(c);
                    c.manager().updateTown(c.town());
                    hint="Rootstone freed. Choose a sapling below to bind a new tree here, or pick up the stone to move it.";
                    RootstoneGrowingSpaceOverlay.hide(c.world(),store.getComponent(ref,PlayerRef.getComponentType()).getUuid());
                }else hint=uprootIssue;
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
                    :"Seren chars "+TimberCharring.LOG_COST+" "+wood(timber.charWood())+" logs into one Burnt log. Place a Campfire inside the mill, within two blocks of storage. The logs supply the fuel; no lighting needed.";
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
        // Capture the geometry even across the town edge so a refused binding can show exactly what is blocked.
        var captured=NativeTimberGrowth.capture(output,root,0,p->true);
        if(captured.ready()){previewTree=captured.receipt();refreshPreview(ref,store,c);}
        String issue=captured.ready()?otherGroveIssue(c,captured.receipt()):captured.issue();
        if(issue==null)issue=timber.plan(free,WorkplaceProductionUpgrades.stoneCount(c.production()),timber.layoutRevision(),captured.receipt(),
            bounds(c.town()),p->NativeTimberPlanting.observe(c.world(),p));
        if(issue!=null){hint=issue;return;}
        if(!inv.removeItemStack(new ItemStack(species.sapling(),1)).succeeded()){timber.uproot(free);hint="The sapling could not be taken from your pack.";return;}
        c.manager().updateTown(c.town());syncSelection(c);
        refreshPreview(ref,store,c);
        String need=CoreProductionRequirements.issueForKind(c.town(),"logger",output);
        hint=need!=null?"The Rootstone accepts the sapling, but Seren needs more training before she can grow it. "+need
            :"The Rootstone accepts the sapling. Seren will plant it on her next round.";
    }
    private void refreshPreview(Ref<EntityStore> ref,Store<EntityStore> store,Context c) {
        var pr=store.getComponent(ref,PlayerRef.getComponentType());
        if(!RootstoneGrowingSpaceOverlay.enabled(c.world(),pr.getUuid()))return;
        var selected=stone>=0?c.production().timber().groves().get(stone):previewTree;
        if(selected!=null)RootstoneGrowingSpaceOverlay.show(c.world(),pr.getUuid(),townId,plotId,selected);
        else RootstoneGrowingSpaceOverlay.hide(c.world(),pr.getUuid());
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
