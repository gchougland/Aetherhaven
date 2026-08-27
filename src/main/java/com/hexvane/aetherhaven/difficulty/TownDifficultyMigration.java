package com.hexvane.aetherhaven.difficulty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Copies legacy per-world {@code difficulty.json} onto towns missing a difficulty choice. */
public final class TownDifficultyMigration {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().create();

    private TownDifficultyMigration() {}

    public static void migrateLegacyWorldDifficultyIfNeeded(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (!PersistentWorldSupport.shouldPersistWorldData(world)) {
            return;
        }
        Path path = legacyDifficultyFile(world, plugin);
        if (!Files.isRegularFile(path)) {
            return;
        }
        TownDifficultySettings legacy = readLegacy(path);
        if (legacy == null || !legacy.isDifficultyChosen()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        boolean changed = false;
        for (TownRecord town : tm.allTowns()) {
            if (town.hasDifficultyChosen()) {
                continue;
            }
            TownDifficultySettings copy = new TownDifficultySettings();
            copy.copyFrom(legacy);
            town.setDifficultySettings(copy);
            tm.updateTown(town);
            changed = true;
        }
        if (changed) {
            LOGGER.atInfo().log(
                "Migrated legacy world difficulty onto towns in world %s from %s",
                world.getName(),
                path
            );
        }
    }

    @Nonnull
    private static Path legacyDifficultyFile(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        String name = sanitizeWorldDirName(world.getName());
        return TownManager.pluginData(plugin).resolve("worlds").resolve(name).resolve("difficulty.json");
    }

    @Nonnull
    private static String sanitizeWorldDirName(@Nonnull String worldName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < worldName.length(); i++) {
            char c = worldName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.isEmpty() ? "world" : sb.toString();
    }

    @Nullable
    private static TownDifficultySettings readLegacy(@Nonnull Path path) {
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            LegacyWorldDifficultyFile file = GSON.fromJson(r, LegacyWorldDifficultyFile.class);
            return file != null ? file.difficulty : null;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to read legacy difficulty file %s", path);
            return null;
        }
    }

    private static final class LegacyWorldDifficultyFile {
        @SerializedName("difficulty")
        @Nullable
        private TownDifficultySettings difficulty;
    }
}
