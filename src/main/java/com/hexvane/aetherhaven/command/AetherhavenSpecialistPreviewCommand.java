package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.*;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.world.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.*;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Fresh shipped prefabs and normal town access in an explicitly temporary, unsaved world. */
final class AetherhavenSpecialistPreviewCommand extends AbstractPlayerCommand {
    private static final List<String> KINDS=List.of("druid","alchemist","doctor","monster_hunter","warehouse","crafter","leatherworker","simple_workplace","lumbermill","farm","barn");
    private static final int PLATFORM_MARGIN=14;
    private static final Map<UUID,Session> SESSIONS=new ConcurrentHashMap<>();
    private final RequiredArg<String> action=withRequiredArg("action","start, visit, workcheck, minework, mineshaft, huntfunds, loggercheck, loggerfire, loggergrove, halloweencoffin, halloweenresident, halloweenstatus, status or finish",ArgTypes.STRING);
    private final OptionalArg<String> layout=withOptionalArg("layout","Mining fixture: straight, branches, stairs_up or stairs_down",ArgTypes.STRING);
    private final OptionalArg<String> fittings=withOptionalArg("fittings","Mining fixture: none, supports, lights or both",ArgTypes.STRING);
    private final OptionalArg<String> profession=withOptionalArg("profession","Next native crafting profession after the current fixture finishes",ArgTypes.STRING);
    private final OptionalArg<String> building=withOptionalArg("building","Building ID for visit, such as plot_druid",ArgTypes.STRING);
    private final OptionalArg<String> stage=withOptionalArg("stage","Profile review: 49, 50, 74, 75, 99, 100, max or readonly",ArgTypes.STRING);
    private static final class Session {
        final PlayerRef player; final String home; final Vector3d position; final Rotation3f rotation;
        final String name="aetherhaven_specialist_preview_"+UUID.randomUUID().toString().replace("-","");
        final Map<String,Vector3d> visits=new LinkedHashMap<>();
        final AtomicBoolean returning=new AtomicBoolean(); final AtomicBoolean disposed=new AtomicBoolean();
        final CompletableFuture<Void> closed=new CompletableFuture<>();
        final CompletableFuture<Void> prepared=new CompletableFuture<>();
        // Protected by this session's monitor. Never time out the native completion future.
        CompletableFuture<Void> travel=CompletableFuture.completedFuture(null);
        CompletableFuture<Void> additionalPreparation=CompletableFuture.completedFuture(null);
        final long expires=System.currentTimeMillis()+30*60_000L;
        volatile World world; volatile boolean ready;
        Vector3i movedCauldron;
        SpecialistWorkPreview work;
        SpecialistLoggerPreview logger;
        SpecialistFieldPreview field;
        SpecialistHerdPreview herd;
        SpecialistArtPreview art;
        SpecialistExcavationPreview excavation;
        SpecialistHalloweenPreview halloween;
        UUID mineshaft;
        boolean huntFunded;
        Session(PlayerRef player,World home,TransformComponent transform) {
            this.player=player;this.home=home.getName();position=new Vector3d(transform.getPosition());rotation=new Rotation3f(transform.getRotation());
        }
    }
    AetherhavenSpecialistPreviewCommand() {
        super("specialistpreview","Visit fresh specialist buildings in a temporary preview world");
        setPermissionGroups("hytale:WorldEditor");
        AetherhavenPlugin.get().getEventRegistry().register((short)(ShutdownEvent.DISCONNECT_PLAYERS-1),ShutdownEvent.class,event->shutdown());
    }
    @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world) {
        var plugin=AetherhavenPlugin.get();if(!AetherhavenDebugUtil.requireDebug(plugin,player))return;
        var session=SESSIONS.get(player.getUuid());
        switch(ctx.get(action)) {
            case "start" -> {
                if(session!=null){say(player,"A preview is already active. Use visit, status or finish.");return;}
                var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return;
                var created=new Session(player,world,transform);
                if(SESSIONS.putIfAbsent(player.getUuid(),created)!=null)return;
                start(created);
            }
            case "visit" -> {
                if(session==null||!session.ready||session.returning.get()){say(player,"Start a preview and wait for its buildings to load or return travel to finish.");return;}
                String id=ctx.provided(building)?ctx.get(building):"plot_druid";
                if(!id.startsWith("plot_"))id="plot_"+id;
                var destination=session.visits.get(id);
                if(destination==null){say(player,"Choose "+String.join(", ",session.visits.keySet()));return;}
                visit(session,destination);
            }
            case "finish" -> {if(session==null)say(player,"No specialist preview is active.");else finish(session);}
            case "away" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world){say(player,"Enter a ready specialist preview first.");return;}
                var home=Universe.get().getWorld(session.home);
                if(home==null){say(player,"Your original world is unavailable.");return;}
                teleport(session,home,session.position,session.rotation,false).whenComplete((done,error)->
                    say(player,error==null?"Preview kept open for the absence check. Use visit to return or finish to close it.":"Preview travel failed. Use visit to retry."));
            }
            case "presence" -> {
                if(session==null||!session.ready||session.returning.get()){say(player,"Start a preview first.");return;}
                session.world.execute(()->{
                    if(session.disposed.get()||session.returning.get())return;
                    var manager=AetherhavenWorldRegistries.getOrCreateTownManager(session.world,plugin);
                    var town=manager.findTownForOwnerInWorld(player.getUuid());
                    if(town==null){say(player,"Preview town is unavailable.");return;}
                    say(player,"Preview presence "+com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(session.world.getEntityStore().getStore(),town)
                        +", wage minutes "+town.getPayroll().onlineMinutes()+", unpaid gold "+com.hexvane.aetherhaven.economy.TownPayrollService.budget(town).unpaidGold()+".");
                    if(session.excavation!=null)say(player,session.excavation.status(session.world,player.getUuid()));
                });
            }
            case "uicheck" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)){say(player,"Enter a ready unsaved specialist preview first.");return;}
                var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(player.getUuid());
                require(town!=null,"Preview town missing");
                String screen=ctx.provided(stage)?ctx.get(stage):"records";
                var pages=store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager();
                switch(screen) {
                    case "journal" -> com.hexvane.aetherhaven.ui.QuestJournalPage.openTown(ref,store,player,town.getTownId());
                    case "warehouse" -> pages.openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage(player,town.getTownId(),"warehouse_manager"));
                    case "orders" -> {
                        require(session.work!=null,"Run workcheck first");
                        pages.openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.WorkplaceOrdersPage(player,town.getTownId(),session.work.siteId()));
                    }
                    case "doctor" -> pages.openCustomPage(ref,store,new com.hexvane.aetherhaven.mastery.CoreMasteryPage(player,town.getTownId(),"doctor"));
                    case "records", "residents" -> {
                        var plot=town.getPlotInstances().stream().filter(p->"plot_crafter".equals(p.getConstructionId())).findFirst().orElseThrow();
                        var pos=new Vector3i(plot.getSignX(),plot.getSignY(),plot.getSignZ());
                        var block=ChunkSectionBlockUtil.blockEntityRefAt(world,pos.x,pos.y,pos.z);require(block!=null&&block.isValid(),"Preview records missing");
                        if(screen.equals("records"))pages.openCustomPage(ref,store,new com.hexvane.aetherhaven.ui.PlotConstructionPage(player,block,pos,true));
                        else pages.openCustomPage(ref,store,new com.hexvane.aetherhaven.ui.VillagerNeedsOverviewPage(player,town.getTownId(),block,pos));
                    }
                    default -> {say(player,"Choose records, residents, journal, doctor, warehouse or orders.");return;}
                }
                say(player,"Preview "+screen+" opened. No supplies or progression were granted.");
            }
            case "labmove", "labstatus" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)){say(player,"Enter a ready unsaved specialist preview first.");return;}
                if("labmove".equals(ctx.get(action))) {
                    require(session.movedCauldron==null,"This preview Laboratory was already moved");
                    var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(player.getUuid());
                    require(town!=null,"Preview town missing");
                    var plot=town.getPlotInstances().stream().filter(p->"plot_alchemist".equals(p.getConstructionId())).findFirst().orElseThrow();
                    var footprint=plot.toFootprint();Vector3i cauldron=null;
                    for(int x=footprint.getMinX();x<=footprint.getMaxX();x++)for(int y=footprint.getMinY();y<=footprint.getMaxY();y++)for(int z=footprint.getMinZ();z<=footprint.getMaxZ();z++) {
                        var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
                        if(block!=null&&"Alchemy_Cauldron_Big".equals(block.getId())&&ChunkSectionBlockUtil.filler(world,x,y,z)==0)cauldron=new Vector3i(x,y,z);
                    }
                    require(cauldron!=null,"Original Laboratory cauldron missing");
                    var def=plugin.getConstructionCatalog().get(plot.getConstructionId());
                    var yaw=plot.resolvePrefabYaw();
                    var anchor=def.resolvePreviewSignAnchorWorld(plot.resolvePrefabAnchorWorld(def),yaw).add(1,0,0);
                    var move=com.hexvane.aetherhaven.placement.PlotPlacementSession.forRelocatingPlot(world,anchor,
                        com.hexvane.aetherhaven.placement.PlotPlacementSession.rotationStepsFromPrefabYaw(yaw),plot.getConstructionId(),plot.getPlotId());
                    require(com.hexvane.aetherhaven.placement.PlotBuildingRelocation.tryCommit(ref,store,move,player.getUuid()),"Normal Laboratory move was rejected");
                    session.movedCauldron=cauldron.add(1,0,0);
                    say(player,"Moving the disposable Laboratory one block through normal relocation. Use labstatus after it finishes.");
                } else {
                    require(session.movedCauldron!=null,"Run labmove first");
                    var at=session.movedCauldron;var block=ChunkSectionBlockUtil.blockType(world,at);
                    require(block!=null&&"Alchemy_Cauldron_Big".equals(block.getId()),"Moved cauldron is missing at "+at);
                    say(player,"Laboratory relocation PASSED: cauldron retained at "+at+". Saved buildings unchanged.");
                }
            }
            case "profilecheck" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)){say(player,"Enter a ready unsaved specialist preview first.");return;}
                String selected=ctx.provided(stage)?ctx.get(stage):"49";
                if(!Set.of("49","50","74","75","99","100","max","readonly").contains(selected)) {
                    say(player,"Choose 49, 50, 74, 75, 99, 100, max or readonly.");return;
                }
                var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
                var town=manager.findTownForOwnerInWorld(player.getUuid());
                require(town!=null,"Preview town missing");
                // Reputation 100 can otherwise trigger a real birthday gift during this visual fixture.
                var time=store.getResource(com.hypixel.hytale.server.core.modules.time.WorldTimeResource.getResourceType());
                var journal=store.getComponent(ref,com.hexvane.aetherhaven.ui.PlayerTownJournalState.getComponentType());
                if(com.hexvane.aetherhaven.calendar.PlayerBirthdayService.isBirthdayToday(journal,
                    com.hexvane.aetherhaven.hud.AetherhavenCalendar.from(time.getGameDateTime())))
                    time.setGameTime(time.getGameTime().plus(1,java.time.temporal.ChronoUnit.DAYS),world,store);
                var resident=town.getResidentNpcRecords().stream().filter(r->"logger".equals(r.getKind())).findFirst().orElseThrow();
                var permissions=TownMemberPermissions.fullMember();
                permissions.setManageConstructions(!"readonly".equals(selected));
                town.putMemberPermissions(player.getUuid(),permissions);
                var entry=new com.hexvane.aetherhaven.reputation.VillagerReputationEntry();
                entry.setReputation(Set.of("max","readonly").contains(selected)?100:Integer.parseInt(selected));
                town.getPlayerVillagerReputation().computeIfAbsent(player.getUuid().toString(),ignored->new HashMap<>())
                    .put(resident.getLastEntityUuid().toString(),entry);
                if("max".equals(selected))while(com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"logger").train()){}
                manager.updateTown(town);
                store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                    .openCustomPage(ref,store,new com.hexvane.aetherhaven.mastery.CoreMasteryPage(player,town.getTownId(),"logger"));
                say(player,"Temporary Logger profile: reputation "+entry.getReputation()+", Mastery "
                    +com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"logger").level()
                    +", management "+permissions.manageConstructions()+". Saved town and inventory unchanged.");
            }
            case "upgradecheck", "upgraderevoke", "upgraderemove" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)){say(player,"Enter a ready unsaved specialist preview first.");return;}
                var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
                var town=manager.findTownForOwnerInWorld(player.getUuid());
                require(town!=null,"Preview town missing");
                if("upgraderevoke".equals(ctx.get(action))) {
                    var permissions=town.getEffectiveMemberPermissions(player.getUuid());permissions.setManageConstructions(false);
                    town.putMemberPermissions(player.getUuid(),permissions);
                    say(player,"Preview building permission revoked. The open screen has not been refreshed.");
                } else if("upgraderemove".equals(ctx.get(action))) {
                    town.getPlotInstances().removeIf(p->"plot_lumbermill".equals(p.getConstructionId()));
                    say(player,"Preview lumbermill registration removed. The open screen has not been refreshed.");
                } else {
                    town.putMemberPermissions(player.getUuid(),TownMemberPermissions.fullMember());
                    store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                        .openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage(player,town.getTownId(),"logger"));
                    say(player,"Preview Logger upgrades opened. No purchase, inventory change or authored block edit was made.");
                }
                manager.updateTown(town);
            }
            case "halloweencoffin", "halloweenresident", "halloweenstatus" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)){say(player,"Enter a ready unsaved specialist preview first.");return;}
                String selected=ctx.get(action);
                if("halloweenstatus".equals(selected)) {
                    say(player,session.halloween==null?"Run halloweencoffin first.":session.halloween.status(world,player.getUuid()));return;
                }
                if("halloweencoffin".equals(selected)&&session.halloween!=null) {
                    say(player,session.halloween.status(world,player.getUuid()));
                    visit(session,session.halloween.viewpoint());return;
                }
                var completion=new CompletableFuture<Void>();
                synchronized(session) {
                    if(session.returning.get()||session.disposed.get()||!session.additionalPreparation.isDone()) {
                        say(player,"Preview scenery is still loading or closing.");return;
                    }
                    session.additionalPreparation=completion;
                }
                try {
                    CompletableFuture<Void> task;
                    if("halloweencoffin".equals(selected)) {
                        task=SpecialistHalloweenPreview.create(world,player.getUuid(),KINDS.size(),
                            ()->!session.returning.get()&&!session.disposed.get()).thenAcceptAsync(fixture->{
                                require(!session.returning.get()&&!session.disposed.get(),"The preview is closing.");
                                session.halloween=fixture;
                                session.visits.put("plot_halloween_coffin",fixture.viewpoint());
                                say(player,"A closed coffin is ready. Open it and accept Jack's rescue through normal dialogue. Then use halloweenresident. No rescue or reward was granted.");
                                say(player,fixture.status(world,player.getUuid()));
                                visit(session,fixture.viewpoint());
                            },world);
                    } else {
                        require(session.halloween!=null,"Run halloweencoffin and accept the rescue first.");
                        session.halloween.requireShelter(world,player.getUuid());
                        task=place(session,"halloween",KINDS.size()).thenRunAsync(()->{
                            require(!session.returning.get()&&!session.disposed.get(),"The preview is closing.");
                            session.visits.put("plot_halloween",session.halloween.shelterViewpoint());
                            say(player,"Fixture shelter and resident supplied after the accepted rescue. This setup skips Inn recruitment. Start hide and seek through Jack's normal dialogue.");
                            say(player,session.halloween.status(world,player.getUuid()));
                            visit(session,session.visits.get("plot_halloween"));
                        },world);
                    }
                    task.whenComplete((done,error)->{
                        if(error!=null)say(player,"Halloween preview setup failed: "+error.getMessage());
                        completion.complete(null);
                    });
                } catch(RuntimeException error) {
                    say(player,"Halloween preview setup failed: "+error.getMessage());completion.complete(null);
                }
            }
            case "workcheck" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world) {
                    say(player,"Enter a ready specialist preview first.");return;
                }
                try {
                    if(session.work==null)session.work=SpecialistWorkPreview.create(world,player.getUuid(),session.visits.get("plot_crafter"));
                    if(ctx.provided(profession)) {
                        var type=com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.byId(ctx.get(profession));
                        if(type==null)throw new IllegalArgumentException("Unknown profession.");
                        session.work=session.work.next(world,player.getUuid(),type);
                    }
                    say(player,session.work.status(world,player.getUuid()));
                } catch(RuntimeException error) {say(player,"Work preview: "+error.getMessage());}
            }
            case "minework", "mineenclosed", "minereview" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world){say(player,"Enter a ready specialist preview first.");return;}
                try {
                    if(session.excavation==null)session.excavation=SpecialistExcavationPreview.create(world,player.getUuid(),session.visits.get("plot_crafter"),
                        ctx.provided(layout)?com.hexvane.aetherhaven.workplace.ExcavationPlan.Layout.valueOf(ctx.get(layout).toUpperCase(Locale.ROOT)):com.hexvane.aetherhaven.workplace.ExcavationPlan.Layout.STAIRS_UP,
                        ctx.provided(fittings)?com.hexvane.aetherhaven.workplace.ExcavationFittings.Mode.valueOf(ctx.get(fittings).toUpperCase(Locale.ROOT)):com.hexvane.aetherhaven.workplace.ExcavationFittings.Mode.NONE,"mineenclosed".equals(ctx.get(action)),!"minereview".equals(ctx.get(action)));
                    if("minereview".equals(ctx.get(action))) {
                        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(player.getUuid());
                        var site=town.getApprenticeWorkplaces().find(session.excavation.siteId());var area=site.area();
                        var bench=new Vector3i(area.minX()+1,area.floorY(),area.minZ()+1);
                        store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                            .openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.ExcavationPage(player,town.getTownId(),site.id(),bench));
                        say(player,"Mining terrain, core storage and an Apprentice are ready. Use Start reviewed tunnel to begin normal work.");
                    }
                    say(player,session.excavation.status(world,player.getUuid()));
                }catch(RuntimeException error){say(player,"Mining preview: "+error.getMessage());}
            }
            case "mineshaft" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)) {say(player,"Enter a ready unsaved specialist preview first.");return;}
                try {
                    var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
                    var town=manager.findTownForOwnerInWorld(player.getUuid());
                    var at=session.visits.get("plot_crafter");require(town!=null&&at!=null,"Artisan platform missing");
                    int x=(int)Math.floor(at.x),y=(int)Math.floor(at.y),z=(int)Math.floor(at.z);
                    var area=new com.hexvane.aetherhaven.workplace.WorkplaceArea(x-6,y,z-8,x+7,z+5);
                    var bench=new Vector3i(x,y,area.minZ()+1);
                    if(session.mineshaft==null) {
                        // Only pristine free space outside the authored prefab may become test terrain.
                        for(int bx=area.minX();bx<area.maxX();bx++)for(int bz=area.minZ();bz<area.maxZ();bz++)for(int by=y;by<y+5;by++)
                            require(ChunkSectionBlockUtil.blockSectionAt(world,bx,by,bz)!=null&&ChunkSectionBlockUtil.blockId(world,bx,by,bz)==0,"Use a fresh preview with an empty Artisan platform");
                        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));
                        var sites=town.getApprenticeWorkplaces();
                        require(sites.unlock(town,com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MINING)==null,"Mining fixture unlock failed");
                        require(sites.save(town,null,com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MINING,area,"Preview Mineshaft")==null,"Mining fixture area failed");
                        session.mineshaft=sites.sites().getLast().id();
                        var playerPosition=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
                        int playerX=(int)Math.floor(playerPosition.x),playerZ=(int)Math.floor(playerPosition.z);
                        for(int bx=area.minX()+3;bx<area.maxX()-3;bx++)for(int bz=area.minZ()+3;bz<area.maxZ()-3;bz++) {
                            if(Math.abs(bx-playerX)<=1&&Math.abs(bz-playerZ)<=1)continue;
                            for(int by=y;by<y+5;by++)require(ChunkSectionBlockUtil.setBlockByKey(world,bx,by,bz,"Rock_Stone",0),"Preview rock failed");
                        }
                        require(ChunkSectionBlockUtil.setBlockByKey(world,bench.x,bench.y,bench.z,"Bench_Workbench",0),"Preview planning bench failed");
                        manager.updateTown(town);
                    }
                    store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                        .openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.ExcavationPage(player,town.getTownId(),session.mineshaft,bench));
                    say(player,"Review-only mineshaft fixture. No core mine or worker was added, so starting excavation stays unavailable.");
                }catch(RuntimeException error){say(player,"Mineshaft preview: "+error.getMessage());}
            }
            case "herdcheck", "herdage", "herdpage" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)){say(player,"Enter a ready unsaved specialist preview first.");return;}
                try {
                    if(session.herd==null)session.herd=SpecialistHerdPreview.create(world,player.getUuid());
                    if("herdage".equals(ctx.get(action)))session.herd.advance(world,player.getUuid());
                    if("herdpage".equals(ctx.get(action))) {
                        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(player.getUuid());
                        store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                            .openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.RancherHerdPage(player,town.getTownId(),session.herd.plotId()));
                    }
                    say(player,session.herd.status(world,player.getUuid()));
                }catch(RuntimeException error){say(player,"Herd preview: "+error.getMessage());}
            }
            case "fieldcheck", "fieldripe", "fieldpage" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world
                    ||PersistentWorldSupport.shouldPersistWorldData(world)){say(player,"Enter a ready unsaved specialist preview first.");return;}
                try {
                    if(session.field==null)session.field=SpecialistFieldPreview.create(world,player.getUuid());
                    if("fieldripe".equals(ctx.get(action)))session.field.ripen(world,player.getUuid());
                    if("fieldpage".equals(ctx.get(action))) {
                        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(player.getUuid());
                        store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                            .openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.FarmerFieldPage(player,town.getTownId(),session.field.plotId()));
                    }
                    say(player,session.field.status(world,player.getUuid()));
                }catch(RuntimeException error){say(player,"Field preview: "+error.getMessage());}
            }
            case "loggercheck", "loggerfire", "loggertool", "loggergrove" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world) {
                    say(player,"Enter a ready specialist preview first.");return;
                }
                try {
                    if(session.logger==null)session.logger=SpecialistLoggerPreview.create(world,player.getUuid(),session.visits.get("plot_lumbermill"));
                    if("loggerfire".equals(ctx.get(action)))say(player,session.logger.toggleFire(world));
                    else if("loggertool".equals(ctx.get(action)))say(player,session.logger.toggleTool(world,player.getUuid()));
                    else if("loggergrove".equals(ctx.get(action))) {
                        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(player.getUuid());
                        store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                            .openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.TimberGrovePage(player,town.getTownId(),session.logger.plotId()));
                    }else say(player,session.logger.status(world,player.getUuid()));
                }catch(RuntimeException error){say(player,"Logger preview: "+error.getMessage());}
            }
            case "kitart" -> {
                if(session==null||!session.ready||session.returning.get()||world!=session.world) {
                    say(player,"Enter a ready specialist preview first.");return;
                }
                try {
                    if(session.art==null)session.art=new SpecialistArtPreview();
                    say(player,session.art.showNext(world,session.visits.get("plot_druid")));
                    visit(session,session.visits.get("plot_druid"));
                }catch(RuntimeException error){say(player,"Item preview: "+error.getMessage());}
            }
            case "huntfunds" -> {
                if(session==null||!session.ready||world!=session.world||session.returning.get()) {say(player,"Enter the ready specialist preview first.");return;}
                if(session.huntFunded) {say(player,"The preview hunt allowance was already supplied.");return;}
                var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get());
                var town=manager.findTownForOwnerInWorld(player.getUuid());
                if(town==null||PersistentWorldSupport.shouldPersistWorldData(world)) {say(player,"Use the unsaved preview town.");return;}
                town.addTreasuryGoldCoins(5000);session.huntFunded=true;manager.updateTown(town);
                say(player,"Added 5000 test gold to this preview treasury. Buy creature unlocks and training through the normal Hunter screen. No combat or spoils were granted.");
            }
            case "status" -> say(player,session==null?"No specialist preview is active.":"Preview "+session.name+". "+(session.ready?String.join(", ",session.visits.keySet()):"Loading buildings.")+" Return with /ah test specialistpreview finish.");
            default -> say(player,"Use start, visit --building=plot_druid, status or finish.");
        }
    }
    private static void start(Session session) {
        var universe=Universe.get();var path=universe.validateWorldPath(session.name);
        if(Files.exists(path)){session.prepared.complete(null);dispose(session);say(session.player,"Preview path already exists. Nothing changed.");return;}
        var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        // Use the vanilla flat world vegetation tint. Untinted void columns turn biome-tinted grass black.
        config.setWorldGenProvider(new VoidWorldGenProvider(FlatWorldGenProvider.DEFAULT_TINT,null));
        config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
        config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
        say(session.player,"Loading fresh specialist buildings and a lumbermill. Your current town is unchanged.");
        CompletableFuture<Void> preparation;
        try { preparation=universe.makeWorld(session.name,path,config).thenCompose(world->{
            session.world=world;
            require(!session.returning.get(),"Preview loading was cancelled");
            CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
            for(int i=0;i<KINDS.size();i++) {
                int index=i;chain=chain.thenCompose(ignored->place(session,KINDS.get(index),index));
            }
            return chain.thenRunAsync(()->{
                var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get());
                var town=manager.findTownForOwnerInWorld(session.player.getUuid());
                require(town!=null,"Preview town missing");
                var druid=town.getPlotInstances().stream().filter(p->"plot_druid".equals(p.getConstructionId())).findFirst().orElseThrow();
                var beds=com.hexvane.aetherhaven.production.NativeDruidCrops.review(world,town,druid);
                require(beds.ready()&&beds.beds().size()==9,"Druid bed review: "+beds.issue()+" ("+beds.beds().size()+" beds)");
                var storage=com.hexvane.aetherhaven.warehouse.ProductionStorageLocator.find(world,druid,System.currentTimeMillis());
                require(storage!=null,"Druid storage was not found in its fresh footprint");
                say(session.player,"Preview ready. Druid has nine reviewable beds and storage at "+storage.x()+", "+storage.y()+", "+storage.z()+". Nothing was adopted. Use visit --building=plot_alchemist or finish. Preview expires in 30 minutes.");
                session.ready=true;
            },world);
        }); } catch(RuntimeException error) { preparation=CompletableFuture.failedFuture(error); }
        preparation.whenComplete((done,error)->{
            session.prepared.complete(null);
            if(error!=null){say(session.player,"Preview could not load: "+error.getMessage());finish(session);}
            else if(!session.returning.get()){visit(session,session.visits.get("plot_druid"));watch(session);}
        });
        // A load timeout requests cancellation. Cleanup still waits for world creation and paste work.
        preparation.copy().orTimeout(90,TimeUnit.SECONDS).whenComplete((done,error)->{
            if(error instanceof TimeoutException){say(session.player,"Preview loading took too long. Closing it when pending work finishes.");finish(session);}
        });
    }
    private static CompletableFuture<Void> place(Session session,String kind,int index) {
        var plugin=AetherhavenPlugin.get();var world=session.world;
        var definition=plugin.getConstructionCatalog().get("plot_"+kind);require(definition!=null,"Missing "+kind);
        var path=PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());require(path!=null,"Missing prefab "+kind);
        var prefab=PrefabBufferUtil.getCached(path);require(prefab!=null,"Unreadable prefab "+kind);
        var sequence=ConstructionPasteOps.buildSequence(prefab,Rotation.None);
        var cells=sequence.pendingBlocks();require(!cells.isEmpty(),"Empty prefab "+kind);
        var origin=new Vector3i(32+(index%4)*64,128,32+(index/4)*64);
        var min=definition.getBoundsLocalMin();var max=definition.getBoundsLocalMax();
        if(min==null||max==null) {
            min=new Vector3i(cells.stream().mapToInt(ConstructionPasteOps.PendingBlock::x).min().orElseThrow(),cells.stream().mapToInt(ConstructionPasteOps.PendingBlock::y).min().orElseThrow(),cells.stream().mapToInt(ConstructionPasteOps.PendingBlock::z).min().orElseThrow());
            max=new Vector3i(cells.stream().mapToInt(ConstructionPasteOps.PendingBlock::x).max().orElseThrow(),cells.stream().mapToInt(ConstructionPasteOps.PendingBlock::y).max().orElseThrow(),cells.stream().mapToInt(ConstructionPasteOps.PendingBlock::z).max().orElseThrow());
        }
        int minX=origin.x+min.x,minY=origin.y+min.y,minZ=origin.z+min.z,maxX=origin.x+max.x,maxY=origin.y+max.y,maxZ=origin.z+max.z;
        var loads=new ArrayList<CompletableFuture<?>>();
        for(int x=Math.floorDiv(minX-PLATFORM_MARGIN,32);x<=Math.floorDiv(maxX+PLATFORM_MARGIN,32);x++)
            for(int z=Math.floorDiv(minZ-PLATFORM_MARGIN,32);z<=Math.floorDiv(maxZ+PLATFORM_MARGIN,32);z++)
                for(int y=Math.floorDiv(minY-1,32);y<=Math.floorDiv(maxY+3,32);y++)
                    loads.add(world.getChunkStore().getChunkSectionReferenceAsync(x,y,z,GetChunkFlags.SET_TICKING));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenRunAsync(()->{
            require(!session.returning.get()&&!session.disposed.get(),"Preview was cancelled");
            if("halloween".equals(kind)) {
                require(session.halloween!=null,"Prepare the preview coffin first");
                session.halloween.requireShelter(world,session.player.getUuid());
            }
            var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
            var town=manager.findTownForOwnerInWorld(session.player.getUuid());
            if(town==null){town=new TownRecord(UUID.randomUUID(),session.player.getUuid(),world.getName(),128,128,64,1,8,System.currentTimeMillis());town.setDisplayName("Specialist Preview");manager.markDirty(town);}
            require(!PersistentWorldSupport.shouldPersistWorldData(world),"Preview must never save town data");
            for(int x=minX-PLATFORM_MARGIN;x<=maxX+PLATFORM_MARGIN;x++)for(int z=minZ-PLATFORM_MARGIN;z<=maxZ+PLATFORM_MARGIN;z++)
                require(ensurePlatformCell(world,x,minY-1,z),"Preview platform failed");
            ConstructionPasteOps.forcePasteAllSolids(world,origin,Rotation.None,false,prefab);
            ConstructionPasteOps.placeInteractiveBlockEntitiesFromPrefab(world,origin,Rotation.None,prefab);
            ConstructionPasteOps.finishFluidsAndEntities(world,origin,sequence.prefabRotation(),index,false,prefab,sequence.prefabEntitiesInOrder(),world.getEntityStore().getStore());
            var management=definition.getManagementBlockLocalPos();
            var sign=management==null?new Vector3i(origin):new Vector3i(origin).add(management[0],management[1],management[2]);
            var plot=new PlotInstance(UUID.randomUUID(),definition.getId(),PlotInstanceState.COMPLETE,new PlotFootprintRecord(minX,minY,minZ,maxX,maxY,maxZ),sign.x,sign.y,sign.z,System.currentTimeMillis());
            plot.setPrefabWorldPlacement(origin.x,origin.y,origin.z,Rotation.None);plot.captureProductionLayout(definition);town.getPlotInstances().add(plot);
            com.hexvane.aetherhaven.poi.PoiExtractor.registerForCompletedBuild(plugin,world,world.getEntityStore().getStore(),town,plot.getPlotId(),plot,definition.getId(),origin,Rotation.None);
            if(management!=null){var block=ChunkSectionBlockUtil.blockEntityRefAt(world,sign.x,sign.y,sign.z);require(block!=null&&block.isValid(),"Records shelf missing "+kind);block.getStore().putComponent(block,ManagementBlock.getComponentType(),new ManagementBlock(plot.getPlotId().toString(),town.getTownId().toString()));}
            var resident=plugin.getVillagerDefinitionCatalog().allByNpcRoleId().values().stream().filter(v->definition.getId().equals(v.getWorkConstructionId())).findFirst().orElse(null);
            if(resident!=null&&!definition.getPois().isEmpty()) {
                var poi=definition.getPois().getFirst();var at=new Vector3d(origin.x+poi.getLocalX()+.5,origin.y+poi.getLocalY(),origin.z+poi.getLocalZ()+.5);
                var npc=TownVillagerSpawner.spawn(world.getEntityStore().getStore(),resident.getNpcRoleId(),at,new TownVillagerBinding(town.getTownId(),resident.getDialogueVillagerKind(),plot.getPlotId(),plot.getPlotId()),"specialist_preview","Temporary fresh building fixture");
                require(npc!=null,"Resident could not spawn "+kind);
                var id=world.getEntityStore().getStore().getComponent(npc,UUIDComponent.getComponentType()).getUuid();
                town.getResidentNpcRecords().add(new ResidentNpcRecord(resident.getNpcRoleId(),resident.getDialogueVillagerKind(),plot.getPlotId(),id));
            }
            manager.markDirty(town);
            if("leatherworker".equals(kind)) {
                // Free-standing review samples on the temporary platform, outside the authored prefab.
                // In particular, never insert the Hunter storage the player is still placing themselves.
                String[] furniture={"Aetherhaven_StitchingFrame","Aetherhaven_PatternCabinet","Aetherhaven_Warehouse_Ledger_Desk",
                    "Aetherhaven_Hunt_Practice_Standard","Aetherhaven_Hunt_Recovery_Brazier","Aetherhaven_Hunt_Warding_Sigil"};
                String[] visitIds={"plot_stitching_frame","plot_pattern_cabinet","plot_ledger_desk",
                    "plot_practice_standard","plot_recovery_brazier","plot_warding_sigil"};
                for(int sample=0;sample<furniture.length;sample++) {
                    int x=minX-8,z=minZ+sample*4;
                    for(var yaw:Rotation.VALUES) {
                        require(ChunkSectionBlockUtil.setBlockByKey(world,x,minY,z,furniture[sample],RotationTuple.index(yaw,Rotation.None,Rotation.None),0),"Furniture review sample failed: "+furniture[sample]);
                        verifyFurnitureCollision(world,x,minY,z,sample,yaw);
                    }
                    require(ChunkSectionBlockUtil.setBlockByKey(world,x,minY,z,furniture[sample],0),"Furniture review reset failed: "+furniture[sample]);
                    session.visits.put(visitIds[sample],new Vector3d(x+.5,minY,z+3.5));
                }
                say(session.player,"Furniture collision checks passed for all six pieces in all four rotations.");
            }
            var viewpoint=new Vector3d((minX+maxX)/2.0+.5,minY,maxZ+10.5);
            session.visits.put(definition.getId(),viewpoint);
            if("druid".equals(kind))world.getWorldConfig().setSpawnProvider(new GlobalSpawnProvider(new Transform(new Vector3d(viewpoint),new Rotation3f(Rotation3f.ZERO))));
        },world);
    }
    private static boolean ensurePlatformCell(World world,int x,int y,int z) {
        if(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,"Rock_Stone",0))return true;
        // Native setBlock returns false for an unchanged cell as well as for failure.
        var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
        return platformCellMatches(block==null?null:block.getId(),ChunkSectionBlockUtil.rotationIndex(world,x,y,z),ChunkSectionBlockUtil.filler(world,x,y,z));
    }
    static boolean platformCellMatches(String block,int rotation,int filler) {
        return "Rock_Stone".equals(block)&&rotation==RotationTuple.NONE_INDEX&&filler==0;
    }
    /** Probe real native collision, including upper and adjacent filler cells, without changing blocks. */
    private static void verifyFurnitureCollision(World world,int x,int y,int z,int sample,Rotation yaw) {
        // x/y/z offsets relative to the model origin, followed by expected occupancy.
        double[][] probes=switch(sample) {
            case 0 -> new double[][] {{-.375,.6,0,1},{0,.6,0,1},{-.375,1.03,0,1},
                {0,.08,0,0},{0,.6,.3,0},{.7,.6,0,0},{0,1.2,0,0}};
            case 1 -> new double[][] {{0,.5,0,1},{-.34,.06,0,1},
                {0,.06,0,0},{.6,.5,0,0},{0,.5,.6,0},{0,1.5,0,0}};
            case 2 -> new double[][] {{-1.25,.5,0,1},{-1.28,.08,-.3,1},
                {-1.7,.5,0,0},{.8,.5,0,0},{-.6,.6,.8,0},{-.6,2,0,0}};
            case 3 -> new double[][] {{0,.4,0,1},{0,1.4,0,1},{.3,.9,.06,1},
                {.3,.4,0,0},{0,.9,.4,0},{0,1.8,0,0}};
            case 4 -> new double[][] {{0,.4,0,1},{.3,.72,0,1},
                {.3,.4,0,0},{0,1.2,0,0},{.6,.72,0,0}};
            case 5 -> new double[][] {{0,.7,0,1},{.3,1.1,.16,1},
                {.3,.3,0,0},{0,1.5,0,0},{0,.7,-.4,0}};
            default -> throw new IllegalArgumentException("Unknown furniture sample");
        };
        var result=new CollisionResult(false,false);
        var probe=new Box(-.005,-.005,-.005,.005,.005,.005);
        for(int i=0;i<probes.length;i++) {
            var p=probes[i];
            double dx=switch(yaw){case None->p[0];case Ninety->p[2];case OneEighty->-p[0];case TwoSeventy->-p[2];};
            double dz=switch(yaw){case None->p[2];case Ninety->-p[0];case OneEighty->-p[2];case TwoSeventy->p[0];};
            CollisionModule.get().findIntersections(world,probe,new Vector3d(x+.5+dx,y+p[1],z+.5+dz),result,false,true);
            require((result.getBlockCollisionCount()>0)==(p[3]>0),"Furniture collision mismatch for sample "+sample+", probe "+i+", rotation "+yaw);
        }
    }
    private static void watch(Session session) {
        AetherhavenPlugin.get().getConstructionScheduler().schedule(()->{
            if(session.disposed.get())return;
            if(!connected(session)||Universe.get().getWorld(session.name)!=session.world||System.currentTimeMillis()>=session.expires)finish(session);
            watch(session);
        },30,TimeUnit.SECONDS);
    }
    private static boolean connected(Session session) {
        return session.player.getPacketHandler().stillActive();
    }
    private static void visit(Session session,Vector3d position) {
        teleport(session,session.world,position,Rotation3f.ZERO,false).whenComplete((done,error)->{
            if(error!=null)say(session.player,"Preview travel failed: "+error.getMessage()+". Use visit to retry or finish to close it.");
        });
    }
    private static void finish(Session session) {
        if(!session.returning.compareAndSet(false,true))return;
        if(!session.prepared.isDone())say(session.player,"The preview will close when loading finishes.");
        session.prepared.whenComplete((done,error)->finishAfterPreparation(session));
    }
    private static void finishAfterPreparation(Session session) {
        synchronized(session) {
            if(session.disposed.get())return;
            if(!session.travel.isDone()) {
                session.travel.whenCompleteAsync((done,error)->finishAfterPreparation(session));
                return;
            }
            if(!session.ready||!connected(session)){dispose(session);return;}
            var ref=session.player.getReference();
            // Cross-world travel temporarily removes the reference, even while still connected.
            if(ref==null||!ref.isValid()) {
                session.returning.set(false);
                say(session.player,"Your current travel has not settled. Preview kept open. Use finish again after arriving.");
                return;
            }
            var home=Universe.get().getWorld(session.home);
            if(home==null){session.returning.set(false);say(session.player,"Your original world is unavailable. Preview kept open so you can return safely.");return;}
            var returning=teleport(session,home,session.position,session.rotation,true);
            returning.whenCompleteAsync((done,error)->{
                if(error==null)dispose(session);
                else {session.returning.set(false);say(session.player,"Return travel failed. Use finish again; the preview remains open.");}
            });
            returning.copy().orTimeout(20,TimeUnit.SECONDS).whenComplete((done,error)->{
                if(error instanceof TimeoutException)say(session.player,"Return travel is still pending. The preview stays open until you arrive.");
            });
        }
    }
    private static CompletableFuture<Void> teleport(Session session,World destination,Vector3d position,Rotation3fc rotation,boolean returning) {
        var completion=new CompletableFuture<Void>();
        synchronized(session) {
            if(session.disposed.get()||!session.travel.isDone()||(!returning&&session.returning.get()))
                return CompletableFuture.failedFuture(new IllegalStateException("Preview travel is already pending or closing"));
            session.travel=completion;
        }
        try {
            require(connected(session),"Player disconnected");
            require(destination!=null&&Universe.get().getWorld(destination.getName())==destination,"Destination world is unavailable");
            var current=session.player.getReference();
            require(current!=null&&current.isValid(),"Player is still travelling or offline");
            var source=current.getStore().getExternalData().getWorld();
            source.execute(()->{
                try {
                    var ref=session.player.getReference();
                    require(ref!=null&&ref.isValid()&&ref.getStore().getExternalData().getWorld()==source,"Player moved while travel began");
                    require(connected(session),"Player disconnected");
                    require(Universe.get().getWorld(destination.getName())==destination,"Destination world was unloaded");
                    require(ref.getStore().getComponent(ref,Teleport.getComponentType())==null,"Another journey is already pending");
                    var teleport=Teleport.createForPlayer(destination,new Vector3d(position),new Rotation3f(rotation));
                    teleport.setOnComplete(completion);ref.getStore().putComponent(ref,Teleport.getComponentType(),teleport);
                } catch(RuntimeException error) {completion.completeExceptionally(error);}
            });
        } catch(RuntimeException error) {completion.completeExceptionally(error);}
        return completion;
    }
    private static void dispose(Session session) {
        synchronized(session) {
            if(!session.prepared.isDone()) {session.prepared.whenCompleteAsync((done,error)->dispose(session));return;}
            if(!session.additionalPreparation.isDone()) {session.additionalPreparation.whenCompleteAsync((done,error)->dispose(session));return;}
            if(!session.travel.isDone()) {session.travel.whenCompleteAsync((done,error)->dispose(session));return;}
            if(!session.disposed.compareAndSet(false,true))return;
        }
        CompletableFuture.runAsync(()->{
            var world=Universe.get().getWorld(session.name);
            if(world!=null&&world==session.world)require(Universe.get().removeWorld(session.name),"Preview world removal was cancelled");
        }).whenComplete((done,error)->{
            if(error==null){SESSIONS.remove(session.player.getUuid(),session);session.closed.complete(null);say(session.player,"Specialist preview closed. Your saved town was unchanged.");}
            else {
                session.disposed.set(false);session.returning.set(false);
                say(session.player,"Preview cleanup failed: "+error.getMessage()+". Use finish to retry.");
                // Keep the session available for retry and the shutdown waiter pending until removal succeeds.
            }
        });
    }
    private static void shutdown() {
        var sessions=List.copyOf(SESSIONS.values());sessions.forEach(AetherhavenSpecialistPreviewCommand::finish);
        // Run before native player disconnect, while their original worlds can still accept return travel.
        CompletableFuture.allOf(sessions.stream().map(s->s.closed).toArray(CompletableFuture[]::new))
            .exceptionally(error->null).completeOnTimeout(null,5,TimeUnit.SECONDS).join();
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void say(PlayerRef player,String message){player.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Specialist preview: %s",message);}
}
