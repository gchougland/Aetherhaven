package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.server.core.Message;
import javax.annotation.Nonnull;

public final class PlotReconstructMessages {
    private PlotReconstructMessages() {}

    @Nonnull
    public static Message forResult(@Nonnull PlotReconstructService.ReconstructResult result) {
        return switch (result) {
            case OK -> Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructOk");
            case CHUNKS_UNLOADED ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructChunks");
            case UNKNOWN_CONSTRUCTION ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructUnknown");
            case PREFAB_MISSING ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructPrefab");
            case WALL_OR_DECORATION ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructWall");
            case ANCHOR_UNKNOWN ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructAnchor");
            case PREFAB_FAILED ->
                Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.reconstructFailed");
        };
    }
}
