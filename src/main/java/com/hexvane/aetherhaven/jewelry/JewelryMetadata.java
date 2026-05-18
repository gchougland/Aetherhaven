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

    /**
     * Whether this stack should show appraised jewelry UI (revealed traits, no “still need appraisal” blurbs).
     * <p>Uses BSON {@code appraised} when present ({@linkplain #readAppraisedLenient tolerant} decoding). When the wire
     * representation drops that flag but keeps {@link #INSTANCE_TRANSLATION_PROPERTIES_KEY}/{@code Description} in sync
     * from {@link #syncInstanceDescriptionForTooltip}, we infer appraisal from matching that text to what an appraised
     * stack would render — so DynamicTooltipsLib tooltips agree with appraisal UI even if network metadata sanitizes booleans.</p>
     */
    public static boolean isAppraised(@Nonnull ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return false;
        }
        if (readAppraisedLenient(root)) {
            return true;
        }
        return inferAppraisedFromSyncedDescription(stack);
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
        return applyRolledTraits(stack, rarity, false, rnd, cfg);
    }

    /**
     * Crafts a new jewelry stack: rolls traits for {@code rarity}, marks appraised so the piece needs no further
     * appraisal.
     */
    @Nonnull
    public static ItemStack rollCraftedAppraised(
        @Nonnull String jewelryItemId, @Nonnull JewelryRarity rarity, @Nonnull ThreadLocalRandom rnd
    ) {
        if (!JewelryItemIds.isJewelry(jewelryItemId)) {
            return new ItemStack(jewelryItemId, 1);
        }
        ItemStack stack = new ItemStack(jewelryItemId, 1);
        AetherhavenPluginConfig cfg = JewelryRolling.config();
        return applyRolledTraits(stack, rarity, true, rnd, cfg);
    }

    @Nonnull
    private static ItemStack applyRolledTraits(
        @Nonnull ItemStack stack,
        @Nonnull JewelryRarity rarity,
        boolean appraised,
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        JewelryGem gem = JewelryGem.fromItemId(stack.getItemId());
        if (gem == null) {
            return syncInstanceDescriptionForTooltip(stack);
        }
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
        next.put("appraised", BsonBoolean.valueOf(appraised));
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

    /** True when BSON carries a truthy {@code appraised} value (boolean, number, or common string forms). */
    private static boolean readAppraisedLenient(@Nonnull BsonDocument jewelryRoot) {
        BsonValue v = jewelryRoot.get("appraised");
        if (v == null || v.isNull()) {
            return false;
        }
        if (v.isBoolean()) {
            return v.asBoolean().getValue();
        }
        if (v.isNumber()) {
            return v.asNumber().doubleValue() != 0.0;
        }
        if (v.isString()) {
            String s = v.asString().getValue().trim();
            return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "1".equals(s);
        }
        return false;
    }

    @Nullable
    private static String readInstanceDescriptionPlain(@Nonnull ItemStack stack) {
        BsonDocument tp =
            stack.getFromMetadataOrNull(INSTANCE_TRANSLATION_PROPERTIES_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
        if (tp == null) {
            return null;
        }
        BsonValue d = tp.get("Description");
        return d != null && d.isString() ? d.asString().getValue() : null;
    }

    @Nonnull
    private static String normalizePlainDescription(@Nonnull String s) {
        return s.replace("\r\n", "\n").trim().replaceAll("[ \t]+", " ");
    }

    /** When wired metadata lacks a truthy appraisal flag but instance description matches an appraised projection. */
    private static boolean inferAppraisedFromSyncedDescription(@Nonnull ItemStack stack) {
        String synced = readInstanceDescriptionPlain(stack);
        if (synced == null || synced.isBlank()) {
            return false;
        }
        ItemStack asIf = duplicateWithAppraisedBson(stack, true);
        if (asIf == stack) {
            return false;
        }
        String expected = JewelryTooltipMessages.toPlainEnglishDescription(asIf);
        String unappraisedBaseline = JewelryTooltipMessages.toPlainEnglishDescription(duplicateWithAppraisedBson(stack, false));
        String nSync = normalizePlainDescription(synced);
        if (!nSync.equals(normalizePlainDescription(unappraisedBaseline))) {
            return nSync.equals(normalizePlainDescription(expected));
        }
        return false;
    }

    /**
     * Same traits/rarity but {@code appraised} overwritten; does not rerun {@link #syncInstanceDescriptionForTooltip}.
     * <p>{@link JewelryTooltipMessages} for probes must not inherit {@link #INSTANCE_TRANSLATION_PROPERTIES_KEY}: that
     * text stays in sync while BSON {@code appraised} may be dropped on the wire — keeping it would make {@link
     * #inferAppraisedFromSyncedDescription} recurse and miscompute baselines.</p>
     */
    @Nonnull
    private static ItemStack duplicateWithAppraisedBson(@Nonnull ItemStack stack, boolean appraised) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return stack;
        }
        BsonDocument next = root.clone();
        next.put("appraised", BsonBoolean.valueOf(appraised));
        return stripInstanceDescriptionOverride(writeRoot(stack, next));
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
