package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.alchemy.AlchemyTownStock;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.workplace.MineshaftOutput;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.protocol.ItemQuantity;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** One live town stock view, regardless of how many links surround a bench. World thread only. */
public final class WarehouseSupplyLink {
    public static final String ITEM="Aetherhaven_Warehouse_Supply_Link";
    private final AetherhavenPlugin plugin;
    private final World world;
    private final TownManager manager;
    private final TownRecord town;
    private final UUID player;
    private final WarehouseBatch.Point link;
    private ItemContainer lastCombined;
    /** What the crafter carries in backpacks. Set by whoever opens the bench; null for none. */
    private java.util.function.Supplier<List<WarehouseCraftingContainer.Slot>> bags;

    /** A link with no warehouse behind it, for a bench that only draws on the crafter's backpacks. */
    public static WarehouseSupplyLink bagsOnly(AetherhavenPlugin plugin,World world,UUID player) {
        return new WarehouseSupplyLink(plugin,world,null,null,player,null);
    }
    public WarehouseSupplyLink withBags(java.util.function.Supplier<List<WarehouseCraftingContainer.Slot>> bags) {
        this.bags=bags;return this;
    }
    public WarehouseSupplyLink(AetherhavenPlugin plugin,World world,TownManager manager,TownRecord town,UUID player,WarehouseBatch.Point link) {
        this.plugin=plugin;this.world=world;this.manager=manager;this.town=town;this.player=player;this.link=link;
    }
    public static WarehouseSupplyLink find(AetherhavenPlugin plugin,World world,UUID player,int x,int y,int z,BlockType block,int rotation) {
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownContainingBlock(world.getName(),x,z);
        if(!WarehouseAccess.canBrowse(town,player)||!WarehouseAccess.hasManager(town))return null;
        var config=world.getGameplayConfig().getCraftingConfig();
        var hitbox=BlockBoundingBoxes.getAssetMap().getAsset(block.getHitboxTypeIndex()).get(rotation).getBoundingBox();
        int h=config.getBenchMaterialHorizontalChestSearchRadius(),v=config.getBenchMaterialVerticalChestSearchRadius();
        for(var point:WarehouseSupplyLinkBlock.loaded(world,manager,town)) {
            if(point.x()>=x+Math.floor(hitbox.min.x)-h&&point.x()<=x+Math.ceil(hitbox.max.x)-1+h
                &&point.y()>=y+Math.floor(hitbox.min.y)-v&&point.y()<=y+Math.ceil(hitbox.max.y)-1+v
                &&point.z()>=z+Math.floor(hitbox.min.z)-h&&point.z()<=z+Math.ceil(hitbox.max.z)-1+h)
                return new WarehouseSupplyLink(plugin,world,manager,town,player,point);
        }
        return null;
    }
    private boolean allowed() {
        if(town==null||manager==null||link==null)return false;
        var owner=manager.findTownContainingBlock(world.getName(),link.x(),link.z());
        var block=ChunkSectionBlockUtil.blockType(world,link.x(),link.y(),link.z());
        return owner==town&&WarehouseAccess.canBrowse(town,player)&&WarehouseAccess.hasManager(town)&&block!=null&&ITEM.equals(block.getId());
    }
    public MaterialExtraResourcesSection append(MaterialExtraResourcesSection section,BlockType block,int tier) {
        // Native invalidation replaces its container. Never wrap our previous combined view again.
        if(section.getItemContainer()==lastCombined)return section;
        var views=new ArrayList<ItemContainer>();views.add(section.getItemContainer());
        var carried=bags==null?List.<WarehouseCraftingContainer.Slot>of():bags.get();
        // Backpacks are the crafter's own, so they need no town permission and nothing to save.
        if(!carried.isEmpty())views.add(new WarehouseCraftingContainer(carried,()->true,()->{}));
        if(!allowed()) {
            if(views.size()==1)return section;
            return publish(section,block,tier,views);
        }
        var slots=new ArrayList<WarehouseCraftingContainer.Slot>();
        var stock=town.getWarehouse();WarehouseCraftingContainer.addStorage(slots,stock);
        var ids=new HashSet<UUID>();
        for(var source:AlchemyTownStock.sources(town,plugin))if(ids.add(source.plot())) {
            var plot=town.findPlotById(source.plot());
            WarehouseCraftingContainer.addProduction(slots,stock,source.plot(),source.inventory(),
                ()->plot!=null&&town.findPlotById(source.plot())==plot&&plot.getState()==PlotInstanceState.COMPLETE);
        }
        for(var site:town.getApprenticeWorkplaces().sites()) {
            var output=MineshaftOutput.find(town,site.id());
            if(output!=null&&ids.add(site.id()))WarehouseCraftingContainer.addProduction(slots,stock,site.id(),output,
                ()->MineshaftOutput.find(town,site.id())!=null);
        }
        views.add(new WarehouseCraftingContainer(slots,this::allowed,()->manager.updateTown(town)));
        return publish(section,block,tier,views);
    }
    private MaterialExtraResourcesSection publish(MaterialExtraResourcesSection section,BlockType block,int tier,List<ItemContainer> views) {
        lastCombined=new CombinedItemContainer(views.toArray(ItemContainer[]::new));
        section.setItemContainer(lastCombined);
        var materials=new TreeMap<String,ItemQuantity>();
        lastCombined.forEach((slot,item)->{
            if(CraftingPlugin.isValidCraftingMaterialForBench(block.getBench(),item)||CraftingPlugin.isValidUpgradeMaterialForBench(block.getBench(),tier,item)) {
                var quantity=materials.computeIfAbsent(item.getItemId(),id->new ItemQuantity(id,0));
                quantity.quantity=(int)Math.min(Integer.MAX_VALUE,(long)quantity.quantity+item.getQuantity());
            }
        });
        section.setExtraMaterials(materials.values().toArray(ItemQuantity[]::new));
        return section;
    }
}
