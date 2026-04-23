package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
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

    public static final String INSTANCE_TRANSLATION_PROPERTIES_KEY = "TranslationProperties";

    private JewelryMetadata() {}

    public static boolean hasJewelryMeta(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return false;
        }
        return stack.getFromMetadataOrNull(BSON_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT) != null;
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
        return syncInstanceDescriptionForTooltip(writeRoot(stack, root));
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
            float raw = readDouble(t, "amt", 0.0);
            out.add(new RolledTrait(idx, stat, calc, JewelryStatTuning.roundForStorage(stat, raw)));
        }
        return out;
    }

    /** Returns a new stack with rarity and traits when missing. */
    @Nonnull
    public static ItemStack ensureRolled(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
            return syncInstanceDescriptionForTooltip(stack);
        }
        BsonDocument root = readRoot(stack);
        if (root != null && root.containsKey("traits")) {
            return syncInstanceDescriptionForTooltip(stack);
        }
        JewelryGem gem = JewelryGem.fromItemId(stack.getItemId());
        if (gem == null) {
            return syncInstanceDescriptionForTooltip(stack);
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        AetherhavenPluginConfig cfg = JewelryRolling.config();
        JewelryRarity rarity = JewelryRarity.roll(rnd, cfg);
        String[] pool = JewelryGemTraits.statIdsFor(gem);
        int want = rarity.traitCount();
        IntSet picked = new IntOpenHashSet();
        while (picked.size() < want) {
            picked.add(rnd.nextInt(3));
        }
        BsonArray traits = new BsonArray();
        float metal = stack.getItemId().contains("_Gold_") ? (float) cfg.getJewelryGoldMetalTraitMultiplier() : 1.0f;
        float neck = JewelryItemIds.isNecklace(stack.getItemId()) ? (float) cfg.getJewelryNecklaceTraitMultiplier() : 1.0f;
        for (int idx : picked) {
            String statId = pool[idx];
            float baseAmt = JewelryStatTuning.rollMagnitudeFor(statId, rarity, rnd, cfg);
            float amt = JewelryStatTuning.roundForStorage(statId, baseAmt * metal * neck);
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
        return syncInstanceDescriptionForTooltip(writeRoot(stack, next));
    }

    /**
     * Writes {@code TranslationProperties.Description} on the stack metadata with plain English text so the default
     * tooltip can show rolled traits (and hidden lines when unappraised) instead of only the static item description key.
     */
    @Nonnull
    public static ItemStack syncInstanceDescriptionForTooltip(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
            return stripInstanceDescriptionOverride(stack);
        }
        if (!hasJewelryMeta(stack)) {
            return stripInstanceDescriptionOverride(stack);
        }
        BsonDocument tp = new BsonDocument();
        tp.put("Description", new BsonString(JewelryTooltipMessages.toPlainEnglishDescription(stack)));
        return stack.withMetadata(INSTANCE_TRANSLATION_PROPERTIES_KEY, tp);
    }

    @Nonnull
    private static ItemStack stripInstanceDescriptionOverride(@Nonnull ItemStack stack) {
        if (stack.getFromMetadataOrNull(INSTANCE_TRANSLATION_PROPERTIES_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT)
            == null) {
            return stack;
        }
        return stack.withMetadata(INSTANCE_TRANSLATION_PROPERTIES_KEY, null);
    }

    @Nullable
    private static BsonDocument readRoot(@Nonnull ItemStack stack) {
        return stack.getFromMetadataOrNull(BSON_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
    }

    @Nonnull
    private static BsonDocument readOrCreateRoot(@Nonnull ItemStack stack) {
        BsonDocument existing = readRoot(stack);
        return existing != null ? existing.clone() : new BsonDocument();
    }

    @Nonnull
    private static ItemStack writeRoot(@Nonnull ItemStack stack, @Nonnull BsonDocument root) {
        return stack.withMetadata(BSON_KEY, root);
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
