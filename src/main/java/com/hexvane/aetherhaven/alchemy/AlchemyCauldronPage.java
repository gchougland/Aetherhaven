package com.hexvane.aetherhaven.alchemy;

import com.hexvane.aetherhaven.ui.AetherhavenLanguage;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.crafter.PatternBookPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage;
import com.hexvane.aetherhaven.warehouse.WarehouseAccess;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3i;

/**
 * Ingredients remain in their original inventories until a complete batch is bottled. Nothing shown here is held
 * against the player later: bottling and crafting look at what can be taken at the moment the button is pressed.
 *
 * <p>The brew is played in beats, one per button press, and judged by {@link AlchemyBrew}. This page never sends an
 * update on a timer. The game drops any button press that arrives while a page update is still waiting for the
 * client's acknowledgement, so a page that redraws itself many times a second loses presses; one update per press
 * loses none.
 */
public final class AlchemyCauldronPage extends AetherhavenInteractiveCustomUIPage<AlchemyCauldronPage.Data> {
    private static final HytaleLogger LOG=HytaleLogger.forEnclosingClass();
    private static final String LANG="aetherhaven_alchemy.aetherhaven.cauldron.";
    /** The gauge art carries heat 0 to 100 on 300 of its 316 pixels, starting 8 above its bottom edge. */
    static final int GAUGE_BASE=8,GAUGE_PIXELS=3;
    private final UUID townId;
    private final Vector3i position;
    private final List<String> selected = new ArrayList<>();
    private boolean appended;
    private int revision;
    private short flaskSlot = -1;
    private ItemStack shownFlask;
    /** The brew in the cauldron: null until Start, kept after its last beat until it is bottled or the mixture changes. */
    private AlchemyBrew brew;
    private Message hint=Message.translation(LANG+"hintStart");
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
    /** What bottling would write into the flask right now: Good unless a brew has been played to its last beat. */
    private AlchemyBrew.Quality quality(){return brew==null?AlchemyBrew.Quality.GOOD:brew.quality();}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder commands,UIEventBuilder events,Store<EntityStore> store) {
        if(!appended){commands.append("Aetherhaven/AlchemyCauldronPage.ui");appended=true;}
        var town=context(ref,store);if(town==null){close();return;}
        revision++;
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
        // For display only. Bottling reads town stock again when the button is pressed.
        var shownStock=WarehouseAccess.canBrowse(town,playerRef.getUuid())?stock(town):null;
        var slots=flasks(inventory);if(!slots.contains(flaskSlot))flaskSlot=slots.isEmpty()?-1:slots.getFirst();
        shownFlask=flaskSlot<0?null:inventory.getItemStack(flaskSlot);
        int capacity=AlchemyFlask.capacity(shownFlask),charges=AlchemyFlask.charges(shownFlask),mastery=CoreVillagerMasteryService.level(town,"alchemist");
        int allowance=town.getAlchemyLaboratory().ingredientSlots();
        while(selected.size()>allowance){selected.removeLast();brew=null;}
        commands.set("#Summary.Text","ORIN'S LABORATORY  |  Mastery "+mastery+" / 10  |  "+allowance+" ingredient "+(allowance==1?"slot":"slots"));
        commands.set("#FlaskState.TextSpans",shownFlask==null?Message.translation(LANG+"noFlask")
            :Message.raw("Flask "+(slots.indexOf(flaskSlot)+1)+" of "+slots.size()+"   |   "+charges+" / "+capacity+" charges"));
        commands.set("#FlaskIcon.ItemId",AlchemyFlask.ITEM);
        commands.set("#NextFlask.Disabled",slots.size()<2);
        commands.set("#Empty.Disabled",charges==0);
        commands.set("#LeatherIcon.Visible",shownFlask!=null&&capacity<5);
        if(capacity<5)commands.set("#LeatherIcon.ItemId",AlchemyFlask.upgradeLeather(capacity));
        commands.set("#UpgradeInfo.Text",capacity>=5?"Maximum capacity reached": "Next: "+(capacity+1)+" charges  |  Mastery "+AlchemyFlask.upgradeMastery(capacity)+"\nRequires "+AlchemyFlask.upgradeLeatherCount(capacity)+" "+AlchemyFlask.upgradeLeatherName(capacity));
        commands.set("#Upgrade.Disabled",shownFlask==null||capacity>=5||mastery<AlchemyFlask.upgradeMastery(capacity));
        commands.set("#CraftCost.TextSpans",Message.translation(LANG+"craftCost").param("materials",materials(AlchemyFlaskCraft.cost())));
        commands.clear("#Ingredients");
        if(selected.removeIf(item->available(inventory,shownStock,item)==0))brew=null;
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
        brew(commands,mastery,allowance);
        // Bottling never waits for the game: a mixture can always be bottled as an ordinary potion.
        commands.set("#Brew.Disabled",selected.isEmpty()||shownFlask==null||charges>0);
        commands.set("#Cost.Text","A full flask consumes "+capacity+" of each selected ingredient.\n"+(shownStock==null?"Uses your bag.":"Uses your bag first, then unreserved town stock.")+" Nothing is spent until bottling.");
        commands.set("#Hint.TextSpans",hint);
        for(String action:List.of("NextFlask","Empty","Upgrade","Brew","Craft","Upgrades","Refresh","Start"))bind(events,"#"+action,action,"");
        for(var move:AlchemyBrew.Move.values())bind(events,"#"+button(move),"Move",move.name());
    }
    private static String button(AlchemyBrew.Move move){return move==AlchemyBrew.Move.STIR?"Stir":move==AlchemyBrew.Move.STOKE?"Stoke":"Wait";}
    /** Pixels above the gauge's bottom edge for a heat value. */
    static int gaugeY(int heat){return GAUGE_BASE+heat*GAUGE_PIXELS;}
    private static void place(UICommandBuilder commands,String selector,int left,int bottom,int width,int height) {
        var anchor=new Anchor();anchor.setLeft(Value.of(left));anchor.setBottom(Value.of(bottom));anchor.setWidth(Value.of(width));anchor.setHeight(Value.of(height));
        commands.setObject(selector+".Anchor",anchor);
    }
    /** The lit pane of the band art sits 3 inside its edge, so the picture is drawn 3 larger on every side. */
    private static void band(UICommandBuilder commands,String selector,int centre,int width) {
        place(commands,selector,13,gaugeY(centre)-width*GAUGE_PIXELS/2-3,70,width*GAUGE_PIXELS+6);
    }
    /**
     * Everything that changes during a brew, and nothing else: this is what one beat sends.
     *
     * @param allowance ingredient slots the laboratory has, for the empty cauldron's instruction
     */
    private void brew(UICommandBuilder commands,int mastery,int allowance) {
        var rules=AlchemyBrew.rules();
        boolean empty=selected.isEmpty(),playing=brew!=null&&!brew.finished(),done=brew!=null&&brew.finished();
        var quality=quality();
        commands.set("#BannerGreat.Visible",done&&quality==AlchemyBrew.Quality.GREAT);
        commands.set("#BannerPerfect.Visible",done&&quality==AlchemyBrew.Quality.PERFECT);
        commands.set("#BrewTitle.Style.TextColor",!done||quality==AlchemyBrew.Quality.GOOD?"#e4c987":quality==AlchemyBrew.Quality.GREAT?"#c8f7d8":"#fff0b8");
        commands.set("#BrewTitle.TextSpans",empty?Message.translation(LANG+"brew.titleEmpty"):playing?Message.translation(LANG+"brew.titleBeat")
            .param("beat",Integer.toString(brew.beat()+1)).param("beats",Integer.toString(brew.beats()))
            :done?Message.translation(LANG+"brew.title"+quality.label()):Message.translation(LANG+"brew.titleReady"));
        commands.set("#BrewCount.TextSpans",brew==null?Message.raw(""):Message.translation(LANG+"brew.count")
            .param("inside",Integer.toString(brew.inside())).param("played",Integer.toString(brew.beat())));
        Message line;
        if(empty)line=Message.translation(LANG+"brew.lineEmpty").param("slots",Integer.toString(allowance));
        else if(brew==null)line=Message.translation(LANG+"brew.lineReady");
        else if(playing)line=brew.beat()==0?Message.translation(LANG+"brew.lineFirst"):Message.translation(LANG+(brew.lastCounted()?"brew.lineInside":"brew.lineOutside"));
        else line=quality==AlchemyBrew.Quality.GOOD?Message.translation(LANG+"brew.resultGood")
            :Message.translation(LANG+"brew.resultBetter").param("duration",Integer.toString(AlchemyBrew.Rules.percent(rules.duration(quality))))
                .param("restore",Integer.toString(AlchemyBrew.Rules.percent(rules.restore(quality))));
        commands.set("#BrewLine.TextSpans",line);
        commands.set("#BrewDrift.TextSpans",playing?Message.translation(LANG+(brew.direction()>0?"brew.driftUp":"brew.driftDown")):Message.raw(""));
        commands.set("#MoveRow.Visible",playing);
        commands.set("#StartRow.Visible",!playing);
        commands.set("#Start.Disabled",empty);
        commands.set("#Start.TextSpans",Message.translation(LANG+(done?"brew.again":"brew.start")));
        // The gauge: the band and the marker stay up after the last beat, the hints for the next beat do not.
        commands.set("#Band.Visible",brew!=null);
        commands.set("#Marker.Visible",brew!=null);
        commands.set("#Ghost.Visible",playing);
        if(brew!=null) {
            band(commands,"#Band",brew.centre(),brew.width());
            place(commands,"#Marker",10,gaugeY(brew.heat())-9,76,18);
        }
        if(playing)band(commands,"#Ghost",brew.expectedCentre(),brew.width());
        for(var move:AlchemyBrew.Move.values()) {
            String pip="#Pip"+button(move);
            // Two choices can land on the same heat at the ends of the gauge: only the stronger one is drawn there.
            boolean shown=playing&&(move!=AlchemyBrew.Move.WAIT||brew.after(move)!=brew.after(AlchemyBrew.Move.STOKE));
            commands.set(pip+".Visible",shown);
            if(shown)place(commands,pip,0,gaugeY(brew.after(move))-8,76,16);
            int change=move==AlchemyBrew.Move.STOKE?rules.rise()+rules.stoke():move==AlchemyBrew.Move.STIR?rules.stir()-rules.rise():rules.rise();
            commands.set("#"+button(move)+"Note.TextSpans",Message.translation(LANG+"brew.move"+button(move)).param("amount",Integer.toString(change)));
            commands.set(pip+" #PipName.TextSpans",Message.translation(LANG+"brew.pip"+button(move)));
        }
        commands.set("#BrewRule.TextSpans",Message.translation(LANG+"brew.rule").param("beats",Integer.toString(rules.beats()))
            .param("great",Integer.toString(rules.greatBeats())).param("perfect",Integer.toString(rules.perfectBeats()))
            .param("width",Integer.toString(rules.width(Math.max(1,selected.size()),mastery))));
        commands.set("#FillNote.TextSpans",empty||shownFlask==null?Message.raw(""):AlchemyFlask.charges(shownFlask)>0?Message.translation(LANG+"fillNoteHolds")
            :Message.translation(LANG+(done?"fillNote"+quality.label():playing?"fillNoteEarly":"fillNoteGood")));
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var town=context(ref,store);if(town==null){close();return;}
        if(!Integer.toString(revision).equals(data.revision))return;
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inventory==null)return;
        int mastery=CoreVillagerMasteryService.level(town,"alchemist");
        switch(data.action==null?"":data.action) {
            case "Ingredient" -> {
                if(selected.remove(data.value)){brew=null;break;}
                if(AlchemyIngredients.find(data.value)!=null&&selected.size()<town.getAlchemyLaboratory().ingredientSlots()
                    &&available(inventory,WarehouseAccess.canBrowse(town,playerRef.getUuid())?stock(town):null,data.value)>=AlchemyFlask.capacity(shownFlask)){selected.add(data.value);brew=null;}
            }
            case "Remove" -> {try {int i=Integer.parseInt(data.value);if(i>=0&&i<selected.size()){selected.remove(i);brew=null;}}catch(NumberFormatException ignored){}}
            case "Start" -> {
                if(!AlchemyIngredients.validMix(selected,town.getAlchemyLaboratory().ingredientSlots()))break;
                brew=new AlchemyBrew(AlchemyBrew.rules(),ThreadLocalRandom.current().nextLong(),selected.size(),mastery);
                hint=Message.translation(LANG+"hintBrewing");
            }
            case "Move" -> {
                AlchemyBrew.Move move;
                try{move=AlchemyBrew.Move.valueOf(data.value);}catch(IllegalArgumentException|NullPointerException ignored){return;}
                if(brew==null||brew.finished())return;
                brew.play(move);
                if(brew.finished())hint=Message.translation(LANG+"hintDone").param("quality",Message.translation(LANG+"quality"+brew.quality().label()));
                // One small update for the beat. The lists and the buttons' bindings are as they were.
                var beat=new UICommandBuilder();brew(beat,mastery,town.getAlchemyLaboratory().ingredientSlots());beat.set("#Hint.TextSpans",hint);
                sendUpdate(beat,null,false);
                return;
            }
            case "NextFlask" -> {var all=flasks(inventory);if(!all.isEmpty())flaskSlot=all.get(Math.floorMod(all.indexOf(flaskSlot)+1,all.size()));}
            case "Empty" -> {
                if(AlchemyFlask.isFlask(shownFlask)&&AlchemyFlask.charges(shownFlask)>0
                    &&AlchemyInventory.exchange(inventory,flaskSlot,shownFlask,AlchemyFlask.empty(shownFlask),Map.of()))hint=Message.raw("The remaining potion has been poured away.");
            }
            case "Upgrade" -> {
                if(!AlchemyFlask.isFlask(shownFlask))break;
                int cap=AlchemyFlask.capacity(shownFlask);
                if(cap>=5||mastery<AlchemyFlask.upgradeMastery(cap))break;
                boolean upgraded=AlchemyInventory.exchange(inventory,flaskSlot,shownFlask,AlchemyFlask.upgrade(shownFlask),
                    Map.of(AlchemyFlask.upgradeLeather(cap),AlchemyFlask.upgradeLeatherCount(cap)));
                hint=Message.raw(upgraded?"Your flask can now hold "+(cap+1)+" charges. Existing potion charges are unchanged.":"Bring the required leather and select your flask again.");
            }
            case "Brew" -> {
                // The brew only ever decides the quality. Whether the flask can be filled is decided from what can be taken right now.
                var quality=quality();
                var result=AlchemyBottling.bottle(town,playerRef.getUuid(),()->AlchemyTownStock.sources(town,AetherhavenPlugin.get()),
                    inventory,flaskSlot,shownFlask,List.copyOf(selected),quality);
                switch(result.outcome()) {
                    case FILLED -> {
                        AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),AetherhavenPlugin.get()).updateTown(town);
                        flaskSlot=result.flaskSlot();
                        hint=Message.translation(LANG+"filled").param("quality",Message.translation(LANG+"quality"+quality.label()))
                            .param("effects",String.join(". ",AlchemyIngredients.effects(selected).stream().map(AlchemyIngredients.Ingredient::description).toList()));
                        // The mixture has left the cauldron. The next flask is a new brew.
                        brew=null;
                    }
                    case MISSING -> hint=Message.translation(LANG+"fillMissing").param("list",ingredients(result.missing()));
                    case NO_FLASK -> hint=Message.translation(LANG+"fillNoFlask");
                    case FLASK_HOLDS_POTION -> {flaskSlot=result.flaskSlot();hint=Message.translation(LANG+"fillHoldsPotion");}
                    case INVALID_MIXTURE -> {}
                    case REFUSED -> {
                        // Should be unreachable: the ingredients were counted a moment ago on this same thread.
                        LOG.atWarning().log("Cauldron fill refused after its ingredients were found: town %s, flask slot %s, mixture %s",townId,result.flaskSlot(),selected);
                        hint=Message.translation(LANG+"fillRefused");
                    }
                }
            }
            case "Craft" -> {
                var pack=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
                var result=AlchemyFlaskCraft.craft(pack,PatternBookPayment.stockFor(town,playerRef.getUuid()));
                hint=switch(result.outcome()) {
                    case CRAFTED -> Message.translation(LANG+"crafted");
                    case MISSING -> Message.translation(LANG+"craftMissing").param("list",materials(result.missing()));
                    case NO_ROOM -> Message.translation(LANG+"craftNoRoom");
                    case REFUSED -> Message.translation(LANG+"craftRefused");
                };
            }
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
    /** "2 Blood Rose, 1 Azure Fern", with the names the ingredient list shows. */
    private static String ingredients(List<AlchemyBottling.Missing> missing) {
        return String.join(", ",missing.stream().map(m->{var known=AlchemyIngredients.find(m.item());return m.count()+" "+(known==null?m.item():known.name());}).toList());
    }
    private String materials(Map<String,Integer> cost) {
        return String.join(", ",cost.entrySet().stream().map(part->part.getValue()+" "+itemName(part.getKey())).toList());
    }
    private String materials(List<AlchemyBottling.Missing> missing) {
        return String.join(", ",missing.stream().map(m->m.count()+" "+itemName(m.item())).toList());
    }
    /** The item's own name in this player's language, as the backpack workshop shows its materials. */
    private String itemName(String id) {
        var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String name=i18n==null?null:AetherhavenLanguage.message(playerRef,new ItemStack(id,1).getItem().getTranslationKey());
        return name==null?id:name;
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Value",Codec.STRING),(d,v)->d.value=v,d->d.value).add()
            .append(new KeyedCodec<>("Revision",Codec.STRING),(d,v)->d.revision=v,d->d.revision).add().build();
        String action,value,revision;
    }
}
