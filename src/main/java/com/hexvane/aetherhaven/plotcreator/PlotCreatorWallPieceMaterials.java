package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialsService;
import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Build costs for a piece of a wall style. A piece being authored has no saved prefab yet, so the blocks inside its box
 * go to a throwaway prefab that is read back through the same counter every other build cost uses. That keeps the
 * numbers identical to what the piece ends up costing once the style is saved.
 */
public final class PlotCreatorWallPieceMaterials {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PlotCreatorWallPieceMaterials() {}

    /** Suggests a cost for the piece the player just arrived at, leaving anything they already set alone. */
    public static void autoFillIfEmpty(@Nonnull PlotCreatorSession session) {
        PlotCreatorDraft draft = session.getDraft();
        if (!draft.getMaterials().isEmpty()) {
            return;
        }
        session.getWorld().execute(() -> {
            if (!draft.getMaterials().isEmpty() || !PlotCreatorWallPieceAuthoring.isMaterialsSubstep(draft)) {
                return;
            }
            List<MaterialRequirement> suggested = generate(session, true);
            if (suggested.isEmpty()) {
                return;
            }
            PlotCreatorMaterialsHelper.applyGeneratedMaterials(session, suggested, true);
            PlotCreatorWallPieceAuthoring.commitMaterialsToCurrentPiece(draft);
        });
    }

    /**
     * Counts up what the current piece is built from.
     *
     * @param suggested true for resource types such as any wood, false for the exact blocks
     * @return an empty list when the piece has no box yet or its blocks could not be read
     */
    @Nonnull
    public static List<MaterialRequirement> generate(@Nonnull PlotCreatorSession session, boolean suggested) {
        PlotCreatorWallPieceDraft piece = session.getDraft().currentWallPiece();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (piece == null || plugin == null || !piece.hasBounds() || piece.getAnchor() == null) {
            return List.of();
        }
        Path folder = null;
        try {
            // A whole folder, because saving a prefab leaves a backup copy next to the file it writes.
            folder = Files.createTempDirectory("aetherhaven_wall_piece_cost_");
            Path prefabFile = folder.resolve("piece.prefab.json");
            PlotCreatorPrefabExporter.ExportResult result =
                PlotCreatorPrefabExporter.export(session.getWorld(), exportDraft(piece), prefabFile, true, false);
            if (result != PlotCreatorPrefabExporter.ExportResult.SUCCESS) {
                return List.of();
            }
            PrefabMaterialsService service = plugin.getPrefabMaterialsService();
            return suggested
                ? service.generateSuggestedResourcesFromPrefabFile(prefabFile)
                : service.generateFromPrefabFile(prefabFile);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read wall piece blocks for build costs");
            return List.of();
        } finally {
            deleteQuietly(folder);
        }
    }

    private static void deleteQuietly(@Nullable Path folder) {
        if (folder == null) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(folder);
        } catch (Exception ignored) {
            // Leftovers in the system temp folder are harmless.
        }
    }

    /** A stand-in draft holding just the box the exporter reads, so the wizard draft is never touched. */
    @Nonnull
    private static PlotCreatorDraft exportDraft(@Nonnull PlotCreatorWallPieceDraft piece) {
        PlotCreatorDraft out = new PlotCreatorDraft();
        out.setCornerFirst(piece.getCornerFirst());
        out.setCornerSecond(piece.getCornerSecond());
        out.setPlotAnchor(piece.getAnchor());
        return out;
    }
}
