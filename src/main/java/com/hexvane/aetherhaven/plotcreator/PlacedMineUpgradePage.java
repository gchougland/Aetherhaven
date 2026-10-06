package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.*;

/** Applies only a reviewed node snapshot. Never pastes a prefab or rewrites a catalog definition. */
public final class PlacedMineUpgradePage extends AetherhavenInteractiveCustomUIPage<PlacedMineUpgradePage.Data> {
    private final UUID townId,plotId;
    private final BuildingProductionLayout layout;
    private final Vector3i anchor,openedAt;
    private final com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation yaw;
    private boolean appended;
    private int selected=-1;
    private String hint="Select a node on the plan to see its exact location.";

    PlacedMineUpgradePage(PlayerRef player,TownRecord town,PlotInstance plot,BuildingProductionLayout layout,Vector3i openedAt) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);
        townId=town.getTownId();plotId=plot.getPlotId();this.layout=layout;
        anchor=plot.getStoredPrefabWorldAnchor();yaw=plot.resolvePrefabYaw();this.openedAt=new Vector3i(openedAt);
    }
    /** Invoked only when the staff is idle, aimed at a completed legacy mine. */
    static boolean tryOpen(PlayerRef player,Ref<EntityStore> ref,Store<EntityStore> store,Vector3i target) {
        var plugin=AetherhavenPlugin.get();if(plugin==null||target==null)return false;
        var world=store.getExternalData().getWorld();
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownContainingBlock(world.getName(),target.x,target.z);
        if(town==null)return false;
        var plot=town.findCompletePlotContaining(target.x,target.y,target.z);
        if(plot==null||!plot.getProductionLayout().legacy()||!plot.hasStoredPrefabWorldAnchor()
            ||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT))return false;
        var entity=store.getComponent(ref,Player.getComponentType());
        if(entity==null||!allowed(town,player.getUuid(),entity.getGameMode()))return false;
        var definition=plugin.getConstructionCatalog().get(plot.getConstructionId());
        entity.getPageManager().openCustomPage(ref,store,new PlacedMineUpgradePage(player,town,plot,
            definition==null?BuildingProductionLayout.LEGACY:definition.getProductionLayout(),target));
        return true;
    }
    static boolean allowed(TownRecord town,UUID player,GameMode mode) {
        return mode==GameMode.Creative&&town.hasMemberOrOwner(player)&&town.playerCanManageConstructions(player);
    }
    private PlotInstance resolve(Ref<EntityStore> ref,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(townId);
        var player=store.getComponent(ref,Player.getComponentType());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(town==null||player==null||!allowed(town,playerRef.getUuid(),player.getGameMode())||transform==null
            ||transform.getPosition().distanceSquared(openedAt.x+.5,openedAt.y+.5,openedAt.z+.5)>64)return null;
        var plot=town.findPlotById(plotId);
        return plot!=null&&plot.getState()==PlotInstanceState.COMPLETE&&plot.hasStoredPrefabWorldAnchor()
            &&anchor.equals(plot.getStoredPrefabWorldAnchor())&&yaw==plot.resolvePrefabYaw()?plot:null;
    }
    private Vector3i point(BuildingProductionLayout.Node node) {
        return new Vector3i(anchor).add(PrefabLocalOffset.rotate(yaw,node.x(),node.y(),node.z()));
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        var plot=resolve(ref,store);if(plot==null){close();return;}
        if(!appended) {
            c.append("Aetherhaven/PlacedMineUpgradePage.ui");appended=true;
            for(String action:List.of("Catalog","Preview","Apply","Previous","Next","Edit","Discard"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
            var cells=layout.validationError()==null?layout.nodes().stream().map(this::point).toList():List.<Vector3i>of();
            if(!cells.isEmpty()&&layout.validationError()==null) {
                int minX=cells.stream().mapToInt(p->p.x).min().orElse(0),minZ=cells.stream().mapToInt(p->p.z).min().orElse(0);
                int spanX=cells.stream().mapToInt(p->p.x).max().orElse(minX)-minX;
                int spanZ=cells.stream().mapToInt(p->p.z).max().orElse(minZ)-minZ;
                double scale=java.lang.Math.min(590.0/java.lang.Math.max(1,spanX),155.0/java.lang.Math.max(1,spanZ));
                for(int i=0;i<cells.size();i++) {
                    var p=cells.get(i);int x=24+(int)java.lang.Math.round((p.x-minX)*scale),z=22+(int)java.lang.Math.round((p.z-minZ)*scale);
                    c.append("#Map","Aetherhaven/MiningReviewNode.ui");String node="#Map["+i+"]";
                    var position=new Anchor();position.setLeft(Value.of(x));position.setTop(Value.of(z));
                    position.setWidth(Value.of(30));position.setHeight(Value.of(30));
                    c.setObject(node+".Anchor",position);c.set(node+" #Number.Text",Integer.toString(i+1));
                    e.addEventBinding(CustomUIEventBindingType.Activating,node,EventData.of("Action","Node").append("Node",Integer.toString(i)),false);
                }
            }
        }
        String issue=layout.legacy()?"This design has no mining nodes yet. Choose cells in this mine to create a local layout.":MiningLayoutAdoption.issue(store.getExternalData().getWorld(),plot,layout);
        c.set("#NodeCount.Text",layout.nodes().size()+" NODE LOCATIONS");
        c.set("#Status.Text",issue==null?"Ready to adopt mining nodes. Your stored resources, selected outputs and upgrades are kept.":issue);
        c.set("#Details.Text",hint);
        c.set("#Previous.Disabled",layout.nodes().isEmpty()||layout.validationError()!=null);
        c.set("#Next.Disabled",layout.nodes().isEmpty()||layout.validationError()!=null);
        if(layout.validationError()==null)for(int i=0;i<layout.nodes().size();i++)c.set("#Map["+i+"] #Selected.Visible",i==selected);
        c.set("#Apply.Disabled",issue!=null);
        c.set("#Preview.Disabled",layout.legacy()||layout.validationError()!=null);
        c.set("#Discard.Visible",PlacedMineNodeEditing.active(playerRef.getUuid()));
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plot=resolve(ref,store);if(plot==null){close();return;}
        if("Catalog".equals(data.action)){PlacedMineNodeEditing.cancel(playerRef.getUuid());BuildingEditorInteractions.openPicker(playerRef,ref,store);return;}
        if("Discard".equals(data.action)){PlacedMineNodeEditing.cancel(playerRef.getUuid());close();return;}
        if("Edit".equals(data.action)) {
            if(PlacedMineNodeEditing.begin(playerRef,ref,store,townId,plot,layout)){close();return;}
            hint="Finish the current building edit before choosing cells here.";
        }
        if("Node".equals(data.action)||"Previous".equals(data.action)||"Next".equals(data.action)) {
            try {int i="Node".equals(data.action)?Integer.parseInt(data.node):layout.nodes().isEmpty()?-1:
                java.lang.Math.floorMod(selected+("Previous".equals(data.action)?-1:1),layout.nodes().size());
                if(layout.validationError()==null&&i>=0&&i<layout.nodes().size()) {
                    selected=i;var n=layout.nodes().get(i);var p=point(n);
                    hint="Node "+(i+1)+": X "+p.x+", Y "+p.y+", Z "+p.z+". Original block: "+n.expectedBlockId().replace('_',' ')+".";
                }
            }catch(NumberFormatException ignored){}
        } else if("Preview".equals(data.action)&&!layout.legacy()&&layout.validationError()==null) {
            for(var n:layout.nodes()) {
                var p=point(n);var m=new Matrix4d().translation(p.x+.5,p.y+.5,p.z+.5).scale(.51);
                playerRef.getPacketHandler().write(new com.hypixel.hytale.protocol.packets.player.DisplayDebug(
                    com.hypixel.hytale.protocol.DebugShape.Cube,com.hypixel.hytale.math.matrix.Matrix4dUtil.asFloatData(m),
                    new Vector3f(.9f,.65f,.25f),20f,(byte)com.hypixel.hytale.protocol.DebugFlags.Fade,null,.35f));
            }
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("Node locations are marked for 20 seconds. Use the Building Editor Staff on the mine again to return to the review."));
            close();return;
        } else if("Apply".equals(data.action)) {
            var world=store.getExternalData().getWorld();
            if(MiningLayoutAdoption.adopt(world,plot,layout)) {
                var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get());
                manager.updateTown(manager.getTown(townId));
                PlacedMineNodeEditing.cancel(playerRef.getUuid());
                playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("This mine now uses mining nodes. Stored resources and upgrades were kept. The building was not replaced."));
                close();return;
            }
            hint="The mine could not be updated. Check the requirements above.";
        }
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Node",Codec.STRING),(d,v)->d.node=v,d->d.node).add().build();
        String action,node;
    }
}
