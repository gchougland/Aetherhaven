package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.hexvane.aetherhaven.jewelry.JewelryStatTuning.formatForDisplay;

/** Hytale rich text for item tooltips. */
public final class JewelryTooltipText {
    private static final String C_LABEL = "#B0B0B8";
    private static final String C_DIM = "#8A8F98";
    private static final String C_BONUS = "#5EFFC8";
    private static final String C_NAME = "#C9B6E8";

    private static final Map<String, String> RARITY_HEX = Map.of(
        "COMMON", "#9BA0AA",
        "UNCOMMON", "#4AE371",
        "RARE", "#5E9BFF",
        "MYTHIC", "#C76CFF",
        "LEGENDARY", "#FFAA00");

    private JewelryTooltipText() {}

    @Nonnull
    public static String rarityColorHex(@Nonnull String rarityKey) {
        return RARITY_HEX.getOrDefault(rarityKey, RARITY_HEX.get("COMMON"));
    }

    @Nonnull
    public static List<String> dynamicDescriptionLines(@Nullable ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
            return out;
        }
        if (!JewelryMetadata.hasJewelryMeta(stack)) {
            return out;
        }
        JewelryRarity rarity = JewelryMetadata.readRarity(stack);
        String key = rarity != null ? rarity.wireName() : "COMMON";
        String rarityName = enRarityName(key);
        String rh = RARITY_HEX.getOrDefault(key, RARITY_HEX.get("COMMON"));
        out.add(c(C_LABEL) + "Rarity: " + cEnd() + c(rh) + rarityName + cEnd());
        if (!JewelryMetadata.isAppraised(stack)) {
            out.add(
                c(C_DIM)
                    + "Appraisal only reveals names on ledgers — the enchantment is already in the metal."
                    + cEnd());
            var traits = JewelryMetadata.readTraits(stack);
            int n = !traits.isEmpty() ? traits.size() : (rarity != null ? rarity.traitCount() : 1);
            for (int i = 0; i < n; i++) {
                out.add(c(C_DIM) + "• ?" + cEnd());
            }
            return out;
        }
        for (JewelryMetadata.RolledTrait rt : JewelryMetadata.readTraits(stack)) {
            String label = enStatName(rt.statId());
            String amt = formatForDisplay(rt.statId(), rt.amount());
            String signed = (rt.amount() < 0f) ? amt : ("+" + amt);
            out.add(
                c(C_NAME) + label + cEnd() + "  " + c(C_BONUS) + signed + cEnd());
        }
        return out;
    }

    private static String enRarityName(String k) {
        return switch (k) {
            case "COMMON" -> "Common";
            case "UNCOMMON" -> "Uncommon";
            case "RARE" -> "Rare";
            case "MYTHIC" -> "Mythic";
            case "LEGENDARY" -> "Legendary";
            default -> "Common";
        };
    }

    private static String enStatName(String statId) {
        return switch (statId) {
            case "Health" -> "Max health";
            case "Mana" -> "Max mana";
            case "Stamina" -> "Max stamina";
            case "Oxygen" -> "Max oxygen";
            case "Ammo" -> "Max ammo";
            case "SignatureEnergy" -> "Signature max";
            default -> statId;
        };
    }

    @Nonnull
    private static String c(String hex) {
        return "<color is=\"" + hex + "\">";
    }

    @Nonnull
    private static String cEnd() {
        return "</color>";
    }
}
