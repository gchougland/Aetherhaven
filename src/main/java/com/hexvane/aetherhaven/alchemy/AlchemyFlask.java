package com.hexvane.aetherhaven.alchemy;

import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.*;
import org.bson.*;

/**
 * Item-local charges, mixture and brew quality. Uninitialized crafted or creative flasks are always empty, and a
 * flask filled before brew quality existed is a Good one.
 */
public final class AlchemyFlask {
    public static final String ITEM = "Aetherhaven_Alchemy_Flask";
    private static final String KEY = "AetherhavenAlchemyFlask";
    private AlchemyFlask() {}
    public static boolean isFlask(ItemStack stack) { return !ItemStack.isEmpty(stack) && ITEM.equals(stack.getItemId()) && stack.getQuantity() == 1; }
    private static BsonDocument data(ItemStack stack) { return isFlask(stack) ? stack.getFromMetadataOrNull(KEY, AetherhavenBsonCodecs.BSON_DOCUMENT) : null; }
    public static boolean initialized(ItemStack stack) { return data(stack) != null; }
    public static boolean needsDisplayRefresh(ItemStack stack) { return isFlask(stack)&&number(data(stack),"displayVersion",0)<2; }
    public static ItemStack refreshDisplay(ItemStack stack) { return write(stack,capacity(stack),charges(stack),ingredients(stack),quality(stack)); }
    private static int number(BsonDocument data, String key, int fallback) {
        var value = data == null ? null : data.get(key);
        return value != null && value.isNumber() ? value.asNumber().intValue() : fallback;
    }
    public static int capacity(ItemStack stack) { return Math.clamp(number(data(stack), "capacity", 1), 1, 5); }
    public static int charges(ItemStack stack) {
        return ingredients(stack).isEmpty() ? 0 : Math.clamp(number(data(stack), "charges", 0), 0, capacity(stack));
    }
    public static List<String> ingredients(ItemStack stack) {
        var data = data(stack); var raw = data == null ? null : data.get("ingredients");
        if (raw == null || !raw.isArray() || raw.asArray().size() > 3) return List.of();
        var result = new ArrayList<String>();
        for (var value : raw.asArray()) {
            if (!value.isString() || AlchemyIngredients.find(value.asString().getValue()) == null) return List.of();
            result.add(value.asString().getValue());
        }
        return List.copyOf(result);
    }
    /** How well the potion inside was brewed. An empty flask, or one filled before quality existed, is Good. */
    public static AlchemyBrew.Quality quality(ItemStack stack) {
        return charges(stack)==0?AlchemyBrew.Quality.GOOD:AlchemyBrew.Quality.of(number(data(stack),"quality",0));
    }
    public static ItemStack fill(ItemStack stack, List<String> ingredients) { return fill(stack, ingredients, AlchemyBrew.Quality.GOOD); }
    public static ItemStack fill(ItemStack stack, List<String> ingredients, AlchemyBrew.Quality quality) {
        return write(stack, capacity(stack), capacity(stack), ingredients, quality);
    }
    public static ItemStack upgrade(ItemStack stack) { return write(stack, Math.min(5, capacity(stack)+1), charges(stack), ingredients(stack), quality(stack)); }
    public static ItemStack consume(ItemStack stack) { return write(stack, capacity(stack), Math.max(0, charges(stack)-1), ingredients(stack), quality(stack)); }
    public static ItemStack empty(ItemStack stack) { return write(stack, capacity(stack), 0, List.of(), AlchemyBrew.Quality.GOOD); }
    private static ItemStack write(ItemStack stack, int capacity, int charges, List<String> ingredients, AlchemyBrew.Quality brewed) {
        if (!isFlask(stack)) throw new IllegalArgumentException("Expected one Alchemy Flask");
        // The quality belongs to the potion, so it leaves with the last charge.
        var quality=charges==0||ingredients.isEmpty()||brewed==null?AlchemyBrew.Quality.GOOD:brewed;
        var data = new BsonDocument().append("capacity", new BsonInt32(capacity)).append("charges", new BsonInt32(charges))
            .append("displayVersion",new BsonInt32(2));
        data.append("ingredients", new BsonArray(ingredients.stream().map(s -> (BsonValue)new BsonString(s)).toList()));
        if(quality!=AlchemyBrew.Quality.GOOD)data.append("quality",new BsonInt32(quality.ordinal()));
        Message description=Message.raw("Charges: "+charges+" / "+capacity).color("#E4C987").bold(true);
        if(charges==0)description.insert(Message.raw("\nEmpty. Refill at the Alchemist's cauldron.").color("#A9B6BD").bold(false));
        else {
            for(var ingredient:AlchemyIngredients.effects(ingredients))
                description.insert(Message.raw("\n"+ingredient.description()).color(effectColor(ingredient.family())).bold(false));
            if(quality!=AlchemyBrew.Quality.GOOD) {
                var rules=AlchemyBrew.rules();
                // The line break is its own piece so the translated line keeps its colour.
                description.insert(Message.raw("\n"));
                description.insert(Message.translation("aetherhaven_alchemy.aetherhaven.flask.quality."+quality.name().toLowerCase(java.util.Locale.ROOT))
                    .param("duration",Integer.toString(AlchemyBrew.Rules.percent(rules.duration(quality))))
                    .param("restore",Integer.toString(AlchemyBrew.Rules.percent(rules.restore(quality)))).color("#F2D58A").bold(false));
            }
        }
        return stack.withMetadata(KEY, data).withMaxDurability(capacity).withDurability(charges)
            .withMetadata(ItemDisplayMetadata.KEYED_CODEC,new ItemDisplayMetadata(Message.translation("aetherhaven_alchemy.flask.name").color("#C7B0EF"),description));
    }
    private static String effectColor(String family) {
        return switch(family) {
            case "Health","HealthBoost" -> "#F29B9B";
            case "Mana","ManaBoost" -> "#91C8FF";
            case "Stamina" -> "#F0D47E";
            case "PoisonWard" -> "#AAD982";
            case "BlastWard" -> "#F3B579";
            case "Regen" -> "#8EE0B0";
            case "FireWard" -> "#FF8A5C";
            case "Repellent" -> "#B79CF2";
            default -> "#D4C7ED";
        };
    }
    public static String upgradeLeather(int currentCapacity) {
        return switch (currentCapacity) { case 1 -> "Ingredient_Leather_Light"; case 2 -> "Ingredient_Leather_Medium";
            case 3 -> "Ingredient_Leather_Heavy"; default -> "Ingredient_Leather_Storm"; };
    }
    public static String upgradeLeatherName(int currentCapacity) {
        return switch (currentCapacity) { case 1 -> "Light Leather"; case 2 -> "Medium Leather";
            case 3 -> "Heavy Leather"; default -> "Storm Leather"; };
    }
    public static int upgradeMastery(int currentCapacity) { return Math.min(10, currentCapacity * 2); }
    public static int upgradeLeatherCount(int currentCapacity) { return currentCapacity * 5; }
    /** Alchemist Mastery for one filled flask. Tuned so a level takes about eight fills at every Mastery. */
    public static long bottlingXp(int ingredients, int capacity) { return 7L * (Math.max(1, ingredients) + Math.clamp(capacity, 1, 5)); }
}
