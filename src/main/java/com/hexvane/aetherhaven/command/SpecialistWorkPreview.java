package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.world.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;
import org.joml.Vector3d;

/** Attended, ordinary courier and Apprentice work in the unsaved specialist preview only. */
record SpecialistWorkPreview(UUID siteId, String recipeId, String worker, String courier, String output) {
    static SpecialistWorkPreview create(World world, UUID owner, Vector3d viewpoint) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")
            && !PersistentWorldSupport.shouldPersistWorldData(world), "Work checks require a disposable preview.");
        var plugin=AetherhavenPlugin.get();
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);
        require(town!=null&&viewpoint!=null,"Preview town or Artisan platform missing.");
        var sites=town.getApprenticeWorkplaces();
        require(sites.sites().stream().noneMatch(s->"Preview Crafting".equals(s.name())),"A crafting fixture already exists; inspect it before retrying.");
        int x=(int)Math.floor(viewpoint.x),y=(int)Math.floor(viewpoint.y),z=(int)Math.floor(viewpoint.z);
        var area=new WorkplaceArea(x-2,y,z-4,x+3,z+1);
        String error=sites.unlock(town,TownsfolkWorkType.CRAFTING);
        if(error==null)error=sites.validate(town,null,TownsfolkWorkType.CRAFTING,area,"Preview Crafting");
        require(error==null,error);
        for(int bx=x-3;bx<=x+5;bx++)for(int bz=z-4;bz<=z+3;bz++)for(int by=y;by<y+3;by++)
            require(ChunkSectionBlockUtil.blockSectionAt(world,bx,by,bz)!=null
                &&ChunkSectionBlockUtil.blockId(world,bx,by,bz)==0,"The preview work area must be empty and loaded.");
        require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z-2,"Bench_Workbench",0,0),"Workbench placement failed.");
        require(ChunkSectionBlockUtil.setBlockByKey(world,x+4,y,z,WarehouseAccess.DESK_ITEM,0,0),"Local ledger placement failed.");
        var equipment=WorkplaceEquipment.scan(world,area);
        var recipe=plugin.getApprenticeRecipes().all().stream()
            .filter(r->r.id().startsWith("native/")&&r.type()==TownsfolkWorkType.CRAFTING&&r.skillLevel()==1)
            .filter(r->r.unlocked(town)&&r.nativeUnlockIssue(town,world)==null)
            .filter(r->equipment.benches().stream().anyMatch(r::acceptsBench))
            .filter(r->r.source()!=null&&r.source().getInput()!=null&&r.source().getInput().length>0
                &&Arrays.stream(r.source().getInput()).allMatch(i->i!=null&&i.getItemId()!=null)
                &&r.source().getOutputs()!=null&&Arrays.stream(r.source().getOutputs()).allMatch(i->i!=null&&i.getItemId()!=null))
            .sorted(Comparator.comparing(WorkplaceRecipe::id)).findFirst().orElseThrow(()->new IllegalStateException("No eligible native Workbench recipe."));
        require(sites.save(town,null,TownsfolkWorkType.CRAFTING,area,"Preview Crafting")==null,"Workplace creation failed.");
        var site=sites.sites().getLast();
        require(site.orders().add(0,recipe.id(),WorkplaceOrders.Mode.COUNT,1)==null,"Order creation failed.");
        var inputs=recipe.source().getInput();
        var inventory=new SimpleItemContainer((short)inputs.length);
        for(short i=0;i<inputs.length;i++) {
            var stack=inputs[i].toItemStack();inventory.setItemStackForSlot(i,stack,false);
            var moved=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,i,stack,TownWarehouse.Amount.STACK,town.getWarehouse().revision());
            require(moved.moved()==stack.getQuantity(),"Preview input deposit failed.");
        }
        String worker=spawn(world,town,manager,new Vector3d(x+.5,y,z+3.5),false);
        String courier=spawn(world,town,manager,new Vector3d(x-3+.5,y,z+.5),true);
        // Normal employment screens register the loaded citizens before enabling work.
        TownsfolkWorkRoster.list(town,manager,world.getEntityStore().getStore(),plugin);
        world.getWorldConfig().setBlockTicking(true);
        world.getWorldConfig().setGameTimePaused(false);
        var store=world.getEntityStore().getStore();
        var time=store.getResource(com.hypixel.hytale.server.core.modules.time.WorldTimeResource.getResourceType());
        time.setGameTime(time.getGameTime().truncatedTo(java.time.temporal.ChronoUnit.DAYS).plus(8,java.time.temporal.ChronoUnit.HOURS),world,store);
        manager.updateTown(town);
        var primary=recipe.source().getPrimaryOutput();
        if(primary==null)primary=recipe.source().getOutputs()[0];
        return new SpecialistWorkPreview(site.id(),recipe.id(),worker,courier,primary.getItemId());
    }
    /** Switch only a finished fixture, leaving the same two citizens to do the next real job. */
    SpecialistWorkPreview next(World world, UUID owner, TownsfolkWorkType type) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")
            && !PersistentWorldSupport.shouldPersistWorldData(world), "Work checks require a disposable preview.");
        var plugin=AetherhavenPlugin.get();
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);
        var site=town.getApprenticeWorkplaces().find(siteId);
        require(site!=null,"The previous fixture is missing.");
        require(town.getWarehouse().batches().stream().noneMatch(b->siteId.equals(b.workplaceId()))
            && site.orders().list().stream().allMatch(o->o.completed()>=o.quantity()),
            "Wait for the current order and delivery to finish before changing benches.");
        String block=switch(type) {
            case CRAFTING -> "Bench_Workbench";
            case BLACKSMITHING -> "Bench_Weapon";
            case ARMOR_CRAFTING -> "Bench_Armour";
            case SMELTING -> "Bench_Furnace";
            case LEATHERWORKING -> "Bench_Tannery";
            case COOKING -> "Bench_Cooking";
            case ALCHEMY -> "Bench_Alchemy";
            default -> throw new IllegalArgumentException("Choose one of the seven native crafting professions.");
        };
        var area=site.area();int x=area.minX()+2,y=area.floorY(),z=area.maxZ()-1;
        var blockType=com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getAsset(block);
        require(blockType!=null&&blockType.getBench()!=null,"Native bench is missing: "+block);
        var candidate=new WorkplaceEquipment.Bench(blockType.getBench().getId(),1,x,y,z-2);
        var recipe=plugin.getApprenticeRecipes().all().stream()
            .filter(r->r.id().startsWith("native/")&&r.type()==type&&r.skillLevel()==1)
            .filter(r->r.unlocked(town)&&r.nativeUnlockIssue(town,world)==null&&r.acceptsBench(candidate))
            .filter(r->r.source()!=null&&r.source().getInput()!=null&&r.source().getInput().length>0
                &&Arrays.stream(r.source().getInput()).allMatch(i->i!=null&&i.getItemId()!=null)
                &&r.source().getOutputs()!=null&&Arrays.stream(r.source().getOutputs()).allMatch(i->i!=null&&i.getItemId()!=null))
            .sorted(Comparator.comparing(WorkplaceRecipe::id)).findFirst()
            .orElseThrow(()->new IllegalStateException("No eligible native recipe for "+type.id));
        String supervisor=WorkplaceRules.supervisor(type);
        if(!TownWorkplaces.hasSupervisor(town,supervisor)) {
            var definition=plugin.getVillagerDefinitionCatalog().allByNpcRoleId().values().stream()
                .filter(v->supervisor.equals(v.getDialogueVillagerKind())).findFirst().orElseThrow();
            // Explicit fixture prerequisite in this unsaved town; no recruitment in the saved world.
            town.getResidentNpcRecords().add(new ResidentNpcRecord(definition.getNpcRoleId(),supervisor,null,UUID.randomUUID()));
        }
        require(town.getApprenticeWorkplaces().unlock(town,type)==null,"Could not unlock the fixture profession.");
        require(town.getApprenticeWorkplaces().validate(town,siteId,type,area,"Preview "+type.id)==null,"Invalid fixture workplace.");
        require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z-2,block,0,0),"Native bench placement failed.");
        require(town.getApprenticeWorkplaces().save(town,siteId,type,area,"Preview "+type.id)==null,"Could not change fixture workplace.");
        for(var order:List.copyOf(site.orders().list()))require(site.orders().change(site.orders().revision(),order.id(),"Remove")==null,"Could not remove completed order.");
        require(site.orders().add(site.orders().revision(),recipe.id(),WorkplaceOrders.Mode.COUNT,1)==null,"Could not add next order.");
        var inputs=new ArrayList<ItemStack>();
        for(var input:recipe.source().getInput())inputs.add(input.toItemStack());
        if(type==TownsfolkWorkType.SMELTING){site.setCharcoalFuel(true);inputs.add(new ItemStack("Ingredient_Charcoal",4));}
        var inventory=new SimpleItemContainer((short)inputs.size());
        for(short i=0;i<inputs.size();i++) {
            var stack=inputs.get(i);inventory.setItemStackForSlot(i,stack,false);
            var moved=town.getWarehouse().deposit(TownWarehouse.Page.GENERAL,inventory,i,stack,TownWarehouse.Amount.STACK,town.getWarehouse().revision());
            require(moved.moved()==stack.getQuantity(),"Fixture input deposit failed.");
        }
        var progress=town.getTownsfolkProgress().get(worker);
        for(var priority:TownsfolkWorkType.values())progress.setPriority(priority,0);
        progress.setSkill(type.skill,1);progress.setPriority(type,1);
        world.getWorldConfig().setBlockTicking(true);
        world.getWorldConfig().setGameTimePaused(false);
        var store=world.getEntityStore().getStore();
        var time=store.getResource(com.hypixel.hytale.server.core.modules.time.WorldTimeResource.getResourceType());
        time.setGameTime(time.getGameTime().truncatedTo(java.time.temporal.ChronoUnit.DAYS).plus(8,java.time.temporal.ChronoUnit.HOURS),world,store);
        manager.updateTown(town);
        var primary=recipe.source().getPrimaryOutput();if(primary==null)primary=recipe.source().getOutputs()[0];
        return new SpecialistWorkPreview(siteId,recipe.id(),worker,courier,primary.getItemId());
    }
    private static String spawn(World world,TownRecord town,TownManager manager,Vector3d position,boolean courier) {
        return spawn(world,town,manager,position,courier?TownsfolkWorkType.LOGISTICS:TownsfolkWorkType.CRAFTING,courier?4001:4000,courier?0:95);
    }
    static String spawn(World world,TownRecord town,TownManager manager,Vector3d position,TownsfolkWorkType type,int seed,int thresholdXp) {
        var plugin=AetherhavenPlugin.get();var store=world.getEntityStore().getStore();
        var person=TownsfolkSpawnService.trySpawn(world,plugin,town,store,position,TownsfolkAssignmentKinds.IDLE,null,new Random(seed))
            .orElseThrow(()->new IllegalStateException("Preview worker could not spawn."));
        var record=new TouristRecord(person.characterId(),person.entityUuid(),UUID.randomUUID(),true,true);
        record.setPortalId(null);town.getTouristRecords().add(record);
        var ref=store.getExternalData().getRefFromUUID(person.entityUuid());
        if(ref!=null&&ref.isValid())store.putComponent(ref,VillagerNeeds.getComponentType(),VillagerNeeds.full());
        var progress=TownsfolkProgressService.getOrCreate(town,manager,plugin,person.characterId());
        for(var priority:TownsfolkWorkType.values())progress.setPriority(priority,0);
        progress.setSkill(type.skill,1);progress.setPriority(type,1);progress.setEmployed(true);
        // Explicit threshold precondition. Actual delivery/crafting XP must come from normal work.
        if(thresholdXp>0)progress.earnMasteryXp(thresholdXp);
        town.getPayroll().ensureCurrentBill(person.characterId());
        return person.characterId();
    }
    String status(World world,UUID owner) {
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);
        var workerProgress=town.getTownsfolkProgress().get(worker);
        var courierProgress=town.getTownsfolkProgress().get(courier);
        return "Native recipe "+recipeId+". Output in storage: "+town.getWarehouse().count(TownWarehouse.Page.OUTPUT,output)
            +". Smelting storage: "+town.getWarehouse().smelting().count(output)+". Worker "+worker+": XP "+workerProgress.masteryXp()+" (95 granted for the threshold check), level "+workerProgress.masteryLevel()
            +", free skill points "+workerProgress.masterySkillPoints()+". Courier "+courier+": XP "+courierProgress.masteryXp()
            +". Batches: "+town.getWarehouse().batches().stream().filter(b->siteId.equals(b.workplaceId())).map(b->b.state().toString()).toList()
            +". Supplies and two test citizens belong only to this temporary world. Visit plot_crafter to watch.";
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
