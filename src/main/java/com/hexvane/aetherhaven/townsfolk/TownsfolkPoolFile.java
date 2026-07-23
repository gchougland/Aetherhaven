package com.hexvane.aetherhaven.townsfolk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

public final class TownsfolkPoolFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @com.google.gson.annotations.SerializedName("checkouts")
    private List<TownsfolkPoolCheckoutRecord> checkouts = new ArrayList<>();

    @Nonnull
    public Map<String, TownsfolkPoolCheckoutRecord> checkoutsByLedgerKey() {
        Map<String, TownsfolkPoolCheckoutRecord> map = new LinkedHashMap<>();
        if (checkouts == null) {
            return map;
        }
        for (TownsfolkPoolCheckoutRecord r : checkouts) {
            if (r == null || r.getCharacterId().isBlank()) {
                continue;
            }
            if (TownsfolkPoolKeys.isBlankTownId(r.getTownId())) {
                continue;
            }
            map.put(TownsfolkPoolKeys.checkoutKey(r.getTownId(), r.getCharacterId()), r);
        }
        return map;
    }

    public void setCheckoutsFromMap(@Nonnull Map<String, TownsfolkPoolCheckoutRecord> map) {
        checkouts = new ArrayList<>(map.values());
    }

    public static TownsfolkPoolFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new TownsfolkPoolFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            TownsfolkPoolFile f = GSON.fromJson(r, TownsfolkPoolFile.class);
            return f != null ? f : new TownsfolkPoolFile();
        }
    }

    public void writeAtomic(@Nonnull Path path) throws IOException {
        Path dir = path.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
