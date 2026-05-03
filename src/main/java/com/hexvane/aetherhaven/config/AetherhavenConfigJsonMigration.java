package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

/**
 * Lifts pre-nested (flat) {@code AetherhavenPluginConfig} keys into {@code LootChest} and {@code Jewelry} objects,
 * or removes duplicate flat keys if nested already exists. Run before the plugin's {@code config} load/merge.
 */
public final class AetherhavenConfigJsonMigration {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AetherhavenConfigJsonMigration() {}

    /**
     * @return true if the file on disk was rewritten
     */
    public static boolean migrateIfNeeded(@Nonnull Path configJsonPath) {
        if (!Files.isRegularFile(configJsonPath)) {
            return false;
        }
        String raw;
        try {
            raw = Files.readString(configJsonPath);
        } catch (IOException e) {
            return false;
        }
        if (raw.isBlank()) {
            return false;
        }
        BsonDocument d;
        try {
            d = BsonDocument.parse(raw);
        } catch (Exception e) {
            return false;
        }
        int pass = 0;
        if (removeDuplicateFlatIfNested(d)) {
            pass++;
        }
        if (migrateLootChest(d)) {
            pass++;
        }
        if (migrateJewelry(d)) {
            pass++;
        }
        if (removeDuplicateFlatIfNested(d)) {
            pass++;
        }
        if (pass == 0) {
            return false;
        }
        BsonUtil.writeDocument(configJsonPath, d, true).join();
        LOGGER.atInfo().log("Migrated Aetherhaven config structure at %s (passes=%d).", configJsonPath, pass);
        return true;
    }

    private static void removeIfPresent(@Nonnull BsonDocument d, @Nonnull String key) {
        d.remove(key);
    }

    /** @return true if the document was modified */
    private static boolean removeDuplicateFlatIfNested(@Nonnull BsonDocument d) {
        boolean m = false;
        if (d.containsKey("LootChest")) {
            m |= removeIfAny(d, FLAT_LOOT);
        }
        if (d.containsKey("Jewelry")) {
            m |= removeIfAny(d, FLAT_JEWELRY);
        }
        return m;
    }

    private static boolean removeIfAny(@Nonnull BsonDocument d, @Nonnull String[] keys) {
        boolean m = false;
        for (String k : keys) {
            if (d.containsKey(k)) {
                d.remove(k);
                m = true;
            }
        }
        return m;
    }

    private static final String[] FLAT_LOOT = {
        "LootChestJewelryChance",
        "LootChestJewelryBlockIdSubstrings",
        "LootChestGoldCoinChance",
        "LootChestGoldCoinMin",
        "LootChestGoldCoinMax",
        "LootChestGoldCoinItemId",
        "LootChestPlotTokenChance",
        "LootChestPlotTokenItemId"
    };

    private static final String[] FLAT_JEWELRY = {
        "JewelryRarityWeightCommon",
        "JewelryRarityWeightUncommon",
        "JewelryRarityWeightRare",
        "JewelryRarityWeightMythic",
        "JewelryRarityWeightLegendary",
        "JewelryGoldMetalTraitMultiplier",
        "JewelryNecklaceTraitMultiplier",
        "JewelryStatHealthCommonMin",
        "JewelryStatHealthCommonMax",
        "JewelryStatHealthLegendaryMin",
        "JewelryStatHealthLegendaryMax",
        "JewelryStatStaminaCommonMin",
        "JewelryStatStaminaCommonMax",
        "JewelryStatStaminaLegendaryMin",
        "JewelryStatStaminaLegendaryMax",
        "JewelryStatAmmoCommonMin",
        "JewelryStatAmmoCommonMax",
        "JewelryStatAmmoLegendaryMin",
        "JewelryStatAmmoLegendaryMax",
        "JewelryStatManaCommonMin",
        "JewelryStatManaCommonMax",
        "JewelryStatManaLegendaryMin",
        "JewelryStatManaLegendaryMax",
        "JewelryStatOxygenCommonMin",
        "JewelryStatOxygenCommonMax",
        "JewelryStatOxygenLegendaryMin",
        "JewelryStatOxygenLegendaryMax",
        "JewelryStatSignatureEnergyCommonMin",
        "JewelryStatSignatureEnergyCommonMax",
        "JewelryStatSignatureEnergyLegendaryMin",
        "JewelryStatSignatureEnergyLegendaryMax"
    };

    private static boolean migrateLootChest(@Nonnull BsonDocument d) {
        if (d.containsKey("LootChest")) {
            return false;
        }
        boolean hasLegacy =
            d.containsKey("LootChestJewelryChance")
                || d.containsKey("LootChestJewelryBlockIdSubstrings")
                || d.containsKey("LootChestGoldCoinChance")
                || d.containsKey("LootChestGoldCoinMin")
                || d.containsKey("LootChestGoldCoinMax")
                || d.containsKey("LootChestGoldCoinItemId")
                || d.containsKey("LootChestPlotTokenChance")
                || d.containsKey("LootChestPlotTokenItemId");
        if (!hasLegacy) {
            return false;
        }
        BsonDocument loot = new BsonDocument();
        loot.put("Note", new BsonString("See LootChest in defaults; this block was auto-migrated from flat root keys."));
        if (d.containsKey("LootChestJewelryChance")) {
            loot.put("JewelryChance", d.get("LootChestJewelryChance"));
        } else {
            loot.put("JewelryChance", new BsonDouble(0.2));
        }
        if (d.containsKey("LootChestJewelryBlockIdSubstrings")) {
            loot.put("BlockIdSubstrings", d.get("LootChestJewelryBlockIdSubstrings"));
        } else {
            loot.put("BlockIdSubstrings", new BsonString(""));
        }
        BsonDocument gold = new BsonDocument();
        gold.put("Chance", bDouble(d, "LootChestGoldCoinChance", 0.06));
        gold.put("Min", bInt(d, "LootChestGoldCoinMin", 5));
        gold.put("Max", bInt(d, "LootChestGoldCoinMax", 10));
        gold.put("ItemId", bString(d, "LootChestGoldCoinItemId", "Aetherhaven_Gold_Coin"));
        gold.put("Note", new BsonString("Gold coin drop range and item id."));

        BsonDocument plot = new BsonDocument();
        plot.put("Chance", bDouble(d, "LootChestPlotTokenChance", 0.0));
        plot.put("ItemId", bString(d, "LootChestPlotTokenItemId", ""));
        plot.put("Note", new BsonString("Plot token (optional)."));

        loot.put("Gold", gold);
        loot.put("PlotToken", plot);
        d.put("LootChest", loot);
        for (String k : FLAT_LOOT) {
            d.remove(k);
        }
        return true;
    }

    private static boolean migrateJewelry(@Nonnull BsonDocument d) {
        if (d.containsKey("Jewelry")) {
            return false;
        }
        if (!d.containsKey("JewelryRarityWeightCommon") && !d.containsKey("JewelryStatHealthCommonMin")) {
            return false;
        }
        BsonDocument jew = new BsonDocument();
        jew.put("Note", new BsonString("Migrated from flat root keys; edit nested RarityWeights, TraitMultipliers, and Stat."));

        BsonDocument w = new BsonDocument();
        w.put("Note", new BsonString("Relative tier weights; normalized in code."));
        w.put("Common", bDouble(d, "JewelryRarityWeightCommon", 50.0));
        w.put("Uncommon", bDouble(d, "JewelryRarityWeightUncommon", 30.0));
        w.put("Rare", bDouble(d, "JewelryRarityWeightRare", 12.0));
        w.put("Mythic", bDouble(d, "JewelryRarityWeightMythic", 4.0));
        w.put("Legendary", bDouble(d, "JewelryRarityWeightLegendary", 1.0));
        jew.put("RarityWeights", w);

        BsonDocument t = new BsonDocument();
        t.put("Note", new BsonString("After base roll, multiply by these."));
        t.put("GoldMetal", bDouble(d, "JewelryGoldMetalTraitMultiplier", 1.2));
        t.put("Necklace", bDouble(d, "JewelryNecklaceTraitMultiplier", 1.15));
        jew.put("TraitMultipliers", t);

        BsonDocument stat = new BsonDocument();
        stat.put("Note", new BsonString("Common/Legendary min-max bands per stat id."));
        stat.put("Health", buildPair(d, "Health"));
        stat.put("Stamina", buildPair(d, "Stamina"));
        stat.put("Ammo", buildPair(d, "Ammo"));
        stat.put("Mana", buildPair(d, "Mana"));
        stat.put("Oxygen", buildPair(d, "Oxygen"));
        stat.put("SignatureEnergy", buildPair(d, "SignatureEnergy"));
        jew.put("Stat", stat);

        d.put("Jewelry", jew);
        for (String k : FLAT_JEWELRY) {
            d.remove(k);
        }
        return true;
    }

    @Nonnull
    private static BsonDocument buildPair(@Nonnull BsonDocument root, @Nonnull String stat) {
        String p = "JewelryStat" + stat;
        BsonDocument common = new BsonDocument();
        common.put("Min", bDouble(root, p + "CommonMin", defCommonMin(stat)));
        common.put("Max", bDouble(root, p + "CommonMax", defCommonMax(stat)));
        BsonDocument leg = new BsonDocument();
        leg.put("Min", bDouble(root, p + "LegendaryMin", defLegMin(stat)));
        leg.put("Max", bDouble(root, p + "LegendaryMax", defLegMax(stat)));
        BsonDocument o = new BsonDocument();
        o.put("Common", common);
        o.put("Legendary", leg);
        return o;
    }

    private static double defCommonMin(@Nonnull String s) {
        return switch (s) {
            case "Health" -> 5.0;
            case "Stamina" -> 1.0;
            case "Ammo" -> 1.0;
            case "Mana" -> 5.0;
            case "Oxygen" -> 10.0;
            case "SignatureEnergy" -> -2.0;
            default -> 1.0;
        };
    }

    private static double defCommonMax(@Nonnull String s) {
        return switch (s) {
            case "Health" -> 16.0;
            case "Stamina" -> 5.0;
            case "Ammo" -> 2.0;
            case "Mana" -> 16.0;
            case "Oxygen" -> 30.0;
            case "SignatureEnergy" -> -1.0;
            default -> 2.0;
        };
    }

    private static double defLegMin(@Nonnull String s) {
        return switch (s) {
            case "Health" -> 32.0;
            case "Stamina" -> 12.0;
            case "Ammo" -> 3.0;
            case "Mana" -> 32.0;
            case "Oxygen" -> 50.0;
            case "SignatureEnergy" -> -10.0;
            default -> 2.0;
        };
    }

    private static double defLegMax(@Nonnull String s) {
        return switch (s) {
            case "Health" -> 50.0;
            case "Stamina" -> 20.0;
            case "Ammo" -> 5.0;
            case "Mana" -> 50.0;
            case "Oxygen" -> 100.0;
            case "SignatureEnergy" -> -6.0;
            default -> 4.0;
        };
    }

    @Nonnull
    private static BsonValue bDouble(@Nonnull BsonDocument d, @Nonnull String key, double def) {
        BsonValue v = d.get(key);
        if (v != null && v.isNumber()) {
            return new BsonDouble(v.asNumber().doubleValue());
        }
        return new BsonDouble(def);
    }

    @Nonnull
    private static BsonValue bInt(@Nonnull BsonDocument d, @Nonnull String key, int def) {
        BsonValue v = d.get(key);
        if (v != null && v.isInt32()) {
            return v;
        }
        if (v != null && v.isNumber()) {
            return new BsonInt32(v.asNumber().intValue());
        }
        return new BsonInt32(def);
    }

    @Nonnull
    private static BsonString bString(@Nonnull BsonDocument d, @Nonnull String key, @Nonnull String def) {
        BsonValue v = d.get(key);
        if (v != null && v.isString()) {
            return new BsonString(v.asString().getValue());
        }
        return new BsonString(def);
    }
}
