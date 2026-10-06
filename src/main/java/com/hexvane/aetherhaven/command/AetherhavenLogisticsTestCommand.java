package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.autonomy.VillagerLifeSystem;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerState;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3d;

/** Deliberate operator fixtures. Never teleports workers or finishes work. The explicit stock action grants labeled test supplies. */
final class AetherhavenLogisticsTestCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> action=withRequiredArg("action","courier, worker, smith, garden, garden2, ranch, rancher, cultivation, food, foodstatus, soil, kit, armory, jewelry, alchemy, artisan, stock, queue or status",ArgTypes.STRING);
    private final DebugTownTargetArgs target;
    AetherhavenLogisticsTestCommand() {
        super("logistics","Prepare and inspect physical delivery tests");setPermissionGroups("hytale:WorldEditor");target=DebugTownTargetArgs.registerOn(this);
    }
    @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> player,PlayerRef pr,World world) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var resolved=target.resolve(ctx,world,store,player,pr,true);
        if(!resolved.isOk()){ctx.sendMessage(resolved.error());return;}
        var town=resolved.townOrThrow();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        switch(ctx.get(action)) {
            case "artisan" -> {
                for(String recipeId:List.of("craft_repair_kit","tan_light_leather")) {
                    var recipe=plugin.getApprenticeRecipes().find(recipeId);
                    if(recipe==null||recipe.source()==null){report(ctx,"Missing artisan recipe: "+recipeId);continue;}
                    var site=town.getApprenticeWorkplaces().sites().stream().filter(s->s.type()==recipe.type()).findFirst().orElse(null);
                    if(site==null){report(ctx,"Create a "+recipe.workplace()+" workplace first.");continue;}
                    if(site.orders().list().stream().anyMatch(o->recipeId.equals(o.recipeId()))) {
                        report(ctx,"An artisan test task already exists at "+site.name()+". No extra materials were granted.");continue;
                    }
                    var inputs=recipe.source().getInput();
                    if(inputs==null||Arrays.stream(inputs).anyMatch(m->m==null||m.getItemId()==null)) {
                        report(ctx,"This fixture needs explicit item ingredients: "+recipeId);continue;
                    }
                    String error=site.orders().add(site.orders().revision(),recipeId,WorkplaceOrders.Mode.COUNT,1);
                    if(error!=null){report(ctx,error);continue;}
                    var inventory=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)1);
                    for(var material:inputs) {
                        var stack=material.toItemStack();
                        inventory.setItemStackForSlot((short)0,stack,false);
                        var moved=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,(short)0,stack,TownWarehouse.Amount.STACK,town.getWarehouse().revision());
                        report(ctx,"Artisan test supplies: "+stack.getItemId()+" x"+moved.moved()+" of "+stack.getQuantity());
                    }
                    manager.updateTown(town);
                    report(ctx,"Added one "+recipe.name()+" task at "+site.name()+". Couriers and skilled Apprentices must perform the work normally.");
                }
            }
            case "cultivation" -> report(ctx,"Herb Garden workplaces have been removed. Use the Druid Conservatory for ingredient production.");
            case "antidote" -> {
                var site=town.getApprenticeWorkplaces().sites().stream().filter(s->s.type()==TownsfolkWorkType.ALCHEMY).findFirst().orElse(null);
                if(site==null){report(ctx,"Create an alchemy workplace first.");return;}
                if(site.orders().list().stream().anyMatch(o->"brew_antidote".equals(o.recipeId()))){report(ctx,"An antidote test task already exists. Use the workplace menu to add more.");return;}
                String error=site.orders().add(site.orders().revision(),"brew_antidote",WorkplaceOrders.Mode.COUNT,1);
                if(error!=null){report(ctx,error);return;}
                var supplies=List.of(new ItemStack("Potion_Empty",1),new ItemStack("Ingredient_Fibre",5),new ItemStack("Ingredient_Life_Essence",2));
                var inventory=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)supplies.size());
                for(short i=0;i<supplies.size();i++) {
                    inventory.setItemStackForSlot(i,supplies.get(i),false);
                    var moved=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,i,supplies.get(i),TownWarehouse.Amount.STACK,town.getWarehouse().revision());
                    report(ctx,"Antidote test supplies: "+supplies.get(i).getItemId()+" x"+moved.moved());
                }
                manager.updateTown(town);report(ctx,"Added one antidote task. No venom granted. Couriers must collect earned venom from lodge storage and a skilled alchemist must brew it.");
            }
            case "alchemy" -> {
                int available=0,missing=0;
                for(var recipe:plugin.getApprenticeRecipes().all()) {
                    if(recipe.type()!=TownsfolkWorkType.ALCHEMY&&recipe.type()!=TownsfolkWorkType.CULTIVATION)continue;
                    var source=recipe.source();
                    boolean valid=source!=null&&!source.isKnowledgeRequired()&&source.getOutputs()!=null;
                    if(valid)for(var output:source.getOutputs())valid &= output.getItemId()!=null
                        &&com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(output.getItemId())!=null;
                    if(valid)available++;else missing++;
                    report(ctx,recipe.name()+" | "+(valid?"Available":"Unavailable")+" | skill "+recipe.skillLevel()
                        +" | "+WorkplaceEquipment.label(recipe.bench())+" tier "+recipe.benchTier());
                }
                report(ctx,"Alchemy plans: "+available+" available, "+missing+" unavailable. No items or progress were granted.");
            }
            case "jewelry" -> {
                var sites=town.getApprenticeWorkplaces();
                if(sites.sites().stream().anyMatch(s->"Test Jeweler".equals(s.name()))){report(ctx,"The test jeweler already exists. Use its task menu to add more plans.");return;}
                var p=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                int x=(int)Math.floor(p.x),y=(int)Math.floor(p.y),z=(int)Math.floor(p.z);
                var area=new WorkplaceArea(x-2,y,z-2,x+3,z+3);
                for(int bx=x-2;bx<=x+2;bx++)for(int bz=z-2;bz<=z+2;bz++)for(int by=y;by<y+3;by++)
                    if(com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockSectionAt(world,bx,by,bz)==null
                        ||com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockId(world,bx,by,bz)!=0){report(ctx,"Find an empty, loaded 5 by 5 space for the jeweler fixture.");return;}
                if(!VillagerBlockUtil.isNpcStandColumn(world,x,y,z)){report(ctx,"The fixture needs solid ground at your feet.");return;}
                var recipe=plugin.getApprenticeRecipes().find("jewel_ring_silver_ruby_common");
                if(recipe==null||recipe.source()==null){report(ctx,"The Jewelry module and its workplace recipes must be loaded.");return;}
                String error=sites.unlock(town,TownsfolkWorkType.JEWELRY);
                if(error==null)error=sites.validate(town,null,TownsfolkWorkType.JEWELRY,area,"Test Jeweler");
                if(error!=null){report(ctx,error);return;}
                if(!com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,WorkplaceEquipment.JEWELRY_BENCH,0,0)){
                    report(ctx,"The jewelry bench could not be placed.");return;
                }
                error=sites.save(town,null,TownsfolkWorkType.JEWELRY,area,"Test Jeweler");
                if(error!=null){report(ctx,error);return;}
                var site=sites.sites().getLast();site.orders().add(0,recipe.id(),WorkplaceOrders.Mode.COUNT,1);
                var supplies=List.of(new ItemStack("Ingredient_Bar_Silver",5),new ItemStack("Rock_Gem_Ruby",1),new ItemStack("Ingredient_Life_Essence",1));
                var inventory=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)supplies.size());
                for(short i=0;i<supplies.size();i++) {
                    inventory.setItemStackForSlot(i,supplies.get(i),false);
                    var moved=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,i,supplies.get(i),TownWarehouse.Amount.STACK,town.getWarehouse().revision());
                    report(ctx,"Granted jeweler test supplies: "+supplies.get(i).getItemId()+" x"+moved.moved());
                }
                manager.updateTown(town);report(ctx,"Created Test Jeweler with one Common silver ruby ring task. Delivery and work must happen normally.");
            }
            case "armory" -> {
                var sites=town.getApprenticeWorkplaces();
                if(sites.sites().stream().anyMatch(s->"Test Armory".equals(s.name()))){report(ctx,"The test armory already exists. No supplies were added.");return;}
                var p=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                int x=(int)Math.floor(p.x),y=(int)Math.floor(p.y),z=(int)Math.floor(p.z);
                var area=new WorkplaceArea(x-2,y,z-2,x+3,z+3);
                for(int bx=x-2;bx<=x+2;bx++)for(int bz=z-2;bz<=z+2;bz++)for(int by=y;by<y+3;by++)
                    if(com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockSectionAt(world,bx,by,bz)==null
                        ||com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockId(world,bx,by,bz)!=0){report(ctx,"Find an empty, loaded 5 by 5 space for the armory fixture.");return;}
                if(!VillagerBlockUtil.isNpcStandColumn(world,x,y,z)){report(ctx,"The fixture needs solid ground at your feet.");return;}
                var recipe=plugin.getApprenticeRecipes().find("craft_copper_knight_kit");
                if(recipe==null||recipe.source()==null){report(ctx,"Load the Copper Knight Kit recipe first.");return;}
                String error=sites.unlock(town,TownsfolkWorkType.ARMOR_CRAFTING);
                if(error==null)error=sites.validate(town,null,TownsfolkWorkType.ARMOR_CRAFTING,area,"Test Armory");
                if(error!=null){report(ctx,error);return;}
                if(!com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,"Bench_Armour",0,0)){
                    report(ctx,"The armor bench could not be placed.");return;
                }
                error=sites.save(town,null,TownsfolkWorkType.ARMOR_CRAFTING,area,"Test Armory");
                if(error!=null){report(ctx,error);return;}
                sites.sites().getLast().orders().add(0,recipe.id(),WorkplaceOrders.Mode.COUNT,1);
                var inputs=recipe.source().getInput();
                var inventory=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)inputs.length);
                for(short i=0;i<inputs.length;i++) {
                    var stack=inputs[i].toItemStack();inventory.setItemStackForSlot(i,stack,false);
                    var moved=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,i,stack,TownWarehouse.Amount.STACK,town.getWarehouse().revision());
                    report(ctx,"Granted armory test supplies: "+stack.getItemId()+" x"+moved.moved());
                }
                manager.updateTown(town);report(ctx,"Created Test Armory with one Copper Knight Kit order. Couriers and Apprentices must deliver and craft normally.");
            }
            case "kit" -> {
                var site=town.getApprenticeWorkplaces().sites().stream().filter(s->"Test Smithy".equals(s.name())).findFirst().orElse(null);
                if(site==null){report(ctx,"Create the test smithy fixture first.");return;}
                if(site.orders().list().stream().anyMatch(o->"craft_smith_kit".equals(o.recipeId()))){report(ctx,"The smith kit test order already exists. No supplies were added.");return;}
                var supplies=List.of(new ItemStack("Ingredient_Bar_Iron",4),new ItemStack("Ingredient_Leather_Light",4),new ItemStack("Wood_Beech_Trunk",4));
                var inventory=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)supplies.size());
                for(short i=0;i<supplies.size();i++) {
                    inventory.setItemStackForSlot(i,supplies.get(i),false);
                    var transfer=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,i,supplies.get(i),TownWarehouse.Amount.STACK,town.getWarehouse().revision());
                    report(ctx,"Granted kit test supplies to General storage: "+supplies.get(i).getItemId()+" x"+transfer.moved());
                }
                String error=site.orders().add(site.orders().revision(),"craft_smith_kit",WorkplaceOrders.Mode.COUNT,1);
                manager.updateTown(town);
                report(ctx,error==null?"Added one smith kit task. Normal skill, courier, equipment, wage and physical work checks still apply.":error);
            }
            case "garden", "garden2" -> {
                String gardenName="garden2".equals(ctx.get(action))?"Test Garden 2":"Test Garden";
                var sites=town.getApprenticeWorkplaces();
                if(sites.sites().stream().anyMatch(s->gardenName.equals(s.name()))){report(ctx,"That test garden already exists.");return;}
                var p=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                int x=(int)Math.floor(p.x),y=(int)Math.floor(p.y),z=(int)Math.floor(p.z);
                var area=new WorkplaceArea(x-3,y,z-3,x+4,z+4);
                for(int bx=x-3;bx<=x+3;bx++)for(int bz=z-3;bz<=z+3;bz++)for(int by=y;by<y+3;by++) {
                    if(com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockSectionAt(world,bx,by,bz)==null
                        ||com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockId(world,bx,by,bz)!=0){report(ctx,"Find an empty, loaded 7 by 7 space for the garden fixture.");return;}
                }
                var error=sites.unlock(town,TownsfolkWorkType.FARMING);
                if(error==null)error=sites.validate(town,null,TownsfolkWorkType.FARMING,area,gardenName);
                if(error!=null){report(ctx,error);return;}
                if(!VillagerBlockUtil.isNpcStandColumn(world,x,y,z)){report(ctx,"The fixture needs solid ground at your feet.");return;}
                for(int dx:new int[]{-2,2})for(int dz:new int[]{0,2}) {
                    var soil=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world,x+dx,y-1,z+dz);
                    if(soil==null||!Set.of("Soil_Grass","Soil_Dirt","Soil_Dirt_Tilled").contains(soil.getId())) {
                        report(ctx,"The fixture needs grass or dirt beneath all four crop cells.");return;
                    }
                }
                boolean placed=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x,y,z-2,"Bench_Farming",0,0);
                for(int dx:new int[]{-2,2})for(int dz:new int[]{0,2}) {
                    placed &= com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x+dx,y-1,z+dz,"Soil_Dirt_Tilled",0,0);
                    placed &= com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x+dx,y,z+dz,"Plant_Crop_Wheat_Block",0,0);
                }
                if(!placed){report(ctx,"A fixture block could not be placed. Inspect the partial garden before retrying.");return;}
                error=sites.save(town,null,TownsfolkWorkType.FARMING,area,gardenName);manager.updateTown(town);
                report(ctx,error==null?"Created "+gardenName+" with a granted farming bench and four wheat plants on cultivated soil. An Apprentice must tend it normally to provide food.":error);
            }
            case "rancher" -> {
                var plot=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),"plot_barn");
                String role="Aetherhaven_Rancher";
                boolean[] loaded={false};
                store.forEachChunk(com.hypixel.hytale.component.query.Query.and(com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType(),com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType()),(chunk,buffer)->{
                    for(int i=0;i<chunk.size();i++)if(town.getTownId().equals(chunk.getComponent(i,com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType()).getTownId())
                        &&role.equals(chunk.getComponent(i,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType()).getRoleName()))loaded[0]=true;
                });
                if(loaded[0]||town.getResidentNpcRecords().stream().anyMatch(r->role.equals(r.getNpcRoleId()))) {
                    report(ctx,"The Rancher already exists or has a saved record. Use ordinary recruitment or Gaia recovery.");return;
                }
                if(plot==null){report(ctx,"Complete the Barn first.");return;}
                var blocked=TownAdmission.blocked(plugin,town,null,role);if(blocked!=null){ctx.sendMessage(blocked);return;}
                var p=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                if(!plot.containsWorldBlock((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z))
                    ||!VillagerBlockUtil.isNpcStandColumn(world,(int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z))) {
                    report(ctx,"Stand in a clear, loaded cell inside the completed Barn.");return;
                }
                var npc=com.hexvane.aetherhaven.villager.TownVillagerSpawner.spawn(store,role,new Vector3d(p),
                    new com.hexvane.aetherhaven.villager.TownVillagerBinding(town.getTownId(),"rancher",plot.getPlotId(),plot.getPlotId()),"ranch_test","Explicit Rancher test fixture");
                if(npc==null){report(ctx,"The Rancher fixture could not be created.");return;}
                ResidentRegistryService.upsert(town,manager,role,"rancher",plot.getPlotId(),store.getComponent(npc,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType()).getUuid());
                manager.updateTown(town);report(ctx,"Created one Rancher test resident after normal food admission. No food care or growth was granted. This does not verify Inn recruitment.");
            }
            case "ranch" -> {
                var sites=town.getApprenticeWorkplaces();
                if(sites.sites().stream().anyMatch(s->"Test Pasture".equals(s.name()))){report(ctx,"The test pasture already exists.");return;}
                var p=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                int x=(int)Math.floor(p.x),y=(int)Math.floor(p.y),z=(int)Math.floor(p.z);
                var area=new WorkplaceArea(x-4,y,z-4,x+5,z+5);
                for(int bx=area.minX();bx<area.maxX();bx++)for(int bz=area.minZ();bz<area.maxZ();bz++) {
                    if(!VillagerBlockUtil.isNpcStandColumn(world,bx,y,bz)) {report(ctx,"Find a flat, empty, loaded 9 by 9 space for the pasture.");return;}
                    for(int by=y;by<y+3;by++)if(com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockId(world,bx,by,bz)!=0) {
                        report(ctx,"The pasture fixture needs three clear blocks above its ground.");return;
                    }
                }
                String error=sites.unlock(town,TownsfolkWorkType.RANCHING);
                if(error==null)error=sites.validate(town,null,TownsfolkWorkType.RANCHING,area,"Test Pasture");
                if(error!=null){report(ctx,error);return;}
                if(!com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x,y,z-2,"Bench_Trough",0,0)) {
                    report(ctx,"The trough could not be placed.");return;
                }
                for(int dx:new int[]{-2,2})com.hypixel.hytale.server.npc.NPCPlugin.get().spawnNPC(store,"Cow",null,
                    new Vector3d(x+dx+.5,y,z+.5),com.hypixel.hytale.math.vector.Rotation3f.ZERO);
                error=sites.save(town,null,TownsfolkWorkType.RANCHING,area,"Test Pasture");manager.updateTown(town);
                report(ctx,error==null?"Created Test Pasture with a granted trough and two cows. Fence the animals in and enable Ranching. No care or growth was advanced.":error);
            }
            case "food", "foodstatus" -> {
                for(var site:town.getApprenticeWorkplaces().sites())if(WorkplaceFood.supports(site.type()))
                    report(ctx,site.name()+" food="+site.food().foodPoints()+" visits="+site.food().completedVisits()+" work="+site.food().workSeconds()+" age="+site.food().minutesSinceTending()+" lastCare="+site.food().lastCare()+" issue="+site.food().careIssue()+" offspring="+site.food().ranch().offspring()+" grown="+site.food().ranch().grown()+" growthMinutes="+site.food().ranch().ageMinutes()+" habitat="+FoodSiteService.inspect(world,site));
                report(ctx,"Population: "+TownPopulation.snapshot(town,plugin.getConstructionCatalog()));
                if("foodstatus".equals(ctx.get(action)))return;
                var position=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                var site=town.getApprenticeWorkplaces().sites().stream().filter(s->WorkplaceFood.supports(s.type()))
                    .min(Comparator.comparingDouble(s->position.distanceSquared((s.area().minX()+s.area().maxX())*.5,s.area().floorY(),(s.area().minZ()+s.area().maxZ())*.5))).orElse(null);
                var entity=store.getComponent(player,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                if(site!=null&&entity!=null)entity.getPageManager().openCustomPage(player,store,new FoodSitePage(pr,town.getTownId(),site.id()));
            }
            case "soil" -> {
                var site=town.getApprenticeWorkplaces().sites().stream().filter(s->"Test Garden".equals(s.name())).findFirst().orElse(null);
                if(site==null){report(ctx,"Create the test garden fixture first.");return;}
                var area=site.area();int changed=0;
                for(int x:new int[]{area.minX()+1,area.maxX()-2})for(int z:new int[]{area.minZ()+3,area.maxZ()-2}) {
                    var crop=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world,x,area.floorY(),z);
                    var soil=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world,x,area.floorY()-1,z);
                    report(ctx,"Fixture crop at "+x+","+area.floorY()+","+z+": "+(crop==null?"missing":crop.getId()+" base="+crop.getDefaultStateKey()));
                    var soilRef=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockEntityRefAt(world,x,area.floorY()-1,z);
                    var cultivated=soilRef!=null&&soilRef.isValid()?world.getChunkStore().getStore().getComponent(soilRef,com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock.getComponentType()):null;
                    report(ctx,"Soil: "+(soil==null?"missing":soil.getId())+" watered until "+(cultivated==null?"not cultivated":cultivated.getWateredUntil()));
                    if(soil!=null&&Set.of("Soil_Grass","Soil_Dirt").contains(soil.getId())
                        &&com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,x,area.floorY()-1,z,"Soil_Dirt_Tilled",0,0))changed++;
                }
                report(ctx,"Cultivated "+changed+" soil blocks under the test fixture crops. No tending progress was granted.");
            }
            case "courier", "worker" -> {
                boolean courier="courier".equals(ctx.get(action));
                var existing=TownsfolkWorkRoster.list(town,manager,store,plugin).stream().filter(w->w.progress().isEmployed()
                    &&w.progress().priority(courier?TownsfolkWorkType.LOGISTICS:TownsfolkWorkType.CRAFTING)>0).findFirst().orElse(null);
                if(existing!=null){report(ctx,"Existing test worker: "+existing.name()+" "+existing.entityId());return;}
                var transform=store.getComponent(player,TransformComponent.getComponentType());if(transform==null)return;
                var pos=transform.getPosition();Vector3d spawn=null;
                for(int[] offset:new int[][]{{3,0},{-3,0},{0,3},{0,-3}}) {
                    int x=(int)Math.floor(pos.x)+offset[0],y=(int)Math.floor(pos.y),z=(int)Math.floor(pos.z)+offset[1];
                    if(VillagerBlockUtil.isNpcStandColumn(world,x,y,z)){spawn=new Vector3d(x+.5,VillagerBlockUtil.resolveFeetYForStandCell(world,x,y,z),z+.5);break;}
                }
                if(spawn==null){report(ctx,"Stand beside an open, loaded space for the test worker.");return;}
                var spawned=TownsfolkSpawnService.trySpawn(world,plugin,town,store,spawn,TownsfolkAssignmentKinds.IDLE,null,new Random(4000));
                if(spawned.isEmpty()){report(ctx,"No available Townsfolk could be spawned.");return;}
                var person=spawned.get();var record=new TouristRecord(person.characterId(),person.entityUuid(),UUID.randomUUID(),true,true);
                record.setPortalId(null);town.getTouristRecords().add(record);
                var ref=store.getExternalData().getRefFromUUID(person.entityUuid());
                if(ref!=null&&ref.isValid())store.putComponent(ref,VillagerNeeds.getComponentType(),VillagerNeeds.full());
                var progress=TownsfolkProgressService.getOrCreate(town,manager,plugin,person.characterId());
                progress.setSkill(TownsfolkSkill.LOGISTICS,Math.max(1,progress.skill(TownsfolkSkill.LOGISTICS)));
                progress.setPriority(TownsfolkWorkType.LOGISTICS,courier?1:0);progress.setEmployed(true);
                if(!courier)for(var type:TownsfolkWorkType.values()) {
                    if(!type.requiresWorkplace())continue;
                    progress.setSkill(type.skill,Math.max(1,progress.skill(type.skill)));
                    progress.setPriority(type,2);
                }town.getPayroll().ensureCurrentBill(person.characterId());
                manager.updateTown(town);TownsfolkWorkRoster.list(town,manager,store,plugin);
                report(ctx,"Created test citizen "+person.characterId()+" at "+spawn+" with "+(courier?"Logistics":"workplace")+" priorities enabled. No work progress was granted.");
            }
            case "smith" -> {
                var position=store.getComponent(player,TransformComponent.getComponentType());if(position==null)return;
                var p=position.getPosition();var sites=town.getApprenticeWorkplaces();
                if(sites.sites().stream().anyMatch(s->"Test Smithy".equals(s.name()))){report(ctx,"The test smithy already exists.");return;}
                var area=WorkplaceArea.corners((int)Math.floor(p.x)-2,(int)Math.floor(p.y)-1,(int)Math.floor(p.z)-2,(int)Math.floor(p.x)+2,(int)Math.floor(p.z)+2);
                var error=sites.unlock(town,TownsfolkWorkType.BLACKSMITHING);
                if(error==null)error=sites.save(town,null,TownsfolkWorkType.BLACKSMITHING,area,"Test Smithy");
                if(error!=null){report(ctx,error);return;}
                var site=sites.sites().getLast();site.orders().add(0,"forge_copper_sword",WorkplaceOrders.Mode.COUNT,1);
                manager.updateTown(town);report(ctx,"Created Test Smithy with one copper sword order at "+area+". Place an anvil inside it.");
            }
            case "stock" -> {
                var inventory=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)2);
                var supplies=List.of(new ItemStack("Ingredient_Bar_Copper",4),new ItemStack("Ingredient_Fibre",3));
                for(short i=0;i<supplies.size();i++) {
                    inventory.setItemStackForSlot(i,supplies.get(i),false);
                    var moved=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,i,supplies.get(i),TownWarehouse.Amount.STACK,town.getWarehouse().revision());
                    report(ctx,"Granted test supplies to General storage: "+supplies.get(i).getItemId()+" x"+moved.moved());
                }
                manager.updateTown(town);
            }
            case "queue" -> {
                List<TownWarehouse.ProductionStock> sources=sources(world,town,plugin);
                for(var site:town.getApprenticeWorkplaces().sites()) {
                    if(!town.getWarehouse().canPrepare(site.id(),site.type()==TownsfolkWorkType.SMELTING,site.type()==TownsfolkWorkType.CULTIVATION))continue;
                    var equipment=WorkplaceEquipment.scan(world,site.area());
                    for(var order:site.orders().list()) {
                        var recipe=plugin.getApprenticeRecipes().find(order.recipeId());
                        if(!site.orders().canComplete(order.id(),order.recipeId())||recipe==null||!recipe.unlocked(town)||recipe.source()==null||recipe.equipmentIssue(site,equipment)!=null)continue;
                        var original=recipe.source();if(original.getOutputs()==null||original.isKnowledgeRequired())continue;
                        var outputs=Arrays.stream(original.getOutputs()).map(m->m==null?null:m.toItemStack()).toList();
                        if(outputs.stream().anyMatch(s->ItemStack.isEmpty(s)))continue;
                        var primary=original.getPrimaryOutput()==null?original.getOutputs()[0]:original.getPrimaryOutput();
                        String item=primary.getItemId();if(item==null)continue;
                        var stock=town.getWarehouse();
                        if(order.batchesNeeded(recipe.stored(stock,item),recipe.incoming(stock,item),primary.getQuantity(),stock.queuedForOrder(order.id()))==0)continue;
                        if(site.type()==TownsfolkWorkType.CULTIVATION) {
                            var point=NativeCultivationService.findPlantPoint(world,site,stock);
                            if(point==null)continue;
                            var batch=stock.reserveCultivation(site.id(),order.id(),recipe.id(),AlchemicalCrop.fromBlock(recipe.output()),sources);
                            if(batch==null)continue;
                            stock.setDestination(batch.id(),point);manager.updateTown(town);
                            report(ctx,"Queued seeds for "+site.name()+". Planting, growth and harvesting must happen normally.");return;
                        }
                        for(var bench:equipment.benches()) {
                        if(!recipe.bench().equals(bench.id())||bench.tier()<recipe.benchTier())continue;
                        var point=new WarehouseBatch.Point(bench.x(),bench.y(),bench.z());
                        var batch=site.type()==TownsfolkWorkType.SMELTING?NativeFurnaceService.queue(world,town,site,order,recipe,point,sources)
                            :town.getWarehouse().reserve(site.id(),order.id(),recipe.id(),original.getInput()==null?List.of():Arrays.asList(original.getInput()),outputs,sources);
                        if(batch==null)continue;
                        town.getWarehouse().setDestination(batch.id(),new WarehouseBatch.Point(bench.x(),bench.y(),bench.z()));manager.updateTown(town);
                        report(ctx,"Queued delivery "+batch.id()+" for "+site.name()+". No resources were granted and no work was completed.");return;
                        }
                    }
                }
                report(ctx,"No batch queued. Check loaded storage, available ingredients, task and required bench. Existing batches are kept.");
            }
            case "status" -> {
                report(ctx,"Loaded desks: "+WarehouseDeskBlock.loaded(world,manager,town)+" | production stores: "+sources(world,town,plugin).stream().map(s->s.plot()+" at "+s.pickup()).toList());
                for(var site:town.getApprenticeWorkplaces().sites()) {
                    var equipment=WorkplaceEquipment.scan(world,site.area());
                    report(ctx,"Workplace "+site.name()+" | simulated="+equipment.fullySimulated()
                        +" | benches="+equipment.benches()+" | furnishings="+equipment.furnishingSummary(site.type())
                        +" | furnishing multiplier="+equipment.workMultiplier(site.type()));
                }
                for(var worker:TownsfolkWorkRoster.list(town,manager,store,plugin))if(worker.progress().isEmployed()) {
                    var ref=store.getExternalData().getRefFromUUID(worker.entityId());
                    if(ref==null||!ref.isValid()){report(ctx,worker.name()+" is not loaded.");continue;}
                    var route=store.getComponent(ref,CourierTravelState.getComponentType());var pos=store.getComponent(ref,TransformComponent.getComponentType());
                    var work=store.getComponent(ref,ApprenticeWorkState.getComponentType());
                    report(ctx,worker.name()+" | apprentice="+(work==null?"none":work.active+" working="+work.working+" batch="+work.batch+" foodSite="+work.foodSite+" approach="+work.approach));
                    var needs=store.getComponent(ref,VillagerNeeds.getComponentType());
                    report(ctx,worker.name()+" at "+(pos==null?"unknown":pos.getPosition())+" | route="+(route==null?"none":route.active+" "+route.action+" "+route.endpoint+" approach="+route.approach)+" | needs="+(needs==null?"missing":needs.getHunger()+"/"+needs.getEnergy()));
                    var npc=store.getComponent(ref,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                    var motion=npc==null||npc.getRole()==null?null:npc.getRole().getActiveMotionController();
                    var autonomy=store.getComponent(ref,VillagerAutonomyState.getComponentType());
                    long now=System.currentTimeMillis();
                    report(ctx,worker.name()+" | state="+NpcSupportUtil.stateName(store,ref)
                        +" nav="+(motion==null?"none":motion.getNavState())
                        +" frozen="+(store.getComponent(ref,com.hypixel.hytale.server.core.entity.Frozen.getComponentType())!=null)
                        +" life="+VillagerLifeSystem.ownsActivity(ref,store)
                        +" following="+VillagerFollowPlayerSystem.shouldSkipAutonomy(store.getComponent(ref,VillagerFollowPlayerState.getComponentType()))
                        +" autonomy="+(autonomy==null?"none":autonomy.getPhase()+" filling="+autonomy.isFillingAnyNeed())
                        +" routeTiming="+(route==null?"none":"check="+(route.nextCheck-now)+" retry="+(route.retryAt-now)+" age="+(now-route.beganAt)+" attempt="+route.approachAttempt));
                }
                for(var batch:town.getWarehouse().batches()) {
                    report(ctx,"Batch "+batch.id()+" "+batch.state()+" workSeconds="+batch.workSeconds()+" carrier="+batch.courierId()+" pending="+batch.pendingItems()+" cargo="+batch.cargoItems()+" delivered="+batch.deliveredItems()+" sources="+batch.pendingSources());
                    if(batch.cultivation()) {
                        var point=batch.destination();
                        var block=point==null?null:com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world,point.x(),point.y(),point.z());
                        report(ctx,"Cultivation at "+point+" | planted="+batch.cropPlanted()+" | block="+(block==null?"unavailable":block.getId())+" | mature="+NativeCultivationService.mature(world,batch));
                    }
                }
            }
            default -> report(ctx,"Use courier, worker, smith, garden, garden2, ranch, rancher, cultivation, food, foodstatus, soil, kit, armory, jewelry, alchemy, artisan, stock, queue or status.");
        }
    }
    private static List<TownWarehouse.ProductionStock> sources(World world,TownRecord town,AetherhavenPlugin plugin) {
        return ProductionStockSources.loaded(world,town,plugin,System.currentTimeMillis());
    }
    private static void report(CommandContext ctx,String message) {ctx.sendMessage(Message.raw(message));HytaleLogger.forEnclosingClass().atInfo().log("Logistics test: %s",message);}
}
