package com.hexvane.aetherhaven.alchemy;

import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.*;
import org.bson.*;

/** Item-local charges and mixture. Uninitialized crafted or creative flasks are always empty. */
public final class AlchemyFlask {
    public static final String ITEM = "Aetherhaven_Alchemy_Flask";
    private static final String KEY = "AetherhavenAlchemyFlask";
    private AlchemyFlask() {}
    public static boolean isFlask(ItemStack stack) { return !ItemStack.isEmpty(stack) && ITEM.equals(stack.getItemId()) && stack.getQuantity() == 1; }
    private static BsonDocument data(ItemStack stack) { return isFlask(stack) ? stack.getFromMetadataOrNull(KEY, AetherhavenBsonCodecs.BSON_DOCUMENT) : null; }
    public static boolean initialized(ItemStack stack) { return data(stack) != null; }
    public static boolean needsDisplayRefresh(ItemStack stack) { return isFlask(stack)&&number(data(stack),"displayVersion",0)<2; }
    public static ItemStack refreshDisplay(ItemStack stack) { return write(stack,capacity(stack),charges(stack),ingredients(stack)); }
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
    public static ItemStack fill(ItemStack stack, List<String> ingredients) { return write(stack, capacity(stack), capacity(stack), ingredients); }
    public static ItemStack upgrade(ItemStack stack) { return write(stack, Math.min(5, capacity(stack)+1), charges(stack), ingredients(stack)); }
    public static ItemStack consume(ItemStack stack) { return write(stack, capacity(stack), Math.max(0, charges(stack)-1), ingredients(stack)); }
    public static ItemStack empty(ItemStack stack) { return write(stack, capacity(stack), 0, List.of()); }
    private static ItemStack write(ItemStack stack, int capacity, int charges, List<String> ingredients) {
        if (!isFlask(stack)) throw new IllegalArgumentException("Expected one Alchemy Flask");
        var data = new BsonDocument().append("capacity", new BsonInt32(capacity)).append("charges", new BsonInt32(charges))
            .append("displayVersion",new BsonInt32(2));
        data.append("ingredients", new BsonArray(ingredients.stream().map(s -> (BsonValue)new BsonString(s)).toList()));
        Message description=Message.raw("Charges: "+charges+" / "+capacity).color("#E4C987").bold(true);
        if(charges==0)description.insert(Message.raw("\nEmpty. Refill at the Alchemist's cauldron.").color("#A9B6BD").bold(false));
        else for(var ingredient:AlchemyIngredients.effects(ingredients))
            description.insert(Message.raw("\n"+ingredient.description()).color(effectColor(ingredient.family())).bold(false));
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
    public static int upgradeLeatherCount(int currentCapacity) { return currentCapacity * 4; }
}
