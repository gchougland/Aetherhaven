package com.hexvane.aetherhaven.alchemy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.crafter.PatternBookPayment;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.io.Reader;
import java.util.*;

/**
 * Making an Alchemy Flask at the laboratory cauldron, the way the Leatherworker makes backpacks: no recipe to learn,
 * materials from the player's inventory first and then from town storage. The cost is data. World thread only.
 */
public final class AlchemyFlaskCraft {
    private AlchemyFlaskCraft() {}
    /** A later asset pack's file at the same path replaces the bundled cost. */
    public static final String FILE="Flask.json";
    private static final HytaleLogger LOG=HytaleLogger.forEnclosingClass();
    private static volatile Map<String,Integer> cost;

    public enum Outcome { CRAFTED, MISSING, NO_ROOM, REFUSED }
    public record Result(Outcome outcome,List<AlchemyBottling.Missing> missing) {
        public Result { missing=List.copyOf(missing); }
    }

    /** Materials for one flask, in the order the file lists them. Empty when the file is missing or invalid. */
    public static Map<String,Integer> cost() {
        var loaded=cost;
        if(loaded==null){loaded=load(AlchemyFlaskCraft.class.getClassLoader());cost=loaded;}
        return loaded;
    }
    static Map<String,Integer> load(ClassLoader loader) {
        var data=AlchemyData.read(loader,FILE,candidate->!parse(candidate).isEmpty());
        if(data==null){LOG.atWarning().log("No valid Alchemy Flask cost was found. Flasks cannot be crafted.");return Map.of();}
        return parse(data);
    }
    static Map<String,Integer> parse(Reader reader){return parse(JsonParser.parseReader(reader).getAsJsonObject());}
    /** An entry without an item or with a quantity below one makes the whole cost invalid, so a typo is never free. */
    static Map<String,Integer> parse(JsonObject root) {
        var parts=new LinkedHashMap<String,Integer>();
        if(!root.has("CraftCost")||!root.get("CraftCost").isJsonArray())return Map.of();
        for(var entry:root.getAsJsonArray("CraftCost")) {
            if(!entry.isJsonObject())return Map.of();
            var part=entry.getAsJsonObject();
            String item=part.has("ItemId")?part.get("ItemId").getAsString():"";
            int quantity=part.has("Quantity")?part.get("Quantity").getAsInt():0;
            if(item.isBlank()||quantity<1)return Map.of();
            parts.merge(item,quantity,Integer::sum);
        }
        return Collections.unmodifiableMap(parts);
    }
    /** What the pack and the town together cannot cover. */
    public static List<AlchemyBottling.Missing> missing(ItemContainer pack,TownWarehouse stock,Map<String,Integer> cost) {
        var missing=new ArrayList<AlchemyBottling.Missing>();
        for(var part:cost.entrySet()) {
            long have=PatternBookPayment.available(pack,stock,part.getKey());
            if(have<part.getValue())missing.add(new AlchemyBottling.Missing(part.getKey(),(int)(part.getValue()-have)));
        }
        return missing;
    }
    public static Result craft(ItemContainer pack,TownWarehouse stock) { return craft(pack,stock,cost()); }
    /**
     * Pays for and hands over one empty flask, or changes nothing.
     *
     * @param pack where the materials are taken from first and where the flask is put
     * @param stock town storage to use after the pack, or null for the pack alone
     */
    public static Result craft(ItemContainer pack,TownWarehouse stock,Map<String,Integer> cost) {
        if(pack==null||cost==null||cost.isEmpty())return new Result(Outcome.REFUSED,List.of());
        var missing=missing(pack,stock,cost);
        if(!missing.isEmpty())return new Result(Outcome.MISSING,missing);
        var flask=AlchemyFlask.empty(new ItemStack(AlchemyFlask.ITEM,1));
        // Checked before anything is spent. Paying only ever frees room, so the flask still fits afterwards.
        if(!pack.canAddItemStack(flask))return new Result(Outcome.NO_ROOM,List.of());
        if(!PatternBookPayment.pay(pack,stock,cost))return new Result(Outcome.REFUSED,List.of());
        if(pack.addItemStack(flask).succeeded())return new Result(Outcome.CRAFTED,List.of());
        for(var part:cost.entrySet())pack.addItemStack(new ItemStack(part.getKey(),part.getValue()));
        return new Result(Outcome.REFUSED,List.of());
    }
}
