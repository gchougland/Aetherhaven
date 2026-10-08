package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.*;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Click-only view over independent town sources. The displayed order is never a storage address. */
public final class WarehousePage extends AetherhavenInteractiveCustomUIPage<WarehousePage.Data> {
    private enum Tab { GENERAL, OUTPUT, PRODUCTION }
    private enum Sort { NAME, QUANTITY, SOURCE }
    private record Building(UUID plot,String name,String location,List<Entry> slots,List<Long> capacities,int unlockedSlots) {}
    private record Entry(boolean player,short slot,ItemStack stack,UUID plot,String location,long amount,long reserved) {}
    private final UUID townId;
    private UUID localPlot;
    private Tab tab=Tab.GENERAL;
    private Sort sort=Sort.NAME;
    private boolean grid=true,appended;
    private String search="",hint="Stored items keep their original properties.";
    private int page;
    private long frame,revision;
    private long huntRevision;
    private final Map<String,Entry> entries=new HashMap<>();
    public WarehousePage(PlayerRef player,UUID townId) { super(player,CustomPageLifetime.CanDismiss,Data.CODEC);this.townId=townId; }
    public WarehousePage(PlayerRef player,UUID townId,UUID localPlot) {this(player,townId);this.localPlot=Objects.requireNonNull(localPlot);tab=Tab.PRODUCTION;}
    private boolean access(TownRecord town) {
        if(!WarehouseAccess.canBrowse(town,playerRef.getUuid()))return false;
        if(localPlot==null)return true;
        if(com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,localPlot)!=null)return true;
        var lodge=town.findPlotById(localPlot);
        return lodge!=null&&lodge.getState()==PlotInstanceState.COMPLETE&&AetherhavenPlugin.get().getConstructionCatalog()
            .matchesGameplayConstruction(lodge.getConstructionId(),com.hexvane.aetherhaven.hunter.HuntService.CONSTRUCTION);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        if(!appended) {
            c.append("Aetherhaven/WarehousePage.ui");
            c.set("#Search.Value",search);
            // Keep the input and its binding alive across result updates. Writing its
            // value back would replace newer keystrokes when a response arrives late.
            e.addEventBinding(CustomUIEventBindingType.ValueChanged,"#Search",
                new EventData().append("Action","SearchChanged").append("@Search","#Search.Value"),false);
            appended=true;
        }
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);if(!access(town)){close();return;}
        var warehouse=town.getWarehouse();revision=warehouse.revision();frame++;entries.clear();
        huntRevision=town.getHunts().revision();
        c.set("#TitleText.Text",localPlot==null?"TOWN WAREHOUSE":com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,localPlot)!=null?"MINESHAFT OUTPUT":"LODGE SPOILS");
        c.set("#TownName.Text",town.getDisplayName());
        var capacityPage=tab==Tab.GENERAL?TownWarehouse.Page.GENERAL:TownWarehouse.Page.OUTPUT;
        c.set("#CapacityPanel.Visible",tab!=Tab.PRODUCTION);
        c.set("#Capacity.Text",warehouse.used(capacityPage)+" / "+warehouse.slots(capacityPage)+" "+(tab==Tab.GENERAL?"general slots":"output slots"));
        var capacityFill = new Anchor();
        capacityFill.setLeft(Value.of(0));
        capacityFill.setTop(Value.of(0));
        capacityFill.setHeight(Value.of(5));
        capacityFill.setWidth(Value.of(210*warehouse.used(capacityPage)/warehouse.slots(capacityPage)));
        c.setObject("#CapacityFill.Anchor",capacityFill);
        c.set("#InventoryHint.Text",tab==Tab.GENERAL?"Click to deposit into General storage.":"Collect town output into your inventory. Use General storage for deposits.");c.set("#Hint.Text",hint);
        c.set("#Description.Text",switch(tab){case GENERAL->"General storage accepts deposits. "+warehouse.used(TownWarehouse.Page.GENERAL)+" / "+warehouse.capacity()+" slots used.";case OUTPUT->"Finished crafting output. Collect items to make room for more work. Deposits are not allowed.";case PRODUCTION->"Resources remain in their original buildings and keep those storage limits. Deposits are not allowed.";});
        for(var t:Tab.values()) {c.set("#"+t.name()+".Disabled",tab==t);e.addEventBinding(CustomUIEventBindingType.Activating,"#"+t.name(),event(t.name()),false);}
        if(localPlot!=null) {
            for(var t:Tab.values())c.set("#"+t.name()+".Visible",false);
            c.set("#Description.Text",com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,localPlot)!=null?"Mined resources stored at this workplace. Couriers collect crafting supplies here.":"Spoils collected during survival runs. Couriers collect crafting supplies here.");
            c.set("#InventoryHint.Text","Click items to collect them. Deposits are not allowed.");
        }
        c.set("#View.Text",grid?"List view":"Grid view");c.set("#Sort.Text","Sort: "+sort.name().toLowerCase(Locale.ROOT));
        c.set("#View.Visible",tab!=Tab.PRODUCTION);
        c.set("#Grid.Visible",tab!=Tab.PRODUCTION&&grid);c.set("#List.Visible",tab!=Tab.PRODUCTION&&!grid);
        c.set("#Production.Visible",tab==Tab.PRODUCTION);c.clear("#Production");c.clear("#Grid");c.clear("#List");c.clear("#PlayerItems");
        List<Entry> stock=new ArrayList<>();
        List<Building> buildings=new ArrayList<>();
        if(tab==Tab.PRODUCTION) {
            for(var plot:town.getPlotInstances()) {
                String type=plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId());
                if(plot.getState()!=PlotInstanceState.COMPLETE||!ProductionCatalog.isProductionWorkplaceConstruction(type))continue;
                var state=town.getOrCreatePlotProduction(plot.getPlotId());
                var catalog=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),type,state);
                if(catalog==null)continue;
                var definition=plugin.getConstructionCatalog().get(plot.getConstructionId());
                String name=definition==null?"Production building":definition.getDisplayName();
                if(definition!=null&&definition.getDisplayNameLangKey()!=null) {
                    var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
                    String translated=i18n==null?null:i18n.getMessage(playerRef.getLanguage(),definition.getDisplayNameLangKey());
                    if(translated!=null&&!translated.isBlank())name=translated;
                }
                List<Entry> slots=new ArrayList<>();List<Long> capacities=new ArrayList<>();Set<String> shown=new HashSet<>();
                int unlockedSlots=WorkplaceProductionUpgrades.slotCount(state);
                if("plot_barn".equals(type)) {
                    unlockedSlots=Integer.MAX_VALUE;
                    for(String item:BarnAnimalCatalog.storageItems(state))if(Item.getAssetMap().getAsset(item)!=null){
                        slots.add(new Entry(false,(short)-1,new ItemStack(item,1),plot.getPlotId(),name,state.getAmount(item),warehouse.reservedProduction(plot.getPlotId(),item)));
                        capacities.add(WorkplaceProductionUpgrades.barnCapacity(state));shown.add(item);
                    }
                } else
                for(int i=0;i<WorkplaceProductionUpgrades.MAX_SLOT_COUNT;i++) {
                    if(i>=unlockedSlots) {
                        slots.add(new Entry(false,(short)i,null,plot.getPlotId(),name,0,0));
                        capacities.add(0L);
                        continue;
                    }
                    String item=catalog.itemAtCursor(state.getSlotCursor(i));
                    var stack=item==null||Item.getAssetMap().getAsset(item)==null?null:new ItemStack(item,1);
                    slots.add(new Entry(false,(short)i,stack,plot.getPlotId(),name,item==null?0:state.getAmount(item),item==null?0:warehouse.reservedProduction(plot.getPlotId(),item)));
                    capacities.add(item==null?0:WorkplaceProductionUpgrades.effectiveMaxStorage(state,catalog,item));shown.add(item);
                }
                // Switching production must not hide resources already held in the building.
                for(var held:state.getAmounts().entrySet()) if(held.getValue()>0&&!shown.contains(held.getKey())&&Item.getAssetMap().getAsset(held.getKey())!=null) {
                    slots.add(new Entry(false,(short)-1,new ItemStack(held.getKey(),1),plot.getPlotId(),name,held.getValue(),warehouse.reservedProduction(plot.getPlotId(),held.getKey())));
                    capacities.add(WorkplaceProductionUpgrades.effectiveMaxStorage(state,catalog,held.getKey()));
                }
                String query=search.toLowerCase(Locale.ROOT);
                if(query.isBlank()||name.toLowerCase(Locale.ROOT).contains(query)||slots.stream().anyMatch(row->matches(row,query)))
                    buildings.add(new Building(plot.getPlotId(),name,plot.getSignX()+", "+plot.getSignY()+", "+plot.getSignZ(),slots,capacities,unlockedSlots));
            }
            var lodge=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),com.hexvane.aetherhaven.hunter.HuntService.CONSTRUCTION);
            if(lodge!=null) {
                var hunts=town.getHunts();List<Entry> slots=new ArrayList<>();List<Long> capacities=new ArrayList<>();
                for(var held:hunts.stock().entrySet())if(held.getValue()>0&&Item.getAssetMap().getAsset(held.getKey())!=null) {
                    slots.add(new Entry(false,(short)-1,new ItemStack(held.getKey(),1),lodge.getPlotId(),"Hunter's Lodge",held.getValue(),warehouse.reservedProduction(lodge.getPlotId(),held.getKey())));
                    capacities.add((long)hunts.capacity());
                }
                String query=search.toLowerCase(Locale.ROOT);
                if(query.isBlank()||"hunter's lodge".contains(query)||slots.stream().anyMatch(row->matches(row,query)))
                    buildings.add(new Building(lodge.getPlotId(),"Hunter's Lodge",lodge.getSignX()+", "+lodge.getSignY()+", "+lodge.getSignZ(),slots,capacities,Integer.MAX_VALUE));
            }
            for(var site:town.getApprenticeWorkplaces().sites()) {
                var output=com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,site.id());if(output==null)continue;
                List<Entry> slots=new ArrayList<>();List<Long> capacities=new ArrayList<>();
                for(var held:output.getAmounts().entrySet())if(held.getValue()>0&&Item.getAssetMap().getAsset(held.getKey())!=null){
                    slots.add(new Entry(false,(short)-1,new ItemStack(held.getKey(),1),site.id(),site.name(),held.getValue(),warehouse.reservedProduction(site.id(),held.getKey())));capacities.add(-1L);
                }
                String query=search.toLowerCase(Locale.ROOT);var point=com.hexvane.aetherhaven.workplace.NativeExcavation.bufferPoint(town,site);
                if(query.isBlank()||site.name().toLowerCase(Locale.ROOT).contains(query)||slots.stream().anyMatch(row->matches(row,query)))
                    buildings.add(new Building(site.id(),site.name()+" mineshaft output",point.x()+", "+point.y()+", "+point.z(),slots,capacities,Integer.MAX_VALUE));
            }
            for(var origin:warehouse.smelting().origins()) {
                var point=origin.location();
                var site=town.getApprenticeWorkplaces().sites().stream().filter(w->w.area().contains(point.x()+.5,point.y()+.5,point.z()+.5)).findFirst().orElse(null);
                String name=site==null?"Smelting output":site.name()+" smelting output";
                List<Entry> slots=new ArrayList<>();List<Long> capacities=new ArrayList<>();
                for(short slot=0;slot<origin.slots();slot++) {
                    var held=origin.stack(slot);if(ItemStack.isEmpty(held))continue;
                    slots.add(new Entry(false,slot,held,origin.id(),name,held.getQuantity(),warehouse.reservedSmelting(origin.id(),slot)));capacities.add((long)warehouse.smelting().limit());
                }
                String query=search.toLowerCase(Locale.ROOT);
                if(!slots.isEmpty()&&(query.isBlank()||name.toLowerCase(Locale.ROOT).contains(query)||slots.stream().anyMatch(row->matches(row,query))))
                    buildings.add(new Building(origin.id(),name,point.x()+", "+point.y()+", "+point.z(),slots,capacities,Integer.MAX_VALUE));
            }
        } else {
            var section=tab==Tab.GENERAL?TownWarehouse.Page.GENERAL:TownWarehouse.Page.OUTPUT;
            for(short i=0;i<warehouse.slots(section);i++) {
                var stack=warehouse.stack(section,i);
                if(!ItemStack.isEmpty(stack))stock.add(new Entry(false,i,stack,null,tab==Tab.GENERAL?"General storage":"Crafting output",stack.getQuantity(),warehouse.reserved(section,i)));
            }
        }
        if(localPlot!=null) {
            var activeLodge=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),com.hexvane.aetherhaven.hunter.HuntService.CONSTRUCTION);
            buildings.removeIf(building->!localPlot.equals(building.plot()));
        }
        stock.removeIf(row->!displayName(row.stack()).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))&&!row.stack().getItemId().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)));
        Comparator<Entry> order=switch(sort){case NAME->Comparator.comparing(row->displayName(row.stack()));case QUANTITY->Comparator.comparingLong(Entry::amount).reversed();case SOURCE->Comparator.comparing(Entry::location);};
        stock.sort(order.thenComparing(row->row.stack().getItemId()).thenComparingInt(Entry::slot));
        buildings.sort(sort==Sort.QUANTITY?Comparator.<Building>comparingLong(b->b.slots().stream().mapToLong(Entry::amount).sum()).reversed().thenComparing(Building::name):Comparator.comparing(Building::name).thenComparing(Building::location));
        int limit=tab==Tab.PRODUCTION?8:grid?36:5,total=tab==Tab.PRODUCTION?buildings.size():stock.size();
        int pages=Math.max(1,(total+limit-1)/limit);page=Math.clamp(page,0,pages-1);
        c.set("#Page.Text",(page+1)+" / "+pages);c.set("#Previous.Disabled",page==0);c.set("#Next.Disabled",page==pages-1);
        if(tab==Tab.PRODUCTION) {
            int index=0;
            for(var building:buildings.subList(Math.min(page*limit,total),Math.min((page+1)*limit,total))) {
                c.append("#Production","Aetherhaven/WarehouseProductionCard.ui");String at="#Production["+(index++)+"]";
                c.set(at+" #BuildingName.Text",building.name());c.set(at+" #Location.Text",building.location());
                var size=new Anchor();size.setHeight(Value.of(64+112*((building.slots().size()+4)/5)));size.setBottom(Value.of(12));c.setObject(at+".Anchor",size);
                for(int slot=0;slot<building.slots().size();slot++) {
                    var row=building.slots().get(slot);
                    render(c,e,at+" #Slots",slot,row,false,"Aetherhaven/WarehouseProductionSlot.ui");
                    String cell=at+" #Slots["+slot+"]";
                    boolean locked=row.slot()>=building.unlockedSlots();
                    c.set(cell+" #Lock.Visible",locked);
                    c.setObject(cell+".Background",new PatchStyle().setColor(Value.of(locked?"#2b3038":"#1a2932")));
                    if(locked) {
                        c.set(cell+" #Name.Text","Output slot "+(row.slot()+1));
                        c.set(cell+" #Name.Style.TextColor","#929da7");
                        c.set(cell+" #Capacity.Text","Locked");
                        c.set(cell+" #Cell.Disabled",true);
                        continue;
                    }
                    c.set(cell+" #Name.Text",row.stack()==null?"No output selected":displayName(row.stack()));
                    c.set(cell+" #Capacity.Text",building.capacities().get(slot)<0?row.amount()+" stored":warehouse.smelting().origin(building.plot())==null?row.amount()+" / "+building.capacities().get(slot)
                        :warehouse.smelting().count(row.stack().getItemId())+" / "+building.capacities().get(slot)+" town total");
                    c.set(cell+" #Cell.Disabled",row.amount()==0||row.stack()==null);
                }
            }
        } else {
            String host=grid?"#Grid":"#List";int index=0;
            for(var row:stock.subList(Math.min(page*limit,stock.size()),Math.min((page+1)*limit,stock.size()))) {
                if(grid) {
                    if(index%12==0)c.append(host,"Aetherhaven/WarehouseGridRow.ui");
                    render(c,e,host+"["+(index/12)+"]",index%12,row,false);
                } else render(c,e,host,index,row,true);
                index++;
            }
        }
        c.set("#Empty.Visible",total==0);
        c.set("#EmptyTitle.Text",!search.isBlank()?"No matching items":tab==Tab.GENERAL?"Room for your town to grow":tab==Tab.OUTPUT?"Awaiting crafted goods":"No production buildings yet");
        c.set("#EmptyHelp.Text",!search.isBlank()?"Try another item name or clear your search.":tab==Tab.GENERAL?"Click something in your inventory to store it here.":tab==Tab.OUTPUT?"Items made by townsfolk will appear here when their work is finished.":"Each completed production building will show its own output slots here.");
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        if(inventory!=null) com.hexvane.aetherhaven.ui.PlayerInventoryLayout.render(c,store,ref,inventory,(host,column,slot)->{
            var stack=inventory.getItemStack(slot);
            render(c,e,host,column,new Entry(true,slot,stack,null,"Your inventory",ItemStack.isEmpty(stack)?0:stack.getQuantity(),0),false);
        });
        c.set("#Return.Visible",localPlot!=null&&com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,localPlot)!=null);
        c.set("#Upgrade.Visible",localPlot==null);
        c.set("#Upgrade.Text","Building upgrades");
        for(String action:List.of("View","Sort","Refresh","SearchButton","Previous","Next","Upgrade","Return")) e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,event(action),false);
    }
    private EventData event(String action) {return new EventData().append("Action",action).append("Frame",Long.toString(frame)).append("@Search","#Search.Value");}
    private void render(UICommandBuilder c,UIEventBuilder e,String host,int index,Entry entry,boolean list) {
        render(c,e,host,index,entry,list,list?"Aetherhaven/WarehouseItemRow.ui":"Aetherhaven/WarehouseItemSlot.ui");
    }
    private boolean matches(Entry row,String query) {
        if(ItemStack.isEmpty(row.stack()))return false;
        return displayName(row.stack()).toLowerCase(Locale.ROOT).contains(query)||row.stack().getItemId().toLowerCase(Locale.ROOT).contains(query);
    }
    private void render(UICommandBuilder c,UIEventBuilder e,String host,int index,Entry entry,boolean list,String template) {
        c.append(host,template);String at=host+"["+index+"]";
        var stack=entry.stack();boolean filled=!ItemStack.isEmpty(stack),known=filled&&Item.getAssetMap().getAsset(stack.getItemId())!=null;
        c.set(at+" #Icon.Visible",known);c.set(at+" #Quantity.Text",filled?Long.toString(entry.amount()):"");
        c.set(at+" #Cell.Disabled",!known);
        if(known) {
            c.set(at+" #Icon.ItemId",stack.getItemId());
            String details=com.hexvane.aetherhaven.jewelry.JewelryItemIds.isJewelry(stack.getItemId())
                ?com.hexvane.aetherhaven.jewelry.JewelryTooltipMessages.toPlainEnglishDescription(stack):"";
            c.set(at+" #Cell.TooltipText",displayName(stack)+"\n"+entry.location()+"\n"+(entry.amount()-entry.reserved())+" available"+(entry.reserved()>0?"\n"+entry.reserved()+" reserved for workplaces":"")+(details.isBlank()?"":"\n"+details));
        }
        if(list){c.set(at+" #Name.Text",filled?displayName(stack):"");c.set(at+" #Source.Text",entry.reserved()>0?entry.location()+" | "+entry.reserved()+" reserved":entry.location());}
        if(!known)return;
        String id=Integer.toString(entries.size());entries.put(id,entry);
        e.addEventBinding(CustomUIEventBindingType.Activating,at+" #Cell",event("Transfer").append("Entry",id).append("Amount","STACK"),false);
        e.addEventBinding(CustomUIEventBindingType.RightClicking,at+" #Cell",event("Transfer").append("Entry",id).append("Amount","ONE"),false);
    }
    private String displayName(ItemStack stack) {
        var item=Item.getAssetMap().getAsset(stack.getItemId());
        if(item==null)return stack.getItemId();
        var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String name=i18n==null?null:i18n.getMessage(playerRef.getLanguage(),item.getTranslationKey());
        return name==null?stack.getItemId():name;
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);if(!access(town)){close();return;}
        if(localPlot!=null&&("GENERAL".equals(data.action)||"OUTPUT".equals(data.action)||"Upgrade".equals(data.action)))return;
        boolean stale=!Long.toString(frame).equals(data.frame);
        if(data.search!=null)search=data.search.strip().substring(0,Math.min(80,data.search.strip().length()));
        if("Return".equals(data.action)&&localPlot!=null&&com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,localPlot)!=null){store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.MineshaftPlannerPage(playerRef,townId,localPlot));return;}
        if("Transfer".equals(data.action)) {
            var row=entries.get(data.entry);var warehouse=town.getWarehouse();
            if(stale||row==null||warehouse.revision()!=revision||town.getHunts().revision()!=huntRevision)hint="Storage changed. The view has been refreshed. Try again.";
            else {
                var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
                var amount="ONE".equals(data.amount)?TownWarehouse.Amount.ONE:TownWarehouse.Amount.STACK;
                if(row.player()&&tab!=Tab.GENERAL)hint="Use General storage to deposit items. Output storage is for collection only.";
                else if(inventory!=null&&row.plot()!=null) {
                    var origin=warehouse.smelting().origin(row.plot());
                    var plot=town.findPlotById(row.plot());
                    var mining=com.hexvane.aetherhaven.workplace.MineshaftOutput.find(town,row.plot());
                    if(mining!=null) {
                        String item=row.stack().getItemId();int count=(int)Math.min(amount==TownWarehouse.Amount.ONE?1:row.stack().getItem().getMaxStack(),Math.max(0,mining.getAmount(item)-warehouse.reservedProduction(row.plot(),item)));
                        if(count>0&&inventory.addItemStack(new ItemStack(item,count),true,false,true).succeeded()){
                            mining.removeAmountUpTo(item,count);warehouse.markProductionWithdrawal();manager.updateTown(town);hint="Collected "+count+" items.";
                        }else hint=count==0?"Those supplies are reserved for a workplace.":"Your inventory is full.";
                    } else if(origin!=null) {
                        int count=Math.min(amount==TownWarehouse.Amount.ONE?1:row.stack().getQuantity(),Math.max(0,row.stack().getQuantity()-warehouse.reservedSmelting(origin.id(),row.slot())));
                        int moved=warehouse.smelting().withdraw(origin.id(),row.slot(),row.stack(),count,inventory,warehouse.smelting().revision());
                        hint=moved>0?"Collected "+moved+" items.":"Storage changed or your inventory is full.";
                        if(moved>0){warehouse.markProductionWithdrawal();manager.updateTown(town);}
                    } else if(plot!=null&&plot.getState()==PlotInstanceState.COMPLETE&&com.hexvane.aetherhaven.hunter.HuntService.CONSTRUCTION.equals(plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId()))) {
                        var hunts=town.getHunts();String item=row.stack().getItemId();
                        int count=(int)Math.min(amount==TownWarehouse.Amount.ONE?1:row.stack().getItem().getMaxStack(),ProductionStockSources.availableSpoils(town,plugin,item));
                        if(count>0&&inventory.addItemStack(new ItemStack(item,count),true,false,true).succeeded()) {
                            hunts.withdraw(item,count);warehouse.markProductionWithdrawal();manager.updateTown(town);hint="Collected "+count+" items.";
                        } else hint=count==0?"Those supplies are reserved for a workplace.":"There is no room for those spoils.";
                    } else if(plot!=null&&plot.getState()==PlotInstanceState.COMPLETE) {
                        var state=town.getOrCreatePlotProduction(row.plot());String type=plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId());
                        var catalog=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),type,state);
                        long count=amount==TownWarehouse.Amount.ONE?1:Math.min(row.amount(),Math.max(1,row.stack().getItem().getMaxStack()));
                        count=Math.min(count,Math.max(0,state.getAmount(row.stack().getItemId())-warehouse.reservedProduction(row.plot(),row.stack().getItemId())));
                        var result=new ProductionWithdrawal.ResultSink();
                        long moved=catalog==null?0:ProductionWithdrawal.withdrawToPlayer(ref,store,store.getComponent(ref,Player.getComponentType()),state,catalog,row.stack().getItemId(),count,result);
                        hint=moved>0?"Collected "+moved+" items.":result.needEmptyBuckets?"Bring empty buckets to collect milk.":count==0?"Those supplies are reserved for a workplace.":"There is no room for that item.";
                        if(moved>0){warehouse.markProductionWithdrawal();manager.updateTown(town);}
                    }
                } else if(inventory!=null) {
                    var section=tab==Tab.GENERAL?TownWarehouse.Page.GENERAL:TownWarehouse.Page.OUTPUT;
                    var result=row.player()?warehouse.deposit(section,inventory,row.slot(),row.stack(),amount,revision):warehouse.withdraw(section,inventory,row.slot(),row.stack(),amount,revision);
                    hint=result.error()!=null?result.error():"Transferred "+result.moved()+(result.moved()==1?" item.":" items.");
                    if(result.moved()>0)manager.updateTown(town);
                }
            }
        } else if("Upgrade".equals(data.action)) {
            var player=store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if(player!=null)player.getPageManager().openCustomPage(ref,store,
                new com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage(playerRef,townId,"warehouse_manager"));
            return;
        } else if("View".equals(data.action)){grid=!grid;page=0;}
        else if("Sort".equals(data.action)){sort=Sort.values()[(sort.ordinal()+1)%Sort.values().length];page=0;}
        else if("Previous".equals(data.action))page--;
        else if("Next".equals(data.action))page++;
        else if("SearchButton".equals(data.action)||"SearchChanged".equals(data.action))page=0;
        else if(data.action!=null)for(var value:Tab.values())if(value.name().equals(data.action)){tab=value;page=0;}
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Frame",Codec.STRING),(d,v)->d.frame=v,d->d.frame).add()
            .append(new KeyedCodec<>("Entry",Codec.STRING),(d,v)->d.entry=v,d->d.entry).add()
            .append(new KeyedCodec<>("Amount",Codec.STRING),(d,v)->d.amount=v,d->d.amount).add()
            .append(new KeyedCodec<>("@Search",Codec.STRING),(d,v)->d.search=v,d->d.search).add().build();
        String action,frame,entry,amount,search;
    }
}
