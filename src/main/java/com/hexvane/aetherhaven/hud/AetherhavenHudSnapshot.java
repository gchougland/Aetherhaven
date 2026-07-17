package com.hexvane.aetherhaven.hud;

import java.util.List;
import javax.annotation.Nonnull;

/** Immutable values consumed by {@link AetherhavenHud}. */
public record AetherhavenHudSnapshot(
    boolean showTime,
    boolean showDate,
    boolean showGold,
    boolean showQuests,
    float backgroundOpacity,
    @Nonnull String dateText,
    @Nonnull String clockText,
    long inventoryCoins,
    long treasuryCoins,
    long totalCoins,
    @Nonnull List<HudQuestEntry> quests
) {
    public AetherhavenHudSnapshot {
        quests = List.copyOf(quests);
    }
}
