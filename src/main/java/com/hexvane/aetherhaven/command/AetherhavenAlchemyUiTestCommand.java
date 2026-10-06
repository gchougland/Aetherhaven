package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.alchemy.AlchemyCauldronAccess;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/** Opens the ordinary page at the nearest reachable, loaded, authorized laboratory cauldron. */
final class AetherhavenAlchemyUiTestCommand extends AbstractPlayerCommand {
    AetherhavenAlchemyUiTestCommand(){super("alchemyui","Open the nearest valid laboratory cauldron using normal access checks");setPermissionGroups("hytale:WorldEditor");}
    @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world) {
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return;
        var position=transform.getPosition();int x=(int)Math.floor(position.x),y=(int)Math.floor(position.y),z=(int)Math.floor(position.z);
        Vector3i nearest=null;double closest=64;
        for(int dy=-8;dy<=8;dy++)for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++) {
            var pos=new Vector3i(x+dx,y+dy,z+dz);double distance=position.distanceSquared(pos.x+.5,pos.y+.5,pos.z+.5);
            if(distance>closest)continue;
            var block=ChunkSectionBlockUtil.blockType(world,pos.x,pos.y,pos.z);
            if(block==null||!"Alchemy_Cauldron_Big".equals(block.getId()))continue;
            var target=AlchemyCauldronAccess.find(world,pos);
            if(!AlchemyCauldronAccess.allowed(target,player.getUuid())||!WorkplaceBenchAccess.inReach(ref,store,pos))continue;
            nearest=target.position();closest=distance;
        }
        if(nearest==null){ctx.sendMessage(Message.raw("Stand within eight blocks of your completed Alchemist's laboratory cauldron."));return;}
        AlchemyCauldronAccess.open(ref,store,player,nearest);
    }
}
