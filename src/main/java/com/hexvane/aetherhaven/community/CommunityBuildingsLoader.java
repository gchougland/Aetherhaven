package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsLoader;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/** Loads installed community building JSON into the construction catalog map. */
public final class CommunityBuildingsLoader {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private CommunityBuildingsLoader() {}

    @Nonnull
    public static Set<String> overlayBuildingsFromCommunityDirectory(
        @Nonnull Gson gson,
        @Nonnull Path dataDirectory,
        @Nonnull Map<String, ConstructionDefinition> map
    ) {
        Path dir = CommunityPaths.buildingsDirectory(dataDirectory);
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .forEach(p -> {
                    String id = loadOne(gson, p, map);
                    if (id != null) {
                        ids.add(id);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to walk community buildings at %s", dir);
        }
        if (!ids.isEmpty()) {
            LOGGER.atInfo().log("Loaded %s community construction(s) from %s", ids.size(), dir);
        }
        return Set.copyOf(ids);
    }

    @javax.annotation.Nullable
    private static String loadOne(@Nonnull Gson gson, @Nonnull Path file, @Nonnull Map<String, ConstructionDefinition> map) {
        try (InputStream in = Files.newInputStream(file)) {
            ConstructionDefinition def = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ConstructionDefinition.class);
            if (def == null || def.getId() == null || def.getId().isBlank()) {
                return null;
            }
            String id = def.getId().trim();
            if (!CommunityBuildingValidator.isValidCommunityId(id)) {
                LOGGER.atWarning().log("Skipping community building with invalid id %s in %s", id, file);
                return null;
            }
            map.put(id, def);
            return id;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to load community building %s", file);
            return null;
        }
    }
}
