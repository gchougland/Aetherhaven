package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.*;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3i;
import java.util.*;

/** Explicit local test conversion. No resource awards, teleports, cooldown skips or need refills. */
final class AetherhavenMiningTestCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> action=withRequiredArg("action","status, editor, open, probe or adopt",ArgTypes.STRING);
    private final OptionalArg<Integer> x=withOptionalArg("x","Node X",ArgTypes.INTEGER);
    private final OptionalArg<Integer> y=withOptionalArg("y","Node Y",ArgTypes.INTEGER);
    private final OptionalArg<Integer> z=withOptionalArg("z","Node Z",ArgTypes.INTEGER);
    private final DebugTownTargetArgs target;
    AetherhavenMiningTestCommand() {
        super("mining","Inspect physical mining or explicitly convert one test cell"); setPermissionGroups("hytale:WorldEditor");
        target=DebugTownTargetArgs.registerOn(this);
    }
    @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> player,PlayerRef pr,World world) {
        var plugin=AetherhavenPlugin.get(); if(plugin==null) return;
        if("assets".equals(ctx.get(action))){
            var catalog=plugin.getProductionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);
            for(int i=0;i<catalog.catalogSize();i++)if(!MiningNodeBlocks.supported(catalog.itemAtCursor(i)))throw new IllegalStateException("Missing native mining block for "+catalog.itemAtCursor(i));
            report(ctx,"All "+catalog.catalogSize()+" core mining outputs resolve to regular native blocks.");return;
        }
        if("editor".equals(ctx.get(action))) {
            var session=com.hexvane.aetherhaven.plotcreator.PlotCreatorSessions.get(pr.getUuid());
            if(session==null || session.getWorld()!=world) { report(ctx,"No building editor session."); return; }
            var draft=session.getDraft();
            report(ctx,"Editor mine="+draft.getConstructionId()+" sign="+draft.getPlotAnchor()+" nodes="+draft.getMiningNodes().size());
            for(var node:draft.getMiningNodes()) {
                var p=com.hexvane.aetherhaven.plotcreator.PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{node.x(),node.y(),node.z()});
                var sides=new ArrayList<String>();
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    var side=new Vector3i(p).add(d[0],0,d[1]);
                    if(MiningNodeBlocks.loaded(world,side) && com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,side.x,side.y,side.z))
                        sides.add(side.toString());
                }
                report(ctx,"Editor node="+p+" expected="+node.expectedBlockId()+" actual="+MiningNodeBlocks.current(world,p)+" clear sides="+sides);
            }
            return;
        }
        var resolved=target.resolve(ctx,world,store,player,pr,true);
        if(!resolved.isOk()) { ctx.sendMessage(resolved.error()); return; }
        var town=resolved.townOrThrow(); var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var plot=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);
        if(plot==null) { report(ctx,"No completed mine in this town."); return; }
        if ("open".equals(ctx.get(action))) {
            var at=com.hexvane.aetherhaven.warehouse.ProductionStorageLocator.find(world,plot,System.currentTimeMillis());
            var entity=store.getComponent(player,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (at==null || entity==null) { report(ctx,"No loaded mine storage found. Try again after moving nearer."); return; }
            entity.getPageManager().openCustomPage(player,store,new com.hexvane.aetherhaven.ui.ProductionStoragePage(
                pr,town.getTownId(),plot.getPlotId(),at.x(),at.y(),at.z()));
            return;
        }
        if("probe".equals(ctx.get(action)) || "adopt".equals(ctx.get(action))) {
            Integer nx=ctx.get(x),ny=ctx.get(y),nz=ctx.get(z);
            if(nx==null || ny==null || nz==null) { report(ctx,"Supply --x, --y and --z for the single test node."); return; }
            var p=new Vector3i(nx,ny,nz); String original=MiningNodeBlocks.current(world,p);
            if(original==null || !plot.hasStoredPrefabWorldAnchor()) { report(ctx,"Cell is not loaded or mine has no placement anchor."); return; }
            var delta=p.sub(plot.getStoredPrefabWorldAnchor(),new Vector3i());
            var local=PrefabLocalOffset.inverseRotateWorldDelta(plot.resolvePrefabYaw(),delta.x,delta.y,delta.z);
            var layout=new BuildingProductionLayout("4.0",BuildingProductionLayout.MINING_NODES,
                List.of(new BuildingProductionLayout.Node(local.x,local.y,local.z,original)));
            var error=MiningLayoutAdoption.issue(world,plot,layout);
            report(ctx,"Mining cell "+p+" block="+original+" local="+local+" preflight="+(error==null?"ready":error));
            if("adopt".equals(ctx.get(action)) && error==null && MiningLayoutAdoption.adopt(world,plot,layout)) {
                manager.updateTown(town); report(ctx,"Converted this test mine. Existing stock and upgrades were preserved. No resources awarded.");
            }
            return;
        }
        if(!"status".equals(ctx.get(action))) { report(ctx,"Use status, editor, open, probe or adopt."); return; }
        var state=town.getOrCreatePlotProduction(plot.getPlotId()); var mining=state.mining();
        report(ctx,"Mining plot="+plot.getPlotId()+" rules="+plot.getProductionLayout()+" slots="+WorkplaceProductionUpgrades.slotCount(state));
        for(int n=0;n<plot.getProductionLayout().nodes().size();n++) {
            var p=MiningNodeBlocks.point(plot,n);
            report(ctx,"Node "+n+" world="+p+" actual="+MiningNodeBlocks.current(world,p)+" owned="+mining.ownedBlock(n)
                +" intact="+MiningNodeBlocks.intact(world,plot,mining,n));
        }
        for(int slot=0;slot<WorkplaceProductionUpgrades.slotCount(state);slot++) {
            var c=mining.cycle(slot); report(ctx,"Slot "+slot+" item="+c.item()+" respawn="+c.remaining()+" work="+c.progress()
                +" stored="+(c.item()==null?0:state.getAmount(c.item())));
        }
        store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(),NPCEntity.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var b=chunk.getComponent(i,TownVillagerBinding.getComponentType());
                if(!town.getTownId().equals(b.getTownId()) || !TownVillagerBinding.KIND_MINER.equals(b.getKind())) continue;
                var ref=chunk.getReferenceTo(i); var pos=store.getComponent(ref,TransformComponent.getComponentType());
                var needs=store.getComponent(ref,VillagerNeeds.getComponentType());
                report(ctx,"Miner job="+b.getJobPlotId()+" pos="+(pos==null?null:String.format(Locale.ROOT,"%.3f,%.3f,%.3f",pos.getPosition().x,pos.getPosition().y,pos.getPosition().z))
                    +" needs="+(needs==null?null:needs.getHunger()+"/"+needs.getEnergy())
                    +" state="+com.hexvane.aetherhaven.npc.NpcSupportUtil.stateName(store,ref)
                    +" physical="+com.hexvane.aetherhaven.autonomy.MiningWorkService.active(ref)
                    +" "+com.hexvane.aetherhaven.autonomy.MiningWorkService.diagnostic(ref));
            }
        });
    }
    private static void report(CommandContext ctx,String text) {
        ctx.sendMessage(Message.raw(text)); com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
    }
}
