package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.difficulty.DifficultyResolver;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Blocks building staff use when town (or forced server) difficulty disables it. */
public final class BuildingStaffDifficultyGate {
    private BuildingStaffDifficultyGate() {}

    public static boolean isDisabled(@Nullable TownRecord town) {
        return DifficultyResolver.effectiveForTown(town).isBuildingStaffDisabled();
    }

    public static boolean failIfDisabled(@Nullable TownRecord town, @Nullable PlayerRef playerRef) {
        if (!isDisabled(town)) {
            return false;
        }
        if (playerRef != null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_difficulty.aetherhaven.difficulty.buildingStaffDisabledMsg")
            );
        }
        return true;
    }
}
