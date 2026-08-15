package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Saves an authored wall style: one prefab and one building file per piece, all sharing a style id so the wand and the
 * crafting bench can treat them as a single wall. Connection points are written prefab local, measured from the piece
 * anchor, because that is what {@link com.hexvane.aetherhaven.wall.WallStyleCatalog} expects.
 */
public final class PlotCreatorWallStyleWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Walls are placed with the wand, so they build fast and never charge the treasury. */
    private static final double SELF_BUILD_GAME_DAYS = 0.25;

    /** One saved piece, so the caller can reload catalogs, generate materials, and submit each building. */
    public record SavedPiece(
        @Nonnull WallPieceRole role,
        @Nonnull String constructionId,
        @Nonnull String prefabPathKey,
        @Nonnull Path writeRoot
    ) {}

    private PlotCreatorWallStyleWriter() {}

    /**
     * Writes every piece of the style.
     *
     * <p>Each piece goes to the root {@link BuildingEditorSavePaths} picks for its own id, so re-saving a style that
     * lives in a writable asset pack (the Gradle {@code build/resources/main} folder while {@code runServer} is up)
     * lands back in that pack and gets synced into the source tree, exactly like buildings and festivals.
     *
     * @param baseId id all pieces hang off, and the style id itself
     * @param forceDataDirectory true when the style is on its way to the marketplace, which reads from the data folder
     * @return the saved pieces, or null when a prefab could not be exported
     */
    @Nullable
    public static List<SavedPiece> write(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull String baseId,
        boolean forceDataDirectory
    ) throws IOException {
        List<SavedPiece> saved = new ArrayList<>();
        String styleId = PlotCreatorWallStyleIds.styleIdForBase(baseId);
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            String pieceId = PlotCreatorWallStyleIds.pieceConstructionId(draft, piece, baseId);
            String prefabKey = PlotCreatorWallStyleIds.piecePrefabPathKey(draft, piece, baseId);
            Vector3i anchor = piece.getAnchor();
            if (!piece.hasBounds() || anchor == null) {
                return null;
            }
            Path writeRoot =
                forceDataDirectory
                    ? plugin.getDataDirectory().toAbsolutePath().normalize()
                    : BuildingEditorSavePaths.resolveWriteRoot(plugin, pieceId);
            PlotCreatorDraft pieceDraft = pieceExportDraft(draft, piece, pieceId, prefabKey);
            Path prefabOut = BuildingEditorSavePaths.prefabFile(writeRoot, prefabKey);
            PlotCreatorPrefabExporter.ExportResult result =
                PlotCreatorPrefabExporter.export(world, pieceDraft, prefabOut, true);
            if (result != PlotCreatorPrefabExporter.ExportResult.SUCCESS) {
                return null;
            }
            writeBuildingFile(
                BuildingEditorSavePaths.buildingFile(writeRoot, pieceId),
                draft,
                piece,
                pieceId,
                prefabKey,
                styleId,
                anchor
            );
            saved.add(new SavedPiece(piece.getRole(), pieceId, prefabKey, writeRoot));
        }
        return saved;
    }

    /** A stand-in draft holding just what the prefab exporter reads, so the wizard draft is never mutated. */
    @Nonnull
    private static PlotCreatorDraft pieceExportDraft(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotCreatorWallPieceDraft piece,
        @Nonnull String pieceId,
        @Nonnull String prefabKey
    ) {
        PlotCreatorDraft out = new PlotCreatorDraft();
        out.setConstructionId(pieceId);
        out.setDisplayName(pieceDisplayName(draft, piece.getRole()));
        out.setPrefabPath(prefabKey);
        out.setPrefabFileName(prefabKey);
        out.setCornerFirst(piece.getCornerFirst());
        out.setCornerSecond(piece.getCornerSecond());
        out.setPlotAnchor(piece.getAnchor());
        out.setSaveEmptySpaces(draft.isSaveEmptySpaces());
        return out;
    }

    private static void writeBuildingFile(
        @Nonnull Path outputFile,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotCreatorWallPieceDraft piece,
        @Nonnull String pieceId,
        @Nonnull String prefabKey,
        @Nonnull String styleId,
        @Nonnull Vector3i anchor
    ) throws IOException {
        Map<String, Object> root = buildingMap(draft, piece, pieceId, prefabKey, styleId, anchor);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    /** The building file contents for one piece. */
    @Nonnull
    static Map<String, Object> buildingMap(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotCreatorWallPieceDraft piece,
        @Nonnull String pieceId,
        @Nonnull String prefabKey,
        @Nonnull String styleId,
        @Nonnull Vector3i anchor
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", pieceId);
        root.put("displayName", pieceDisplayName(draft, piece.getRole()));
        String description = draft.getDescription();
        if (description != null && !description.isBlank()) {
            root.put("description", description.trim());
        }
        root.put("prefabPath", prefabKey);
        root.put("plotTokenItemId", AetherhavenConstants.PLOT_TOKEN_UNIFIED);
        root.put("plotAnchorOffset", anchorOffsetFor(piece));
        root.put("rotationYaw", "None");
        root.put("selfBuildGameDays", SELF_BUILD_GAME_DAYS);
        root.put("consumesPlotToken", false);
        root.put("excludeFromTownJournal", true);
        root.put("wallSegment", true);
        root.put("styleId", styleId);
        if (!piece.getMaterials().isEmpty()) {
            root.put("materials", PlotCreatorJsonWriter.materialMaps(piece.getMaterials()));
        }
        if (!draft.getBuildingTags().isEmpty()) {
            root.put("tags", new ArrayList<>(draft.getBuildingTags()));
        }
        root.put("wallPiece", wallPieceMap(piece, anchor));
        return root;
    }

    /**
     * A freshly authored piece is relativized at the plot sign cell the player picked, so its offset undoes the sign
     * lift or the piece would paste a block too low. A piece loaded from an existing wall keeps the offset it already
     * had, so re-saving it never moves the wall up or down.
     */
    @Nonnull
    private static List<Integer> anchorOffsetFor(@Nonnull PlotCreatorWallPieceDraft piece) {
        int[] existing = piece.getPlotAnchorOffset();
        if (existing != null) {
            return List.of(existing[0], existing[1], existing[2]);
        }
        return List.of(0, AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR, 0);
    }

    @Nonnull
    private static Map<String, Object> wallPieceMap(
        @Nonnull PlotCreatorWallPieceDraft piece,
        @Nonnull Vector3i anchor
    ) {
        Vector3i min = piece.boundsMin();
        Vector3i max = piece.boundsMax();
        Map<String, Object> bounds = new LinkedHashMap<>();
        bounds.put("min", List.of(min.x - anchor.x, min.y - anchor.y, min.z - anchor.z));
        bounds.put("max", List.of(max.x - anchor.x, max.y - anchor.y, max.z - anchor.z));
        List<Map<String, Object>> connections = new ArrayList<>();
        for (PlotCreatorWallPieceDraft.Connection c : piece.getConnections()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("face", c.face().name().toLowerCase(java.util.Locale.ROOT));
            row.put("local", List.of(c.worldCell().x - anchor.x, c.worldCell().z - anchor.z));
            connections.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", piece.getRole().serialized());
        out.put("boundsLocal", bounds);
        out.put("connections", connections);
        return out;
    }

    /** "Mossy wall" for the straight run, "Mossy wall corner tower" and so on for the rest. */
    @Nonnull
    public static String pieceDisplayName(@Nonnull PlotCreatorDraft draft, @Nonnull WallPieceRole role) {
        String base = draft.getDisplayName() != null && !draft.getDisplayName().isBlank()
            ? draft.getDisplayName().trim()
            : "Wall";
        return switch (role) {
            case SEGMENT -> base;
            case GATE -> base + " gate";
            case TOWER_END -> base + " end tower";
            case TOWER_STRAIGHT -> base + " tower";
            case TOWER_CORNER -> base + " corner tower";
        };
    }
}
