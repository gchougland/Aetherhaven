package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.icon.PlotCreatorIconExporter;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Console entry point to the same native icon exporter used by the plot creator. */
final class AetherhavenBuildingIconsTestCommand extends AbstractAsyncCommand {
    AetherhavenBuildingIconsTestCommand(){super("buildingicons","Generate all new Core building icons with the plot creator");setPermissionGroups("hytale:WorldEditor");}
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx){
        var plugin=AetherhavenPlugin.get();var failed=new ArrayList<String>();int generated=0;
        for(String kind:List.of("alchemist","doctor","druid","monster_hunter","warehouse","crafter","leatherworker","simple_workplace","halloween")){
            String id="plot_"+kind;
            try{
                var def=plugin.getConstructionCatalog().get(id);
                if(def==null)throw new IllegalStateException("Missing building "+id);
                var path=PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
                if(path==null)throw new IllegalStateException("Missing prefab "+def.getPrefabPath());
                if(!PlotCreatorIconExporter.tryExportIcon(PrefabStore.get().getPrefab(path),id,plugin.getDataDirectory(),def.getFrontFacing()))
                    throw new IllegalStateException("Native icon render failed");
                generated++;
                report(ctx,id+": "+CustomBuildingsPaths.iconFile(plugin.getDataDirectory(),id));
            }catch(Exception error){failed.add(id+": "+error.getMessage());}
        }
        for(String error:failed)report(ctx,error);
        report(ctx,"Core building icons: "+(failed.isEmpty()?"PASSED":"FAILED")+". "+generated+" generated with the plot creator, "+failed.size()+" failed.");
        return failed.isEmpty()?CompletableFuture.completedFuture(null):CompletableFuture.failedFuture(new IllegalStateException(String.join("; ",failed)));
    }
    private static void report(CommandContext ctx,String text){ctx.sendMessage(Message.raw(text));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);}
}
