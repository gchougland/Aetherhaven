package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gold coin bonus when players break eligible world containers. JSON path: {@code BreakableContainers.Gold.*} */
public final class BreakableContainersGoldConfig {
    public static final BuilderCodec<BreakableContainersGoldConfig> CODEC =
        BuilderCodec.builder(BreakableContainersGoldConfig.class, BreakableContainersGoldConfig::new)
            .append(
                new KeyedCodec<>("ItemId", Codec.STRING),
                (o, v) -> o.itemId = v != null ? v : "Aetherhaven_Gold_Coin",
                o -> o.itemId
            )
            .documentation("Registered item id for bonus coin drops.")
            .add()
            .append(
                new KeyedCodec<>("WeightNone", Codec.INTEGER),
                (o, v) -> o.weightNone = v != null ? v : 70,
                o -> o.weightNone
            )
            .documentation("Relative weight for dropping zero coins (default 70).")
            .add()
            .append(
                new KeyedCodec<>("WeightOne", Codec.INTEGER),
                (o, v) -> o.weightOne = v != null ? v : 20,
                o -> o.weightOne
            )
            .documentation("Relative weight for dropping one coin (default 20).")
            .add()
            .append(
                new KeyedCodec<>("WeightTwo", Codec.INTEGER),
                (o, v) -> o.weightTwo = v != null ? v : 10,
                o -> o.weightTwo
            )
            .documentation("Relative weight for dropping two coins (default 10).")
            .add()
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Human-readable; safe to clear.")
            .add()
            .build();

    @Nonnull
    private String itemId = "Aetherhaven_Gold_Coin";
    private int weightNone = 70;
    private int weightOne = 20;
    private int weightTwo = 10;
    @Nullable
    private String note;

    public BreakableContainersGoldConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "One weighted roll among zero, one, or two coins when a player breaks an eligible crate, barrel, pot, sack, or coffin."
                + " Higher WeightNone means fewer coin drops overall.";
    }

    @Nonnull
    public String getItemId() {
        if (itemId == null || itemId.isBlank()) {
            return "Aetherhaven_Gold_Coin";
        }
        return itemId.trim();
    }

    public int getWeightNone() {
        return Math.max(0, weightNone);
    }

    public int getWeightOne() {
        return Math.max(0, weightOne);
    }

    public int getWeightTwo() {
        return Math.max(0, weightTwo);
    }

    @Nullable
    public String getNote() {
        return note;
    }

    /**
     * Weighted pick: 0, 1, or 2 coins. Uses the same relative-weight algorithm as vanilla Choice droplists.
     */
    public int rollQuantity(@Nonnull ThreadLocalRandom rnd) {
        int w0 = getWeightNone();
        int w1 = getWeightOne();
        int w2 = getWeightTwo();
        long sum = (long) w0 + w1 + w2;
        if (sum <= 0L) {
            return 0;
        }
        double r = rnd.nextDouble() * sum;
        if (r < w0) {
            return 0;
        }
        r -= w0;
        if (r < w1) {
            return 1;
        }
        return 2;
    }

    public boolean isItemRegistered() {
        return Item.getAssetMap().getAsset(getItemId()) != null;
    }

    /** Town Journal: clamp weights to non-negative sane values. */
    public void applyJournalTuning(int weightNone, int weightOne, int weightTwo) {
        this.weightNone = clampWeight(weightNone);
        this.weightOne = clampWeight(weightOne);
        this.weightTwo = clampWeight(weightTwo);
    }

    private static int clampWeight(int w) {
        if (w < 0) {
            return 0;
        }
        return Math.min(w, 10_000);
    }
}
