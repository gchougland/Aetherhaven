package com.hexvane.aetherhaven.command;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
record SpecialistHerdPreview(UUID plotId) {
    static SpecialistHerdPreview create(World world,UUID owner) {
        if(!world.getName().startsWith("aetherhaven_specialist_preview_"))throw new IllegalStateException("Use the disposable preview");
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);var town=manager.findTownForOwnerInWorld(owner);
        var plot=town.getPlotInstances().stream().filter(p->NativeHerdService.barn(plugin,p)).findFirst().orElseThrow();
        var state=town.getOrCreatePlotProduction(plot.getPlotId());var herd=state.herd();
        if(herd.animals().isEmpty())herd.buy(herd.revision(),"Sheep",10,()->true);
        herd.assign(herd.revision(),0,herd.animals().getFirst().id(),WorkplaceProductionUpgrades.slotCount(state));
        for(int i=0;i<8;i++)herd.supply(BarnAnimalCatalog.get("Sheep").food(),()->true);
        NativeHerdService.update(world,plugin,town,plot);manager.updateTown(town);return new SpecialistHerdPreview(plot.getPlotId());
    }
    void advance(World world,UUID owner){throw new IllegalStateException("Barn care now follows the in-game day. Use normal time progression.");}
    String status(World world,UUID owner) {
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get()).findTownForOwnerInWorld(owner);
        var state=town.getOrCreatePlotProduction(plotId);
        return "Barn: "+state.herd().animals().size()+" owned animals, foods "+state.herd().foods()+", stored resources "+state.getAmounts();
    }
}
