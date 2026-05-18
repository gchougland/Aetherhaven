package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Bonus loot when players break vanilla soft containers (crates, barrels, pots, coffins). */
public final class BreakableContainersConfig {
    public static final BuilderCodec<BreakableContainersConfig> CODEC =
        BuilderCodec.builder(BreakableContainersConfig.class, BreakableContainersConfig::new)
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Explains this section. Safe to delete or replace.")
            .add()
            .append(
                new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                (o, v) -> o.enabled = v == null || v,
                o -> o.enabled
            )
            .documentation("When false, no bonus gold from breaking eligible containers.")
            .add()
            .append(
                new KeyedCodec<>("ApplyInCreative", Codec.BOOLEAN),
                (o, v) -> o.applyInCreative = v != null && v,
                o -> o.applyInCreative
            )
            .documentation("When true, container coin rolls also run in Creative. Default false.")
            .add()
            .append(
                new KeyedCodec<>("Gold", BreakableContainersGoldConfig.CODEC),
                (o, v) -> o.gold = v != null ? v : new BreakableContainersGoldConfig(),
                o -> o.gold
            )
            .add()
            .build();

    @Nullable
    private String note;
    private boolean enabled = true;
    private boolean applyInCreative = false;
    @Nonnull
    private BreakableContainersGoldConfig gold = new BreakableContainersGoldConfig();

    public BreakableContainersConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "Runtime bonus gold when players smash eligible world crates, barrels, pots, sacks, and coffins."
                + " Does not edit vanilla droplist JSON.";
    }

    @Nullable
    public String getNote() {
        return note;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isApplyInCreative() {
        return applyInCreative;
    }

    @Nonnull
    public BreakableContainersGoldConfig getGold() {
        return gold != null ? gold : new BreakableContainersGoldConfig();
    }
}
