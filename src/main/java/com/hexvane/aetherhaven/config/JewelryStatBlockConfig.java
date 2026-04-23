package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-stat min/max for common and legendary quality bands. JSON: {@code Jewelry.Stat.<StatName>.Common.Min} etc.
 * Uncommon/Rare/Mythic interpolate between common and legendary bounds in {@link com.hexvane.aetherhaven.jewelry.JewelryStatTuning}.
 */
public final class JewelryStatBlockConfig {
    public static final BuilderCodec<JewelryStatBlockConfig> CODEC =
        BuilderCodec.builder(JewelryStatBlockConfig.class, JewelryStatBlockConfig::new)
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Explains interpolation. Safe to delete.")
            .add()
            .append(
                new KeyedCodec<>("Health", StatRarityPairConfig.CODEC),
                (o, v) -> o.health = v != null ? v : defaultHealth(),
                o -> o.health
            )
            .add()
            .append(
                new KeyedCodec<>("Stamina", StatRarityPairConfig.CODEC),
                (o, v) -> o.stamina = v != null ? v : defaultStamina(),
                o -> o.stamina
            )
            .add()
            .append(
                new KeyedCodec<>("Ammo", StatRarityPairConfig.CODEC),
                (o, v) -> o.ammo = v != null ? v : defaultAmmo(),
                o -> o.ammo
            )
            .add()
            .append(
                new KeyedCodec<>("Mana", StatRarityPairConfig.CODEC),
                (o, v) -> o.mana = v != null ? v : defaultMana(),
                o -> o.mana
            )
            .add()
            .append(
                new KeyedCodec<>("Oxygen", StatRarityPairConfig.CODEC),
                (o, v) -> o.oxygen = v != null ? v : defaultOxygen(),
                o -> o.oxygen
            )
            .add()
            .append(
                new KeyedCodec<>("SignatureEnergy", StatRarityPairConfig.CODEC),
                (o, v) -> o.signatureEnergy = v != null ? v : defaultSignature(),
                o -> o.signatureEnergy
            )
            .add()
            .build();

    @Nullable
    private String note;
    /**
     * Non-null defaults so first-time config save (and {@code "Stat": {}} merge) still round-trip a full, editable
     * structure in {@code config.json} instead of an empty object.
     */
    @Nonnull
    private StatRarityPairConfig health = defaultHealth();
    @Nonnull
    private StatRarityPairConfig stamina = defaultStamina();
    @Nonnull
    private StatRarityPairConfig ammo = defaultAmmo();
    @Nonnull
    private StatRarityPairConfig mana = defaultMana();
    @Nonnull
    private StatRarityPairConfig oxygen = defaultOxygen();
    @Nonnull
    private StatRarityPairConfig signatureEnergy = defaultSignature();

    public JewelryStatBlockConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "For each stat, 'Common' and 'Legendary' set the ends of a band; rarities in the middle (Uncommon, Rare, Mythic)"
                + " interpolate the low/high window before a uniform roll."
                + " See mod javadoc: vanilla base max is Health 100, Stamina 10, Oxygen 100; Mana/Signature in base data are often 0/0"
                + " and scale with gear. Signature uses negative rolls (smaller max pool) so the bar is faster to fill"
                + " in absolute energy; see Defaults for Oxygen (+10 to +100 band) and Signature."
                + " Example path: Jewelry.Stat.Mana.Common.Min";
    }

    @Nonnull
    private static StatRarityPairConfig defaultHealth() {
        return new StatRarityPairConfig(
            new StatMinMaxConfig(5.0, 16.0),
            new StatMinMaxConfig(32.0, 50.0)
        );
    }

    @Nonnull
    private static StatRarityPairConfig defaultStamina() {
        return new StatRarityPairConfig(
            new StatMinMaxConfig(1.0, 5.0),
            new StatMinMaxConfig(12.0, 20.0)
        );
    }

    @Nonnull
    private static StatRarityPairConfig defaultAmmo() {
        return new StatRarityPairConfig(
            new StatMinMaxConfig(1.0, 2.0),
            new StatMinMaxConfig(3.0, 5.0)
        );
    }

    @Nonnull
    private static StatRarityPairConfig defaultMana() {
        return new StatRarityPairConfig(
            new StatMinMaxConfig(5.0, 16.0),
            new StatMinMaxConfig(32.0, 50.0)
        );
    }

    @Nonnull
    private static StatRarityPairConfig defaultOxygen() {
        return new StatRarityPairConfig(
            new StatMinMaxConfig(10.0, 30.0),
            new StatMinMaxConfig(50.0, 100.0)
        );
    }

    /** Additive to max; negative means a smaller bar (less energy to 100% at fixed gain per hit). */
    @Nonnull
    private static StatRarityPairConfig defaultSignature() {
        return new StatRarityPairConfig(
            new StatMinMaxConfig(-2.0, -1.0),
            new StatMinMaxConfig(-10.0, -6.0)
        );
    }

    @Nullable
    public String getNote() {
        return note;
    }

    @Nonnull
    public StatRarityPairConfig getHealth() {
        return health;
    }

    @Nonnull
    public StatRarityPairConfig getStamina() {
        return stamina;
    }

    @Nonnull
    public StatRarityPairConfig getAmmo() {
        return ammo;
    }

    @Nonnull
    public StatRarityPairConfig getMana() {
        return mana;
    }

    @Nonnull
    public StatRarityPairConfig getOxygen() {
        return oxygen;
    }

    @Nonnull
    public StatRarityPairConfig getSignatureEnergy() {
        return signatureEnergy;
    }
}
