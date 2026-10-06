package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3i;

/** Local retrofit editing uses the existing staff and owner-only ore preview entities. */
public final class PlacedMineNodeEditing {
    private static final String HUD="AetherhavenMineNodeEditor";
    private static final Map<UUID,Session> SESSIONS=new ConcurrentHashMap<>();
    private static final long EXPIRY=30L*60*1_000_000_000;
    private PlacedMineNodeEditing() {}
    private static final class Session {
        final World world;final UUID townId,owner;final PlacedMineNodeDraft draft;
        final PlayerRef player;
        long lastInput=System.nanoTime(),nextRefresh;
        String hint="Choose rock, ore or soil inside this mine. Nothing changes until you apply the reviewed layout.";
        Session(World world,UUID town,PlayerRef player,UUID owner,PlacedMineNodeDraft draft) {
            this.world=world;townId=town;this.player=player;this.owner=owner;this.draft=draft;
        }
    }
    static boolean active(UUID player) {return SESSIONS.containsKey(player);}
    static boolean begin(PlayerRef pr,Ref<EntityStore> ref,Store<EntityStore> store,UUID town,PlotInstance plot,BuildingProductionLayout seed) {
        if(PlotCreatorSessions.get(pr.getUuid())!=null)return false;
        var world=store.getExternalData().getWorld();
        var session=new Session(world,town,pr,PlotCreatorSpotMarkerSync.requireOwnerEntityUuid(store,ref),new PlacedMineNodeDraft(plot,seed));
        if(resolve(session,ref,store)==null)return false;
        cancel(pr.getUuid());SESSIONS.put(pr.getUuid(),session);return true;
    }
    private static PlotInstance resolve(Session s,Ref<EntityStore> ref,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null||s.world!=store.getExternalData().getWorld())return null;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(s.world,plugin).getTown(s.townId);
        var player=store.getComponent(ref,Player.getComponentType());
        if(town==null||player==null||!PlacedMineUpgradePage.allowed(town,s.player.getUuid(),player.getGameMode()))return null;
        var plot=town.findPlotById(s.draft.plotId);
        if(!s.draft.matches(plot)||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT))return null;
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return null;
        var p=transform.getPosition();var box=plot.toFootprint();
        double dx=Math.max(box.getMinX()-p.x,Math.max(0,p.x-box.getMaxX()-1));
        double dy=Math.max(box.getMinY()-p.y,Math.max(0,p.y-box.getMaxY()-1));
        double dz=Math.max(box.getMinZ()-p.z,Math.max(0,p.z-box.getMaxZ()-1));
        return dx*dx+dy*dy+dz*dz<=64?plot:null;
    }
    static boolean review(PlayerRef pr,Ref<EntityStore> ref,Store<EntityStore> store) {
        var s=SESSIONS.get(pr.getUuid());if(s==null)return false;
        var plot=resolve(s,ref,store);if(plot==null){cancel(s);return true;}
        s.lastInput=System.nanoTime();
        var p=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(s.world,AetherhavenPlugin.get()).getTown(s.townId);
        store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,
            new PlacedMineUpgradePage(pr,town,plot,s.draft.snapshot(),new Vector3i((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z))));
        return true;
    }
    static boolean click(PlayerRef pr,Ref<EntityStore> ref,Store<EntityStore> store,Vector3i point,InteractionType type) {
        var s=SESSIONS.get(pr.getUuid());if(s==null)return false;
        var plot=resolve(s,ref,store);if(plot==null){cancel(s);return true;}
        var position=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        if(point==null||position.distanceSquared(point.x+.5,point.y+.5,point.z+.5)>64)return true;
        s.lastInput=System.nanoTime();s.nextRefresh=0;
        if(type==InteractionType.Secondary) {
            s.hint=s.draft.remove(point)?"Mining cell removed from the plan.":"Aim at a selected cell to remove it.";return true;
        }
        if(type!=InteractionType.Primary)return true;
        if(!plot.containsWorldBlock(point.x,point.y,point.z)||!MiningNodeBlocks.loaded(s.world,point)) {
            s.hint="Choose a loaded cell inside this mine.";return true;
        }
        var block=ChunkSectionBlockUtil.blockType(s.world,point.x,point.y,point.z);
        if(!MiningNodeBlocks.safeOriginal(block)||ChunkSectionBlockUtil.filler(s.world,point.x,point.y,point.z)!=0
            ||ChunkSectionBlockUtil.blockEntityHolderAt(s.world,point.x,point.y,point.z)!=null) {
            s.hint="Select plain rock, ore or soil. Furniture and storage cannot become mining cells.";return true;
        }
        String issue=s.draft.add(plot,point,block.getId());
        s.hint=issue==null?"Mining cell selected. Leave a clear standing space beside it.":issue;return true;
    }
    /** Called from the existing editor preview system, on its world thread. */
    static boolean tick(PlayerRef pr,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        var player=store.getComponent(ref,Player.getComponentType());var s=SESSIONS.get(pr.getUuid());
        if(s==null){if(player.getHudManager().getCustomHud(HUD)!=null)player.getHudManager().removeCustomHud(pr,HUD);return false;}
        if(resolve(s,ref,store)==null||PlotCreatorSessions.get(pr.getUuid())!=null||System.nanoTime()-s.lastInput>EXPIRY) {
            cancel(s);return true;
        }
        long now=System.nanoTime();if(now<s.nextRefresh)return true;s.nextRefresh=now+500_000_000L;
        boolean show=BuildingEditorInteractions.isBuildingEditorStaff(InventoryComponent.getItemInHand(buffer,ref))&&player.getPageManager().getCustomPage()==null;
        var desired=new ArrayList<PlotCreatorSpotMarkerCollector.DesiredSpotMarker>();
        if(show)for(var n:s.draft.snapshot().nodes()) {
            var p=s.draft.point(n);if(!MiningNodeBlocks.loaded(s.world,p))continue;
            desired.add(new PlotCreatorSpotMarkerCollector.DesiredSpotMarker(p.x,p.y,p.z,PlotCreatorSubstepType.MINING_NODE,null,"Mining node","",null));
        }
        PlotCreatorSpotMarkerSync.sync(s.world,s.owner,desired,buffer);
        if(!show) {if(player.getHudManager().getCustomHud(HUD)!=null)player.getHudManager().removeCustomHud(pr,HUD);}
        else {
            var hud=player.getHudManager().getCustomHud(HUD);
            if(!(hud instanceof EditorHud)){hud=new EditorHud(pr);player.getHudManager().addCustomHud(pr,hud);}
            ((EditorHud)hud).refresh(s.draft.snapshot().nodes().size(),s.hint);
        }
        return true;
    }
    /** Disconnects, world changes and cancellation discard only transient selection and preview entities. */
    public static void cancel(UUID player) {
        var s=SESSIONS.remove(player);if(s==null)return;
        cleanup(s);
    }
    private static void cancel(Session s) {
        if(SESSIONS.remove(s.player.getUuid(),s))cleanup(s);
    }
    private static void cleanup(Session s) {
        s.world.execute(()->{
            PlotCreatorSpotMarkerSync.clearAll(s.world,s.owner,null);
            var ref=s.player.getReference();if(ref!=null&&ref.isValid()&&ref.getStore().getExternalData().getWorld()==s.world) {
                var entity=ref.getStore().getComponent(ref,Player.getComponentType());
                if(entity!=null&&entity.getHudManager().getCustomHud(HUD)!=null)entity.getHudManager().removeCustomHud(s.player,HUD);
            }
        });
    }
    private static final class EditorHud extends CustomUIHud {
        final PlayerRef viewer;
        int renderedCount=-1;String renderedHint;
        EditorHud(PlayerRef player){super(player,HUD,0);viewer=player;}
        @Override protected void build(UICommandBuilder c){c.append("Aetherhaven/PlacedMineEditorHud.ui");}
        void refresh(int count,String hint) {
            if(renderedCount==count&&Objects.equals(renderedHint,hint))return;
            renderedCount=count;renderedHint=hint;
            var c=new UICommandBuilder();c.set("#Count.Text",count+" mining cells selected");c.set("#Hint.Text",hint);c.clear("#Controls");
            var slots=new com.hexvane.aetherhaven.ui.ToolKeybindSlot[]{com.hexvane.aetherhaven.ui.ToolKeybindSlot.PRIMARY,com.hexvane.aetherhaven.ui.ToolKeybindSlot.SECONDARY,com.hexvane.aetherhaven.ui.ToolKeybindSlot.USE,com.hexvane.aetherhaven.ui.ToolKeybindSlot.ABILITY3};
            String[] labels={"Select mining cell","Remove mining cell","Review and apply","Discard selection"};
            for(int i=0;i<slots.length;i++)com.hexvane.aetherhaven.ui.ToolHudHotkeyRows.appendRow(c,"#Controls",i,slots[i],Message.raw(labels[i]),viewer);
            update(false,c);
        }
    }
}
