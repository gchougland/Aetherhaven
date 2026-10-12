package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.world.*;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Opt-in crops inside a disposable copy of the shipped Farm. Never completes worker actions. */
final class SpecialistFieldPreview {
    private static final String CROP="Plant_Crop_Wheat_Block",OUTPUT="Plant_Crop_Wheat_Item";
    private final UUID plotId;
    private final List<FieldProductionState.Bed> beds;
    private final long startingStock;
    private boolean ripened;
    private SpecialistFieldPreview(UUID plot,List<FieldProductionState.Bed> beds,long stock){plotId=plot;this.beds=List.copyOf(beds);startingStock=stock;}
    UUID plotId(){return plotId;}
    static SpecialistFieldPreview create(World world,UUID owner) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")&&!PersistentWorldSupport.shouldPersistWorldData(world),"Use the unsaved preview.");
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);require(town!=null,"Preview town missing.");
        var plot=town.getPlotInstances().stream().filter(p->"plot_farm".equals(p.getConstructionId())).findFirst().orElseThrow();
        var state=town.getOrCreatePlotProduction(plot.getPlotId());require(!state.usesPhysicalField(),"Preview farm is already active.");
        require(ProductionStorageLocator.find(world,plot,System.currentTimeMillis())!=null,"Preview farm storage missing.");
        var reviewed=NativeFarmCrops.review(world,town,plot);require(reviewed.ready(),reviewed.issue());
        var beds=reviewed.beds().stream().filter(b->CROP.equals(b.crop())).limit(3).toList();
        require(beds.size()==3,"Preview farm needs three native wheat beds.");
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),"plot_farm",state);
        require(entry!=null&&state.setSlotCursorToItem(0,entry,OUTPUT),"Wheat output unavailable.");
        require(state.field().adopt(state.field().revision(),beds)==null,"Fixture bed adoption failed.");
        var resident=town.getResidentNpcRecords().stream().filter(r->"farmer".equals(r.getKind())).findFirst().orElseThrow();
        var store=world.getEntityStore().getStore();var ref=store.getExternalData().getRefFromUUID(resident.getLastEntityUuid());
        require(ref!=null&&ref.isValid(),"Preview Farmer is not loaded.");store.putComponent(ref,VillagerNeeds.getComponentType(),VillagerNeeds.full());
        var time=store.getResource(WorldTimeResource.getResourceType());
        time.setGameTime(time.getGameTime().truncatedTo(ChronoUnit.DAYS).plus(8,ChronoUnit.HOURS),world,store);
        world.getWorldConfig().setGameTimePaused(false);world.getWorldConfig().setBlockTicking(true);manager.updateTown(town);
        return new SpecialistFieldPreview(plot.getPlotId(),beds,state.getAmount(OUTPUT));
    }
    void ripen(World world,UUID owner) {
        require(!ripened,"The initial crop setup was already ripened. Further growth must be natural.");
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get()).findTownForOwnerInWorld(owner);
        var state=town.peekPlotProduction(plotId);require(state.field().cargo()==null&&state.field().pending()==null,"Wait for the current harvest to finish.");
        var block=BlockType.getAssetMap().getAsset(CROP);var farming=block.getFarming();var stages=farming.getStages().get(farming.getStartingStageSet());
        for(var bed:beds) {
            var p=bed.point();var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,p.x(),p.y(),p.z());
            require(ref!=null&&ref.isValid(),"Crop entity is not ready.");
            var store=world.getChunkStore().getStore();var growth=store.getComponent(ref,FarmingBlock.getComponentType());require(growth!=null,"Crop growth is not ready.");
            growth.setCurrentStageSet(farming.getStartingStageSet());growth.setPreviousBlockType(CROP);
            growth.setGeneration(stages.length-1);growth.setGrowthProgress(stages.length-1);
            stages[stages.length-1].apply(store,ChunkSectionBlockUtil.sectionRefAt(world,p.x(),p.y(),p.z()),ref,
                ChunkUtil.localCoordinate(p.x()),ChunkUtil.localCoordinate(p.y()),ChunkUtil.localCoordinate(p.z()),stages[0]);
        }
        ripened=true;
    }
    String status(World world,UUID owner) {
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get()).findTownForOwnerInWorld(owner);
        var state=town.peekPlotProduction(plotId);
        return "Preview Farmer: wheat "+state.getAmount(OUTPUT)+" (initial "+startingStock+"), ripe beds "+beds.stream().filter(b->NativeFarmCrops.ripe(world,b)).count()
            +" / "+beds.size()+", beds needing water "+beds.stream().filter(b->NativeFarmCrops.needsWater(world,b)).count()
            +", held "+state.farm().carried()+", planned cells "+state.farm().plannedCount()+". Initial ripening supplied: "+ripened+". Harvests and delivery are ordinary worker actions.";
    }
    private static void require(boolean value,String issue){if(!value)throw new IllegalStateException(issue);}
}
