package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.warehouse.SmeltingOutputStore;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import static com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.*;

/** One upgrade surface for a resident's apprentice sites and completed core buildings. */
public final class WorkplaceUpgradesPage extends AetherhavenInteractiveCustomUIPage<WorkplaceUpgradesPage.Data> {
    private final UUID townId;
    private final String supervisor;
    private boolean appended;
    private String tab="Workplaces", selected="home";
    private int plotIndex;
    private String hint="Choose a node to inspect its benefits and requirements. Scroll to see later upgrades.";
    public WorkplaceUpgradesPage(PlayerRef player, UUID townId, String supervisor) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC); this.townId=townId; this.supervisor=supervisor;
        if(!hasWorkplaces(supervisor))tab="Production";
        if("alchemist".equals(supervisor))tab="Alchemy";
        if("warehouse_manager".equals(supervisor))tab="Storage";
        if("innkeeper".equals(supervisor))tab="Inn";
    }
    /** The Druid's upgrades, opened straight on the Conservatory's garden tracks. */
    public static WorkplaceUpgradesPage garden(PlayerRef player, UUID townId) {
        var page=new WorkplaceUpgradesPage(player,townId,SpiritGarden.KIND);page.tab="Production";return page;
    }
    private boolean garden() { return SpiritGarden.KIND.equals(supervisor); }
    /** The Farmer's upgrades, opened straight on the farm's field tracks. */
    public static WorkplaceUpgradesPage farm(PlayerRef player, UUID townId) {
        var page=new WorkplaceUpgradesPage(player,townId,FarmFields.KIND);page.tab="Production";return page;
    }
    private boolean farm() { return FarmFields.KIND.equals(supervisor); }
    private List<RestaurantUpgrades.IngredientCost> farmCosts(TownRecord town,Node n) {
        var cost=FarmUpgrades.cost(FarmUpgrades.Track.valueOf(n.branch()),n.tier(),town.effectiveDifficultyForGameplay().getBuildingUpgradeResourceMultiplier());
        return cost.entrySet().stream().map(part -> new RestaurantUpgrades.IngredientCost(part.getKey(),part.getValue())).toList();
    }
    /** Farm upgrades are paid like garden upgrades: the player's pack, then the farm's own stores, then the warehouse. */
    private String buyFarm(Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town,PlotInstance p,Node n) {
        if(!production(p)||!FarmFields.isPlot(p))return "This building is no longer available.";
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inv==null)return "Your inventory is unavailable.";
        var state=FarmFields.production(town,p);
        var warehouse=com.hexvane.aetherhaven.crafter.PatternBookPayment.stockFor(town,playerRef.getUuid());
        var purse=new GardenUpgrades.Purse() {
            @Override public long available(String item) {
                return com.hexvane.aetherhaven.crafter.PatternBookPayment.available(inv,warehouse,item)+gardenSpare(town,p,n,item);
            }
            @Override public boolean take(Map<String,Integer> cost) {
                var stores=GardenUpgrades.fromStores(cost,item -> InventoryMaterials.count(inv,item),item -> gardenSpare(town,p,n,item));
                var rest=new LinkedHashMap<String,Integer>();
                for(var part:cost.entrySet()) {
                    int left=part.getValue()-(int)(long)stores.getOrDefault(part.getKey(),0L);
                    if(left>0)rest.put(part.getKey(),left);
                }
                if(!com.hexvane.aetherhaven.crafter.PatternBookPayment.pay(inv,warehouse,rest))return false;
                for(var part:stores.entrySet())state.removeAmountUpTo(part.getKey(),part.getValue());
                return true;
            }
        };
        String issue=FarmUpgrades.purchase(state,FarmUpgrades.Track.valueOf(n.branch()),n.tier(),
            town.effectiveDifficultyForGameplay().getBuildingUpgradeResourceMultiplier(),purse);
        return issue==null?null:switch(issue) {
            case "short" -> "You need more of the shown Life Essence and crops.";
            case "order" -> "Unlock the earlier level first.";
            default -> "Cannot purchase this upgrade. Review its requirements.";
        };
    }
    /** Garden upgrades may also be paid from the Conservatory's own stores, after the player's pack. */
    private long gardenSpare(TownRecord town,PlotInstance p,Node n,String item) {
        if(n.kind()!=Kind.GARDEN&&n.kind()!=Kind.FARM||p==null)return 0;
        return Math.max(0,town.getOrCreatePlotProduction(p.getPlotId()).getAmount(item)-town.getWarehouse().reservedProduction(p.getPlotId(),item));
    }
    private List<RestaurantUpgrades.IngredientCost> gardenCosts(TownRecord town,Node n) {
        var cost=GardenUpgrades.cost(GardenUpgrades.Track.valueOf(n.branch()),n.tier(),town.effectiveDifficultyForGameplay().getBuildingUpgradeResourceMultiplier());
        return cost.entrySet().stream().map(part -> new RestaurantUpgrades.IngredientCost(part.getKey(),part.getValue())).toList();
    }
    private String buyGarden(Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town,PlotInstance p,Node n) {
        if(!production(p)||!SpiritGarden.isPlot(p))return "This building is no longer available.";
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inv==null)return "Your inventory is unavailable.";
        var state=SpiritGarden.production(town,p);
        var warehouse=com.hexvane.aetherhaven.crafter.PatternBookPayment.stockFor(town,playerRef.getUuid());
        var purse=new GardenUpgrades.Purse() {
            @Override public long available(String item) {
                return com.hexvane.aetherhaven.crafter.PatternBookPayment.available(inv,warehouse,item)+gardenSpare(town,p,n,item);
            }
            @Override public boolean take(Map<String,Integer> cost) {
                var stores=GardenUpgrades.fromStores(cost,item -> InventoryMaterials.count(inv,item),item -> gardenSpare(town,p,n,item));
                var rest=new LinkedHashMap<String,Integer>();
                for(var part:cost.entrySet()) {
                    int left=part.getValue()-(int)(long)stores.getOrDefault(part.getKey(),0L);
                    if(left>0)rest.put(part.getKey(),left);
                }
                if(!com.hexvane.aetherhaven.crafter.PatternBookPayment.pay(inv,warehouse,rest))return false;
                for(var part:stores.entrySet())state.removeAmountUpTo(part.getKey(),part.getValue());
                return true;
            }
        };
        String issue=GardenUpgrades.purchase(state,GardenUpgrades.Track.valueOf(n.branch()),n.tier(),
            town.effectiveDifficultyForGameplay().getBuildingUpgradeResourceMultiplier(),purse);
        return issue==null?null:switch(issue) {
            case "short" -> "You need more of the shown Life Essence and plants.";
            case "order" -> "Unlock the earlier level first.";
            default -> "Cannot purchase this upgrade. Review its requirements.";
        };
    }
    private static boolean hasWorkplaces(String kind) {
        return Arrays.stream(TownsfolkWorkType.values()).anyMatch(t -> t.requiresWorkplace() && t.availableForNewWork() && WorkplaceRules.supervisor(t).equals(kind));
    }
    public static boolean supports(String kind) { return "innkeeper".equals(kind) || hasWorkplaces(kind) || "warehouse_manager".equals(kind) || "logger".equals(kind) || "druid".equals(kind); }
    private List<PlotInstance> plots(TownRecord town) {
        var catalog=AetherhavenPlugin.get().getConstructionCatalog();
        return town.getPlotInstances().stream().filter(p -> p.getState()==PlotInstanceState.COMPLETE
            && ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog,p.getConstructionId()).contains(supervisor))
            .filter(p -> production(p)||restaurant(p)).toList();
    }
    private boolean production(PlotInstance p) { return p!=null && AetherhavenPlugin.get().getConstructionCatalog()
        .resolveGameplayConstructionIds(p.getConstructionId()).stream().anyMatch(ProductionCatalog::isProductionWorkplaceConstruction); }
    private boolean restaurant(PlotInstance p) { return p!=null && AetherhavenPlugin.get().getConstructionCatalog()
        .matchesGameplayConstruction(p.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT); }
    private PlotInstance plot(TownRecord town) {
        var all=plots(town); if(all.isEmpty())return null;
        plotIndex=Math.floorMod(plotIndex,all.size()); return all.get(plotIndex);
    }
    private List<Node> nodes(PlotInstance p) {
        return switch(tab) {
            case "Inn" -> "innkeeper".equals(supervisor)?BuildingUpgradeTree.inn():List.of();
            case "Storage" -> "warehouse_manager".equals(supervisor)?BuildingUpgradeTree.warehouse():List.of();
            case "Alchemy" -> "alchemist".equals(supervisor)?BuildingUpgradeTree.alchemy():List.of();
            case "Production" -> !production(p)?List.of():garden()?BuildingUpgradeTree.garden():farm()?BuildingUpgradeTree.farm():BuildingUpgradeTree.production("rancher".equals(supervisor));
            case "Restaurant" -> restaurant(p)?BuildingUpgradeTree.restaurant():List.of();
            default -> hasWorkplaces(supervisor)?BuildingUpgradeTree.workplaces(supervisor):List.of();
        };
    }
    private boolean owned(TownRecord town,PlotInstance plot,Node n) {
        return switch(n.kind()) {
            case FOUNDATION -> "innkeeper".equals(supervisor)?town.isInnActive():TownWorkplaces.hasSupervisor(town,supervisor);
            case INN -> town.getOutreach().unlocked();
            case WORKPLACE -> town.getApprenticeWorkplaces().unlocked(TownsfolkWorkType.byId(n.branch()));
            case SLOT -> town.getApprenticeWorkplaces().extraSlots(supervisor)>=n.tier();
            case WAREHOUSE -> town.getWarehouse().upgrades()>=n.tier();
            case WAREHOUSE_STACKS -> town.getWarehouse().stackUpgrades()>=n.tier();
            case SMELTING -> town.getWarehouse().smelting().upgrades()>=n.tier();
            case ALCHEMY -> town.getAlchemyLaboratory().upgrades()>=n.tier();
            case PRODUCTION -> plot!=null && WorkplaceProductionUpgrades.currentLevel(town.getOrCreatePlotProduction(plot.getPlotId()),WorkplaceProductionUpgrades.Branch.valueOf(n.branch()))>=n.tier();
            case RESTAURANT -> plot!=null && RestaurantUpgrades.currentLevel(town.getOrCreatePlotRestaurant(plot.getPlotId()),RestaurantUpgrades.Branch.valueOf(n.branch()))>=n.tier();
            case GARDEN -> plot!=null && GardenUpgrades.level(SpiritGarden.production(town,plot),GardenUpgrades.Track.valueOf(n.branch()))>=n.tier();
            case FARM -> plot!=null && FarmUpgrades.level(FarmFields.production(town,plot),FarmUpgrades.Track.valueOf(n.branch()))>=n.tier();
        };
    }
    private String blocked(TownRecord town,PlotInstance plot,Node n,List<Node> all) {
        if(owned(town,plot,n))return "Already unlocked";
        if(!town.playerCanManageConstructions(playerRef.getUuid()))return "Building permission required";
        if("innkeeper".equals(supervisor)?!town.isInnActive():!TownWorkplaces.hasSupervisor(town,supervisor))return "Invite this villager first";
        if(TownPopulation.levelFor(TownPopulation.count(town))<n.level())return "Requires Town Level "+n.level();
        if(n.kind()==Kind.ALCHEMY&&com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.level(town,"alchemist")<n.tier()*3)return "Requires Alchemist Mastery "+n.tier()*3;
        for(String parent:n.parents())if(all.stream().filter(p -> p.id().equals(parent)).noneMatch(p -> owned(town,plot,p)))return "Unlock the connected nodes first";
        return null;
    }
    private long gold(TownRecord town,Node n) {
        return switch(n.kind()) {
            case WORKPLACE -> TownWorkplaces.TYPE_UNLOCK_COST;
            case INN -> com.hexvane.aetherhaven.inn.InnOutreach.UNLOCK_COST;
            case WAREHOUSE -> n.tier()*150L;
            case WAREHOUSE_STACKS -> n.tier()*com.hexvane.aetherhaven.warehouse.TownWarehouse.STACK_UPGRADE_GOLD;
            case SLOT -> n.tier()*100L;
            case SMELTING -> n.tier()*150L;
            case ALCHEMY -> n.tier()==1?150L:350L;
            case PRODUCTION -> WorkplaceProductionUpgrades.effectiveGoldCost(WorkplaceProductionUpgrades.Branch.valueOf(n.branch()),n.tier(),town);
            case RESTAURANT -> RestaurantUpgrades.effectiveGoldCost(RestaurantUpgrades.Branch.valueOf(n.branch()),n.tier(),town);
            default -> 0;
        };
    }
    /** Upgrade materials may come from the warehouse: whatever the player is short of is brought to them first. */
    private void fetchMaterials(com.hypixel.hytale.server.core.inventory.container.ItemContainer inv,TownRecord town,Node n) {
        var cost=new java.util.LinkedHashMap<String,Integer>();
        for(var part:costs(town,n))cost.merge(part.itemId(),part.count(),Integer::sum);
        com.hexvane.aetherhaven.crafter.PatternBookPayment.topUp(inv,com.hexvane.aetherhaven.crafter.PatternBookPayment.stockFor(town,playerRef.getUuid()),cost);
    }
    private List<RestaurantUpgrades.IngredientCost> costs(TownRecord town,Node n) {
        if(n.kind()==Kind.GARDEN)return gardenCosts(town,n);
        if(n.kind()==Kind.FARM)return farmCosts(town,n);
        if(n.kind()==Kind.PRODUCTION) {
            var branch=WorkplaceProductionUpgrades.Branch.valueOf(n.branch());
            return List.of(new RestaurantUpgrades.IngredientCost(WorkplaceProductionUpgrades.ingotItemId(branch),WorkplaceProductionUpgrades.effectiveIngotCost(branch,n.tier(),town)));
        }
        return n.kind()==Kind.RESTAURANT?RestaurantUpgrades.effectiveIngredientCosts(RestaurantUpgrades.Branch.valueOf(n.branch()),n.tier(),town):List.of();
    }
    private String token(PlotInstance p,Node n) { return tab+"/"+(p==null?"":p.getPlotId())+"/"+n.id(); }
    private static void anchor(UICommandBuilder c,String selector,int x,int y,int width,int height) {
        var a=new Anchor();a.setLeft(Value.of(x));a.setTop(Value.of(y));a.setWidth(Value.of(width));a.setHeight(Value.of(height));
        c.setObject(selector+".Anchor",a);
    }
    private static void line(UICommandBuilder c,int x,int y,int w,int h,String color) {
        c.appendInline("#Canvas","Group { Anchor: (Left: "+x+", Top: "+y+", Width: "+Math.max(2,w)+", Height: "+Math.max(2,h)+"); Background: "+color+"; }");
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        if(!appended){c.append("Aetherhaven/WorkplaceUpgradesPage.ui");appended=true;}
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(townId);
        if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())){close();return;}
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
        long available=inventory==null?0:GoldCoinPayment.totalAvailable(town,inventory,town.playerCanSpendTreasuryGold(playerRef.getUuid()));
        var p=plot(town);var all=nodes(p);int level=TownPopulation.levelFor(TownPopulation.count(town));
        if(all.stream().noneMatch(n -> n.id().equals(selected)))selected=all.isEmpty()?"":all.getFirst().id();
        var sites=town.getApprenticeWorkplaces();String name=supervisor.replace('_',' ');
        c.set("#Summary.Text",Character.toUpperCase(name.charAt(0))+name.substring(1)+"  |  Town Level "+level+("innkeeper".equals(supervisor)?"": "warehouse_manager".equals(supervisor)?"  |  "+town.getWarehouse().capacity()+" general storage slots"+(town.getWarehouse().stacksPerSlot()>1?", "+town.getWarehouse().stacksPerSlot()+" stacks each":""):"  |  "+sites.used(supervisor)+"/"+sites.slots(supervisor)+" workplaces")+"  |  Available: "+available+" gold");
        for(String t:List.of("Workplaces","Production","Restaurant","Alchemy","Storage","Inn")) {
            c.set("#"+t+".Visible",t.equals("Inn")?"innkeeper".equals(supervisor):t.equals("Storage")?"warehouse_manager".equals(supervisor):t.equals("Alchemy")?"alchemist".equals(supervisor):t.equals("Workplaces")?hasWorkplaces(supervisor):t.equals("Production")?production(p):restaurant(p));
            c.set("#"+t+".Disabled",tab.equals(t));
            if(t.equals("Production"))c.set("#Production.Text",garden()?"Garden":farm()?"Fields":"Production");
            e.addEventBinding(CustomUIEventBindingType.Activating,"#"+t,new EventData().append("Action","Tab").append("Type",t),false);
        }
        int total=plots(town).size();boolean plotControls=total>1&&!tab.equals("Workplaces")&&!tab.equals("Alchemy")&&!tab.equals("Storage")&&!tab.equals("Inn");
        c.set("#Previous.Visible",plotControls);c.set("#Next.Visible",plotControls);c.set("#Plot.Visible",!tab.equals("Workplaces")&&!tab.equals("Alchemy")&&!tab.equals("Storage")&&!tab.equals("Inn"));
        c.set("#Plot.Text",total==0?"No building":"Building "+(plotIndex+1)+" / "+total);
        for(String a:List.of("Previous","Next"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+a,new EventData().append("Action",a),false);
        c.clear("#Canvas");anchor(c,"#Canvas",0,0,640,all.stream().mapToInt(n -> n.y()+140).max().orElse(240));
        var connections=BuildingUpgradeConnections.build(all,n -> owned(town,p,n)?2:
            n.parents().stream().anyMatch(parent -> all.stream().anyMatch(o -> o.id().equals(parent)&&owned(town,p,o)))?1:0);
        c.appendInline("#Canvas","Group #Bands { Anchor: (Full: 0); }");
        int bi=0;
        for(int l=1;l<=6;l++) { final int band=l;if(all.stream().noneMatch(n -> n.level()==band))continue;
            c.append("#Bands","Aetherhaven/BuildingUpgradeBand.ui");String s="#Bands["+bi+++ "]";
            int top=bandTop(l,!tab.equals("Workplaces"));
            anchor(c,s,24,top,592,30);c.set(s+" #Label.Text","TOWN LEVEL "+l+"\n"+(level>=l?"REACHED":"LOCKED"));
            // The divider yields to the uninterrupted prerequisite paths, never the reverse.
            int y=top+15,start=132;
            var crossing=connections.stream().filter(path -> path.y()<=y+1 && path.y()+path.height()>y)
                .sorted(Comparator.comparingInt(BuildingUpgradeConnections.Stroke::x)).toList();
            for(var path:crossing) {
                int end=Math.min(616,path.x()-8);
                if(end>start)line(c,start,y,end-start,1,"#48576a");
                start=Math.max(start,path.x()+path.width()+8);
            }
            if(start<616)line(c,start,y,616-start,1,"#48576a");
        }
        for(var path:connections)line(c,path.x(),path.y(),path.width(),path.height(),
            path.state()==2?"#7bad99":path.state()==1?"#ab9060":"#48576a");
        c.appendInline("#Canvas","Group #Nodes { Anchor: (Full: 0); }");
        for(int i=0;i<all.size();i++) {
            var n=all.get(i);c.append("#Nodes","Aetherhaven/BuildingUpgradeNode.ui");String s="#Nodes["+i+"]";
            anchor(c,s,n.x(),n.y(),174,112);c.set(s+" #Title.Text",n.title());
            c.set(s+" #Selected.Visible",selected.equals(n.id()));
            c.set(s+" #Icon.Visible",n.icon()!=null);if(n.icon()!=null)c.set(s+" #Icon.AssetPath","UI/Custom/"+n.icon());
            c.set(s+" #Item.Visible",n.item()!=null);if(n.item()!=null)c.set(s+" #Item.ItemId",n.item());
            boolean bought=owned(town,p,n);String reason=blocked(town,p,n,all);
            boolean prerequisitesMet=n.parents().stream().allMatch(parent -> all.stream()
                .anyMatch(other -> other.id().equals(parent)&&owned(town,p,other)));
            c.set(s+" #LockedBackground.Visible",!bought);
            c.setObject(s+" #LockedBackground.Background",new com.hypixel.hytale.server.core.ui.PatchStyle()
                .setColor(Value.of(prerequisitesMet?"#3e392d":"#2b3038")));
            c.set(s+" #Lock.Visible",!bought&&!prerequisitesMet);
            c.set(s+" #Title.Style.TextColor",bought?"#c5cdd5":prerequisitesMet?"#e8c984":"#98a3ae");
            c.set(s+" #State.Text",bought?"UNLOCKED":reason==null?"AVAILABLE":level<n.level()?"TOWN LEVEL "+n.level():"LOCKED");
            c.set(s+" #State.Style.TextColor",bought?"#96d4b4":reason==null?"#e8c984":"#889bad");
            e.addEventBinding(CustomUIEventBindingType.Activating,s,new EventData().append("Action","Select").append("Type",n.id()),false);
        }
        var n=all.stream().filter(o -> o.id().equals(selected)).findFirst().orElse(null);
        c.clear("#Costs");c.set("#Hint.Text",hint);
        // The restaurant's menu takes the description's place, and on the Restaurant node the unused purchase rows too.
        var board=com.hexvane.aetherhaven.ui.RestaurantMenuBoardUi.mode(tab,n);
        com.hexvane.aetherhaven.ui.RestaurantMenuBoardUi.apply(c,board,n,restaurant(p)?town.getOrCreatePlotRestaurant(p.getPlotId()):null,
            plugin.getRestaurantMenu(),TownPopulation.farmAndRanchFood(town,plugin.getConstructionCatalog()));
        boolean menuOnly=board==com.hexvane.aetherhaven.ui.RestaurantMenuBoardUi.Mode.MENU;
        c.set("#Description.Visible",board==com.hexvane.aetherhaven.ui.RestaurantMenuBoardUi.Mode.NONE);
        c.set("#Requires.Visible",!menuOnly);c.set("#Costs.Visible",!menuOnly);c.set("#Gold.Visible",!menuOnly);
        if(n==null){c.set("#DetailLock.Visible",false);c.set("#DetailIcon.Visible",false);c.set("#DetailItem.Visible",false);c.set("#DetailState.Text","");c.set("#Requires.Text","");c.set("#Gold.Text","");c.set("#DetailTitle.Text","Build a workplace");c.set("#Description.Text","Complete this villager's building to see its upgrades.");c.set("#Purchase.Disabled",true);return;}
        String reason=blocked(town,p,n,all);boolean bought=owned(town,p,n);
        c.set("#DetailLock.Visible",!bought);
        c.set("#DetailTitle.Style.TextColor",bought?"#e8c984":"#acb5bf");
        c.set("#DetailTitle.Text",n.title());c.set("#Description.Text",n.description());c.set("#DetailState.Text",bought?"UNLOCKED":reason==null?"READY TO UNLOCK":reason);
        c.set("#DetailState.Style.TextColor",bought?"#96d4b4":reason==null?"#e8c984":"#b4becb");
        c.set("#DetailIcon.Visible",n.icon()!=null);if(n.icon()!=null)c.set("#DetailIcon.AssetPath","UI/Custom/"+n.icon());
        c.set("#DetailItem.Visible",n.item()!=null);if(n.item()!=null)c.set("#DetailItem.ItemId",n.item());
        var parentNames=all.stream().filter(o -> n.parents().contains(o.id())).map(Node::title).toList();
        c.set("#Requires.Text","Town Level "+n.level()+(parentNames.isEmpty()?"":"\nRequires: "+String.join(", ",parentNames)));
        int ci=0;for(var cost:costs(town,n)) {
            c.append("#Costs","Aetherhaven/BuildingUpgradeCost.ui");String s="#Costs["+ci+++"]";
            c.set(s+" #Item.ItemId",cost.itemId());c.set(s+" #Count.Text",(inventory==null?0:com.hexvane.aetherhaven.crafter.PatternBookPayment.available(inventory,
                com.hexvane.aetherhaven.crafter.PatternBookPayment.stockFor(town,playerRef.getUuid()),cost.itemId())+gardenSpare(town,p,n,cost.itemId()))+" / "+cost.count());
        }
        boolean affordable=inventory!=null && available>=gold(town,n)
            && costs(town,n).stream().allMatch(cost -> com.hexvane.aetherhaven.crafter.PatternBookPayment.available(inventory,
                com.hexvane.aetherhaven.crafter.PatternBookPayment.stockFor(town,playerRef.getUuid()),cost.itemId())+gardenSpare(town,p,n,cost.itemId())>=cost.count());
        c.set("#Gold.Text",bought?"Purchased":gold(town,n)==0?"No gold required":gold(town,n)+" gold\nFrom inventory or treasury");
        if(reason==null&&!affordable)c.set("#DetailState.Text","You need more gold or materials");
        c.set("#Purchase.Text",bought?"Unlocked":"Unlock upgrade");c.set("#Purchase.Disabled",reason!=null||n.kind()==Kind.FOUNDATION||!affordable);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#Purchase",new EventData().append("Action","Purchase").append("Type",token(p,n)),false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())){close();return;}
        var p=plot(town);var all=nodes(p);
        if("Tab".equals(data.action)) {
            if(!List.of("Workplaces","Production","Restaurant","Alchemy","Storage","Inn").contains(data.type))return;
            tab=data.type;selected="home";
        } else if("Select".equals(data.action)) {
            if(all.stream().noneMatch(n -> n.id().equals(data.type)))return;selected=data.type;
        } else if("Next".equals(data.action)||"Previous".equals(data.action)) {
            plotIndex+="Next".equals(data.action)?1:-1;selected="home";
            p=plot(town);if(tab.equals("Production")&&!production(p)&&restaurant(p))tab="Restaurant";
            else if(tab.equals("Restaurant")&&!restaurant(p)&&production(p))tab="Production";
        } else if("Purchase".equals(data.action)) {
            var purchasePlot=p;
            var n=all.stream().filter(o -> token(purchasePlot,o).equals(data.type)).findFirst().orElse(null);
            if(n==null) {
                // The press named an upgrade of another building or one this building does not offer.
                hint="That upgrade is not offered by the building shown here. Choose one from this list.";
                selected="home";
            } else {
                String error=blocked(town,p,n,all);
                if(error==null)error=purchase(ref,store,town,p,n);
                if(error==null){
                    manager.updateTown(town);hint=n.title()+" unlocked.";
                    if(n.kind()==Kind.WORKPLACE)com.hexvane.aetherhaven.quest.WorkplaceQuestHooks.typeUnlocked(store.getExternalData().getWorld(),manager,town);
                }else hint=error;
            }
        } else return;
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    private String purchase(Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town,PlotInstance p,Node n) {
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
        if(inventory==null)return "Your inventory is unavailable.";
        switch(n.kind()) {
            case INN: return "innkeeper".equals(supervisor)?town.getOutreach().unlock(town,playerRef.getUuid(),inventory):"Choose the Inn upgrades.";
            case WAREHOUSE: {
                if(!"warehouse_manager".equals(supervisor))return "Choose the warehouse's upgrades.";
                var warehouse=town.getWarehouse();
                if(warehouse.upgrades()!=n.tier()-1)return tierIssue(warehouse.upgrades(),n.tier());
                long cost=warehouse.upgradeCost();
                return buyGoldUpgrade(ref,store,town,inventory,cost,()->warehouse.upgrade(n.tier()-1));
            }
            case WAREHOUSE_STACKS: {
                if(!"warehouse_manager".equals(supervisor))return "Choose the warehouse's upgrades.";
                var warehouse=town.getWarehouse();
                if(warehouse.stackUpgrades()!=n.tier()-1)return tierIssue(warehouse.stackUpgrades(),n.tier());
                return buyGoldUpgrade(ref,store,town,inventory,warehouse.stackUpgradeCost(),()->warehouse.upgradeStacks(n.tier()-1));
            }
            case ALCHEMY: {
                if(!"alchemist".equals(supervisor)||com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.level(town,"alchemist")<n.tier()*3)return "The Alchemist needs more Mastery.";
                if(town.getAlchemyLaboratory().upgrades()!=n.tier()-1)return tierIssue(town.getAlchemyLaboratory().upgrades(),n.tier());
                return buyGoldUpgrade(ref,store,town,inventory,gold(town,n),()->town.getAlchemyLaboratory().upgrade(n.tier()-1));
            }
            case WORKPLACE: return town.getApprenticeWorkplaces().buyUnlock(town,playerRef.getUuid(),TownsfolkWorkType.byId(n.branch()),inventory);
            case SLOT: return town.getApprenticeWorkplaces().buySlot(town,playerRef.getUuid(),supervisor,n.tier()-1,inventory);
            case SMELTING: {
                var storage=town.getWarehouse().smelting();
                if(storage.upgrades()!=n.tier()-1||storage.upgrades()>=SmeltingOutputStore.MAX_UPGRADES)return tierIssue(storage.upgrades(),n.tier());
                return buyGoldUpgrade(ref,store,town,inventory,gold(town,n),()->storage.upgrade(n.tier()-1));
            }
            case PRODUCTION: {
                if(!production(p))return "This building is no longer available.";
                var state=town.getOrCreatePlotProduction(p.getPlotId());var branch=WorkplaceProductionUpgrades.Branch.valueOf(n.branch());
                if(WorkplaceProductionUpgrades.nextTier(state,branch)!=n.tier())return "Unlock the earlier tier first.";
                var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inv==null)return "Your inventory is unavailable.";
                fetchMaterials(inv,town,n);
                var result=WorkplaceProductionUpgrades.tryPurchase(state,branch,town,inv,town.playerCanSpendTreasuryGold(playerRef.getUuid()),"rancher".equals(supervisor));
                return switch(result){case OK -> null;case NEED_INGOT -> "You need more of the shown ingots.";case NEED_GOLD -> "You need more gold.";default -> "Cannot purchase this upgrade. Review its requirements.";};
            }
            case GARDEN: return buyGarden(ref,store,town,p,n);
            case FARM: return buyFarm(ref,store,town,p,n);
            case RESTAURANT: {
                if(!restaurant(p))return "This building is no longer available.";
                var state=town.getOrCreatePlotRestaurant(p.getPlotId());var branch=RestaurantUpgrades.Branch.valueOf(n.branch());
                if(RestaurantUpgrades.nextTier(state,branch)!=n.tier())return "Unlock the earlier tier first.";
                var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);if(inv==null)return "Your inventory is unavailable.";
                fetchMaterials(inv,town,n);
                var result=RestaurantUpgrades.tryPurchase(state,branch,town,inv,town.playerCanSpendTreasuryGold(playerRef.getUuid()));
                return switch(result){case OK -> null;case NEED_INGREDIENT -> "You need more of the shown ingredients.";case NEED_GOLD -> "You need more gold.";default -> "Cannot purchase this upgrade. Review its requirements.";};
            }
            default: return "Invite this resident to begin.";
        }
    }
    private String buyGoldUpgrade(Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town,
            CombinedItemContainer inventory,long cost,java.util.function.BooleanSupplier apply) {
        var player=store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if(player==null)return "Your inventory is unavailable.";
        var spent=GoldCoinPayment.trySpendReturningBreakdown(town,inventory,cost,town.playerCanSpendTreasuryGold(playerRef.getUuid()));
        if(spent==null)return "You need more gold in your inventory or treasury.";
        if(apply.getAsBoolean())return null;
        GoldCoinPayment.refund(town,player,ref,store,spent);
        return "This tier is already unlocked. Your gold was returned.";
    }
    /** Why a tier cannot be bought when the town does not own exactly the tiers before it. */
    static String tierIssue(int owned,int tier) {
        return owned>=tier?"This tier is already unlocked. Nothing was spent.":"Unlock the earlier tier first.";
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Type",Codec.STRING),(d,v)->d.type=v,d->d.type).add().build();
        String action,type;
    }
}
