package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Ray picks the owner's actual preview entities, including posed holograms, without needing a terrain hit. */
final class PlotCreatorSpotRay {
    static final double REACH=8;
    record Visual(PlotCreatorSpotRemoval.Spot spot,Box bounds) {}
    private PlotCreatorSpotRay() {}

    static boolean showing(PlotCreatorDraft draft) {
        return !draft.isEditingBounds()&&(draft.getStep()==PlotCreatorStep.SUBSTEP||draft.getStep()==PlotCreatorStep.REVIEW
            ||draft.isBuildingEditorMode()&&draft.getStep()==PlotCreatorStep.IMPORTANT_SPOTS);
    }
    static PlotCreatorSpotRemoval.Spot find(PlotCreatorSession session,Ref<EntityStore> player,Store<EntityStore> store) {
        var draft=session.getDraft();if(!showing(draft))return null;
        var owner=store.getComponent(player,UUIDComponent.getComponentType());if(owner==null)return null;
        var filter=draft.getStep()==PlotCreatorStep.SUBSTEP?PlotCreatorService.currentSubstep(draft):null;
        if(draft.getStep()==PlotCreatorStep.SUBSTEP&&filter==null)return null;
        var world=session.getWorld();var visuals=new ArrayList<Visual>();
        for(var marker:PlotCreatorSpotMarkerCollector.collect(draft,world,filter)) {
            // Hunter arena guides explain the beacon's layout; they are not individually authored spots.
            if(marker.type()==PlotCreatorSubstepType.SPECIALTY_BLOCK&&!CoreBuildingSpecialties.isSpecialty(marker.workResidentKind()))continue;
            var ref=PlotCreatorSpotMarkerSync.visibleRef(world,owner.getUuid(),marker.key());
            add(visuals,store,ref,new PlotCreatorSpotRemoval.Spot(marker.type(),marker.workResidentKind(),new Vector3i(marker.x(),marker.y(),marker.z())),false);
        }
        for(var preview:PlotCreatorSpotPreviewCollector.collect(draft,world,filter)) {
            var ref=PlotCreatorSpotPreviewSync.visibleRef(world,owner.getUuid(),preview.key());
            // A seated/sleeping hologram can stand at a different cell from the POI that owns it.
            var cell=preview.poiBlockX()==null?new Vector3i(preview.standX(),preview.standY(),preview.standZ())
                :new Vector3i(preview.poiBlockX(),preview.poiBlockY(),preview.poiBlockZ());
            add(visuals,store,ref,new PlotCreatorSpotRemoval.Spot(preview.type(),preview.workResidentKind(),cell),true);
        }
        var look=TargetUtil.getLook(player,store);
        var block=TargetUtil.getTargetBlock(player,REACH,store);
        return pick(look.getPosition(),look.getDirection(),REACH,visuals,block);
    }

    /** Legacy support clicks are exact or directly below a spot, never a radius through neighboring walls. */
    static PlotCreatorSpotRemoval.Spot supportFallback(PlotCreatorDraft draft,World world,Vector3i block) {
        var filter=PlotCreatorService.currentSubstep(draft);if(filter==null)return null;
        var spots=new ArrayList<PlotCreatorSpotRemoval.Spot>();
        for(var marker:PlotCreatorSpotMarkerCollector.collect(draft,world,filter)) {
            if(marker.type()==PlotCreatorSubstepType.SPECIALTY_BLOCK&&!CoreBuildingSpecialties.isSpecialty(marker.workResidentKind()))continue;
            spots.add(new PlotCreatorSpotRemoval.Spot(marker.type(),marker.workResidentKind(),new Vector3i(marker.x(),marker.y(),marker.z())));
        }
        for(var preview:PlotCreatorSpotPreviewCollector.collect(draft,world,filter)) {
            var cell=preview.poiBlockX()==null?new Vector3i(preview.standX(),preview.standY(),preview.standZ())
                :new Vector3i(preview.poiBlockX(),preview.poiBlockY(),preview.poiBlockZ());
            spots.add(new PlotCreatorSpotRemoval.Spot(preview.type(),preview.workResidentKind(),cell));
        }
        return supported(spots,block);
    }
    static PlotCreatorSpotRemoval.Spot supported(List<PlotCreatorSpotRemoval.Spot> spots,Vector3i block) {
        for(var spot:spots)if(spot.cell().equals(block))return spot;
        var above=new Vector3i(block).add(0,1,0);
        for(var spot:spots)if(spot.cell().equals(above))return spot;
        return null;
    }
    private static void add(List<Visual> out,Store<EntityStore> store,Ref<EntityStore> ref,PlotCreatorSpotRemoval.Spot spot,boolean person) {
        if(ref==null||!ref.isValid())return;
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return;
        var bounds=store.getComponent(ref,BoundingBox.getComponentType());
        var model=store.getComponent(ref,ModelComponent.getComponentType());
        Box box=bounds==null?model==null?null:model.getModel().getBoundingBox():bounds.getBoundingBox();
        if(box==null)box=person?new Box(-.4,0,-.4,.4,2,.4):new Box(-.5,-.5,-.5,.5,.5,.5);
        out.add(new Visual(spot,new Box(box).offset(transform.getPosition())));
    }
    static PlotCreatorSpotRemoval.Spot pick(Vector3d origin,Vector3d direction,double reach,List<Visual> visuals) {
        return pick(origin,direction,reach,visuals,null);
    }
    static PlotCreatorSpotRemoval.Spot pick(Vector3d origin,Vector3d direction,double reach,List<Visual> visuals,Vector3i terrain) {
        if(!origin.isFinite()||!direction.isFinite()||direction.lengthSquared()<1e-12||!Double.isFinite(reach)||reach<=0)return null;
        var ray=new Vector3d(direction).normalize();double nearest=reach;PlotCreatorSpotRemoval.Spot found=null;
        // A marker may touch its own specialty block, but a wall must hide the markers behind it.
        if(terrain!=null)nearest=Math.min(nearest,entry(origin,ray,
            new Box(terrain.x,terrain.y,terrain.z,terrain.x+1,terrain.y+1,terrain.z+1),reach)+.02);
        for(var visual:visuals) {
            double distance=entry(origin,ray,visual.bounds(),reach);
            if(distance<=nearest){nearest=distance;found=visual.spot();}
        }
        return found;
    }
    private static double entry(Vector3d origin,Vector3d direction,Box box,double reach) {
        double near=0,far=reach;
        for(int axis=0;axis<3;axis++) {
            double o=origin.get(axis),d=direction.get(axis),low=box.min.get(axis),high=box.max.get(axis);
            if(Math.abs(d)<1e-10){if(o<low||o>high)return Double.POSITIVE_INFINITY;continue;}
            double a=(low-o)/d,b=(high-o)/d;
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
            if(near>far)return Double.POSITIVE_INFINITY;
        }
        return near;
    }
}
