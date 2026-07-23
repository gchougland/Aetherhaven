package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TouristPortalRecord;
import com.hexvane.aetherhaven.tourist.TouristPortalRegistry;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared helpers for Aetherhaven command tab completion. */
public final class AetherhavenCommandSuggest {
    public static final int MAX = 20;

    private AetherhavenCommandSuggest() {}

    public static void suggestPrefix(@Nonnull SuggestionResult result, @Nullable String partial, @Nonnull Iterable<String> values) {
        String lower = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (lower.isEmpty() || value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.suggest(value);
                if (++count >= MAX) {
                    return;
                }
            }
        }
    }

    public static void suggestPrefix(@Nonnull SuggestionResult result, @Nullable String partial, @Nonnull String... values) {
        suggestPrefix(result, partial, List.of(values));
    }

    @Nullable
    public static PlayerRef playerRef(@Nonnull CommandSender sender) {
        return sender instanceof PlayerRef ref ? ref : null;
    }

    @Nullable
    public static World playerWorld(@Nonnull CommandSender sender) {
        PlayerRef ref = playerRef(sender);
        if (ref == null) {
            return null;
        }
        UUID worldUuid = ref.getWorldUuid();
        if (worldUuid == null) {
            return null;
        }
        return Universe.get().getWorld(worldUuid);
    }

    @Nullable
    public static AetherhavenPlugin plugin() {
        return AetherhavenPlugin.get();
    }

    @Nullable
    public static TownManager townManager(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
    }

    @Nonnull
    public static List<TownRecord> townsInWorld(@Nonnull World world, @Nonnull TownManager tm) {
        List<TownRecord> out = new ArrayList<>();
        for (TownRecord town : tm.allTowns()) {
            if (world.getName().equals(town.getWorldName())) {
                out.add(town);
            }
        }
        return out;
    }

    public static void suggestTownNames(@Nonnull SuggestionResult result, @Nullable String partial, @Nonnull World world) {
        AetherhavenPlugin plugin = plugin();
        if (plugin == null) {
            return;
        }
        TownManager tm = townManager(world, plugin);
        if (tm == null) {
            return;
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (TownRecord town : townsInWorld(world, tm)) {
            if (town.getDisplayName() != null && !town.getDisplayName().isBlank()) {
                names.add(town.getDisplayName().trim());
            }
            names.add(town.getTownId().toString());
        }
        suggestPrefix(result, partial, names);
    }

    @Nullable
    public static TownRecord primaryPlayerTown(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        AetherhavenPlugin plugin = plugin();
        if (plugin == null) {
            return null;
        }
        TownManager tm = townManager(world, plugin);
        if (tm == null) {
            return null;
        }
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return null;
        }
        List<TownRecord> towns = tm.findAllTownsForPlayerInWorld(playerUuid);
        for (TownRecord town : towns) {
            if (world.getName().equals(town.getWorldName())) {
                return town;
            }
        }
        return null;
    }

    @Nonnull
    public static Set<String> constructionIdsInTown(@Nonnull TownRecord town, @Nonnull ConstructionCatalog catalog) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PlotInstance plot : town.getPlotInstances()) {
            String stored = plot.getConstructionId();
            if (stored == null || stored.isBlank()) {
                continue;
            }
            String gameplay = catalog.resolveGameplayConstructionId(stored);
            if (!gameplay.isBlank()) {
                out.add(gameplay);
            }
            out.add(stored.trim());
        }
        return out;
    }

    public static void suggestPlotIds(@Nonnull SuggestionResult result, @Nullable String partial, @Nonnull TownRecord town) {
        Set<String> ids = new TreeSet<>();
        for (PlotInstance plot : town.getPlotInstances()) {
            ids.add(plot.getPlotId().toString());
        }
        suggestPrefix(result, partial, ids);
    }

    public static void suggestVillagerTargets(@Nonnull SuggestionResult result, @Nullable String partial, @Nonnull TownRecord town) {
        Set<String> targets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (town.getElderEntityUuid() != null) {
            targets.add(AetherhavenConstants.ELDER_NPC_ROLE_ID);
        }
        if (town.getInnkeeperEntityUuid() != null) {
            targets.add(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID);
        }
        for (ResidentNpcRecord resident : town.getResidentNpcRecords()) {
            String role = resident.getNpcRoleId();
            if (role != null && !role.isBlank()) {
                targets.add(role.trim());
            }
        }
        for (UUID id : TownVillagerTargetResolver.distinctVillagerEntityUuids(town)) {
            targets.add(id.toString());
        }
        suggestPrefix(result, partial, targets);
    }

    public static void suggestTouristPortals(
        @Nonnull SuggestionResult result,
        @Nullable String partial,
        @Nonnull World world,
        @Nonnull TownRecord town
    ) {
        AetherhavenPlugin plugin = plugin();
        if (plugin == null) {
            return;
        }
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        Set<String> ids = new TreeSet<>();
        for (TouristPortalRecord record : registry.recordsForTown(town.getTownId())) {
            if (record.getPortalId() != null) {
                ids.add(record.getPortalId().toString());
            }
        }
        suggestPrefix(result, partial, ids);
    }

    @Nonnull
    public static List<String> customBuildingIds(@Nonnull AetherhavenPlugin plugin) {
        List<String> out = new ArrayList<>();
        Path dir = CustomBuildingsPaths.buildingsDirectory(plugin.getDataDirectory());
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.endsWith(".json")) {
                    out.add(name.substring(0, name.length() - 5));
                }
            }
        } catch (IOException ignored) {
            // No suggestions when directory is unreadable.
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static int plotCountForConstruction(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String constructionId
    ) {
        int count = 0;
        for (PlotInstance plot : town.getPlotInstances()) {
            String stored = plot.getConstructionId();
            if (stored == null) {
                continue;
            }
            if (constructionId.equalsIgnoreCase(stored)
                || constructionId.equalsIgnoreCase(catalog.resolveGameplayConstructionId(stored))) {
                count++;
            }
        }
        return count;
    }
}
