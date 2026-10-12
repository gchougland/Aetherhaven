package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.construction.*;
import com.hexvane.aetherhaven.construction.assembly.*;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.*;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Real rotated prefab frontiers in a disposable world. Does not claim to exercise the NPC pathfinder. */
final class AetherhavenBuildingTestCommand extends AbstractCommandCollection {
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean();
    AetherhavenBuildingTestCommand(){super("building","Check Apprentice building in an isolated world");setPermissionGroups("hytale:WorldEditor");addSubCommand(new Check());addSubCommand(new BuilderMasteryWorldCheck());}
    private static final class Check extends AbstractAsyncCommand {
        Check(){super("worldcheck","Check paid project gates, rotated placement and duplicate prevention");setPermissionGroups("hytale:WorldEditor");}
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if(!RUNNING.compareAndSet(false,true)){report(ctx,"A building check is already running.");return CompletableFuture.completedFuture(null);}
            String name="aetherhaven_building_check_"+UUID.randomUUID().toString().replace("-","");var universe=Universe.get();var path=universe.validateWorldPath(name);
            if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));}
            var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
            config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
            return universe.makeWorld(name,path,config).thenCompose(world->exercise(world).orTimeout(60,TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
                if(error==null)report(ctx,"Building isolated world check: PASSED. Four prefab rotations, attended placement, queue pause, changed permission, wrong target, duplicate placement and filler exclusion.");
                else {Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,"Building isolated world check: FAILED: "+cause.getMessage());}
                universe.removeWorld(name);
            })).whenComplete((done,error)->RUNNING.set(false));
        }
    }
    private static CompletableFuture<Void> exercise(World world) {
        var sections=new ArrayList<CompletableFuture<?>>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=3;y<=5;y++)sections.add(world.getChunkStore().getChunkSectionReferenceAsync(x,y,z,GetChunkFlags.SET_TICKING));
        return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new)).thenRunAsync(()->{
            var plugin=AetherhavenPlugin.get();var definition=plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE);
            require(definition!=null,"House definition missing");var path=PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());require(path!=null,"House prefab missing");
            var prefab=PrefabBufferUtil.getCached(path);require(prefab!=null,"House prefab did not open");
            for(var yaw:Rotation.NORMAL) {
                var owner=UUID.randomUUID();var town=new TownRecord(UUID.randomUUID(),owner,world.getName(),16,128,16,1,1,0);
                var sequence=ConstructionPasteOps.buildSequence(prefab,yaw);var pending=ConstructionPasteOps.withoutPureAirCells(sequence.pendingBlocks());require(pending.size()>20,"House fixture too small");
                var anchor=new Vector3i(16,128,16);var plot=new PlotInstance(UUID.randomUUID(),definition.getId(),PlotInstanceState.ASSEMBLING,new PlotFootprintRecord(-31,96,-31,63,191,63),16,128,16,0);
                plot.setPrefabWorldPlacement(anchor.x,anchor.y,anchor.z,yaw);plot.resetAssemblyPlacementProgress();plot.setAssemblyOwnerUuid(owner);town.getPlotInstances().add(plot);
                var job=new PlotAssemblyJob(plot.getPlotId(),owner,anchor,yaw,sequence.pendingBlocks(),AssemblyFootprintIndex.build(sequence.pendingBlocks()),pending,sequence.prefabEntitiesInOrder(),prefab,sequence.prefabRotation(),0,1000000,false,definition.getId());
                AssemblyWorldRegistry.put(world,plot.getPlotId(),town.getTownId(),job,PlotAssemblyPhase.PLACING,PlotAssemblyFrontierRuntime.create(pending,plot,null),null,null);
                var site=new ApprenticeWorkplace("Fixture yard",TownsfolkWorkType.BUILDING,new WorkplaceArea(4,128,4,8,8));
                require(site.building().add(plot.getPlotId(),owner)==null,"Project queue failed");var order=site.building().list().getFirst();
                require(NativeBuildingWork.issue(world,town,order)==null,"Started project blocked");
                require(NativeBuildingWork.issue(world,town,new BuildingOrders.Order(UUID.randomUUID(),plot.getPlotId(),UUID.randomUUID()))!=null,"Nonmember can authorize construction");
                plot.setState(PlotInstanceState.COMPLETE);require(NativeBuildingWork.issue(world,town,order)!=null,"Completed project considered work");plot.setState(PlotInstanceState.ASSEMBLING);
                var candidates=NativeBuildingWork.candidates(world,town,site,new Vector3d(16,128,16));require(!candidates.isEmpty(),"No physical frontier for "+yaw);
                NativeBuildingWork.Target target=null;Vector3d worker=null;
                for(var candidate:candidates) {
                    var c=candidate.cell();
                    for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2},{3,0},{-3,0},{0,3},{0,-3}}) {
                        var stand=new Vector3d(c.x()+.5+offset[0],c.y(),c.z()+.5+offset[1]);
                        if(NativeBuildingWork.inReach(stand,candidate)){target=candidate;worker=stand;break;}
                    }
                    if(target!=null)break;
                }
                require(target!=null,"No safe placement bounds for "+yaw);var cell=target.cell();var nativeBlock=pending.get(cell.index());
                require(nativeBlock.filler()==0&&!ConstructionPasteOps.isInteractiveBlockEntityOrigin(nativeBlock,BlockType.getAssetMap()),"Deferred furniture selected as visible work");
                ChunkSectionBlockUtil.setBlockEmpty(world,cell.x(),cell.y(),cell.z(),0);
                require(site.building().focus(cell),"Focus failed");
                require(!NativeBuildingWork.place(world,plugin,town,site,target,worker,1,1),"Unattended placement succeeded");
                for(int i=0;i<20;i++)site.building().attend(cell,.5,true,1);
                require(!NativeBuildingWork.place(world,plugin,town,site,target,new Vector3d(100,200,100),1,1),"Distant placement succeeded");
                var forged=new NativeBuildingWork.Target(new BuildingOrders.Cell(cell.order(),cell.index(),cell.x()+1,cell.y(),cell.z(),cell.block(),cell.rotation()),job);
                require(!NativeBuildingWork.valid(world,town,site,forged),"Forged cell points at a different block");
                site.building().pause(true);require(!NativeBuildingWork.place(world,plugin,town,site,target,worker,1,1),"Paused placement succeeded");site.building().pause(false);
                require(NativeBuildingWork.place(world,plugin,town,site,target,worker,1,1),"Physical placement failed: "+yaw+" "+cell);
                require(cell.block().equals(ChunkSectionBlockUtil.blockType(world,cell.x(),cell.y(),cell.z()).getId()),"Placed block differs from native prefab");
                require(cell.rotation()==ChunkSectionBlockUtil.rotationIndex(world,cell.x(),cell.y(),cell.z()),"Rotation differs from native prefab");
                require(plot.getAssemblyPlacedBlockCount()==1&&site.building().contributions()==1,"Placement accounting differs");
                require(!NativeBuildingWork.place(world,plugin,town,site,target,worker,1,1),"Repeat placement succeeded");
                var other=new ApprenticeWorkplace("Other yard",TownsfolkWorkType.BUILDING,site.area());other.building().add(plot.getPlotId(),owner);
                var otherCell=new BuildingOrders.Cell(other.building().list().getFirst().id(),cell.index(),cell.x(),cell.y(),cell.z(),cell.block(),cell.rotation());other.building().focus(otherCell);
                for(int i=0;i<20;i++)other.building().attend(otherCell,.5,true,1);
                require(!NativeBuildingWork.place(world,plugin,town,other,new NativeBuildingWork.Target(otherCell,job),worker,1,1),"Second yard duplicated placement");
                for(int i=0;i<pending.size();i++)if(pending.get(i).filler()!=0) {
                    var filler=pending.get(i);var block=BlockType.getAssetMap().getAsset(filler.blockId());
                    var fillerCell=new BuildingOrders.Cell(order.id(),i,anchor.x+filler.x(),anchor.y+filler.y(),anchor.z+filler.z(),block.getId(),filler.blockRotation());
                    require(!NativeBuildingWork.valid(world,town,site,new NativeBuildingWork.Target(fillerCell,job)),"Filler can become a second furniture origin");
                }
                require(plot.getState()==PlotInstanceState.ASSEMBLING,"Partial fixture completed a whole house");
            }
        },world);
    }
    private static void require(boolean yes,String issue){if(!yes)throw new IllegalStateException(issue);}
    private static void report(CommandContext ctx,String message){ctx.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);}
}
