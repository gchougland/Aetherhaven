package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Optional hint for a player's own town, without moving or revealing the hidden block. */
public final class AetherhavenHalloweenCommand extends AbstractCommandCollection {
    public AetherhavenHalloweenCommand(){
        super("halloween","Jack Hollow's hide and seek game");
        setPermissionGroups("hytale:Adventurer");
        addSubCommand(new Head());
    }
    private static final class Head extends AbstractPlayerCommand {
        Head(){super("head","Show which building contains Jack Hollow's pumpkin");requireNoPermission();}
        @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
            var plugin=AetherhavenPlugin.get();if(plugin==null)return;
            var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
            var town=TownPlayerResolution.resolveActiveTown(world,store,ref,manager);
            if(town==null||!town.hasMemberOrOwner(player.getUuid())){player.sendMessage(Message.raw("You are not in one of your towns."));return;}
            var game=town.halloweenGame().active();
            if(game==null){player.sendMessage(Message.raw("Jack has not hidden his pumpkin."));return;}
            if(game.found()){player.sendMessage(Message.raw("The pumpkin has been found. Bring it back to Jack."));return;}
            var plot=town.findPlotById(game.spot().plot());
            var building=plot==null?null:plugin.getConstructionCatalog().get(plot.getConstructionId());
            player.sendMessage(Message.raw("Jack's pumpkin is hidden in "+(building==null?"an unavailable building":building.getDisplayName())+"."));
        }
    }
}
