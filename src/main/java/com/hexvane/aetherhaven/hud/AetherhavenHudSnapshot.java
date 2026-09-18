package com.hexvane.aetherhaven.hud;

import com.hexvane.aetherhaven.economy.api.Balance;
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
    @Nonnull Balance gold,
    @Nonnull List<HudQuestEntry> quests
) {
    public AetherhavenHudSnapshot {
        quests = List.copyOf(quests);
    }
}
