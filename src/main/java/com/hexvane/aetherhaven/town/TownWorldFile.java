package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Gson root for {@code towns.json} per world. */
public final class TownWorldFile {
    private static final Gson COMPACT_GSON = new GsonBuilder().create();

    @com.google.gson.annotations.SerializedName("towns")
    private List<TownRecord> towns = new ArrayList<>();

    @Nonnull
    public List<TownRecord> getTowns() {
        if (towns == null) {
            towns = new ArrayList<>();
        }
        return towns;
    }

    public static TownWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new TownWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            TownWorldFile f = COMPACT_GSON.fromJson(r, TownWorldFile.class);
            return f != null ? f : new TownWorldFile();
        }
    }

    @Nonnull
    public static byte[] toJsonBytes(@Nonnull List<TownRecord> towns) {
        TownWorldFile file = new TownWorldFile();
        file.getTowns().addAll(towns);
        return COMPACT_GSON.toJson(file).getBytes(StandardCharsets.UTF_8);
    }

    public void writeAtomic(@Nonnull Path path) throws IOException {
        writeBytesAtomic(path, toJsonBytes(getTowns()));
    }

    public static void writeBytesAtomic(@Nonnull Path path, @Nonnull byte[] jsonUtf8) throws IOException {
        Path dir = path.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        if (Files.isRegularFile(path)) {
            Path bak = path.resolveSibling("towns.json.bak");
            Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(tmp, jsonUtf8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            replaceDestinationFromTemp(tmp, path);
        } catch (IOException e) {
            if (isReplaceBlockedOnWindows(e)) {
                replaceDestinationFromTemp(tmp, path);
            } else {
                throw e;
            }
        }
    }

    /**
     * Windows / synced folders often reject {@link Files#move} into an existing file; copy-over is more reliable.
     */
    private static void replaceDestinationFromTemp(@Nonnull Path tmp, @Nonnull Path path) throws IOException {
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailed) {
            if (!isReplaceBlockedOnWindows(moveFailed)) {
                throw moveFailed;
            }
            Files.copy(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tmp);
        }
    }

    private static boolean isReplaceBlockedOnWindows(@Nonnull IOException e) {
        return e instanceof java.nio.file.AccessDeniedException
            || (e.getCause() instanceof java.nio.file.AccessDeniedException);
    }
}
