package com.hexvane.aetherhaven.patrol;

import java.util.List;
import javax.annotation.Nonnull;

/** Mode-specific HUD control rows for the patrol wand. */
public final class PatrolWandHudControls {
    public record Row(@Nonnull String keyLabel, @Nonnull String descriptionLangKey, boolean infoOnly) {}

    private PatrolWandHudControls() {}

    @Nonnull
    public static List<Row> rowsFor(@Nonnull PatrolWandMode mode) {
        return switch (mode) {
            case Build -> List.of(
                row("Secondary", "aetherhaven.patrolWand.hud.build.secondary"),
                row("Primary", "aetherhaven.patrolWand.hud.build.primary"),
                row("F", "aetherhaven.patrolWand.hud.build.f"),
                row("R", "aetherhaven.patrolWand.hud.build.r"),
                row("E", "aetherhaven.patrolWand.hud.build.e"),
                row("Q", "aetherhaven.patrolWand.hud.build.q")
            );
            case Assign -> List.of(
                row("Primary", "aetherhaven.patrolWand.hud.assign.primary"),
                row("F", "aetherhaven.patrolWand.hud.assign.f"),
                row("E", "aetherhaven.patrolWand.hud.assign.e"),
                row("Q", "aetherhaven.patrolWand.hud.assign.q")
            );
        };
    }

    @Nonnull
    private static Row row(@Nonnull String key, @Nonnull String langKey) {
        return new Row(key, langKey, false);
    }
}
