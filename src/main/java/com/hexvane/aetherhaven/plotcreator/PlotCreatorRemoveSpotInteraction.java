package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.TargetUtil;

/** Secondary for both authoring staffs: marker/hologram first, existing terrain action when no marker is aimed at. */
public final class PlotCreatorRemoveSpotInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<PlotCreatorRemoveSpotInteraction> CODEC=BuilderCodec.builder(
        PlotCreatorRemoveSpotInteraction.class,PlotCreatorRemoveSpotInteraction::new,SimpleInstantInteraction.CODEC).build();
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return false;}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown) {
        if(SubpluginInteractionGuard.failIfDisabled(context,AetherhavenPluginIds.PLOT_CREATOR))return;
        var buffer=context.getCommandBuffer();var ref=context.getEntity();
        if(type!=InteractionType.Secondary||buffer==null||ref==null){context.getState().state=InteractionState.Failed;return;}
        var player=buffer.getComponent(ref,PlayerRef.getComponentType());var store=buffer.getStore();var hand=context.getHeldItem();
        boolean editor=BuildingEditorInteractions.isBuildingEditorStaff(hand);
        if(player==null||!(editor?BuildingEditorSessionStarter.requireCreative(player,ref,store)
            :PlotCreatorInteractions.isPlotCreatorStaff(hand)&&PlotCreatorInteractions.hasPlotCreatorPermission(player))) {
            context.getState().state=InteractionState.Failed;return;
        }
        var session=PlotCreatorSessions.get(player.getUuid());
        if(session!=null&&session.getWorld()==store.getExternalData().getWorld()&&(!editor||session.getDraft().isBuildingEditorMode())) {
            var spot=PlotCreatorSpotRay.find(session,ref,store);
            if(spot!=null) {
                if(PlotCreatorSpotRemoval.remove(session.getDraft(),spot)) {
                    if(spot.type()==PlotCreatorSubstepType.FESTIVAL_RACE_LANE)session.clearPendingRaceLaneStartLocal();
                    player.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spotRemoved"));
                    PlotCreatorInteractions.refreshHud(player,ref,store,session);
                }
                context.getState().state=InteractionState.Finished;return;
            }
        }
        // Server ray preserves the old support-block removal and editor mine-node action, with an eight-block limit.
        var block=TargetUtil.getTargetBlock(ref,PlotCreatorSpotRay.REACH,store);
        if(block!=null&&editor&&session==null)PlacedMineNodeEditing.click(player,ref,store,block,type);
        if(block!=null&&session!=null&&session.getWorld()==store.getExternalData().getWorld()
            &&(!editor||session.getDraft().isBuildingEditorMode())&&!session.getDraft().isEditingBounds()) {
            var draft=session.getDraft();
            if(draft.getStep()==PlotCreatorStep.SUBSTEP) {
                var supported=PlotCreatorSpotRay.supportFallback(draft,session.getWorld(),block);
                if(supported!=null&&PlotCreatorSpotRemoval.remove(draft,supported)) {
                    if(supported.type()==PlotCreatorSubstepType.FESTIVAL_RACE_LANE)session.clearPendingRaceLaneStartLocal();
                    player.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.spotRemoved"));
                    PlotCreatorInteractions.refreshHud(player,ref,store,session);
                }
            }else if(!editor&&draft.getStep()==PlotCreatorStep.WALL_PIECES
                &&PlotCreatorWallPieceAuthoring.removeCurrentConnectionNear(draft,block)) {
                player.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.hint.wallConnectionRemoved"));
                PlotCreatorInteractions.refreshHud(player,ref,store,session);
            }
        }
        context.getState().state=InteractionState.Finished;
    }
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown) {}
}
