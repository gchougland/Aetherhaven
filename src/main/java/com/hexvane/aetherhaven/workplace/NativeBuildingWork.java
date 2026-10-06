package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.assembly.*;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;
import org.joml.Vector3d;

/** Adds attended placements to the existing paid assembly job. Never reconstructs a prefab or spawns its entities. */
public final class NativeBuildingWork {
    private NativeBuildingWork() {}
    public record Target(BuildingOrders.Cell cell,PlotAssemblyJob job) {
        public WarehouseBatch.Point point(){return new WarehouseBatch.Point(cell.x(),cell.y(),cell.z());}
    }
    public static String issue(World world,TownRecord town,BuildingOrders.Order order) {
        if(order==null)return "Choose a project for this yard.";
        if(!town.hasMemberOrOwner(order.author())||!town.playerCanManageConstructions(order.author()))return "The player who queued this project no longer has building permission.";
        var plot=town.findPlotById(order.plot());if(plot==null)return "This project no longer exists.";
        if(plot.getState()==PlotInstanceState.COMPLETE)return "Project complete.";
        if(plot.getState()!=PlotInstanceState.ASSEMBLING)return "Start construction and supply its materials first.";
        var job=AssemblyWorldRegistry.get(world,plot.getPlotId());
        if(job==null||!town.getTownId().equals(AssemblyWorldRegistry.townId(world,plot.getPlotId())))return "Visit the building site to prepare its assembly.";
        if(AssemblyWorldRegistry.phase(world,plot.getPlotId())!=PlotAssemblyPhase.PLACING)return "Waiting for the building site to finish clearing.";
        return null;
    }
    public static List<Target> candidates(World world,TownRecord town,ApprenticeWorkplace site,Vector3d from) {
        if(site.type()!=TownsfolkWorkType.BUILDING||site.building().paused())return List.of();
        for(var order:site.building().list()) {
            if(issue(world,town,order)!=null)continue;
            var plot=town.findPlotById(order.plot());var footprint=plot.toFootprint();var job=AssemblyWorldRegistry.get(world,order.plot());var runtime=AssemblyWorldRegistry.frontierRuntime(world,order.plot());
            if(runtime==null)continue;var found=new ArrayList<Target>();var it=runtime.frontierIterator();int scanned=0;
            while(it.hasNext()&&scanned++<2048) {
                int index=it.nextInt();var block=job.pendingBlocks().get(index);int x=job.anchor().x+block.x(),y=job.anchor().y+block.y(),z=job.anchor().z+block.z();
                if(!physicalPiece(block))continue;
                if(!footprint.containsBlock(x,y,z)||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(x),ChunkUtil.chunkCoordinate(z))||ChunkSectionBlockUtil.worldChunkIfTicking(world,x,z)==null)continue;
                var type=BlockType.getAssetMap().getAsset(block.blockId());if(type==null)continue;
                found.add(new Target(new BuildingOrders.Cell(order.id(),index,x,y,z,type.getId(),block.blockRotation()),job));
            }
            found.sort(Comparator.comparingDouble((Target target)->from.distanceSquared(target.cell().x()+.5,target.cell().y()+.5,target.cell().z()+.5)).thenComparing(Comparator.comparingInt((Target t)->t.cell().index()).reversed()));
            if(!found.isEmpty())return List.copyOf(found.subList(0,Math.min(96,found.size())));
        }
        return List.of();
    }
    public static boolean valid(World world,TownRecord town,ApprenticeWorkplace site,Target target) {
        if(target==null||site.type()!=TownsfolkWorkType.BUILDING||site.building().paused())return false;
        var order=site.building().find(target.cell().order());if(issue(world,town,order)!=null)return false;
        var job=AssemblyWorldRegistry.get(world,order.plot());if(job!=target.job())return false;
        var runtime=AssemblyWorldRegistry.frontierRuntime(world,order.plot());if(runtime==null||!runtime.frontierContains(target.cell().index()))return false;
        var c=target.cell();if(c.index()<0||c.index()>=job.pendingBlocks().size())return false;
        var block=job.pendingBlocks().get(c.index());if(!physicalPiece(block))return false;var type=BlockType.getAssetMap().getAsset(block.blockId());
        return type!=null&&type.getId().equals(c.block())&&block.blockRotation()==c.rotation()
            &&job.anchor().x+block.x()==c.x()&&job.anchor().y+block.y()==c.y()&&job.anchor().z+block.z()==c.z()
            &&town.findPlotById(order.plot()).toFootprint().containsBlock(c.x(),c.y(),c.z())&&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(c.x()),ChunkUtil.chunkCoordinate(c.z()))&&ChunkSectionBlockUtil.worldChunkIfTicking(world,c.x(),c.z())!=null;
    }
    public static boolean inReach(Vector3d position,Target target){return position!=null&&target!=null&&position.distanceSquared(target.cell().x()+.5,target.cell().y()+.5,target.cell().z()+.5)<=16&&safeStanding(position,target);}
    /** Keep the worker outside the rotated bounds, including furniture companion cells. */
    private static boolean safeStanding(Vector3d position,Target target) {
        var cell=target.cell();var type=BlockType.getAssetMap().getAsset(cell.block());if(type==null)return false;
        var hitbox=BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());if(hitbox==null)return false;
        var box=hitbox.get(cell.rotation()).getBoundingBox();
        return position.x+.35<=cell.x()+box.min.x||position.x-.35>=cell.x()+box.max.x
            ||position.y+1.8<=cell.y()+box.min.y||position.y>=cell.y()+box.max.y
            ||position.z+.35<=cell.z()+box.min.z||position.z-.35>=cell.z()+box.max.z;
    }
    private static boolean physicalPiece(ConstructionPasteOps.PendingBlock block) {
        return block.blockId()!=0&&block.filler()==0&&!ConstructionPasteOps.isInteractiveBlockEntityOrigin(block,BlockType.getAssetMap());
    }
    public static boolean place(World world,AetherhavenPlugin plugin,TownRecord town,ApprenticeWorkplace site,Target target,Vector3d worker,int skill,int tier) {
        if(!valid(world,town,site,target)||!inReach(worker,target)||!site.building().ready(target.cell(),BuildingOrders.duration(skill,tier)))return false;
        var order=site.building().find(target.cell().order());var plot=town.findPlotById(order.plot());
        var block=target.job().pendingBlocks().get(target.cell().index());
        var type=BlockType.getAssetMap().getAsset(block.blockId());var hitbox=BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        var footprint=plot.toFootprint();
        if(hitbox==null||!FillerBlockUtil.testFillerBlocks(hitbox.get(block.blockRotation()),(dx,dy,dz)->{
            int x=target.cell().x()+dx,y=target.cell().y()+dy,z=target.cell().z()+dz;
            return footprint.containsBlock(x,y,z)&&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(x),ChunkUtil.chunkCoordinate(z))&&ChunkSectionBlockUtil.worldChunkIfTicking(world,x,z)!=null;
        }))return false;
        var plan=site.building();if(!plan.beginPlacement(target.cell()))return false;
        try {
            var result=PlotAssemblyService.advancePlacementAtIndex(world,plugin,world.getEntityStore().getStore(),town,plot,target.job(),target.cell().index(),false,null,true,false);
            return result.progressed()&&plan.committed(target.cell());
        } finally {
            if(plan.endPlacement())AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).updateTown(town);
        }
    }
}
