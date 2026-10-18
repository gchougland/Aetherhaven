package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Separate dialogue shop and barn management views with disjoint allowed actions. */
public final class RancherHerdPage extends AetherhavenInteractiveCustomUIPage<RancherHerdPage.Data> {
    private record Context(TownManager manager,TownRecord town,PlotInstance plot,PlotProductionState production) {}
    private final UUID townId,plotId;
    private final boolean shopOnly;
    private int choosingPen=-1;
    private boolean appended;private String tab="Animals",hint="Choose an owned animal for each unlocked pen. Pet animals in the world once each day.";
    private int page;private long revision;private int slots;
    public RancherHerdPage(PlayerRef player,UUID town,UUID plot){this(player,town,plot,false);}
    public RancherHerdPage(PlayerRef player,UUID town,UUID plot,boolean shop){super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;plotId=plot;shopOnly=shop;if(shop){tab="Shop";hint="Buy animals here, then choose their active pens at the barn.";}}
    private RancherHerdPage(PlayerRef player,UUID town,UUID plot,int pen){this(player,town,plot,false);choosingPen=pen;hint="Choose an animal for pen "+(pen+1)+". Choosing an animal in another pen swaps the occupants.";}
    private Context current(Ref<EntityStore> ref,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);var town=manager.getTown(townId);
        var position=store.getComponent(ref,TransformComponent.getComponentType());
        if(town==null||position==null||!town.hasMemberOrOwner(playerRef.getUuid())||!town.playerCanManageConstructions(playerRef.getUuid()))return null;
        var p=position.getPosition();
        if(!TownTerritoryClaims.contains(town,com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.z))))return null;
        var plot=town.findPlotById(plotId);if(!NativeHerdService.barn(plugin,plot))return null;
        return new Context(manager,town,plot,town.getOrCreatePlotProduction(plotId));
    }
    private EventData event(String action,String key){return new EventData().append("Action",action).append("Key",key);}
    /** The amount the courier button moves to from this one. The button carries it, so a press cannot skip a step. */
    static int nextCourierTarget(int current){return switch(current){case 0->10;case 10->25;case 25->50;case 50->75;default->0;};}
    private void bind(UIEventBuilder e,String path,String action,String key){e.addEventBinding(CustomUIEventBindingType.Activating,path,event(action,key),false);}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store) {
        var c=current(ref,store);if(c==null){close();return;}
        boolean cards=shopOnly||choosingPen>=0;
        if(!appended){b.append(cards?"Aetherhaven/RancherAnimalShopPage.ui":"Aetherhaven/RancherHerdPage.ui");appended=true;}
        var state=c.production();var herd=state.herd();slots=WorkplaceProductionUpgrades.slotCount(state);
        long day=Math.floorDiv(NativeHerdService.minute(store),1440);int mastery=CoreVillagerMasteryService.level(c.town(),"rancher");
        b.set("#Summary.Text",herd.animals().size()+" owned animals   |   "+slots+" active pens   |   Rancher Mastery "+mastery);
        for(String action:cards?List.of("Previous","Next","Refresh"):List.of("Animals","Food","Storage","Previous","Next","Refresh"))bind(e,"#"+action,action,"");
        if(!cards)bind(e,"#Courier","Courier",Integer.toString(nextCourierTarget(herd.courierFeedTarget())));
        if(choosingPen>=0){b.set("#ScreenTitle.Text","CHOOSE AN ANIMAL FOR PEN "+(choosingPen+1));b.set("#Instructions.Text","Select a card below. Animals keep their name and hearts when resting or changing pens.");b.set("#Return.Visible",true);bind(e,"#Return","Return","");}
        if(!cards){
        b.set("#Courier.Text",herd.courierFeedTarget()==0?"Courier food: Off":"Keep "+herd.courierFeedTarget()+" of each food");
        b.clear("#Slots");
        var pens=BarnPenCompatibility.resolve(store.getExternalData().getWorld(),AetherhavenPlugin.get(),c.plot(),herd);
        for(int i=0;i<5;i++) {
            b.append("#Slots","Aetherhaven/BarnAnimalSlot.ui");String row="#Slots["+i+"]";var animal=herd.slot(i);
            boolean locked=i>=slots;var species=animal==null?null:BarnAnimalCatalog.get(animal.species());
            b.set(row+" #Title.Text","PEN "+(i+1));b.set(row+" #Lock.Visible",locked);b.set(row+" #Picture.Visible",!locked&&species!=null);
            if(species!=null){b.set(row+" #Picture.AssetPath",species.icon());b.set(row+" #FoodIcon.ItemId",species.food());}
            b.set(row+" #FoodIcon.Visible",!locked&&species!=null);
            b.set(row+".Background",Value.ref("Aetherhaven/TownUiTheme.ui",locked?"BarnCardLocked":"BarnCard"));
            b.set(row+" #Name.Text",locked?"Building upgrade needed":animal==null?"Empty pen":animal.name());
            b.set(row+" #Status.Text",locked?"Ask the Rancher about upgrades":!pens.containsKey(i)?"Needs a clear 3 by 3 floor, 4 blocks high":animal==null?pens.get(i).automatic()?"Automatic pen ready; choose an animal":"Choose an owned animal":animal.fed(day)?"Fed today":"Waiting for food");
            // Emptying names the animal that was drawn in the pen, so it can never send another one to rest.
            for(String action:List.of("ChooseAnimal","Empty")){b.set(row+" #"+action+".Disabled",locked||herd.animals().isEmpty());
                bind(e,row+" #"+action,action,"Empty".equals(action)?i+"|"+(animal==null?"":animal.id()):Integer.toString(i));}
        }
        }
        List<?> rows=switch(tab) {
            case "Shop"->BarnAnimalCatalog.all();
            case "Food"->{var foods=new LinkedHashSet<String>();herd.animals().forEach(a->{var species=BarnAnimalCatalog.get(a.species());if(species!=null)foods.add(species.food());});foods.addAll(herd.foods().keySet());yield foods.stream().sorted().toList();}
            case "Storage"->BarnAnimalCatalog.storageItems(state);
            default->herd.animals();
        };
        int pageSize=cards?8:4;
        page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/pageSize));int start=page*pageSize,end=Math.min(rows.size(),start+pageSize);
        b.set("#Page.Text",tab+"   "+(rows.isEmpty()?0:start+1)+" to "+end+" of "+rows.size());
        b.set("#Previous.Disabled",page==0);b.set("#Next.Disabled",end>=rows.size());
        b.clear("#Rows");
        for(int i=start;i<end;i++) {
            b.append("#Rows",cards?"Aetherhaven/BarnAnimalCard.ui":"Aetherhaven/BarnAnimalRow.ui");String row="#Rows["+(i-start)+"]";Object value=rows.get(i);
            if(cards){var anchor=new Anchor();anchor.setLeft(Value.of((i-start)%4*257));anchor.setTop(Value.of((i-start)/4*238));anchor.setWidth(Value.of(245));anchor.setHeight(Value.of(230));b.setObject(row+".Anchor",anchor);}
            boolean owned=value instanceof HerdProductionState.Animal,shop=value instanceof BarnAnimalCatalog.Species;
            var animal=owned?(HerdProductionState.Animal)value:null;
            var species=shop?(BarnAnimalCatalog.Species)value:owned?BarnAnimalCatalog.get(animal.species()):null;
            b.set(row+" #Portrait.Visible",owned||shop);
            if(!cards){b.set(row+" #AnimalPicture.Visible",owned||shop);b.set(row+" #Item.Visible",!owned&&!shop);b.set(row+" #RenameGroup.Visible",owned);b.set(row+" #Secondary.Visible","Food".equals(tab));b.set(row+" #Primary.Visible",!owned);}
            if(species!=null) {
                b.set(row+" #Portrait.AssetPath",species.icon());
                b.set(row+" #FoodIcon.Visible",true);b.set(row+" #FoodIcon.ItemId",species.food());
                b.set(row+" #FoodIcon.TextTooltipStyle",Value.ref("Common.ui","DefaultTextTooltipStyle"));b.set(row+" #FoodIcon.TooltipText","Eats "+foodName(species.food())+" daily");
                b.set(row+" #Title.Text",cards?(shop?species.id():animal.name()):animal.name()+"  |  "+species.id());
                String food=foodName(species.food())+(species.farmingTier()>0?" (Farming Bench tier "+species.farmingTier()+")":"");
                b.set(row+" #Detail.Text",shop?species.rarity().label+" | Eats "+foodName(species.food()):cards?species.id()+" | "+animal.hearts()+" / 5 hearts":"Hearts "+animal.hearts()+" / 5  |  "+animal.friendship()+" / 500 affection  |  "+(animal.petted(day)?"Pet today":"Pet in its pen"));
                b.set(row+" #Info.Text",shop?"Mastery "+species.rarity().mastery+"  |  "+species.rarity().gold+" gold":(herd.assignedSlot(animal.id())<0?"Resting":"Active")+"  |  "+(animal.fed(day)?"Fed today":"Needs "+foodName(species.food()))+"  |  Yield +"+(animal.hearts()*20)+"%");
                if(shop) {
                    boolean locked=mastery<species.rarity().mastery;b.set(row+" #Lock.Visible",locked);
                    b.set(row+".Background",Value.ref("Aetherhaven/TownUiTheme.ui",locked?"BarnCardLocked":"BarnCard"));
                    b.set(row+" #Primary.Text",locked?"Locked":"Buy "+species.rarity().gold+" gold");b.set(row+" #Primary.Disabled",locked);
                    bind(e,row+" #Primary","Buy",species.id());
                }else {
                    b.set(row+" #Hearts.Visible",true);
                    for(int heart=0;heart<5;heart++) {
                        b.append(row+" #Hearts","Aetherhaven/BarnHeartSlot.ui");
                        int points=Math.clamp(animal.friendship()-heart*100,0,100);
                        b.set(row+" #Hearts["+heart+"] #Fg.Visible",points>=100);
                    }
                    if(cards){int assigned=herd.assignedSlot(animal.id());b.set(row+" #Info.Text",assigned<0?"Resting":"In pen "+(assigned+1));b.set(row+" #Primary.Text",assigned==choosingPen?"Selected":assigned<0?"Choose":"Swap from pen "+(assigned+1));b.set(row+" #Primary.Disabled",assigned==choosingPen);bind(e,row+" #Primary","Assign",animal.id().toString());}
                    else{b.set(row+" #Rename.Value",animal.name());e.addEventBinding(CustomUIEventBindingType.Activating,row+" #RenameButton",event("Rename",animal.id().toString()).append("@Name",row+" #Rename.Value"),false);}
                }
            }else {
                String item=(String)value;b.set(row+" #Item.ItemId",item);b.set(row+" #Title.TextSpans",itemName(item));
                if("Food".equals(tab)) {
                    b.set(row+" #Detail.Text",herd.feed(item)+" / 75 in the food bin");b.set(row+" #Info.Text","One serving per active animal each day");
                    b.set(row+" #Primary.Text","Supply all");b.set(row+" #Primary.Disabled",!BarnAnimalCatalog.food(item)||herd.feed(item)>=HerdProductionState.FEED_CAPACITY);
                    b.set(row+" #Primary.TextTooltipStyle",Value.ref("Common.ui","DefaultTextTooltipStyle"));
                    b.set(row+" #Primary.TooltipText","Transfer as much of this food from your inventory as fits in the bin.");
                    b.set(row+" #Secondary.Text","Take one");b.set(row+" #Secondary.Disabled",herd.feed(item)==0);
                    bind(e,row+" #Primary","Supply",item);bind(e,row+" #Secondary","TakeFood",item);
                }else {
                    long reserved=c.town().getWarehouse().reservedProduction(plotId,item);long available=Math.max(0,state.getAmount(item)-reserved);
                    b.set(row+" #Detail.Text",state.getAmount(item)+" / "+WorkplaceProductionUpgrades.barnCapacity(state)+" capacity  |  "+reserved+" reserved for deliveries");
                    b.set(row+" #Info.Text",ProductionWithdrawal.isMilkBucketItem(item)?"Bring empty buckets to collect milk":"Produced by your fed animals");
                    b.set(row+" #Primary.Text","Collect up to 10");b.set(row+" #Primary.Disabled",available==0);bind(e,row+" #Primary","Collect",item);
                }
            }
        }
        b.set("#FooterStatus.Text",hint);b.set("#EmptyList.Visible",rows.isEmpty());
        b.set("#EmptyList.Text",switch(tab){case "Animals"->"Talk to the Rancher to visit the Animal shop.";case "Food"->"Buy an animal to see its food here.";default->"No resources stored yet. Active, fed animals produce all their resources.";});
    }
    private static String foodName(String item){return item.replace("Plant_Crop_","").replace("_Item","").replace("Food_","").replace('_',' ');}
    private static Message itemName(String item){var asset=Item.getAssetMap().getAsset(item);return asset==null?Message.raw(foodName(item)):Message.translation(asset.getTranslationKey());}
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var c=current(ref,store);if(c==null){close();return;}var state=c.production();var herd=state.herd();var world=store.getExternalData().getWorld();
        if(data.action==null)return;
        if(!allowedAction(shopOnly,data.action))return;
        if("Return".equals(data.action)){open(ref,store,new RancherHerdPage(playerRef,townId,plotId));return;}
        if("ChooseAnimal".equals(data.action)){
            int pen;try{pen=Integer.parseInt(data.key);}catch(Exception invalid){return;}
            if(choosingPen<0&&pen>=0&&pen<WorkplaceProductionUpgrades.slotCount(state))open(ref,store,new RancherHerdPage(playerRef,townId,plotId,pen));return;
        }
        if(choosingPen>=0&&!List.of("Assign","Previous","Next","Refresh").contains(data.action))return;
        if(List.of("Animals","Food","Storage").contains(data.action)){tab=data.action;page=0;}
        else if("Previous".equals(data.action))page=Math.max(0,page-1);
        else if("Next".equals(data.action))page++;
        else if(!"Refresh".equals(data.action)) {
            var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
            switch(data.action) {
                case "Buy"->{
                    var species=BarnAnimalCatalog.get(data.key);if(species==null||inventory==null)return;
                    hint=herd.buy(data.key,CoreVillagerMasteryService.level(c.town(),"rancher"),()->GoldCoinPayment.trySpend(c.town(),inventory,species.rarity().gold,c.town().playerCanSpendTreasuryGold(playerRef.getUuid())));
                    if(hint==null)hint=species.id()+" purchased. Choose it in an active pen.";
                }
                case "Assign"->{
                    if(choosingPen<0)return;UUID selected;try{selected=UUID.fromString(data.key);}catch(Exception invalid){return;}
                    NativeHerdService.update(world,AetherhavenPlugin.get(),c.town(),c.plot());
                    if(!herd.assign(choosingPen,selected,WorkplaceProductionUpgrades.slotCount(state))){
                        hint=herd.animal(selected)==null?"That animal is no longer in this barn. Nothing was changed."
                            :choosingPen>=WorkplaceProductionUpgrades.slotCount(state)?"Pen "+(choosingPen+1)+" is not unlocked."
                            :herd.animal(selected).name()+" is already in pen "+(choosingPen+1)+".";break;}
                    NativeHerdService.update(world,AetherhavenPlugin.get(),c.town(),c.plot());c.manager().updateTown(c.town());open(ref,store,new RancherHerdPage(playerRef,townId,plotId));return;
                }
                case "Empty"->{
                    int slot;String named;
                    try{int bar=data.key.indexOf('|');slot=Integer.parseInt(bar<0?data.key:data.key.substring(0,bar));named=bar<0?null:data.key.substring(bar+1);}catch(Exception ex){return;}
                    int unlocked=WorkplaceProductionUpgrades.slotCount(state);if(slot<0||slot>=unlocked)return;
                    var held=herd.slot(slot);
                    if(held==null){hint="Pen "+(slot+1)+" is already empty.";break;}
                    if(named!=null&&!named.equals(held.id().toString())){hint="Pen "+(slot+1)+" holds "+held.name()+" now. Nothing was changed.";break;}
                    NativeHerdService.update(world,AetherhavenPlugin.get(),c.town(),c.plot());
                    herd.assign(slot,null,unlocked);hint="Pen selection updated. Resting animals keep their name and hearts.";
                }
                case "Rename"->{UUID id;try{id=UUID.fromString(data.key);}catch(Exception ex){return;}
                    hint=herd.animal(id)==null?"That animal is no longer in this barn. Nothing was changed."
                        :herd.rename(id,data.name)?"Animal renamed.":"Choose a new name from 1 to 24 characters.";}
                case "Supply","TakeFood"->{
                    if(inventory==null||data.key==null||(!BarnAnimalCatalog.food(data.key)&&herd.feed(data.key)==0))return;var stack=new ItemStack(data.key,1);boolean supply="Supply".equals(data.action);
                    if(supply){
                        int amount=herd.supply(data.key,com.hexvane.aetherhaven.inventory.InventoryMaterials.count(inventory,data.key),
                            count->inventory.removeItemStack(new ItemStack(data.key,count),true,true).succeeded());
                        hint=amount>0?"Added "+amount+" servings to the food bin.":"No food transferred. Check your inventory and the food bin's space.";
                    }else hint=herd.takeFeed(data.key,()->inventory.addItemStack(stack,true,false,true).succeeded())?"Food returned to your inventory.":"Check your available inventory space.";
                }
                case "Courier"->{int target;try{target=Integer.parseInt(data.key);}catch(Exception ex){return;}
                    if(!herd.configureCourierFeed(target)){hint=target==herd.courierFeedTarget()?"Courier food is already set to that.":"Choose the courier food amount again.";break;}
                    hint=target==0?"Automatic food deliveries stopped.":"Couriers will bring each active animal's food from farm output or town storage. A Warehouse Manager and Logistics worker are needed.";}
                case "Collect"->{
                    if(data.key==null||Item.getAssetMap().getAsset(data.key)==null)return;
                    var plugin=AetherhavenPlugin.get();var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),"plot_barn",state);
                    var player=store.getComponent(ref,Player.getComponentType());if(entry==null||player==null)return;
                    long available=Math.max(0,state.getAmount(data.key)-c.town().getWarehouse().reservedProduction(plotId,data.key));
                    var result=new ProductionWithdrawal.ResultSink();long collected=ProductionWithdrawal.withdrawToPlayer(ref,store,player,state,entry,data.key,Math.min(10,available),result);
                    if(collected>0)c.town().getWarehouse().markProductionWithdrawal();
                    hint=result.needEmptyBuckets?"Bring empty buckets to collect milk.":"Collected "+collected+" items.";
                }
                default->{return;}
            }
            NativeHerdService.update(world,AetherhavenPlugin.get(),c.town(),c.plot());c.manager().updateTown(c.town());
        }
        refresh(ref,store);
    }
    static boolean allowedAction(boolean shop,String action){
        if(List.of("Previous","Next","Refresh").contains(action))return true;
        return shop?"Buy".equals(action):List.of("Animals","Food","Storage","ChooseAnimal","Assign","Return","Empty","Rename","Supply","TakeFood","Courier","Collect").contains(action);
    }
    private void open(Ref<EntityStore> ref,Store<EntityStore> store,RancherHerdPage page){store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,page);}
    private void refresh(Ref<EntityStore> ref,Store<EntityStore> store){var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);}
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Key",Codec.STRING),(d,v)->d.key=v,d->d.key).add()
            .append(new KeyedCodec<>("@Name",Codec.STRING),(d,v)->d.name=v,d->d.name).add().build();
        String action,key,name;
    }
}
