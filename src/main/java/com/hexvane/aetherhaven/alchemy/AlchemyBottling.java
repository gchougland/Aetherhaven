package com.hexvane.aetherhaven.alchemy;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseAccess;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;
import java.util.function.Supplier;

/**
 * Filling a flask at the cauldron. The only question asked is whether the ingredients can be taken right now: the
 * player's inventory first, then town stock when the player may use it. Nothing an open page showed earlier is
 * compared, so a courier, a harvest, a finished craft or a flask moved to another slot cannot refuse a fill.
 * How well the mixture was brewed is the page's business: it arrives here as a quality and is written into the
 * flask. World thread only.
 */
public final class AlchemyBottling {
    private AlchemyBottling() {}
    public enum Outcome { FILLED, NO_FLASK, FLASK_HOLDS_POTION, INVALID_MIXTURE, MISSING, REFUSED }
    /** An ingredient that cannot be covered, and how many more are needed. */
    public record Missing(String item,int count) {}
    public record Result(Outcome outcome,short flaskSlot,int charges,List<Missing> missing) {
        public Result { missing=List.copyOf(missing); }
        public boolean filled(){return outcome==Outcome.FILLED;}
    }
    /** Town stock a player may draw on: storage, finished goods and what production buildings hold. */
    public record TownStock(TownWarehouse warehouse,Supplier<List<AlchemyTownStock.Source>> sources) {}
    private static Result result(Outcome outcome,short slot){return new Result(outcome,slot,0,List.of());}

    /**
     * The flask to fill: the one the page showed, wherever it sits now. Failing that the flask now in its slot, then an
     * empty flask, then any flask.
     *
     * @return the slot, or -1 when no flask is carried
     */
    public static short locate(ItemContainer bag,short preferred,ItemStack shown) {
        if(bag==null)return -1;
        boolean known=AlchemyFlask.isFlask(shown);
        if(known&&preferred>=0&&preferred<bag.getCapacity()&&shown.equals(bag.getItemStack(preferred)))return preferred;
        short inPlace=-1,empty=-1,any=-1;
        for(short slot=0;slot<bag.getCapacity();slot++) {
            var stack=bag.getItemStack(slot);
            if(!AlchemyFlask.isFlask(stack))continue;
            if(known&&shown.equals(stack))return slot;
            if(slot==preferred)inPlace=slot;
            if(empty<0&&AlchemyFlask.charges(stack)==0)empty=slot;
            if(any<0)any=slot;
        }
        return inPlace>=0?inPlace:empty>=0?empty:any;
    }
    /** What the bag and the town together cannot cover, in the order the costs are listed. */
    public static List<Missing> missing(ItemContainer bag,TownStock stock,Map<String,Integer> costs) {
        var live=stock==null?null:AlchemyTownStock.snapshot(stock.warehouse(),stock.sources().get());
        var missing=new ArrayList<Missing>();
        for(var cost:costs.entrySet()) {
            long have=InventoryMaterials.count(bag,cost.getKey())+(live==null?0:live.count(cost.getKey()));
            if(have<cost.getValue())missing.add(new Missing(cost.getKey(),(int)(cost.getValue()-have)));
        }
        return missing;
    }
    /**
     * Takes one of each ingredient per charge and fills the flask, or changes nothing.
     *
     * @param preferred the slot the page last showed the flask in
     * @param shown the flask the page last showed, or null
     * @param stock town stock to use after the bag, or null for the bag alone
     */
    public static Result fill(ItemContainer bag,short preferred,ItemStack shown,List<String> mixture,TownStock stock) {
        return fill(bag,preferred,shown,mixture,stock,AlchemyBrew.Quality.GOOD);
    }
    /** @param quality how well the mixture was brewed; it travels with the potion inside the flask */
    public static Result fill(ItemContainer bag,short preferred,ItemStack shown,List<String> mixture,TownStock stock,AlchemyBrew.Quality quality) {
        if(bag==null||!AlchemyIngredients.validMix(mixture,3))return result(Outcome.INVALID_MIXTURE,(short)-1);
        short slot=locate(bag,preferred,shown);
        if(slot<0)return result(Outcome.NO_FLASK,slot);
        var flask=bag.getItemStack(slot);
        if(AlchemyFlask.charges(flask)>0)return result(Outcome.FLASK_HOLDS_POTION,slot);
        var costs=AlchemyInventory.brewCosts(mixture,AlchemyFlask.capacity(flask));
        var missing=missing(bag,stock,costs);
        if(!missing.isEmpty())return new Result(Outcome.MISSING,slot,0,missing);
        var filled=AlchemyFlask.fill(flask,mixture,quality);
        boolean done=stock==null?AlchemyInventory.exchange(bag,slot,flask,filled,costs)
            :AlchemyTownStock.brew(stock.warehouse(),stock.sources(),bag,slot,flask,filled,costs);
        return done?new Result(Outcome.FILLED,slot,AlchemyFlask.charges(filled),List.of()):result(Outcome.REFUSED,slot);
    }
    /**
     * The whole Fill flask action for a town: the laboratory's ingredient slots, the player's right to use town
     * stock, the fill itself, and on success the discovery and Orin's Mastery. The caller saves the town.
     *
     * @param sources the town's production stock, read only if the bag and storage are not enough
     */
    public static Result bottle(TownRecord town,UUID player,Supplier<List<AlchemyTownStock.Source>> sources,ItemContainer bag,
                                short preferred,ItemStack shown,List<String> mixture) {
        return bottle(town,player,sources,bag,preferred,shown,mixture,AlchemyBrew.Quality.GOOD);
    }
    /** Orin's Mastery is the same whatever the quality: it is earned by filling flasks, not by playing well. */
    public static Result bottle(TownRecord town,UUID player,Supplier<List<AlchemyTownStock.Source>> sources,ItemContainer bag,
                                short preferred,ItemStack shown,List<String> mixture,AlchemyBrew.Quality quality) {
        if(town==null||!AlchemyIngredients.validMix(mixture,town.getAlchemyLaboratory().ingredientSlots()))
            return result(Outcome.INVALID_MIXTURE,(short)-1);
        var stock=WarehouseAccess.canBrowse(town,player)?new TownStock(town.getWarehouse(),sources):null;
        var result=fill(bag,preferred,shown,mixture,stock,quality);
        if(result.filled()) {
            town.getAlchemyLaboratory().discover(mixture);
            CoreVillagerMasteryService.progress(town,"alchemist").earnXp(AlchemyFlask.bottlingXp(mixture.size(),result.charges()));
        }
        return result;
    }
}
