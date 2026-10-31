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

/** Adds attended clearing and placements to the existing paid assembly job. Never reconstructs a prefab or spawns its entities. */
public final class NativeBuildingWork {
    private NativeBuildingWork() {}
    public record Target(BuildingOrders.Cell cell,PlotAssemblyJob job) {
        public WarehouseBatch.Point point(){return new WarehouseBatch.Point(cell.x(),cell.y(),cell.z());}
        public boolean clearing(){return cell.clearing();}
    }
    public static String issue(World world,TownRecord town,BuildingOrders.Order order) {
        if(order==null)return "Choose a project for this yard.";
        if(!town.hasMemberOrOwner(order.author())||!town.playerCanManageConstructions(order.author()))return "The player who queued this project no longer has building permission.";
        var plot=town.findPlotById(order.plot());if(plot==null)return "This project no longer exists.";
        if(plot.getState()==PlotInstanceState.COMPLETE)return "Project complete.";
        if(plot.getState()!=PlotInstanceState.ASSEMBLING)return "Start construction and supply its materials first.";
        var job=AssemblyWorldRegistry.get(world,plot.getPlotId());
        if(job==null||!town.getTownId().equals(AssemblyWorldRegistry.townId(world,plot.getPlotId())))return "Visit the building site to prepare its assembly.";
        if(!workPhase(AssemblyWorldRegistry.phase(world,plot.getPlotId())))return "Visit the building site to prepare its assembly.";
        return null;
    }
    static boolean workPhase(PlotAssemblyPhase phase){return phase==PlotAssemblyPhase.CLEARING||phase==PlotAssemblyPhase.PLACING;}
    public static List<Target> candidates(World world,TownRecord town,ApprenticeWorkplace site,Vector3d from) {
        if(site.type()!=TownsfolkWorkType.BUILDING||site.building().paused())return List.of();
        for(var order:site.building().list()) {
            if(issue(world,town,order)!=null)continue;
            var plot=town.findPlotById(order.plot());var footprint=plot.toFootprint();var job=AssemblyWorldRegistry.get(world,order.plot());
            if(AssemblyWorldRegistry.phase(world,order.plot())==PlotAssemblyPhase.CLEARING) {
                var clearing=AssemblyWorldRegistry.clearingFrontierRuntime(world,order.plot());
                if(clearing==null)continue;
                var found=new ArrayList<Target>();
                var exposed=new ArrayList<Integer>();
                for(int index=0;index<job.footprintCells().size();index++) {
                    var block=job.footprintCells().get(index);int x=job.anchor().x+block.x(),y=job.anchor().y+block.y(),z=job.anchor().z+block.z();
                    if(clearing.containsWorldCell(x,y,z)&&footprint.containsBlock(x,y,z))exposed.add(index);
                }
                exposed.sort(Comparator.comparingDouble(index->{var b=job.footprintCells().get(index);return from.distanceSquared(job.anchor().x+b.x()+.5,job.anchor().y+b.y()+.5,job.anchor().z+b.z()+.5);}));
                // A large flat site can expose thousands of cells. Bound terrain reads, nearest first.
                for(int cursor=0;cursor<Math.min(2048,exposed.size());cursor++) {
                    int index=exposed.get(cursor);var block=job.footprintCells().get(index);
                    int x=job.anchor().x+block.x(),y=job.anchor().y+block.y(),z=job.anchor().z+block.z();
                    if(!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(x),ChunkUtil.chunkCoordinate(z))||ChunkSectionBlockUtil.worldChunkIfTicking(world,x,z)==null)continue;
                    var type=ChunkSectionBlockUtil.blockType(world,x,y,z);
                    // Work on the origin of large blocks so their filler cannot be removed outside the project.
                    if(type==null||type==BlockType.EMPTY||ChunkSectionBlockUtil.filler(world,x,y,z)!=0)continue;
                    found.add(new Target(new BuildingOrders.Cell(order.id(),index,x,y,z,type.getId(),ChunkSectionBlockUtil.rotationIndex(world,x,y,z),true),job));
                }
                if(!found.isEmpty())return nearest(found,from);
                continue;
            }
            var runtime=AssemblyWorldRegistry.frontierRuntime(world,order.plot());
            if(runtime==null)continue;var found=new ArrayList<Target>();var it=runtime.frontierIterator();int scanned=0;
            while(it.hasNext()&&scanned++<2048) {
                int index=it.nextInt();var block=job.pendingBlocks().get(index);int x=job.anchor().x+block.x(),y=job.anchor().y+block.y(),z=job.anchor().z+block.z();
                if(!physicalPiece(block))continue;
                if(!footprint.containsBlock(x,y,z)||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(x),ChunkUtil.chunkCoordinate(z))||ChunkSectionBlockUtil.worldChunkIfTicking(world,x,z)==null)continue;
                var type=BlockType.getAssetMap().getAsset(block.blockId());if(type==null)continue;
                found.add(new Target(new BuildingOrders.Cell(order.id(),index,x,y,z,type.getId(),block.blockRotation()),job));
            }
            if(!found.isEmpty())return nearest(found,from);
        }
        return List.of();
    }
    private static List<Target> nearest(List<Target> found,Vector3d from) {
        found.sort(Comparator.comparingDouble((Target target)->from.distanceSquared(target.cell().x()+.5,target.cell().y()+.5,target.cell().z()+.5)).thenComparing(Comparator.comparingInt((Target t)->t.cell().index()).reversed()));
        return List.copyOf(found.subList(0,Math.min(96,found.size())));
    }
    /** Foundations and terrain are below a worker's feet; high wall pieces may be above them. */
    public static Vector3d findStand(Target target,java.util.function.Function<WarehouseBatch.Point,Vector3d> search) {
        for(int down=-2;down<=3;down++) {
            var stand=search.apply(new WarehouseBatch.Point(target.cell().x(),target.cell().y()-down,target.cell().z()));
            if(stand!=null)return stand;
        }
        return null;
    }
    public static boolean valid(World world,TownRecord town,ApprenticeWorkplace site,Target target) {
        if(target==null||site.type()!=TownsfolkWorkType.BUILDING||site.building().paused())return false;
        var order=site.building().find(target.cell().order());if(issue(world,town,order)!=null)return false;
        var job=AssemblyWorldRegistry.get(world,order.plot());if(job!=target.job())return false;
        if(target.clearing()) {
            var c=target.cell();var clearing=AssemblyWorldRegistry.clearingFrontierRuntime(world,order.plot());
            if(AssemblyWorldRegistry.phase(world,order.plot())!=PlotAssemblyPhase.CLEARING||clearing==null||!clearing.containsWorldCell(c.x(),c.y(),c.z())||c.index()<0||c.index()>=job.footprintCells().size())return false;
            var block=job.footprintCells().get(c.index());var type=ChunkSectionBlockUtil.blockType(world,c.x(),c.y(),c.z());
            return type!=null&&type.getId().equals(c.block())&&ChunkSectionBlockUtil.rotationIndex(world,c.x(),c.y(),c.z())==c.rotation()
                &&ChunkSectionBlockUtil.filler(world,c.x(),c.y(),c.z())==0
                &&job.anchor().x+block.x()==c.x()&&job.anchor().y+block.y()==c.y()&&job.anchor().z+block.z()==c.z()
                &&town.findPlotById(order.plot()).toFootprint().containsBlock(c.x(),c.y(),c.z())&&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(c.x()),ChunkUtil.chunkCoordinate(c.z()))&&ChunkSectionBlockUtil.worldChunkIfTicking(world,c.x(),c.z())!=null;
        }
        if(AssemblyWorldRegistry.phase(world,order.plot())!=PlotAssemblyPhase.PLACING)return false;
        var runtime=AssemblyWorldRegistry.frontierRuntime(world,order.plot());if(runtime==null||!runtime.frontierContains(target.cell().index()))return false;
        var c=target.cell();if(c.index()<0||c.index()>=job.pendingBlocks().size())return false;
        var block=job.pendingBlocks().get(c.index());if(!physicalPiece(block))return false;var type=BlockType.getAssetMap().getAsset(block.blockId());
        return type!=null&&type.getId().equals(c.block())&&block.blockRotation()==c.rotation()
            &&job.anchor().x+block.x()==c.x()&&job.anchor().y+block.y()==c.y()&&job.anchor().z+block.z()==c.z()
            &&town.findPlotById(order.plot()).toFootprint().containsBlock(c.x(),c.y(),c.z())&&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(c.x()),ChunkUtil.chunkCoordinate(c.z()))&&ChunkSectionBlockUtil.worldChunkIfTicking(world,c.x(),c.z())!=null;
    }
    public static boolean inReach(Vector3d position,Target target){return position!=null&&target!=null&&position.distanceSquared(target.cell().x()+.5,target.cell().y()+.5,target.cell().z()+.5)<=16&&safeStanding(position,target);}
    /**
     * Reach for a worker whose real size is known: the same distance, and the piece's box must be clear of the worker's
     * own bounding box from the engine. A townsfolk can be a full block wide or two blocks tall; the sizes assumed by
     * the older check below fit only the common model.
     */
    public static boolean inReach(Vector3d position,com.hypixel.hytale.math.shape.Box body,Target target) {
        return target!=null&&inReach(position,body,target.cell(),pieceBox(target));
    }
    public static boolean readyToWork(Vector3d position,Vector3d stand,com.hypixel.hytale.math.shape.Box body,Target target) {
        return target!=null&&readyToWork(position,stand,body,target.cell(),pieceBox(target));
    }
    static boolean readyToWork(Vector3d position,Vector3d stand,com.hypixel.hytale.math.shape.Box body,BuildingOrders.Cell cell,com.hypixel.hytale.math.shape.Box box) {
        return position!=null&&stand!=null&&com.hexvane.aetherhaven.autonomy.CourierWorkService.arrived(position,stand)&&inReach(position,body,cell,box);
    }
    static boolean inReach(Vector3d position,com.hypixel.hytale.math.shape.Box body,BuildingOrders.Cell cell,com.hypixel.hytale.math.shape.Box box) {
        return position!=null&&body!=null&&cell!=null&&box!=null&&position.distanceSquared(cell.x()+.5,cell.y()+.5,cell.z()+.5)<=16
            &&!SiteBodies.Body.at(null,false,position,body).overlapsBlock(cell.x(),cell.y(),cell.z(),box);
    }
    /** The space the piece fills once placed, relative to its cell. */
    public static com.hypixel.hytale.math.shape.Box pieceBox(Target target) {
        var type=BlockType.getAssetMap().getAsset(target.cell().block());if(type==null)return null;
        var hitbox=BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        if(hitbox==null)return null;
        var box=hitbox.get(target.cell().rotation()).getBoundingBox();
        // Do not dig out the ground directly beneath a worker or another resident.
        return target.clearing()?new com.hypixel.hytale.math.shape.Box(box.min.x,box.min.y,box.min.z,box.max.x,box.max.y+.1,box.max.z):box;
    }
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
        return place(world,plugin,town,site,target,null,worker,skill,tier);
    }
    /**
     * @param builder townsfolk id of the builder whose effort pays for this piece; null for the yard's unnamed slot.
     *     The piece is placed and credited at most once however many builders are ready: the assembly frontier
     *     refuses a cell that is already placed, and only this builder's own effort is spent.
     */
    public static boolean place(World world,AetherhavenPlugin plugin,TownRecord town,ApprenticeWorkplace site,Target target,String builder,Vector3d worker,int skill,int tier) {
        return place(world,plugin,town,site,target,builder,worker,null,skill,tier);
    }
    public static boolean place(World world,AetherhavenPlugin plugin,TownRecord town,ApprenticeWorkplace site,Target target,String builder,Vector3d worker,com.hypixel.hytale.math.shape.Box body,int skill,int tier) {
        if(!valid(world,town,site,target)||!(body==null?inReach(worker,target):inReach(worker,body,target))||!site.building().ready(builder,target.cell(),BuildingOrders.duration(skill,tier)))return false;
        var order=site.building().find(target.cell().order());var plot=town.findPlotById(order.plot());
        var type=BlockType.getAssetMap().getAsset(target.cell().block());var hitbox=BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        var footprint=plot.toFootprint();
        if(hitbox==null||!FillerBlockUtil.testFillerBlocks(hitbox.get(target.cell().rotation()),(dx,dy,dz)->{
            int x=target.cell().x()+dx,y=target.cell().y()+dy,z=target.cell().z()+dz;
            return footprint.containsBlock(x,y,z)&&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(x),ChunkUtil.chunkCoordinate(z))&&ChunkSectionBlockUtil.worldChunkIfTicking(world,x,z)!=null;
        }))return false;
        if(target.clearing()) {
            // Somebody may have stepped onto the obstruction during the swing. Never dig out their footing.
            var occupants=SiteBodies.near(world.getEntityStore().getStore(),target.cell().x()+.5,target.cell().y()+.5,target.cell().z()+.5,32);
            if(SiteBodies.blocker(occupants,target.cell().x(),target.cell().y(),target.cell().z(),pieceBox(target))!=null)return false;
        }
        var plan=site.building();if(!plan.beginPlacement(builder,target.cell()))return false;
        try {
            if(target.clearing()) {
                var store=world.getEntityStore().getStore();
                var result=PlotAssemblyService.advanceClearingAtCell(world,plugin,store,store,town,plot,target.job(),new org.joml.Vector3i(target.cell().x(),target.cell().y(),target.cell().z()),null);
                return result.brokeBlock()&&plan.committed(builder,target.cell());
            }
            var result=PlotAssemblyService.advancePlacementAtIndex(world,plugin,world.getEntityStore().getStore(),town,plot,target.job(),target.cell().index(),false,null,true,false);
            return result.progressed()&&plan.committed(builder,target.cell());
        } finally {
            if(plan.endPlacement())AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).updateTown(town);
        }
    }
}
