package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Per-stat roll magnitudes and display rounding. Ranges for each stat are read from
 * {@link AetherhavenPluginConfig} (interpolated by {@link JewelryRarity} from common to legendary
 * bounds). See {@link JewelryRolling#config()}.
 */
public final class JewelryStatTuning {
    private JewelryStatTuning() {}

    public static float rollMagnitudeFor(
        @Nonnull String statId,
        @Nonnull JewelryRarity rarity,
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg) {
        float t = rarity.ordinal() / 4.0f;
        float cMin;
        float cMax;
        float lMin;
        float lMax;
        switch (statId) {
            case "Health" -> {
                cMin = (float) cfg.getJewelryStatHealthCommonMin();
                cMax = (float) cfg.getJewelryStatHealthCommonMax();
                lMin = (float) cfg.getJewelryStatHealthLegendaryMin();
                lMax = (float) cfg.getJewelryStatHealthLegendaryMax();
            }
            case "Stamina" -> {
                cMin = (float) cfg.getJewelryStatStaminaCommonMin();
                cMax = (float) cfg.getJewelryStatStaminaCommonMax();
                lMin = (float) cfg.getJewelryStatStaminaLegendaryMin();
                lMax = (float) cfg.getJewelryStatStaminaLegendaryMax();
            }
            case "Ammo" -> {
                cMin = (float) cfg.getJewelryStatAmmoCommonMin();
                cMax = (float) cfg.getJewelryStatAmmoCommonMax();
                lMin = (float) cfg.getJewelryStatAmmoLegendaryMin();
                lMax = (float) cfg.getJewelryStatAmmoLegendaryMax();
            }
            case "Mana" -> {
                cMin = (float) cfg.getJewelryStatManaCommonMin();
                cMax = (float) cfg.getJewelryStatManaCommonMax();
                lMin = (float) cfg.getJewelryStatManaLegendaryMin();
                lMax = (float) cfg.getJewelryStatManaLegendaryMax();
            }
            case "Oxygen" -> {
                cMin = (float) cfg.getJewelryStatOxygenCommonMin();
                cMax = (float) cfg.getJewelryStatOxygenCommonMax();
                lMin = (float) cfg.getJewelryStatOxygenLegendaryMin();
                lMax = (float) cfg.getJewelryStatOxygenLegendaryMax();
            }
            case "SignatureEnergy" -> {
                cMin = (float) cfg.getJewelryStatSignatureEnergyCommonMin();
                cMax = (float) cfg.getJewelryStatSignatureEnergyCommonMax();
                lMin = (float) cfg.getJewelryStatSignatureEnergyLegendaryMin();
                lMax = (float) cfg.getJewelryStatSignatureEnergyLegendaryMax();
            }
            default -> {
                cMin = 1.0f;
                cMax = 2.0f;
                lMin = 2.0f;
                lMax = 4.0f;
            }
        }
        float low = cMin + t * (lMin - cMin);
        float high = cMax + t * (lMax - cMax);
        if (low > high) {
            float x = low;
            low = high;
            high = x;
        }
        float raw = low + (high > low ? rnd.nextFloat() * (high - low) : 0.0f);
        return roundForStorage(statId, raw);
    }

    public static float roundForStorage(@Nonnull String statId, float raw) {
        if ("Stamina".equals(statId)) {
            float a = Math.round(raw * 10f) / 10f;
            return Math.max(0.4f, a);
        }
        if ("SignatureEnergy".equals(statId)) {
            float a = Math.round(raw);
            if (a > -1f) {
                a = -1f;
            }
            if (a < -10f) {
                a = -10f;
            }
            return a;
        }
        float a = Math.round(raw);
        return switch (statId) {
            case "Health" -> Math.max(2f, a);
            case "Mana" -> Math.max(2f, a);
            case "Oxygen" -> Math.max(10f, a);
            case "Ammo" -> Math.max(1f, a);
            default -> Math.max(0.5f, a);
        };
    }

    @Nonnull
    public static String formatForDisplay(@Nonnull String statId, float amount) {
        float a = roundForStorage(statId, amount);
        if ("Stamina".equals(statId)) {
            if (Math.abs(a - Math.rint(a)) < 0.001f) {
                return String.valueOf((int) Math.rint(a));
            }
            return String.format(Locale.US, "%.1f", a);
        }
        return String.valueOf(Math.round(a));
    }
}
