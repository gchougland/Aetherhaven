package com.hexvane.aetherhaven.alchemy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.workplace.TownWorkplaces;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.joml.Vector3i;
import java.util.UUID;

/** Revalidates the live cauldron, completed laboratory, membership and reach on every UI action. */
public final class AlchemyCauldronAccess {
    private AlchemyCauldronAccess() {}
    public record Target(TownRecord town,Vector3i position) {}
    public static Target find(World world,Vector3i clicked) {
        var plugin=AetherhavenPlugin.get(); if(plugin==null)return null;
        var pos=new Vector3i(clicked);
        int filler=ChunkSectionBlockUtil.filler(world,pos.x,pos.y,pos.z);
        pos.sub(FillerBlockUtil.unpackX(filler),FillerBlockUtil.unpackY(filler),FillerBlockUtil.unpackZ(filler));
        var block=ChunkSectionBlockUtil.blockType(world,pos.x,pos.y,pos.z);
        if(block==null||!"Alchemy_Cauldron_Big".equals(block.getId()))return null;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownContainingBlock(world.getName(),pos.x,pos.z);
        if(town==null||!TownWorkplaces.hasSupervisor(town,"alchemist"))return null;
        boolean laboratory=town.getPlotInstances().stream().anyMatch(p -> p.getState()==PlotInstanceState.COMPLETE&&p.containsWorldBlock(pos.x,pos.y,pos.z)
            &&plugin.getConstructionCatalog().matchesGameplayConstruction(p.getConstructionId(),"plot_alchemist"));
        return laboratory?new Target(town,pos):null;
    }
    public static boolean allowed(Target target,UUID player) { return target!=null&&target.town().hasMemberOrOwner(player); }
    public static void open(Ref<EntityStore> ref,Store<EntityStore> store,PlayerRef player,Vector3i pos) {
        var target=find(store.getExternalData().getWorld(),pos);
        if(!allowed(target,player.getUuid())||!WorkplaceBenchAccess.inReach(ref,store,pos))return;
        var entity=store.getComponent(ref,Player.getComponentType());
        if(entity!=null)entity.getPageManager().openCustomPage(ref,store,new AlchemyCauldronPage(player,target.town().getTownId(),target.position()));
    }
}
