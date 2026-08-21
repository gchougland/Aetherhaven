package com.hexvane.aetherhaven.plotcreator.icon;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/** Writes a plot creator building thumbnail PNG under the plugin data {@code Common/} tree. */
public final class PlotCreatorIconExporter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PlotCreatorIconExporter() {}

    public static boolean tryExportIcon(
        @Nonnull BlockSelection prefab,
        @Nullable String constructionId,
        @Nonnull Path dataDirectory
    ) {
        return tryExportIcon(prefab, constructionId, dataDirectory, null);
    }

    public static boolean tryExportIcon(
        @Nonnull BlockSelection prefab,
        @Nullable String constructionId,
        @Nonnull Path dataDirectory,
        @Nullable String frontFacing
    ) {
        if (constructionId == null || constructionId.isBlank()) {
            LOGGER.atWarning().log("Plot creator icon export: missing construction id");
            return false;
        }
        try {
            BufferedImage image = PrefabIsometricIconRenderer.render(prefab, frontFacing);
            if (image == null) {
                LOGGER.atWarning().log("Plot creator icon export: no renderable blocks for %s", constructionId);
                return false;
            }
            Path out = CustomBuildingsPaths.iconFile(dataDirectory, constructionId.trim());
            Files.createDirectories(out.getParent());
            ImageIO.write(image, "png", out.toFile());
            LOGGER.atInfo().log("Plot creator icon saved: %s", out);
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin != null) {
                CustomBuildingIconAssetRegistry.registerIconFile(plugin, out);
            }
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Plot creator icon export failed for %s", constructionId);
            return false;
        }
    }
}
