package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import java.util.List;
import javax.annotation.Nonnull;

/** Mode-specific HUD control rows for the patrol wand. */
public final class PatrolWandHudControls {
    public record Row(@Nonnull ToolKeybindSlot slot, @Nonnull String descriptionLangKey, boolean infoOnly) {}

    private PatrolWandHudControls() {}

    @Nonnull
    public static List<Row> rowsFor(@Nonnull PatrolWandMode mode) {
        return switch (mode) {
            case Build -> List.of(
                row(ToolKeybindSlot.SECONDARY, "aetherhaven.patrolWand.hud.build.secondary"),
                row(ToolKeybindSlot.PRIMARY, "aetherhaven.patrolWand.hud.build.primary"),
                row(ToolKeybindSlot.USE, "aetherhaven.patrolWand.hud.build.f"),
                row(ToolKeybindSlot.ABILITY3, "aetherhaven.patrolWand.hud.build.r"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.patrolWand.hud.build.e"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.patrolWand.hud.build.q")
            );
            case Assign -> List.of(
                row(ToolKeybindSlot.PRIMARY, "aetherhaven.patrolWand.hud.assign.primary"),
                row(ToolKeybindSlot.USE, "aetherhaven.patrolWand.hud.assign.f"),
                row(ToolKeybindSlot.ABILITY2, "aetherhaven.patrolWand.hud.assign.e"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.patrolWand.hud.assign.q")
            );
            case Remove -> List.of(
                row(ToolKeybindSlot.PRIMARY, "aetherhaven.patrolWand.hud.remove.primary"),
                row(ToolKeybindSlot.USE, "aetherhaven.patrolWand.hud.remove.f"),
                row(ToolKeybindSlot.ABILITY1, "aetherhaven.patrolWand.hud.remove.q")
            );
        };
    }

    @Nonnull
    private static Row row(@Nonnull ToolKeybindSlot slot, @Nonnull String langKey) {
        return new Row(slot, langKey, false);
    }
}
