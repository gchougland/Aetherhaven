package com.hexvane.aetherhaven.hud;

import com.hypixel.hytale.server.core.Message;
import java.util.Objects;
import javax.annotation.Nonnull;

/** One read-only quest row ready for display by the HUD. */
public record HudQuestEntry(
    @Nonnull Source source,
    @Nonnull String id,
    @Nonnull Message title,
    @Nonnull Message objectives
) {
    public enum Source {
        STORY,
        QUEST_BOARD
    }

    /** Stable comparison value used to avoid sending unchanged quest UI commands. */
    @Nonnull
    public String fingerprint() {
        return source.name()
            + '\u0000'
            + id
            + '\u0000'
            + Objects.toString(title.getAnsiMessage(), "")
            + '\u0000'
            + Objects.toString(objectives.getAnsiMessage(), "");
    }
}
