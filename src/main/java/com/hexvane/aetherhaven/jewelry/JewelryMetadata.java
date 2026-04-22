package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

public final class JewelryMetadata {
    public static final String BSON_KEY = "AetherhavenJewelry";

    private JewelryMetadata() {}

    public static boolean hasJewelryMeta(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack) || stack.getMetadata() == null) {
            return false;
        }
        return stack.getMetadata().containsKey(BSON_KEY);
    }

    public static boolean isAppraised(@Nonnull ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return false;
        }
        BsonValue v = root.get("appraised");
        return v != null && v.isBoolean() && v.asBoolean().getValue();
    }

    @Nonnull
    public static ItemStack setAppraised(@Nonnull ItemStack stack, boolean appraised) {
        BsonDocument root = readOrCreateRoot(stack);
        root.put("appraised", BsonBoolean.valueOf(appraised));
        return writeRoot(stack, root);
    }

    @Nullable
    public static JewelryRarity readRarity(@Nonnull ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return null;
        }
        BsonValue v = root.get("rarity");
        if (v == null || !v.isString()) {
            return null;
        }
        return JewelryRarity.fromWire(v.asString().getValue());
    }

    @Nonnull
    public static List<RolledTrait> readTraits(@Nonnull ItemStack stack) {
        List<RolledTrait> out = new ArrayList<>();
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return out;
        }
        BsonValue tv = root.get("traits");
        if (tv == null || !tv.isArray()) {
            return out;
        }
        BsonArray arr = tv.asArray();
        for (BsonValue v : arr) {
            if (!v.isDocument()) {
                continue;
            }
            BsonDocument t = v.asDocument();
            int idx = readInt(t, "i", 0);
            String stat = readString(t, "stat", "");
            String calc = readString(t, "calc", "ADDITIVE");
            float amt = readDouble(t, "amt", 0.0);
            out.add(new RolledTrait(idx, stat, calc, amt));
        }
        return out;
    }

    /** Returns a new stack with rarity and traits when missing. */
    @Nonnull
    public static ItemStack ensureRolled(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
            return stack;
        }
        BsonDocument root = readRoot(stack);
        if (root != null && root.containsKey("traits")) {
            return stack;
        }
        JewelryGem gem = JewelryGem.fromItemId(stack.getItemId());
        if (gem == null) {
            return stack;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        JewelryRarity rarity = JewelryRarity.roll(rnd);
        String[] pool = JewelryGemTraits.statIdsFor(gem);
        int want = rarity.traitCount();
        IntSet picked = new IntOpenHashSet();
        while (picked.size() < want) {
            picked.add(rnd.nextInt(3));
        }
        BsonArray traits = new BsonArray();
        float metal = stack.getItemId().contains("_Gold_") ? 1.12f : 1.0f;
        float neck = JewelryItemIds.isNecklace(stack.getItemId()) ? 1.15f : 1.0f;
        for (int idx : picked) {
            String statId = pool[idx];
            float base = baseAmountFor(statId);
            float amt = base * rarity.magnitudeMultiplier() * metal * neck;
            BsonDocument td = new BsonDocument();
            td.put("i", new BsonInt32(idx));
            td.put("stat", new BsonString(statId));
            td.put("calc", new BsonString("ADDITIVE"));
            td.put("amt", new BsonDouble(amt));
            traits.add(td);
        }
        BsonDocument next = readOrCreateRoot(stack);
        next.put("rarity", new BsonString(rarity.wireName()));
        next.put("appraised", BsonBoolean.FALSE);
        next.put("traits", traits);
        return writeRoot(stack, next);
    }

    private static float baseAmountFor(@Nonnull String statId) {
        return switch (statId) {
            case "Health", "Mana", "Stamina", "Oxygen", "Ammo", "SignatureEnergy" -> 1.0f;
            default -> 0.5f;
        };
    }

    @Nullable
    private static BsonDocument readRoot(@Nonnull ItemStack stack) {
        if (stack.getMetadata() == null) {
            return null;
        }
        BsonValue v = stack.getMetadata().get(BSON_KEY);
        return v != null && v.isDocument() ? v.asDocument() : null;
    }

    @Nonnull
    private static BsonDocument readOrCreateRoot(@Nonnull ItemStack stack) {
        BsonDocument existing = readRoot(stack);
        return existing != null ? existing.clone() : new BsonDocument();
    }

    @Nonnull
    private static ItemStack writeRoot(@Nonnull ItemStack stack, @Nonnull BsonDocument root) {
        BsonDocument meta = stack.getMetadata() == null ? new BsonDocument() : stack.getMetadata().clone();
        meta.put(BSON_KEY, root);
        return stack.withMetadata(meta);
    }

    private static int readInt(@Nonnull BsonDocument d, @Nonnull String k, int def) {
        BsonValue v = d.get(k);
        if (v == null || !v.isNumber()) {
            return def;
        }
        return v.asNumber().intValue();
    }

    @Nonnull
    private static String readString(@Nonnull BsonDocument d, @Nonnull String k, @Nonnull String def) {
        BsonValue v = d.get(k);
        if (v == null || !v.isString()) {
            return def;
        }
        return v.asString().getValue();
    }

    private static float readDouble(@Nonnull BsonDocument d, @Nonnull String k, double def) {
        BsonValue v = d.get(k);
        if (v == null || !v.isNumber()) {
            return (float) def;
        }
        return (float) v.asNumber().doubleValue();
    }

    public record RolledTrait(int gemTraitIndex, @Nonnull String statId, @Nonnull String calculationType, float amount) {}
}
