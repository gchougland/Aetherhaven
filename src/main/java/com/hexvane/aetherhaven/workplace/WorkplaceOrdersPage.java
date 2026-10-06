package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkRoster;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Recipe planning uses saved IDs; changing site equipment never discards the player's orders. */
public final class WorkplaceOrdersPage extends AetherhavenInteractiveCustomUIPage<WorkplaceOrdersPage.Data> {
    private final UUID townId, siteId;
    private final org.joml.Vector3i station;
    private String search="", selected, quantity="1", hint="Apprentices work here after their supplies arrive.";
    private WorkplaceOrders.Mode mode=WorkplaceOrders.Mode.COUNT;
    private boolean appended;
    private int recipePage;
    private static final int RECIPES_PER_PAGE=12;
    private boolean jewelryAppended, browseJewelry;
    private boolean alchemyAppended, browseAlchemy;
    private boolean browseCultivation;
    private boolean armoryAppended, browseArmory;
    private String armorClass="knight", armorTier="copper";
    private static final List<String> ARMOR_CLASSES=List.of("knight","archer","rogue","mage");
    private static final List<String> ARMOR_TIERS=List.of("copper","iron","thorium","cobalt","adamantite");
    private String potionFamily="health",potionStrength="lesser";
    private static final List<String> POTION_FAMILIES=List.of("health","stamina","signature");
    private static final List<String> POTION_STRENGTHS=List.of("lesser","small","standard","greater");
    private com.hexvane.aetherhaven.jewelry.JewelryGem jewelGem=com.hexvane.aetherhaven.jewelry.JewelryGem.RUBY;
    private com.hexvane.aetherhaven.jewelry.JewelryRarity jewelGrade=com.hexvane.aetherhaven.jewelry.JewelryRarity.COMMON;
    private boolean jewelGold,jewelNecklace;
    public WorkplaceOrdersPage(PlayerRef player,UUID townId,UUID siteId) {
        this(player,townId,siteId,null);
    }
    public WorkplaceOrdersPage(PlayerRef player,UUID townId,UUID siteId,org.joml.Vector3i station) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC); this.townId=townId; this.siteId=siteId;
        this.station=station==null?null:new org.joml.Vector3i(station);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        if(!appended) {
            c.append("Aetherhaven/WorkplaceOrdersPage.ui"); appended=true;
            c.set("#Search.Value",search);
            e.addEventBinding(CustomUIEventBindingType.ValueChanged,"#Search",
                new EventData().append("Action","SearchChanged").append("@Search","#Search.Value"),false);
        }
        var plugin=AetherhavenPlugin.get(); if(plugin==null) return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);
        if(!canManage(town)) { close(); return; }
        var site=town.getApprenticeWorkplaces().find(siteId); if(site==null) { close(); return; }
        var equipment=WorkplaceEquipment.scan(store.getExternalData().getWorld(),site.area());
        var workers=TownsfolkWorkRoster.list(town,manager,store,plugin);
        c.set("#TitleText.Text",site.name()+" | TASKS");
        c.set("#Station.Visible",station!=null);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#Station",event("Station",site),false);
        c.set("#FuelRow.Visible",site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SMELTING);
        c.set("#Fuel.Text",site.charcoalFuel()?"Fuel: charcoal":"Fuel: wood");
        e.addEventBinding(CustomUIEventBindingType.Activating,"#Fuel",event("Fuel",site).append("Id",Boolean.toString(!site.charcoalFuel())),false);
        String benches=equipment.benches().stream().map(b->WorkplaceEquipment.label(b.id())+" tier "+b.tier()).distinct().sorted().collect(java.util.stream.Collectors.joining(", "));
        String furnishings=equipment.furnishingSummary(site.type());
        if(!furnishings.isEmpty())benches+=(benches.isEmpty()?"": " | ")+furnishings+" ("+Math.round((equipment.workMultiplier(site.type())-1)*100)+"% faster work)";
        c.set("#MainBench.Text",WorkplaceRules.mainBench(site.type()));
        c.set("#Equipment.Text",!equipment.fullySimulated() ? "Part of this workplace is not loaded." : benches.isEmpty() ? "No workbench found inside this workplace." : benches);
        // Do not echo live search text back to the client; a later keystroke may already be queued.
        c.set("#OrderQuantity.Value",quantity);
        c.set("#Mode.Text",modeLabel(mode)); c.set("#OrderQuantity.Visible",mode!=WorkplaceOrders.Mode.FOREVER);
        c.set("#Hint.Text",hint);
        boolean designer=site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.JEWELRY && !browseJewelry;
        boolean alchemy=site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.ALCHEMY;
        boolean brewing=alchemy&&!browseAlchemy;
        boolean cultivation=site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.CULTIVATION;
        boolean cropSheet=cultivation&&!browseCultivation;
        boolean armory=site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.ARMOR_CRAFTING;
        boolean equipmentSheet=armory&&!browseArmory;
        c.set("#CatalogHeading.Text",cultivation?"CRYSTAL SOIL CROPS":equipmentSheet?"EQUIP YOUR GUARDS":"RECIPES");
        c.set("#JewelryDesigner.Visible",designer);c.set("#RecipeSearch.Visible",!designer&&!brewing&&!cropSheet&&!equipmentSheet);c.set("#RecipeList.Visible",!designer&&!brewing&&!cropSheet&&!equipmentSheet);
        c.set("#ArmoryDesigner.Visible",equipmentSheet);c.set("#ArmoryBrowse.Visible",armory);
        c.set("#ArmoryBrowse.Text",browseArmory?"Choose a guard kit":"Browse all recipes");
        e.addEventBinding(CustomUIEventBindingType.Activating,"#ArmoryBrowse",event("ArmoryBrowse",site),false);
        c.set("#CultivationDesigner.Visible",cropSheet);c.set("#CultivationBrowse.Visible",cultivation);
        c.set("#CultivationBrowse.Text",browseCultivation?"Choose a crop":"Browse all crops");
        e.addEventBinding(CustomUIEventBindingType.Activating,"#CultivationBrowse",event("CultivationBrowse",site),false);
        if(cultivation)c.set("#Mode.Text",mode==WorkplaceOrders.Mode.COUNT?"Grow an amount":mode==WorkplaceOrders.Mode.FOREVER?"Keep growing":"Maintain in storage");
        c.set("#AlchemyDesigner.Visible",brewing);c.set("#AlchemyBrowse.Visible",alchemy);
        c.set("#AlchemyBrowse.Text",browseAlchemy?"Choose a potion":"Browse all recipes");
        e.addEventBinding(CustomUIEventBindingType.Activating,"#AlchemyBrowse",event("AlchemyBrowse",site),false);
        c.set("#JewelryBrowse.Visible",site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.JEWELRY);
        c.set("#JewelryBrowse.Text",browseJewelry?"Design a piece":"Browse all plans");
        e.addEventBinding(CustomUIEventBindingType.Activating,"#JewelryBrowse",event("JewelryBrowse",site),false);
        if(designer)buildJewelry(c,e,site,equipment,town,workers,plugin);
        if(brewing)buildAlchemy(c,e,site,equipment,town,workers,plugin);
        if(cropSheet)buildCultivation(c,e,site,town,plugin);
        if(equipmentSheet)buildArmory(c,e,site,equipment,town,workers,plugin);
        c.clear("#Recipes"); int index=0;
        boolean recipeList=!designer&&!brewing&&!cropSheet&&!equipmentSheet;
        var matches=recipeList?plugin.getApprenticeRecipes().all().stream()
            .filter(r->r.type()==site.type()&&r.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
            .sorted(Comparator.comparing((WorkplaceRecipe r)->locked(r,site,equipment,town,workers))
                .thenComparing(WorkplaceRecipe::name,String.CASE_INSENSITIVE_ORDER).thenComparing(WorkplaceRecipe::id))
            .toList():List.<WorkplaceRecipe>of();
        int pages=Math.max(1,(matches.size()+RECIPES_PER_PAGE-1)/RECIPES_PER_PAGE);
        recipePage=Math.clamp(recipePage,0,pages-1);
        c.set("#RecipePaging.Visible",recipeList);
        c.set("#RecipePage.Text",(recipePage+1)+" / "+pages+"  |  "+matches.size()+" recipes");
        c.set("#RecipePrevious.Disabled",recipePage==0);
        c.set("#RecipeNext.Disabled",recipePage==pages-1);
        for(String action:List.of("RecipePrevious","RecipeNext"))
            e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,event(action,site),false);
        for(var recipe:matches.subList(Math.min(recipePage*RECIPES_PER_PAGE,matches.size()),Math.min((recipePage+1)*RECIPES_PER_PAGE,matches.size()))) {
            c.append("#Recipes","Aetherhaven/WorkplaceRecipeRow.ui"); String row="#Recipes["+index+++"]";
            c.set(row+" #Name.Text",recipe.name());
            boolean locked=locked(recipe,site,equipment,town,workers);
            c.set(row+" #Lock.Visible",locked);
            if(locked) {
                c.setObject(row+".Background",new com.hypixel.hytale.server.core.ui.PatchStyle().setColor(com.hypixel.hytale.server.core.ui.Value.of("#30353b")));
                c.set(row+" #Name.Style.TextColor","#92a0aa");
                c.set(row+" #Needs.Style.TextColor","#92a0aa");
            }
            outputIcon(c,row,recipe);
            c.set(row+" #Needs.Text",Character.toUpperCase(recipe.skill().id.charAt(0))+recipe.skill().id.substring(1)+" "+recipe.skillLevel()+" | "+WorkplaceEquipment.label(recipe.bench())+" tier "+recipe.benchTier()+(recipe.townRank()==null?"":" | Rank "+recipe.townRank()));
            c.set(row+" #Status.Text",status(recipe,site,equipment,town,workers));
            c.set(row+" #Choose.Text",recipe.id().equals(selected)?"Selected":locked?"View":"Choose");
            e.addEventBinding(CustomUIEventBindingType.Activating,row+" #Choose",event("Choose",site).append("Id",recipe.id()),false);
        }
        c.clear("#Orders"); index=0;
        for(var order:site.orders().list()) {
            c.append("#Orders","Aetherhaven/WorkplaceOrderRow.ui"); String row="#Orders["+index+++"]";
            var recipe=plugin.getApprenticeRecipes().find(order.recipeId());
            outputIcon(c,row,recipe);
            c.set(row+" #Name.Text",index+". "+(recipe==null?"Unavailable recipe":recipe.name()));
            c.set(row+" #Amount.Text",switch(order.mode()) {
                case COUNT -> cultivation?"Grow "+order.quantity()+(order.quantity()==1?" plant | ":" plants | ")+order.completed()+" harvested":"Craft "+order.quantity()+" batches | "+order.completed()+" complete";
                case FOREVER -> cultivation?"Keep growing while seeds are available":"Keep crafting while resources are available";
                case MAINTAIN -> "Maintain "+order.quantity()+" items in town storage";
            });
            var batch=town.getWarehouse().batches().stream().filter(b->b.workplaceId().equals(siteId)&&b.orderId().equals(order.id())).findFirst().orElse(null);
            String delivery=batch==null||!batch.orderId().equals(order.id())?null:switch(batch.state()) {
                case AWAITING_PICKUP -> "Supplies reserved. Waiting for a courier.";
                case IN_TRANSIT -> "A courier is bringing supplies.";
                case READY -> "Supplies delivered. Ready for an Apprentice.";
                case PROCESSING -> "The furnace is processing its supplies.";
                case RETURNING -> "Returning supplies to the warehouse.";
            };
            if(batch!=null&&recipe!=null&&!batch.cancelled()&&!batch.cultivation()&&!batch.smelting()
                    &&batch.state()==com.hexvane.aetherhaven.warehouse.WarehouseBatch.State.READY) {
                String crafter=null;
                for(var worker:workers) {
                    if(worker.entityId()==null)continue;
                    var workerRef=store.getExternalData().getRefFromUUID(worker.entityId());
                    if(workerRef==null||!workerRef.isValid())continue;
                    var work=store.getComponent(workerRef,ApprenticeWorkState.getComponentType());
                    if(work!=null&&work.active&&work.working&&batch.id().equals(work.batch)){crafter=worker.name();break;}
                }
                var craftRecipe=recipe.source();
                double duration=craftRecipe==null?recipe.workSeconds():craftRecipe.getTimeSeconds();
                int percent=(int)Math.min(100,Math.floor(100*batch.workSeconds()/Math.max(6,duration)));
                delivery=crafter==null?batch.workSeconds()>0?"Work paused at "+percent+"%. Waiting for an Apprentice.":delivery
                    :crafter+" is crafting | "+percent+"% complete";
            }
            if(batch!=null&&batch.cultivation()&&!batch.cancelled()) {
                int growing=0,ready=0,preparing=0;
                for(var crop:town.getWarehouse().batches()) {
                    if(!crop.workplaceId().equals(siteId)||!crop.orderId().equals(order.id())||crop.cancelled()||!crop.cultivation())continue;
                    if(!crop.cropPlanted())preparing++;
                    else if(NativeCultivationService.mature(store.getExternalData().getWorld(),crop))ready++;
                    else growing++;
                }
                delivery=growing+" growing | "+ready+" ready to harvest | "+preparing+" being prepared";
            }
            if(batch!=null&&!batch.cancelled()&&!batch.smelting()&&!town.getWarehouse().hasOutputRoom(batch.id()))
                delivery="Work paused. Make room in Crafted Goods at the warehouse.";
            c.set(row+" #Status.Text",order.mode()==WorkplaceOrders.Mode.COUNT&&order.completed()>=order.quantity()?"Completed":order.paused()?"Paused":recipe==null?"Recipe unavailable. This task is kept for later.":delivery!=null&&recipe.unlocked(town)&&recipe.source()!=null&&recipe.equipmentIssue(site,equipment)==null&&recipe.nativeUnlockIssue(town,equipment.memoriesLevel())==null?delivery:status(recipe,site,equipment,town,workers));
            c.set(row+" #Up.Disabled",index==1); c.set(row+" #Down.Disabled",index==site.orders().list().size());
            c.set(row+" #Pause.Text",order.paused()?"Resume":"Pause");
            for(String action:List.of("Up","Down","Pause","Remove")) e.addEventBinding(CustomUIEventBindingType.Activating,row+" #"+action,event(action,site).append("Id",order.id().toString()),false);
        }
        var recipe=selected==null?null:plugin.getApprenticeRecipes().find(selected);
        c.set("#Selected.Text",recipe==null?"Choose a recipe to see its materials.":recipe.name());
        c.set("#Details.Text",recipe==null?"":cultivation?status(recipe,site,equipment,town,workers):"Craft amounts count batches. Maintain targets count items in town storage.");
        var source=recipe==null?null:recipe.source();
        materials(c,"#Inputs",source==null?null:source.getInput());
        materials(c,"#Outputs",source==null?null:source.getOutputs());
        c.set("#Add.Disabled",!site.type().availableForNewWork() || recipe==null || recipe.type()!=site.type() || site.orders().list().size()>=WorkplaceOrders.MAX_ORDERS);
        if(!site.type().availableForNewWork())c.set("#Hint.Text","The Druid now grows plants at the conservatory. Existing orders are kept and reserved work can finish. No new garden batches will start.");
        for(String action:List.of("Add","Mode","Refresh","Back")) e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,event(action,site),false);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#SearchButton",event("Search",site),false);
    }
    private boolean canManage(TownRecord town) { return town!=null && town.hasMemberOrOwner(playerRef.getUuid()) && town.playerCanManageConstructions(playerRef.getUuid()); }
    private void buildArmory(UICommandBuilder c,UIEventBuilder e,ApprenticeWorkplace site,WorkplaceEquipment.Snapshot equipment,
            TownRecord town,List<TownsfolkWorkRoster.Worker> workers,AetherhavenPlugin plugin) {
        String host="#ArmoryDesigner";
        if(!armoryAppended){c.append(host,"Aetherhaven/ArmoryDesigner.ui");armoryAppended=true;}
        c.clear(host+" #Classes");int index=0;
        for(String role:ARMOR_CLASSES) {
            String row=host+" #Classes["+index+++"]";
            c.append(host+" #Classes","Aetherhaven/AlchemyFamilyChoice.ui");
            c.set(row+" #Name.Text",pretty(role));
            var plan=plugin.getApprenticeRecipes().find(armorRecipeId(role,armorTier));
            var output=plan==null||plan.source()==null?null:plan.source().getPrimaryOutput();
            c.set(row+" #Icon.Visible",output!=null&&output.getItemId()!=null);
            if(output!=null&&output.getItemId()!=null)c.set(row+" #Icon.ItemId",output.getItemId());
            c.set(row+" #Mark.Visible",role.equals(armorClass));
            e.addEventBinding(CustomUIEventBindingType.Activating,row,event("ArmorClass",site).append("Id",role),false);
        }
        c.clear(host+" #Tiers");index=0;
        for(String tier:ARMOR_TIERS) {
            String row=host+" #Tiers["+index+++"]";
            c.append(host+" #Tiers","Aetherhaven/JewelryGradeChoice.ui");
            var plan=plugin.getApprenticeRecipes().find(armorRecipeId(armorClass,tier));
            c.set(row+" #Name.Text",pretty(tier));
            c.set(row+" #Skill.Text",plan==null?"No recipe":plan.townRank()==null?"Skill "+plan.skillLevel():"Rank "+plan.townRank()+" | Skill "+plan.skillLevel());
            c.set(row+" #Mark.Visible",tier.equals(armorTier));
            e.addEventBinding(CustomUIEventBindingType.Activating,row,event("ArmorTier",site).append("Id",tier),false);
        }
        var choice=plugin.getApprenticeRecipes().find(armorRecipeId(armorClass,armorTier));
        if(choice!=null&&choice.type()!=site.type())choice=null;
        selected=choice==null?null:choice.id();
        var output=choice==null||choice.source()==null?null:choice.source().getPrimaryOutput();
        c.set(host+" #Preview.Visible",output!=null&&output.getItemId()!=null);
        var kit=output==null?null:plugin.getGuardKits().find(output.getItemId());
        if(output!=null&&output.getItemId()!=null) {
            c.set(host+" #Preview.ItemId",output.getItemId());
            c.set(host+" #Preview.TooltipTextSpans",materialName(output));
        }
        c.set(host+" #PreviewName.Text",choice==null?"Recipe unavailable":choice.name());
        c.set(host+" #Bonuses.Text",kit==null?"":kit.description());
        c.set(host+" #Requirements.Text",choice==null?"":"Blacksmithing "+choice.skillLevel()+" | Bench tier "+choice.benchTier()+(choice.townRank()==null?"":" | Rank "+choice.townRank()));
        String issue=choice==null?"Browse all recipes for other armor plans.":status(choice,site,equipment,town,workers);
        String unlock=choice==null?null:choice.unlockIssue(town);
        if(unlock!=null&&!issue.contains(unlock))issue=unlock+" "+issue;
        c.set(host+" #Status.Text",issue);
    }
    private static String armorRecipeId(String role,String tier) { return "craft_"+tier+"_"+role+"_kit"; }
    private void buildCultivation(UICommandBuilder c,UIEventBuilder e,ApprenticeWorkplace site,TownRecord town,AetherhavenPlugin plugin) {
        c.clear("#CropFamilies");int familyIndex=0;
        for(String family:List.of("Health","Stamina","Mana")) {
            String row="#CropFamilies["+familyIndex+++"]";
            c.append("#CropFamilies","Aetherhaven/CultivationFamilyRow.ui");
            c.set(row+" #Family.Text",family.toUpperCase(Locale.ROOT));
            for(int tier=1;tier<=3;tier++) {
                var crop=new AlchemicalCrop(family,tier);
                var recipe=plugin.getApprenticeRecipes().find("cultivate_"+family.toLowerCase(Locale.ROOT)+tier);
                String card=row+" #Crops["+(tier-1)+"]";
                c.append(row+" #Crops","Aetherhaven/CultivationCropChoice.ui");
                c.set(card+" #Icon.ItemId",crop.item());
                c.set(card+" #Name.TextSpans",materialName(new MaterialQuantity(crop.item(),null,null,1,null)));
                c.set(card+" #Skill.Text","Farming "+(recipe==null?crop.skill():recipe.skillLevel()));
                c.set(card+" #Mark.Visible",recipe!=null&&recipe.id().equals(selected));
                c.set(card+".Disabled",recipe==null);
                if(recipe!=null)e.addEventBinding(CustomUIEventBindingType.Activating,card,event("Choose",site).append("Id",recipe.id()),false);
            }
        }
        long planted=town.getWarehouse().batches().stream().filter(b->b.workplaceId().equals(site.id())&&b.cultivation()&&!b.cancelled()).count();
        c.set("#Beds.Text",planted+" / "+com.hexvane.aetherhaven.warehouse.TownWarehouse.CULTIVATION_BEDS+" beds planned. One Apprentice plants and harvests. Growth is natural.");
    }
    private void buildAlchemy(UICommandBuilder c,UIEventBuilder e,ApprenticeWorkplace site,WorkplaceEquipment.Snapshot equipment,
            TownRecord town,List<TownsfolkWorkRoster.Worker> workers,AetherhavenPlugin plugin) {
        String host="#AlchemyDesigner";
        if(!alchemyAppended){c.append(host,"Aetherhaven/AlchemyDesigner.ui");alchemyAppended=true;}
        c.clear(host+" #Families");int index=0;
        for(String family:POTION_FAMILIES) {
            String row=host+" #Families["+index+++"]";
            c.append(host+" #Families","Aetherhaven/AlchemyFamilyChoice.ui");
            c.set(row+" #Name.Text",family.equals("signature")?"Energy":pretty(family));
            c.set(row+" #Icon.ItemId","Potion_"+pretty(family)+"_Lesser");
            c.set(row+" #Mark.Visible",family.equals(potionFamily));
            e.addEventBinding(CustomUIEventBindingType.Activating,row,event("PotionFamily",site).append("Id",family),false);
        }
        c.clear(host+" #Strengths");index=0;
        for(String strength:POTION_STRENGTHS) {
            String row=host+" #Strengths["+index+++"]";
            c.append(host+" #Strengths","Aetherhaven/JewelryGradeChoice.ui");
            var plan=plugin.getApprenticeRecipes().find(potionRecipeId(potionFamily,strength));
            c.set(row+" #Name.Text",pretty(strength));
            c.set(row+" #Skill.Text",plan==null?"No recipe":"Skill "+plan.skillLevel());
            c.set(row+" #Mark.Visible",strength.equals(potionStrength));
            e.addEventBinding(CustomUIEventBindingType.Activating,row,event("PotionStrength",site).append("Id",strength),false);
        }
        var choice=plugin.getApprenticeRecipes().find(potionRecipeId(potionFamily,potionStrength));
        if(choice!=null&&choice.type()!=site.type())choice=null;
        selected=choice==null?null:choice.id();
        var source=choice==null?null:choice.source();
        var output=source==null?null:source.getPrimaryOutput();
        c.set(host+" #Preview.Visible",output!=null&&output.getItemId()!=null);
        if(output!=null&&output.getItemId()!=null) {
            c.set(host+" #Preview.ItemId",output.getItemId());
            c.set(host+" #Preview.TooltipTextSpans",materialName(output));
        }
        c.set(host+" #PreviewName.Text",choice==null?"Recipe unavailable":choice.name());
        c.set(host+" #Requirements.Text",choice==null?"":"Alchemy "+choice.skillLevel()+"  |  Bench tier "+choice.benchTier());
        c.set(host+" #Status.Text",choice==null?"Browse all recipes to see other brewing options.":status(choice,site,equipment,town,workers));
    }
    private static String potionRecipeId(String family,String strength) {
        return "brew_"+(strength.equals("standard")?"":strength+"_")+family;
    }
    private void buildJewelry(UICommandBuilder c,UIEventBuilder e,ApprenticeWorkplace site,WorkplaceEquipment.Snapshot equipment,
            TownRecord town,List<TownsfolkWorkRoster.Worker> workers,AetherhavenPlugin plugin) {
        String host="#JewelryDesigner";
        if(!jewelryAppended){c.append(host,"Aetherhaven/JewelryDesigner.ui");jewelryAppended=true;}
        String output=com.hexvane.aetherhaven.jewelry.JewelryCraftingItems.outputItemId(jewelNecklace,jewelGold,jewelGem);
        var choice=plugin.getApprenticeRecipes().all().stream().filter(r->r.type()==site.type()&&r.rarity()==jewelGrade&&output.equals(r.output())).findFirst().orElse(null);
        selected=choice==null?null:choice.id();
        c.clear(host+" #Gems");int i=0;
        for(var gem:com.hexvane.aetherhaven.jewelry.JewelryGem.values()) {
            String row=host+" #Gems["+i+++"]";c.append(host+" #Gems","Aetherhaven/JewelryGemChoice.ui");
            c.set(row+" #Icon.ItemId","Rock_Gem_"+pretty(gem.name()));c.set(row+" #Mark.Visible",gem==jewelGem);
            c.set(row+".TooltipText",pretty(gem.name()));
            e.addEventBinding(CustomUIEventBindingType.Activating,row,event("JewelryGem",site).append("Id",gem.name()),false);
        }
        c.clear(host+" #Grades");i=0;
        for(var grade:com.hexvane.aetherhaven.jewelry.JewelryRarity.values()) {
            String row=host+" #Grades["+i+++"]";c.append(host+" #Grades","Aetherhaven/JewelryGradeChoice.ui");
            var plan=plugin.getApprenticeRecipes().all().stream().filter(r->r.type()==site.type()&&r.rarity()==grade&&output.equals(r.output())).findFirst().orElse(null);
            c.set(row+" #Name.Text",pretty(grade.name()));c.set(row+" #Skill.Text",plan==null?"No plan":"Skill "+plan.skillLevel());
            c.set(row+" #Mark.Visible",grade==jewelGrade);
            e.addEventBinding(CustomUIEventBindingType.Activating,row,event("JewelryGrade",site).append("Id",grade.name()),false);
        }
        for(String option:List.of("Silver","Gold","Ring","Necklace")) {
            boolean chosen=switch(option){case "Silver"->!jewelGold;case "Gold"->jewelGold;case "Ring"->!jewelNecklace;default->jewelNecklace;};
            c.set(host+" #"+option+".Disabled",chosen);
            e.addEventBinding(CustomUIEventBindingType.Activating,host+" #"+option,event("JewelryShape",site).append("Id",option),false);
        }
        c.set(host+" #Preview.ItemId",output);c.set(host+" #PreviewName.Text",choice==null?"Plan unavailable":choice.name());
        c.set(host+" #Requirements.Text",choice==null?"":"Jewelry skill "+choice.skillLevel()+"  |  "+Math.round(choice.workSeconds())+" seconds");
        c.set(host+" #Appraisal.Text",equipment.tier("Aetherhaven_Appraisal_Bench")>0?"Appraised when finished":"Unidentified when finished");
        c.set(host+" #Status.Text",choice==null?"Browse all plans to find recipes supplied by other mods.":status(choice,site,equipment,town,workers));
    }
    private static String pretty(String word){return Character.toUpperCase(word.charAt(0))+word.substring(1).toLowerCase(Locale.ROOT);}
    private EventData event(String action,ApprenticeWorkplace site) {
        return new EventData().append("Action",action).append("Revision",Long.toString(site.orders().revision()))
            .append("@Quantity","#OrderQuantity.Value").append("@Search","#Search.Value");
    }
    private static String modeLabel(WorkplaceOrders.Mode mode) {
        return switch(mode) { case COUNT -> "Craft an amount"; case FOREVER -> "Craft forever"; case MAINTAIN -> "Maintain in storage"; };
    }
    private static String status(WorkplaceRecipe recipe,ApprenticeWorkplace site,WorkplaceEquipment.Snapshot equipment,TownRecord town,List<TownsfolkWorkRoster.Worker> workers) {
        var source=recipe.source();
        if(source==null) return "Recipe unavailable. This task is kept for later.";
        String issue=recipe.equipmentIssue(site,equipment); if(issue!=null) return issue;
        if(!recipe.unlocked(town)) return recipe.unlockIssue(town);
        String nativeIssue=recipe.nativeUnlockIssue(town,equipment.memoriesLevel());
        if(nativeIssue!=null)return nativeIssue;
        boolean worker=workers.stream().anyMatch(w->w.progress().skill(recipe.skill())>=recipe.skillLevel()
            && w.progress().canPerform(recipe.type(),w.guard(),town.getPayroll().overdue(w.characterId()),w.alive()));
        return worker?"Equipment ready. Awaiting materials and work.":"Enable an employed Apprentice with skill "+recipe.skillLevel()+" in the journal.";
    }
    private static boolean locked(WorkplaceRecipe recipe,ApprenticeWorkplace site,WorkplaceEquipment.Snapshot equipment,
                                  TownRecord town,List<TownsfolkWorkRoster.Worker> workers) {
        return recipe.source()==null || recipe.equipmentIssue(site,equipment)!=null || !recipe.unlocked(town)
            || recipe.nativeUnlockIssue(town,equipment.memoriesLevel())!=null
            || workers.stream().noneMatch(w->w.progress().skill(recipe.skill())>=recipe.skillLevel());
    }
    private static void outputIcon(UICommandBuilder c,String row,WorkplaceRecipe rule) {
        var source=rule==null?null:rule.source();
        var output=source==null?null:source.getPrimaryOutput();
        if(output==null && source!=null && source.getOutputs()!=null && source.getOutputs().length>0) output=source.getOutputs()[0];
        String item=output==null?null:output.getItemId();
        c.set(row+" #Icon.Visible",item!=null);
        if(item!=null) { c.set(row+" #Icon.ItemId",item); c.set(row+" #Icon.TooltipTextSpans",materialName(output)); }
    }
    private static Message materialName(MaterialQuantity material) {
        if(material.getItemId()!=null) {
            var item=Item.getAssetMap().getAsset(material.getItemId());
            return item==null?Message.raw(material.getItemId()):Message.translation(item.getTranslationKey());
        }
        return Message.raw(material.getResourceTypeId()==null?"Matching material":material.getResourceTypeId().replace('_',' '));
    }
    static void materials(UICommandBuilder c,String container,MaterialQuantity[] materials) {
        c.clear(container); if(materials==null) return;
        int index=0;
        for(var material:materials) {
            String host=container+"["+index/2+"]";
            if(index%2==0)c.append(container,"Aetherhaven/WorkplaceMaterialGridRow.ui");
            c.append(host,"Aetherhaven/WorkplaceMaterialCard.ui"); String row=host+"["+index++%2+"]";
            c.set(row+" #Name.TextSpans",materialName(material)); c.set(row+" #Quantity.Text","x"+material.getQuantity());
            c.set(row+".TooltipTextSpans",materialName(material).insert(Message.raw(" | "+material.getQuantity())));
            var requirement=material.getResourceTypeId()!=null?com.hexvane.aetherhaven.construction.MaterialRequirement.ofResourceType(material.getResourceTypeId(),material.getQuantity())
                :material.getItemId()!=null?com.hexvane.aetherhaven.construction.MaterialRequirement.ofItem(material.getItemId(),material.getQuantity()):null;
            String path=requirement==null?null:com.hexvane.aetherhaven.ui.UiMaterialIcons.assetPathFor(requirement);
            c.set(row+" #Icon.Visible",path!=null); if(path!=null) c.set(row+" #Icon.AssetPath",path);
        }
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plugin=AetherhavenPlugin.get(); if(plugin==null) return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId); if(!canManage(town)) { close(); return; }
        var site=town.getApprenticeWorkplaces().find(siteId); if(site==null) { close(); return; }
        if("Station".equals(data.action)&&station!=null) {
            store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,
                new WorkplaceStationPage(playerRef,townId,siteId,station));return;
        }
        if(data.quantity!=null) quantity=data.quantity.strip();
        if(data.search!=null) {
            String query=data.search.strip().substring(0,Math.min(data.search.strip().length(),80));
            if(!query.equals(search))recipePage=0;
            search=query;
        }
        if("RecipePrevious".equals(data.action))recipePage--;
        if("RecipeNext".equals(data.action))recipePage++;
        if("CultivationBrowse".equals(data.action)&&site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.CULTIVATION)browseCultivation=!browseCultivation;
        if(site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.ARMOR_CRAFTING) {
            if("ArmoryBrowse".equals(data.action))browseArmory=!browseArmory;
            if("ArmorClass".equals(data.action)&&data.id!=null&&ARMOR_CLASSES.contains(data.id))armorClass=data.id;
            if("ArmorTier".equals(data.action)&&data.id!=null&&ARMOR_TIERS.contains(data.id))armorTier=data.id;
        }
        if(site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.ALCHEMY) {
            if("AlchemyBrowse".equals(data.action))browseAlchemy=!browseAlchemy;
            if("PotionFamily".equals(data.action)&&data.id!=null&&POTION_FAMILIES.contains(data.id))potionFamily=data.id;
            if("PotionStrength".equals(data.action)&&data.id!=null&&POTION_STRENGTHS.contains(data.id))potionStrength=data.id;
        }
        if(site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.JEWELRY) {
            try {
                if("JewelryBrowse".equals(data.action))browseJewelry=!browseJewelry;
                if("JewelryGem".equals(data.action))jewelGem=com.hexvane.aetherhaven.jewelry.JewelryGem.valueOf(data.id);
                if("JewelryGrade".equals(data.action))jewelGrade=com.hexvane.aetherhaven.jewelry.JewelryRarity.valueOf(data.id);
                if("JewelryShape".equals(data.action))switch(data.id){case "Silver"->jewelGold=false;case "Gold"->jewelGold=true;case "Ring"->jewelNecklace=false;case "Necklace"->jewelNecklace=true;default->{}}
            }catch(IllegalArgumentException | NullPointerException ignored){return;}
        }
        if("Back".equals(data.action)) {
            store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,new WorkplacePage(playerRef,townId,siteId,site.area())); return;
        }
        if("Fuel".equals(data.action)&&site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SMELTING) {
            site.setCharcoalFuel(Boolean.parseBoolean(data.id));manager.updateTown(town);hint="Fuel choice saved for new deliveries. Existing supplies stay with their task.";
        } else if("Choose".equals(data.action)) selected=data.id;
        else if("Mode".equals(data.action)) mode=WorkplaceOrders.Mode.values()[(mode.ordinal()+1)%3];
        else if("Add".equals(data.action)) {
            if(!site.type().availableForNewWork())return;
            var recipe=selected==null?null:plugin.getApprenticeRecipes().find(selected);
            if(recipe==null || recipe.type()!=site.type()) hint="Choose a recipe for this workplace.";
            else try {
                String error=site.orders().add(Long.parseLong(data.revision),recipe.id(),mode,mode==WorkplaceOrders.Mode.FOREVER?1:Integer.parseInt(quantity));
                hint=error==null?"Task added. Enable a skilled Apprentice in the journal.":error;
                if(error==null) manager.updateTown(town);
            } catch(RuntimeException error) { hint="Enter a whole amount from 1 to 9999."; }
        } else if(data.action!=null && List.of("Up","Down","Pause","Remove").contains(data.action)) {
            try {
                String error=site.orders().change(Long.parseLong(data.revision),UUID.fromString(data.id),data.action);
                hint=error==null?"Task list updated.":error;
                if(error==null) {
                    if("Remove".equals(data.action)) {
                        for(var batch:List.copyOf(town.getWarehouse().batches()))
                            if(batch.workplaceId().equals(siteId)&&batch.orderId().toString().equals(data.id))town.getWarehouse().cancel(batch.id());
                    }
                    manager.updateTown(town);
                }
            } catch(RuntimeException error) { hint="Refresh the task list and try again."; }
        }
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Id",Codec.STRING),(d,v)->d.id=v,d->d.id).add()
            .append(new KeyedCodec<>("Revision",Codec.STRING),(d,v)->d.revision=v,d->d.revision).add()
            .append(new KeyedCodec<>("@Quantity",Codec.STRING),(d,v)->d.quantity=v,d->d.quantity).add()
            .append(new KeyedCodec<>("@Search",Codec.STRING),(d,v)->d.search=v,d->d.search).add().build();
        String action,id,revision,quantity,search;
    }
}
