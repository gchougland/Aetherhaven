package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class TownNameCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE = "/defaults/town_names.json";

    private final List<String> names;

    private TownNameCatalog(@Nonnull List<String> names) {
        this.names = names;
    }

    @Nonnull
    public static TownNameCatalog loadFromClasspath() {
        try (InputStream in = TownNameCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("Missing %s; town names fallback to generic.", RESOURCE);
                return new TownNameCatalog(List.of("Newford", "Fairhaven", "Millbrook"));
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = new Gson().fromJson(json, JsonObject.class);
            if (root == null || !root.has("names") || !root.get("names").isJsonArray()) {
                return new TownNameCatalog(List.of("Newford", "Fairhaven"));
            }
            JsonArray arr = root.getAsJsonArray("names");
            List<String> out = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                if (arr.get(i).isJsonPrimitive()) {
                    String s = arr.get(i).getAsString().trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            if (out.isEmpty()) {
                return new TownNameCatalog(List.of("Fairhaven"));
            }
            return new TownNameCatalog(Collections.unmodifiableList(out));
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load town names");
            return new TownNameCatalog(List.of("Fairhaven"));
        }
    }

    @Nonnull
    public List<String> names() {
        return names;
    }

    /**
     * Picks a random name from the pool that is not already used in this world; retries and appends a suffix if needed.
     */
    @Nonnull
    public String pickUniqueDisplayName(@Nonnull TownManager tm, @Nonnull Random random) {
        if (names.isEmpty()) {
            return "Town " + UUID.randomUUID().toString().substring(0, 8);
        }
        for (int attempt = 0; attempt < 24; attempt++) {
            String n = names.get(random.nextInt(names.size()));
            if (tm.isDisplayNameAvailable(n, null)) {
                return n;
            }
        }
        for (int n = 1; n < 100_000; n++) {
            String base = names.get(random.nextInt(names.size()));
            String candidate = base + " " + n;
            if (tm.isDisplayNameAvailable(candidate, null)) {
                return candidate;
            }
        }
        return "Town " + UUID.randomUUID().toString().substring(0, 8);
    }
}
