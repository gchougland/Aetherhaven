package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.production.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.UUID;
import org.joml.Vector3i;

/** Temporary Building Staff ground selection. Choosing a point opens a review, never plants. */
final class TimberGroveSelection {
    record Selection(World world, UUID town, UUID plot, TimberHarvestPlan.Position anchor,
            com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation yaw,
            int slot, long revision, String output, long expires) {}
    private TimberGroveSelection() {}
    static void begin(Ref<EntityStore> ref, Store<EntityStore> store, WorkplaceToolState state,
            TimberGrovePage.Context c, int slot) {
        state.choose(WorkplaceToolState.Mode.BUILD, ref, store);
        var p=c.plot().getStoredPrefabWorldAnchor();
        state.grove=new Selection(store.getExternalData().getWorld(),c.town().getTownId(),c.plot().getPlotId(),
            new TimberHarvestPlan.Position(p.x,p.y,p.z),c.plot().resolvePrefabYaw(),slot,c.production().timber().layoutRevision(),
            c.entry().itemAtCursor(c.production().getSlotCursor(slot)),System.nanoTime()+30L*60*1_000_000_000);
        state.hint="Choose soil for tree "+(slot+1)+". Its whole canopy must fit inside town and stay clear of other trees.";
    }
    static void expire(Ref<EntityStore> ref, Store<EntityStore> store, WorkplaceToolState state) {
        var s=state.grove;if(s==null)return;
        var c=TimberGrovePage.resolve(ref,store,s.town(),s.plot());
        boolean valid=s.world()==store.getExternalData().getWorld()&&System.nanoTime()<s.expires()&&c!=null;
        if(valid) {
            var a=c.plot().getStoredPrefabWorldAnchor();
            valid=a.x==s.anchor().x()&&a.y==s.anchor().y()&&a.z==s.anchor().z()&&c.plot().resolvePrefabYaw()==s.yaw()
                &&s.slot()<WorkplaceProductionUpgrades.slotCount(c.production())
                &&s.revision()==c.production().timber().layoutRevision()
                &&s.output().equals(c.entry().itemAtCursor(c.production().getSlotCursor(s.slot())));
        }
        if(!valid){state.clearSelection();state.hint="Grove selection ended. Open the lumbermill's grove plan to choose again.";}
    }
    static boolean handle(InteractionType type, InteractionContext context, WorkplaceToolState state) {
        var buffer=context.getCommandBuffer();var ref=context.getEntity();var store=buffer.getStore();
        expire(ref,store,state);var s=state.grove;if(s==null)return true;
        if(type==InteractionType.Pick||type==InteractionType.Ability1){state.clearSelection();return false;}
        if(type==InteractionType.Secondary){state.clearSelection();state.hint="Tree selection cancelled. The saved grove plan is unchanged.";return true;}
        if(type!=InteractionType.Primary&&type!=InteractionType.Use)return true;
        var player=buffer.getComponent(ref,Player.getComponentType());var pr=buffer.getComponent(ref,PlayerRef.getComponentType());
        if(player==null||pr==null)return true;
        if(type==InteractionType.Use) {
            s.world().execute(()->{if(ref.isValid())player.getPageManager().openCustomPage(ref,store,new TimberGrovePage(pr,s.town(),s.plot(),s.slot(),null,s.revision()));});
            return true;
        }
        var sync=context.getClientState();var block=sync!=null?sync.blockPosition:context.getTargetBlock();
        Vector3i target=block==null?TargetUtil.getTargetBlock(ref,6,store):new Vector3i(block.x,block.y,block.z);
        var transform=buffer.getComponent(ref,TransformComponent.getComponentType());
        if(target==null||transform==null||target.y<0||target.y>=319||transform.getPosition().distanceSquared(target.x+.5,target.y+.5,target.z+.5)>64) {
            state.hint="Choose nearby soil within eight blocks.";return true;
        }
        // Block reads and page changes run after the current entity command buffer.
        var root=new TimberHarvestPlan.Position(target.x,target.y+1,target.z);
        s.world().execute(()->{
            if(!ref.isValid()||state.grove!=s)return;
            expire(ref,store,state);if(state.grove!=s)return;
            var c=TimberGrovePage.resolve(ref,store,s.town(),s.plot());if(c==null)return;
            var capture=NativeTimberGrowth.capture(s.output(),root,0,TimberGrovePage.bounds(c.town()));
            if(!capture.ready()){state.hint=capture.issue();state.nextPreviewNanos=0;return;}
            String conflict=TimberGrovePage.otherGroveIssue(c,capture.receipt());
            if(conflict!=null){state.hint=conflict;state.nextPreviewNanos=0;return;}
            var prepared=NativeTimberPlanting.prepare(s.world(),c.production(),s.slot(),capture.receipt(),TimberGrovePage.bounds(c.town()));
            if(!prepared.ready()){state.hint=prepared.issue();state.nextPreviewNanos=0;return;}
            player.getPageManager().openCustomPage(ref,store,new TimberGrovePage(pr,s.town(),s.plot(),s.slot(),capture.receipt(),s.revision()));
        });
        return true;
    }
}
