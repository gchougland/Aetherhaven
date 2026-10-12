package com.hexvane.aetherhaven.command;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownAdmission;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgressService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkRoster;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchRows;
import com.hexvane.aetherhaven.workplace.WorkplaceEquipment;
import com.hexvane.aetherhaven.workplace.WorkplaceNativeBench;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Starting work priorities and workplace benches on real state, in a disposable world.
 *
 * <p>The resident half spawns real townsfolk and lets the game's own tick find that they have become residents. The
 * bench half places real furnaces and workbenches inside marked workplaces. No player takes part, so the last step of
 * opening a bench, handing its screen to a client, is not run here.
 */
final class AetherhavenWorkUiTestCommand extends AbstractCommandCollection {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static final Gson GSON=new Gson();
    AetherhavenWorkUiTestCommand() {
        super("workui","Check starting work priorities and workplace benches in an isolated world");
        setPermissionGroups("hytale:WorldEditor");addSubCommand(new Check());
    }
    private static final class Check extends AbstractAsyncCommand {
        Check() { super("worldcheck","Check new residents' starting priorities and that every furnace of a smeltery opens itself");setPermissionGroups("hytale:WorldEditor"); }
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if(!RUNNING.compareAndSet(false,true)){report(ctx,"A work check is already running.");return CompletableFuture.completedFuture(null);}
            String name="aetherhaven_workui_check_"+UUID.randomUUID().toString().replace("-","");var universe=Universe.get();var path=universe.validateWorldPath(name);
            if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));}
            var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
            config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
            var notes=new ArrayList<String>();
            return universe.makeWorld(name,path,config).thenCompose(world->exercise(world,notes).orTimeout(90,TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
                if(error==null)report(ctx,"Work UI isolated world check: PASSED. "+String.join(" ",notes));
                else {Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,"Work UI isolated world check: FAILED: "+cause.getMessage()+" | reached: "+String.join(" ",notes));}
                universe.removeWorld(name);
            })).whenComplete((done,error)->RUNNING.set(false));
        }
    }

    private static final int FLOOR=127,Y=128;
    private static final WorkplaceArea SMELTERY=new WorkplaceArea(2,Y,2,18,8),WORKSHOP=new WorkplaceArea(2,Y,12,18,18);
    private static final List<Vector3i> FURNACES=List.of(new Vector3i(4,Y,4),new Vector3i(9,Y,4),new Vector3i(14,Y,4));
    private static final List<Vector3i> WORKBENCHES=List.of(new Vector3i(4,Y,14),new Vector3i(11,Y,14));
    private static final Vector3i LOOSE_FURNACE=new Vector3i(24,Y,24);

    private static CompletableFuture<Void> exercise(World world,List<String> notes) {
        var plugin=AetherhavenPlugin.get();
        var owner=UUID.randomUUID();var town=new TownRecord(UUID.randomUUID(),owner,world.getName(),16,Y,16,1,1,0);
        town.setDisplayName("Work Fixture");
        var manager=new TownManager[1];
        var newcomer=new String[1];var chooser=new String[1];var newcomerId=new UUID[1];var chooserId=new UUID[1];
        var chain=CompletableFuture.allOf(world.getChunkStore().getChunkSectionReferenceAsync(0,3,0,GetChunkFlags.SET_TICKING),
            world.getChunkStore().getChunkSectionReferenceAsync(0,4,0,GetChunkFlags.SET_TICKING)).thenCompose(ignored->onWorld(world,()->{
            manager[0]=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);manager[0].putTown(town);
            for(int x=0;x<30;x++)for(int z=0;z<30;z++)require(ChunkSectionBlockUtil.setBlockByKey(world,x,FLOOR,z,"Rock_Stone",0),"Floor placement failed");
            authoredPriorities(plugin,notes);
            oldSave(notes);
            // Two townsfolk arrive as visitors. Nothing has made them residents yet.
            var store=world.getEntityStore().getStore();
            var first=TownsfolkSpawnService.trySpawn(world,plugin,town,store,new Vector3d(22.5,Y,6.5),TownsfolkAssignmentKinds.IDLE,null,new Random(11))
                .orElseThrow(()->new IllegalStateException("The first townsfolk could not be spawned"));
            var second=TownsfolkSpawnService.trySpawn(world,plugin,town,store,new Vector3d(24.5,Y,8.5),TownsfolkAssignmentKinds.IDLE,null,new Random(23))
                .orElseThrow(()->new IllegalStateException("The second townsfolk could not be spawned"));
            newcomer[0]=first.characterId();newcomerId[0]=first.entityUuid();chooser[0]=second.characterId();chooserId[0]=second.entityUuid();
            placeBenches(world,town);
        }));
        // Give the game time to tick the visitors: a visitor must not be given priorities.
        chain=chain.thenCompose(ignored->after(world,()->{})).thenCompose(ignored->after(world,()->{
            require(!TownAdmission.isMember(town,newcomerId[0]),"A visitor counts as a resident");
            var early=town.getTownsfolkProgress().get(newcomer[0].toLowerCase(Locale.ROOT));
            require(early==null||!early.startingPrioritiesSet(),"A visitor was given starting priorities");
            // The second one has a priority the player chose before they moved in.
            var chosen=TownsfolkProgressService.getOrCreate(town,manager[0],plugin,chooser[0]);
            chosen.setSkill(com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.COOKING,Math.max(2,chosen.skill(com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.COOKING)));
            chosen.setPriority(TownsfolkWorkType.COOKING,4);
            // Both become residents the way a hired townsfolk does: a citizen record. Their needs are left to the game.
            for(int i=0;i<2;i++) {
                var record=new TouristRecord(i==0?newcomer[0]:chooser[0],i==0?newcomerId[0]:chooserId[0],UUID.randomUUID(),true,true);
                record.setPortalId(null);town.getTouristRecords().add(record);
            }
            require(TownAdmission.isMember(town,newcomerId[0])&&TownAdmission.isMember(town,chooserId[0]),"The fixture residents are not residents");
        }));
        // The game's own tick notices the new residents. No page is opened and nothing here calls the priority code.
        chain=chain.thenCompose(ignored->await(world,"the game to give the new residents their needs and starting priorities",()->{
            var a=town.getTownsfolkProgress().get(newcomer[0].toLowerCase(Locale.ROOT));
            var b=town.getTownsfolkProgress().get(chooser[0].toLowerCase(Locale.ROOT));
            return a!=null&&a.startingPrioritiesSet()&&b!=null&&b.startingPrioritiesSet()
                &&hasNeeds(world,newcomerId[0])&&hasNeeds(world,chooserId[0]);
        })).thenCompose(ignored->onWorld(world,()->{
            var progress=town.getTownsfolkProgress().get(newcomer[0].toLowerCase(Locale.ROOT));
            var expected=new TownsfolkProgress();
            for(var skill:com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.values())expected.setSkill(skill,progress.skill(skill));
            var want=expected.startingPriorities();
            require(!want.isEmpty(),newcomer[0]+" has no skill above 1, so the fixture proves nothing");
            for(var type:TownsfolkWorkType.values()) {
                if(type==TownsfolkWorkType.COMMERCE)continue;
                require(progress.priority(type)==want.getOrDefault(type,0),newcomer[0]+" "+type.id+" priority "+progress.priority(type)+", expected "+want.getOrDefault(type,0));
            }
            require(!progress.isEmployed(),"Becoming a resident must not employ anyone: wages are the player's choice");
            var chosen=town.getTownsfolkProgress().get(chooser[0].toLowerCase(Locale.ROOT));
            require(chosen.priority(TownsfolkWorkType.COOKING)==4,"The player's own priority was replaced");
            for(var type:TownsfolkWorkType.values())
                if(type!=TownsfolkWorkType.COOKING&&type!=TownsfolkWorkType.COMMERCE)
                    require(chosen.priority(type)==0,chooser[0]+" was given "+type.id+" beside the player's own choice");
            // Listing the roster, as the journal does, changes nothing a second time.
            var before=GSON.toJson(town.getTownsfolkProgress());
            TownsfolkWorkRoster.list(town,manager[0],world.getEntityStore().getStore(),plugin);
            require(!TownsfolkWorkRoster.startResidentWork(town,manager[0],plugin,newcomer[0]),"Starting priorities were applied twice");
            require(before.equals(GSON.toJson(town.getTownsfolkProgress())),"Listing the residents changed their priorities again");
            var said=new StringBuilder();
            for(var start:want.entrySet())said.append(said.isEmpty()?"":", ").append(start.getKey().id).append(" (skill ").append(progress.skill(start.getKey().skill)).append(") priority ").append(start.getValue());
            notes.add("Residents: "+newcomer[0]+" became a resident and the game's tick gave them "+said+"; "+chooser[0]
                +" kept only the Cooking 4 a player had chosen; neither was employed; a visitor got nothing; a second pass changed nothing.");
        }));
        chain=chain.thenCompose(ignored->after(world,()->benches(world,town,notes)));
        return chain;
    }

    /** Every townsfolk in the loaded catalog, with their real starting skills. */
    private static void authoredPriorities(AetherhavenPlugin plugin,List<String> notes) {
        int people=0,withWork=0;String sample=null;
        for(var character:plugin.getTownsfolkCharacterCatalog().allById().values()) {
            var progress=TownsfolkProgressService.startingProgress(character);
            require(progress.applyStartingPriorities(),character.getId()+" could not be given starting priorities");
            int best=0;
            for(var type:TownsfolkWorkType.values())if(type.availableForNewWork()&&type!=TownsfolkWorkType.COMMERCE)best=Math.max(best,progress.skill(type.skill));
            boolean any=false;
            for(var type:TownsfolkWorkType.values()) {
                if(!type.availableForNewWork()||type==TownsfolkWorkType.COMMERCE)continue;
                int skill=progress.skill(type.skill),priority=progress.priority(type);
                require(skill>1?priority>=1&&priority<=4:priority==0,character.getId()+" "+type.id+" skill "+skill+" priority "+priority);
                require(skill<=1||skill!=best||priority==1,character.getId()+" does not put their best work "+type.id+" first");
                any|=priority>0;
            }
            people++;if(any)withWork++;
            if(sample==null&&any)sample=character.getId();
        }
        require(people>=60,"Only "+people+" townsfolk are loaded");
        require(withWork==people,(people-withWork)+" townsfolk would start with no work switched on");
        notes.add("Catalog: all "+people+" townsfolk start with their best skill at priority 1 and nothing at skill 1 switched on.");
    }

    /** A town written before starting priorities existed, read back the way a save is. */
    private static void oldSave(List<String> notes) {
        var old=GSON.fromJson("{\"townsfolkProgress\":{\"untouched\":{\"skills\":{\"farming\":5,\"cooking\":3},\"employed\":true},"
            +"\"chosen\":{\"skills\":{\"farming\":5},\"workPriorities\":{\"mining\":2}}}}",TownRecord.class);
        for(String id:List.of("untouched","chosen")) {
            var uuid=UUID.randomUUID();
            var row=new ResidentNpcRecord("Aetherhaven_Townsfolk",com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_TOWNSFOLK,null,uuid);
            row.setTownsfolkIdentity(new com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding(id,"","","",List.of()));
            old.getResidentNpcRecords().add(row);old.getTouristRecords().add(new TouristRecord(id,uuid,UUID.randomUUID(),true,true));
        }
        require(TownsfolkWorkRoster.applyStartingPriorities(old),"An old save's residents were not given starting priorities");
        var untouched=old.getTownsfolkProgress().get("untouched");
        require(untouched.priority(TownsfolkWorkType.FARMING)==1&&untouched.priority(TownsfolkWorkType.COOKING)==2,"Old save defaults differ");
        var chosen=old.getTownsfolkProgress().get("chosen");
        require(chosen.priority(TownsfolkWorkType.MINING)==2&&chosen.priority(TownsfolkWorkType.FARMING)==0,"An old save's chosen priorities were replaced");
        untouched.setPriority(TownsfolkWorkType.FARMING,0);
        var saved=GSON.fromJson(GSON.toJson(old),JsonObject.class);
        require(saved.getAsJsonObject("townsfolkProgress").getAsJsonObject("untouched").get("startingPrioritiesSet").getAsBoolean(),"The one time mark is not saved");
        var again=GSON.fromJson(saved,TownRecord.class);
        require(!TownsfolkWorkRoster.applyStartingPriorities(again),"A second load applied starting priorities again");
        require(again.getTownsfolkProgress().get("untouched").priority(TownsfolkWorkType.FARMING)==0,"A second load switched work back on");
        notes.add("Old save: a resident with no priorities got them on the first load only; one with a chosen priority was left alone.");
    }

    private static void placeBenches(World world,TownRecord town) {
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Blacksmith","blacksmith",null,UUID.randomUUID()));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Crafter","crafter",null,UUID.randomUUID()));
        var sites=town.getApprenticeWorkplaces();
        require(sites.unlock(town,TownsfolkWorkType.SMELTING)==null&&sites.unlock(town,TownsfolkWorkType.CRAFTING)==null,"Workplace unlock failed");
        for(var at:FURNACES)require(ChunkSectionBlockUtil.setBlockByKey(world,at.x,at.y,at.z,"Bench_Furnace",0,0),"Furnace placement failed at "+at);
        for(var at:WORKBENCHES)require(ChunkSectionBlockUtil.setBlockByKey(world,at.x,at.y,at.z,"Bench_Workbench",0,0),"Workbench placement failed at "+at);
        require(ChunkSectionBlockUtil.setBlockByKey(world,LOOSE_FURNACE.x,LOOSE_FURNACE.y,LOOSE_FURNACE.z,"Bench_Furnace",0,0),"Loose furnace placement failed");
        String refused=sites.save(town,null,TownsfolkWorkType.SMELTING,SMELTERY,"Fixture smeltery");
        require(refused==null,"Smeltery not saved: "+refused);
        refused=sites.save(town,null,TownsfolkWorkType.CRAFTING,WORKSHOP,"Fixture workshop");
        require(refused==null,"Workshop not saved: "+refused);
    }

    /** Each furnace of the smeltery, and each workbench of the workshop, is found as itself and opens its own contents. */
    private static void benches(World world,TownRecord town,List<String> notes) {
        ApprenticeWorkplace smeltery=null,workshop=null;
        for(var site:town.getApprenticeWorkplaces().sites()) {
            if(site.type()==TownsfolkWorkType.SMELTING)smeltery=site;
            if(site.type()==TownsfolkWorkType.CRAFTING)workshop=site;
        }
        require(smeltery!=null&&workshop!=null,"Fixture workplaces missing");
        var equipment=WorkplaceEquipment.scan(world,SMELTERY);
        require(equipment.fullySimulated(),"The smeltery is not loaded");
        var rows=WorkplaceBenchRows.rows(equipment.benches(),"Furnace");
        require(rows.size()==FURNACES.size(),"The page would list "+rows.size()+" furnaces, not "+FURNACES.size());
        var cs=world.getChunkStore().getStore();
        var states=new ArrayList<ProcessingBenchBlock>();var windows=new ArrayList<ProcessingBenchWindow>();
        var seen=new HashSet<Vector3i>();int fillers=0;
        for(var row:rows) {
            var at=row.position();
            require(FURNACES.contains(at)&&seen.add(at),"Row "+row.number()+" stands for "+at+", which is not one furnace of the fixture");
            // What the row's button sends back is this furnace and no other.
            require(at.equals(WorkplaceBenchRows.position(row.key())),"Row "+row.number()+" sends "+row.key());
            var found=WorkplaceBenchAccess.find(world,at);
            require(found!=null&&found.site().id().equals(smeltery.id())&&found.pos().equals(at),"Furnace "+row.number()+" at "+at+" is not found as itself");
            require("Use this bench".equals(WorkplaceNativeBench.label(world,at)),"Furnace "+row.number()+" offers no button");
            // Using any part of a furnace leads to that furnace, not to a neighbour.
            for(int dx=-3;dx<=3;dx++)for(int dy=0;dy<=3;dy++)for(int dz=-3;dz<=3;dz++) {
                int filler=ChunkSectionBlockUtil.filler(world,at.x+dx,at.y+dy,at.z+dz);
                if(filler==0||dx!=FillerBlockUtil.unpackX(filler)||dy!=FillerBlockUtil.unpackY(filler)||dz!=FillerBlockUtil.unpackZ(filler))continue;
                var part=WorkplaceBenchAccess.find(world,new Vector3i(at.x+dx,at.y+dy,at.z+dz));
                require(part!=null&&part.pos().equals(at),"A side block of furnace "+row.number()+" leads to "+(part==null?"nothing":part.pos()));
                fillers++;
            }
            var block=ChunkSectionBlockUtil.blockType(world,at.x,at.y,at.z);
            var section=world.getChunkStore().getChunkSectionReferenceAtBlock(at.x,at.y,at.z);
            var entity=BlockModule.getBlockEntity(cs,section,at.x,at.y,at.z);
            require(entity!=null&&entity.isValid(),"Furnace "+row.number()+" has no block entity");
            var bench=cs.getComponent(entity,BenchBlock.getComponentType());var state=cs.getComponent(entity,ProcessingBenchBlock.getComponentType());
            require(bench!=null&&state!=null,"Furnace "+row.number()+" has no bench state");
            require(bench.getWindows().isEmpty(),"Furnace "+row.number()+" already has a screen open");
            for(var other:states)require(other!=state,"Two rows share one furnace");
            // The screen the button builds: the base game's own, bound to this furnace's block and contents.
            var window=new ProcessingBenchWindow(state,bench,cs.getComponent(entity,BlockModule.BlockStateInfo.getComponentType()),
                at.x,at.y,at.z,ChunkSectionBlockUtil.rotationIndex(world,at.x,at.y,at.z),block);
            require(window.getX()==at.x&&window.getY()==at.y&&window.getZ()==at.z,"The screen of furnace "+row.number()+" points at another block");
            states.add(state);windows.add(window);
        }
        // Something put into the second furnace shows in the second furnace's screen only.
        var second=states.get(1).getInputContainer();
        require(second!=null&&second.getCapacity()>0,"The second furnace has no input slots yet");
        second.setItemStackForSlot((short)0,new ItemStack("Ore_Iron",3),false);
        for(int i=0;i<windows.size();i++) {
            int held=0;var shown=windows.get(i).getItemContainer();
            for(short slot=0;slot<shown.getCapacity();slot++){var stack=shown.getItemStack(slot);if(!ItemStack.isEmpty(stack))held+=stack.getQuantity();}
            require(held==(i==1?3:0),"The screen of furnace "+(i+1)+" shows "+held+" items after 3 went into furnace 2");
        }
        // A workshop with two workbenches: each is found as itself and offers its own crafting screen.
        for(var at:WORKBENCHES) {
            var found=WorkplaceBenchAccess.find(world,at);
            require(found!=null&&found.site().id().equals(workshop.id())&&found.pos().equals(at),"The workbench at "+at+" is not found as itself");
            require("Craft at this bench".equals(WorkplaceNativeBench.label(world,at)),"The workbench at "+at+" offers no button");
        }
        // A furnace outside every workplace is the base game's business: its use is never redirected.
        require(ChunkSectionBlockUtil.blockType(world,LOOSE_FURNACE.x,LOOSE_FURNACE.y,LOOSE_FURNACE.z).getBench()!=null,"The loose furnace was not placed");
        require(WorkplaceBenchAccess.find(world,LOOSE_FURNACE)==null,"A furnace outside every workplace is treated as a workplace bench");
        notes.add("Benches: "+rows.size()+" furnaces in one smeltery, each row found as its own block with its own base game screen ("+fillers
            +" side blocks lead to the right furnace); 3 ore put in furnace 2 showed in screen 2 only; 2 workbenches of one workshop each open themselves; a furnace outside a workplace is left alone. Handing the screen to a client needs a player and was not run.");
    }

    private static boolean hasNeeds(World world,UUID entity) {
        var store=world.getEntityStore().getStore();var ref=store.getExternalData().getRefFromUUID(entity);
        return ref!=null&&ref.isValid()&&store.getComponent(ref,VillagerNeeds.getComponentType())!=null;
    }
    /** Looks again every 150 ms, for up to 30 seconds. */
    private static CompletableFuture<Void> await(World world,String what,Supplier<Boolean> ready) {
        return await(world,what,ready,200);
    }
    private static CompletableFuture<Void> await(World world,String what,Supplier<Boolean> ready,int left) {
        var met=new boolean[1];
        return after(world,()->met[0]=ready.get()).thenCompose(ignored->{
            if(met[0])return CompletableFuture.completedFuture(null);
            if(left<=0)return CompletableFuture.failedFuture(new IllegalStateException("Gave up waiting for "+what));
            return await(world,what,ready,left-1);
        });
    }
    private static CompletableFuture<Void> after(World world,Runnable action){return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(150,TimeUnit.MILLISECONDS)).thenCompose(ignored->onWorld(world,action));}
    private static CompletableFuture<Void> onWorld(World world,Runnable action){return CompletableFuture.runAsync(()->{
        // No player holds this disposable fixture's one chunk active between delayed checks.
        var chunk=ChunkSectionBlockUtil.worldChunkIfInMemory(world,0,0);
        if(chunk!=null)ChunkSectionBlockUtil.startTicking(world,chunk);
        action.run();
    },world);}
    private static void require(boolean yes,String issue){if(!yes)throw new IllegalStateException(issue);}
    private static void report(CommandContext ctx,String message){ctx.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);}
}
