package com.hexvane.aetherhaven.feast;

/** Which long-term or one-shot effect a feast applies. */
public enum FeastEffectKind {
    /** Higher treasury tax from residents for 7 dawn-days. */
    STEWARDS_TAX,
    /** Slower villager needs decay for 7 dawn-days. */
    HEARTHGLASS_DECAY,
    /** Instant +10 rep with each resident; 7 dawn-day cooldown, no timed buff. */
    BERRYCIRCLE_REP
}
