package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRankTierJson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldQuestBoardCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().create();

    private final Map<String, WorldQuestBoardProfileJson> byId;

    private WorldQuestBoardCatalog(@Nonnull Map<String, WorldQuestBoardProfileJson> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static WorldQuestBoardCatalog empty() {
        return new WorldQuestBoardCatalog(Map.of());
    }

    @Nonnull
    public static WorldQuestBoardCatalog loadFromAssetPacks() {
        Map<String, WorldQuestBoardProfileJson> map = new LinkedHashMap<>();
        List<PackJsonFile> files =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.WORLD_QUEST_BOARDS);
        for (PackJsonFile f : files) {
            try {
                String json = Files.readString(f.absolutePath(), StandardCharsets.UTF_8);
                WorldQuestBoardProfileJson profile = GSON.fromJson(json, WorldQuestBoardProfileJson.class);
                if (profile == null) {
                    continue;
                }
                String id = profile.profileIdOrEmpty();
                if (id.isEmpty()) {
                    String name = f.absolutePath().getFileName().toString();
                    if (name.endsWith(".json")) {
                        id = name.substring(0, name.length() - 5);
                    }
                    profile.setProfileId(id);
                }
                if (id.isEmpty()) {
                    continue;
                }
                map.put(id, profile);
                LOGGER.atInfo().log("Loaded world quest board profile %s from %s", id, f.absolutePath());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to load world quest board %s", f.absolutePath());
            }
        }
        return new WorldQuestBoardCatalog(Collections.unmodifiableMap(map));
    }

    @Nullable
    public WorldQuestBoardProfileJson get(@Nonnull String profileId) {
        return byId.get(profileId.trim());
    }

    @Nonnull
    public Map<String, WorldQuestBoardProfileJson> all() {
        return byId;
    }

    @Nonnull
    public List<QuestBoardRankTierJson> ranksFor(@Nonnull String profileId) {
        WorldQuestBoardProfileJson p = get(profileId);
        if (p == null || p.ranksOrEmpty().isEmpty()) {
            return defaultRanks();
        }
        return p.ranksOrEmpty();
    }

    @Nonnull
    private static List<QuestBoardRankTierJson> defaultRanks() {
        // Minimal E/D/C ladder when profile omits ranks.
        QuestBoardRankTierJson e = new QuestBoardRankTierJson();
        // Gson-populated types may lack setters; return empty and rely on TownQuestBoardRank defaults when empty.
        return List.of();
    }
}
