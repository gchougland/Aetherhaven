package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.production.NativeHerdService;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;
import org.joml.Vector3i;
import java.util.List;

final class PlotCreatorBarnPens {
    private PlotCreatorBarnPens() {}
    static boolean add(PlotCreatorSession session,Vector3i target,PlayerRef player,Ref<EntityStore> ref,Store<EntityStore> store) {
        var draft=session.getDraft();var stand=new Vector3i(target).add(0,1,0);
        if(!draft.isInsideBounds(stand)||!com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(session.getWorld(),stand.x,stand.y,stand.z)) {
            player.sendMessage(Message.raw("Choose a clear pen floor inside the building bounds."));return true;
        }
        var pens=draft.getPois().stream().filter(p->p.getTags().contains(NativeHerdService.PEN_TAG)).toList();
        int[] local=PlotCreatorLocalCoords.toLocal(draft,stand);
        if(pens.size()>=5){player.sendMessage(Message.raw("A barn has at most five pen markers."));return true;}
        if(pens.stream().anyMatch(p->p.getLocalX()==local[0]&&p.getLocalY()==local[1]&&p.getLocalZ()==local[2]))return true;
        var pen=new PlotCreatorPoiDraft();pen.setLocal(local[0],local[1],local[2]);pen.setTags(List.of(NativeHerdService.PEN_TAG));
        var head=store.getComponent(ref,HeadRotation.getComponentType());
        pen.setInteractionTargetYawDegrees(head==null?0f:(float)Math.toDegrees(PlotCreatorSpotPlacement.prefabYawFromWorld(draft,head.getRotation().yaw())));
        draft.getPois().add(pen);player.sendMessage(Message.raw("Animal pen "+(pens.size()+1)+" marked. The animal will face the direction you are looking."));return true;
    }
}
