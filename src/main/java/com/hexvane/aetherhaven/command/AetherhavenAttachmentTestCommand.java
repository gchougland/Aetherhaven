package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

/** Read actual assembled resident models, including runtime cosmetics and add-on appearances. */
final class AetherhavenAttachmentTestCommand extends AbstractPlayerCommand {
    AetherhavenAttachmentTestCommand() {
        super("attachments", "Report loaded resident attachment paths for client warning diagnosis");
        setPermissionGroups("hytale:WorldEditor");
    }
    @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> player,PlayerRef pr,World world) {
        var reports=new TreeSet<String>();
        store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(),NPCEntity.getComponentType(),ModelComponent.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var model=chunk.getComponent(i,ModelComponent.getComponentType()).getModel();
                if(model==null)continue;
                var attachments=new ArrayList<String>();
                if(model.getAttachments()!=null)for(var a:model.getAttachments()) {
                    if(a!=null&&a.getModel()!=null)attachments.add(a.getModel());
                }
                reports.add("Resident attachments role="+chunk.getComponent(i,NPCEntity.getComponentType()).getRoleName()
                    +" model="+model.getModelAssetId()+" mesh="+model.getModel()+" attachments="+attachments);
            }
        });
        for(var report:reports)com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(report);
        ctx.sendMessage(Message.raw("Logged "+reports.size()+" distinct loaded resident appearances. No models were changed."));
    }
}
