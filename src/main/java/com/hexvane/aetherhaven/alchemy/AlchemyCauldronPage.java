package com.hexvane.aetherhaven.alchemy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage;
import com.hexvane.aetherhaven.warehouse.WarehouseAccess;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3i;

/** Ingredients remain in their original inventories until a complete batch is bottled. */
public final class AlchemyCauldronPage extends AetherhavenInteractiveCustomUIPage<AlchemyCauldronPage.Data> {
    private final UUID townId;
    private final Vector3i position;
    private final List<String> selected = new ArrayList<>();
    private boolean appended;
    private int revision, step;
    private short flaskSlot = -1;
    private ItemStack shownFlask;
    private AlchemyTownStock.Snapshot shownStock;
    private String hint = "Choose an ingredient, prepare the mixture, then fill an empty flask.";
    public AlchemyCauldronPage(PlayerRef player,UUID townId,Vector3i position) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);this.townId=townId;this.position=new Vector3i(position);
    }
    private TownRecord context(Ref<EntityStore> ref,Store<EntityStore> store) {
        var target=AlchemyCauldronAccess.find(store.getExternalData().getWorld(),position);
        return AlchemyCauldronAccess.allowed(target,playerRef.getUuid())&&target.town().getTownId().equals(townId)
            &&WorkplaceBenchAccess.inReach(ref,store,position)?target.town():null;
    }
    private void bind(UIEventBuilder events,String selector,String action,String value) {
        events.addEventBinding(CustomUIEventBindingType.Activating,selector,
            new EventData().append("Action",action).append("Value",value).append("Revision",Integer.toString(revision)),false);
    }
    private List<Short> flasks(CombinedItemContainer inventory) {
        var result=new ArrayList<Short>();
        if(inventory!=null)for(short s=0;s<inventory.getCapacity();s++)if(AlchemyFlask.isFlask(inventory.getItemStack(s)))result.add(s);
        return result;
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder commands,UIEventBuilder events,Store<EntityStore> store) {
        if(!appended){commands.append("Aetherhaven/AlchemyCauldronPage.ui");appended=true;}
        var town=context(ref,store);if(town==null){close();return;}
        revision++;
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
        shownStock=WarehouseAccess.canBrowse(town,playerRef.getUuid())?stock(town):null;
        var slots=flasks(inventory);if(!slots.contains(flaskSlot))flaskSlot=slots.isEmpty()?-1:slots.getFirst();
        shownFlask=flaskSlot<0?null:inventory.getItemStack(flaskSlot);
        int capacity=AlchemyFlask.capacity(shownFlask),charges=AlchemyFlask.charges(shownFlask),mastery=CoreVillagerMasteryService.level(town,"alchemist");
        int allowance=town.getAlchemyLaboratory().ingredientSlots();
        while(selected.size()>allowance){selected.removeLast();step=0;}
        commands.set("#Summary.Text","ORIN'S LABORATORY  |  Mastery "+mastery+" / 10  |  "+allowance+" ingredient "+(allowance==1?"slot":"slots"));
        commands.set("#FlaskState.Text",shownFlask==null?"Bring an Alchemy Flask":"Flask "+(slots.indexOf(flaskSlot)+1)+" of "+slots.size()+"   |   "+charges+" / "+capacity+" charges");
        commands.set("#FlaskIcon.ItemId",AlchemyFlask.ITEM);
        commands.set("#NextFlask.Disabled",slots.size()<2);
        commands.set("#Empty.Disabled",charges==0);
        commands.set("#LeatherIcon.Visible",shownFlask!=null&&capacity<5);
        if(capacity<5)commands.set("#LeatherIcon.ItemId",AlchemyFlask.upgradeLeather(capacity));
        commands.set("#UpgradeInfo.Text",capacity>=5?"Maximum capacity reached": "Next: "+(capacity+1)+" charges  |  Mastery "+AlchemyFlask.upgradeMastery(capacity)+"\nRequires "+AlchemyFlask.upgradeLeatherCount(capacity)+" "+AlchemyFlask.upgradeLeatherName(capacity));
        commands.set("#Upgrade.Disabled",shownFlask==null||capacity>=5||mastery<AlchemyFlask.upgradeMastery(capacity));
        var player=store.getComponent(ref,Player.getComponentType());
        boolean recipeKnown=player!=null&&player.getPlayerConfigData().getKnownRecipes().contains(AlchemyFlask.ITEM);
        commands.set("#Learn.Disabled",recipeKnown);
        commands.set("#Learn.Text",recipeKnown?"Recipe learned":"Learn flask recipe");
        commands.clear("#Ingredients");
        if(selected.removeIf(item->available(inventory,shownStock,item)==0))step=0;
        int rows=0;
        for(var ingredient:AlchemyIngredients.all()) {
            int bagCount=inventory==null?0:InventoryMaterials.count(inventory,ingredient.item());
            long stockCount=shownStock==null?0:shownStock.count(ingredient.item());
            if(bagCount+stockCount==0)continue;
            String row="#Ingredients["+(rows++)+"]";
            commands.append("#Ingredients","Aetherhaven/AlchemyIngredientRow.ui");
            commands.set(row+" #Icon.ItemId",ingredient.item());commands.set(row+" #Name.Text",ingredient.name());
            commands.set(row+" #Count.Text","Bag: "+bagCount+(shownStock==null?"":"   Town storage: "+stockCount));
            commands.set(row+" #Property.Text",town.getAlchemyLaboratory().discovered(ingredient.item())?ingredient.description():"Unknown property. Brew to discover.");
            commands.set(row+" #Mark.Visible",selected.contains(ingredient.item()));
            String issue = selected.contains(ingredient.item()) ? null : bagCount+stockCount<capacity
                ? "Requires "+capacity+" for this flask. You have "+(bagCount+stockCount)+"."
                : selected.size()>=allowance ? "All ingredient slots are filled. Remove an ingredient or upgrade the laboratory." : null;
            commands.set(row+".Disabled",issue!=null);
            commands.set(row+".TooltipText",issue==null?"Choose this ingredient. A full flask uses "+capacity+".":issue);
            if(issue!=null) commands.set(row+" #Count.Text",issue);
            bind(events,row,"Ingredient",ingredient.item());
        }
        commands.set("#EmptyIngredients.Visible",rows==0);
        commands.set("#EmptyIngredients.Text",shownStock==null?"Bring alchemy ingredients in your bag.":"Bring alchemy ingredients or place them in town storage.");
        for(int i=0;i<3;i++) {
            String selector="#Slot"+i;boolean filled=i<selected.size();
            commands.set(selector+" #Lock.Visible",i>=allowance);
            commands.set(selector+" #Shade.Visible",i>=allowance);
            commands.set(selector+" #Icon.Visible",filled);
            if(filled)commands.set(selector+" #Icon.ItemId",selected.get(i));
            commands.set(selector+" #Name.Text",i>=allowance?"Locked":filled?AlchemyIngredients.find(selected.get(i)).name():"Choose ingredient");
            commands.set(selector+".Disabled",!filled);bind(events,selector,"Remove",Integer.toString(i));
        }
        var sequence=AlchemyIngredients.sequence(selected);boolean prepared=!sequence.isEmpty()&&step>=sequence.size();
        commands.set("#Progress.Text",selected.isEmpty()?"THE EMPTY CAULDRON":prepared?"MIXTURE READY":"PREPARATION "+step+" / "+sequence.size());
        commands.set("#Clue.Text",selected.isEmpty()?"Select up to "+allowance+" available ingredients.":prepared?"The potion is clear and fragrant. Bottle it while it is fresh.":AlchemyIngredients.clue(sequence.get(step)));
        commands.set("#Brew.Disabled",!prepared||shownFlask==null||charges>0);
        for(String action:List.of("Grind","Stir","Simmer")){commands.set("#"+action+".Disabled",selected.isEmpty()||prepared);bind(events,"#"+action,"Prepare",action);}
        commands.set("#Cost.Text","A full flask consumes "+capacity+" of each selected ingredient.\n"+(shownStock==null?"Uses your bag.":"Uses your bag first, then unreserved town stock.")+" Nothing is spent until bottling.");
        commands.set("#Hint.Text",hint);
        for(String action:List.of("NextFlask","Empty","Upgrade","Brew","Learn","Upgrades","Refresh"))bind(events,"#"+action,action,"");
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var town=context(ref,store);if(town==null){close();return;}
        if(!Integer.toString(revision).equals(data.revision))return;
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inventory==null)return;
        switch(data.action==null?"":data.action) {
            case "Ingredient" -> {
                if(selected.remove(data.value)){step=0;break;}
                if(AlchemyIngredients.find(data.value)!=null&&selected.size()<town.getAlchemyLaboratory().ingredientSlots()
                    &&available(inventory,WarehouseAccess.canBrowse(town,playerRef.getUuid())?stock(town):null,data.value)>=AlchemyFlask.capacity(shownFlask)){selected.add(data.value);step=0;}
            }
            case "Remove" -> {try {int i=Integer.parseInt(data.value);if(i>=0&&i<selected.size()){selected.remove(i);step=0;}}catch(NumberFormatException ignored){}}
            case "Prepare" -> {
                var sequence=AlchemyIngredients.sequence(selected);
                if(step<sequence.size()) {
                    if(sequence.get(step).equals(data.value)){step++;hint=step==sequence.size()?"Your mixture is ready to bottle.":"Well done. Watch the mixture for the next step.";}
                    else {step=0;hint="The mixture has separated. Start preparation again. Your ingredients are safe.";}
                }
            }
            case "NextFlask" -> {var all=flasks(inventory);if(!all.isEmpty()){flaskSlot=all.get(Math.floorMod(all.indexOf(flaskSlot)+1,all.size()));step=0;}}
            case "Empty" -> {
                if(AlchemyFlask.isFlask(shownFlask)&&AlchemyFlask.charges(shownFlask)>0
                    &&AlchemyInventory.exchange(inventory,flaskSlot,shownFlask,AlchemyFlask.empty(shownFlask),Map.of()))hint="The remaining potion has been poured away.";
            }
            case "Upgrade" -> {
                if(!AlchemyFlask.isFlask(shownFlask))break;
                int cap=AlchemyFlask.capacity(shownFlask);
                if(cap>=5||CoreVillagerMasteryService.level(town,"alchemist")<AlchemyFlask.upgradeMastery(cap))break;
                boolean upgraded=AlchemyInventory.exchange(inventory,flaskSlot,shownFlask,AlchemyFlask.upgrade(shownFlask),
                    Map.of(AlchemyFlask.upgradeLeather(cap),AlchemyFlask.upgradeLeatherCount(cap)));
                hint=upgraded?"Your flask can now hold "+(cap+1)+" charges. Existing potion charges are unchanged.":"Bring the required leather and select your flask again.";step=0;
            }
            case "Brew" -> {
                if(!AlchemyFlask.isFlask(shownFlask)||AlchemyFlask.charges(shownFlask)>0
                    ||!AlchemyIngredients.validMix(selected,town.getAlchemyLaboratory().ingredientSlots())||step<AlchemyIngredients.sequence(selected).size())break;
                var costs=AlchemyInventory.brewCosts(selected,AlchemyFlask.capacity(shownFlask));
                boolean bottled=shownStock==null
                    ?AlchemyInventory.exchange(inventory,flaskSlot,shownFlask,AlchemyFlask.fill(shownFlask,selected),costs)
                    :WarehouseAccess.canBrowse(town,playerRef.getUuid())&&AlchemyTownStock.brew(town.getWarehouse(),()->AlchemyTownStock.sources(town,AetherhavenPlugin.get()),shownStock,inventory,flaskSlot,shownFlask,AlchemyFlask.fill(shownFlask,selected),costs);
                if(bottled) {
                    town.getAlchemyLaboratory().discover(selected);
                    CoreVillagerMasteryService.progress(town,"alchemist").earnXp(2L * selected.size() * AlchemyFlask.capacity(shownFlask));
                    AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),AetherhavenPlugin.get()).updateTown(town);
                    hint="Flask filled. "+String.join(". ",AlchemyIngredients.effects(selected).stream().map(AlchemyIngredients.Ingredient::description).toList())+".";step=0;
                } else hint="Your inventory changed or ingredients are missing. Nothing was spent.";
            }
            case "Learn" -> {CraftingPlugin.learnRecipe(ref,AlchemyFlask.ITEM,store);hint="Orin's Alchemy Flask recipe is available at the Town Planning Desk.";}
            case "Upgrades" -> {var player=store.getComponent(ref,Player.getComponentType());if(player!=null)player.getPageManager().openCustomPage(ref,store,new WorkplaceUpgradesPage(playerRef,townId,"alchemist"));return;}
            case "Refresh" -> {}
            default -> {return;}
        }
        var commands=new UICommandBuilder();var events=new UIEventBuilder();build(ref,commands,events,store);sendUpdate(commands,events,false);
    }
    private static AlchemyTownStock.Snapshot stock(TownRecord town) {
        return AlchemyTownStock.snapshot(town.getWarehouse(),AlchemyTownStock.sources(town,AetherhavenPlugin.get()));
    }
    private static long available(CombinedItemContainer inventory,AlchemyTownStock.Snapshot stock,String item) {
        return (inventory==null?0:InventoryMaterials.count(inventory,item))+(stock==null?0:stock.count(item));
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Value",Codec.STRING),(d,v)->d.value=v,d->d.value).add()
            .append(new KeyedCodec<>("Revision",Codec.STRING),(d,v)->d.revision=v,d->d.revision).add().build();
        String action,value,revision;
    }
}
