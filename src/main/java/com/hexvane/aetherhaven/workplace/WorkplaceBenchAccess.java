package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.*;
import org.joml.Vector3i;

/** Bench ownership is derived from the site, never stamped onto base game blocks. */
public final class WorkplaceBenchAccess {
    private WorkplaceBenchAccess() {}
    private static final Map<TownsfolkWorkType,Set<String>> EXTRA=new java.util.concurrent.ConcurrentHashMap<>();
    /** Integrations may nominate their own primary bench IDs without replacing interactions. */
    public static void registerPrimaryBench(TownsfolkWorkType type,String id) {
        if(type==null||id==null||id.isBlank())throw new IllegalArgumentException("A workplace type and bench ID are required");
        EXTRA.computeIfAbsent(type,k->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(id);
    }
    public static boolean matches(TownsfolkWorkType type,String bench,String block) {
        if(type==null||bench==null)return false;
        if(EXTRA.getOrDefault(type,Set.of()).contains(bench))return true;
        return switch(type) {
            case SMELTING -> "Furnace".equals(bench);
            case BLACKSMITHING -> "Weapon_Bench".equals(bench);
            case ARMOR_CRAFTING -> "Armor_Bench".equals(bench);
            case FARMING,CULTIVATION,BERRIES -> "Farmingbench".equals(bench)&&!"Bench_Trough".equals(block);
            case RANCHING -> "Bench_Trough".equals(block);
            case ALCHEMY -> "Alchemybench".equals(bench);
            case CRAFTING,MINING -> "Workbench".equals(bench);
            case BUILDING,BLOCK_WORKSHOP -> WorkplaceEquipment.BUILDERS_BENCH.equals(bench);
            case LEATHERWORKING -> "Tannery".equals(bench);
            case SALVAGING -> "Salvagebench".equals(bench);
            case COOKING -> "Cookingbench".equals(bench);
            case JEWELRY -> WorkplaceEquipment.JEWELRY_BENCH.equals(bench);
            default -> false;
        };
    }
    public record Target(TownRecord town,ApprenticeWorkplace site,Vector3i pos) {}
    public static Target find(World world,Vector3i clicked) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var pos=new Vector3i(clicked);
        int filler=ChunkSectionBlockUtil.filler(world,pos.x,pos.y,pos.z);
        pos.sub(FillerBlockUtil.unpackX(filler),FillerBlockUtil.unpackY(filler),FillerBlockUtil.unpackZ(filler));
        var block=ChunkSectionBlockUtil.blockType(world,pos.x,pos.y,pos.z);var id=WorkplaceEquipment.benchId(block);
        if(id==null)return null;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownContainingBlock(world.getName(),pos.x,pos.z);
        if(town==null)return null;
        var base=com.hypixel.hytale.builtin.crafting.component.BenchBlock.getBaseBlockType(block);
        for(var site:town.getApprenticeWorkplaces().sites())
            if(site.area().contains(pos.x,pos.y,pos.z)&&matches(site.type(),id,base.getId()))return new Target(town,site,pos);
        return null;
    }
    public static boolean allowed(Target target,UUID player) {
        return target!=null&&target.town().hasMemberOrOwner(player)&&target.town().playerCanManageConstructions(player);
    }
    public static boolean inReach(Ref<EntityStore> ref,ComponentAccessor<EntityStore> store,Vector3i pos) {
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        return transform!=null&&transform.getPosition().distanceSquared(pos.x+.5,pos.y+.5,pos.z+.5)<=64;
    }
    public static void open(Ref<EntityStore> ref,Store<EntityStore> store,PlayerRef player,Vector3i pos) {
        var target=find(store.getExternalData().getWorld(),pos);
        if(!allowed(target,player.getUuid())||!inReach(ref,store,pos))return;
        var entity=store.getComponent(ref,Player.getComponentType());if(entity==null)return;
        // A bench that opens its workplace's page is the station of that workplace, standing inside it.
        var world=store.getExternalData().getWorld();var plugin=AetherhavenPlugin.get();
        if(plugin!=null)com.hexvane.aetherhaven.quest.WorkplaceQuestHooks.stationSeen(world,
            AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin),target.town(),target.site().type());
        var town=target.town().getTownId();var site=target.site().id();
        entity.getPageManager().openCustomPage(ref,store,switch(screen(target.site().type())) {
            case BUILDING_PROJECTS -> new BuildingProjectsPage(player,town,site,target.pos());
            case MINESHAFT -> new MineshaftPlannerPage(player,town,site,target.pos());
            case FOOD -> new FoodSitePage(player,town,site,target.pos());
            case GROWER -> new GrowerSitePage(player,town,site,target.pos());
            case ORDERS -> new WorkplaceOrdersPage(player,town,site,target.pos());
        });
    }
    /** Which page a site's bench opens. Two types may share a bench, so the site's own type decides. */
    public enum Screen { BUILDING_PROJECTS, MINESHAFT, FOOD, GROWER, ORDERS }
    public static Screen screen(TownsfolkWorkType type) {
        if(type==TownsfolkWorkType.BUILDING)return Screen.BUILDING_PROJECTS;
        if(type==TownsfolkWorkType.MINING)return Screen.MINESHAFT;
        if(GrowerPlan.supports(type))return Screen.GROWER;
        return WorkplaceFood.supports(type)?Screen.FOOD:Screen.ORDERS;
    }
}
