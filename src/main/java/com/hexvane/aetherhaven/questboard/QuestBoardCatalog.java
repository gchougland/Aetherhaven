package com.hexvane.aetherhaven.questboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.questboard.data.QuestBoardDefinitionJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardHuntEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardQuestTypeWeightJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRankTierJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardVillagerJson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestBoardCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String CONFIG_PATH = "Server/Aetherhaven/quest_board.json";

    private final QuestBoardDefinitionJson definition;

    private QuestBoardCatalog(@Nonnull QuestBoardDefinitionJson definition) {
        this.definition = definition;
    }

    @Nonnull
    public static QuestBoardCatalog empty() {
        QuestBoardDefinitionJson def = new QuestBoardDefinitionJson();
        return new QuestBoardCatalog(def);
    }

    @Nonnull
    public static QuestBoardCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        QuestBoardDefinitionJson merged = null;
        int boardFiles = 0;
        int extensionFiles = 0;
        com.hypixel.hytale.server.core.asset.AssetModule module = com.hypixel.hytale.server.core.asset.AssetModule.get();
        if (module != null) {
            for (com.hypixel.hytale.assetstore.AssetPack pack : module.getAssetPacks()) {
                Path file = pack.getRoot().resolve(CONFIG_PATH);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                QuestBoardDefinitionJson loaded = readDefinition(gson, file);
                if (loaded == null) {
                    continue;
                }
                merged = QuestBoardMerger.merge(merged, loaded);
                boardFiles++;
                LOGGER.atInfo().log("Merged quest board config from pack %s (%s)", pack.getName(), file);
            }
        }
        if (merged == null) {
            try (InputStream in = classLoader.getResourceAsStream(CONFIG_PATH)) {
                if (in != null) {
                    merged = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), QuestBoardDefinitionJson.class);
                    boardFiles++;
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load quest board config from classpath %s", CONFIG_PATH);
            }
        }
        List<PackJsonFile> extensions =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.QUEST_BOARD_EXTENSIONS);
        for (PackJsonFile f : extensions) {
            QuestBoardDefinitionJson ext = readDefinition(gson, f.absolutePath());
            if (ext == null) {
                continue;
            }
            merged = QuestBoardMerger.merge(merged, ext);
            extensionFiles++;
            LOGGER.atInfo().log("Merged quest board extension from pack %s (%s)", f.packName(), f.absolutePath());
        }
        if (merged == null) {
            LOGGER.atWarning().log("No quest board config found; using empty defaults");
            return empty();
        }
        if (merged.schemaVersion() != QuestBoardDefinitionJson.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.atWarning().log(
                "Quest board schemaVersion %s (expected %s)",
                merged.schemaVersion(),
                QuestBoardDefinitionJson.SUPPORTED_SCHEMA_VERSION
            );
        }
        LOGGER.atInfo().log(
            "Loaded quest board config (%s board file(s), %s extension(s), %s rank tiers, %s villager roles)",
            boardFiles,
            extensionFiles,
            merged.ranksOrEmpty().size(),
            merged.villagersOrEmpty().size()
        );
        return new QuestBoardCatalog(merged);
    }

    @Nullable
    private static QuestBoardDefinitionJson readDefinition(@Nonnull Gson gson, @Nonnull Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), QuestBoardDefinitionJson.class);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load quest board config %s", file);
            return null;
        }
    }

    public int slotCount() {
        return definition.slotCount();
    }

    @Nonnull
    public List<QuestBoardRankTierJson> ranks() {
        return definition.ranksOrEmpty();
    }

    @Nonnull
    public Map<String, QuestBoardVillagerJson> villagers() {
        return definition.villagersOrEmpty();
    }

    @Nullable
    public QuestBoardVillagerJson villager(@Nonnull String npcRoleId) {
        return villagers().get(npcRoleId.trim());
    }

    @Nonnull
    public List<QuestBoardFetchEntryJson> fetchEntriesForRole(@Nonnull String npcRoleId) {
        QuestBoardVillagerJson v = villager(npcRoleId);
        return v != null ? v.fetchEntriesOrEmpty() : List.of();
    }

    @Nonnull
    public List<QuestBoardHuntEntryJson> huntEntriesForRole(@Nonnull String npcRoleId) {
        QuestBoardVillagerJson v = villager(npcRoleId);
        return v != null ? v.huntEntriesOrEmpty() : List.of();
    }

    @Nonnull
    public List<QuestBoardRaidEntryJson> raidEntriesForRole(@Nonnull String npcRoleId) {
        QuestBoardVillagerJson v = villager(npcRoleId);
        return v != null ? v.raidEntriesOrEmpty() : List.of();
    }

    @Nonnull
    public Map<String, QuestBoardQuestTypeWeightJson> questTypes() {
        return definition.questTypesOrEmpty();
    }

    public int questTypeWeight(@Nonnull String typeId) {
        QuestBoardQuestTypeWeightJson w = questTypes().get(typeId.trim());
        return w != null ? w.weight() : 0;
    }

    @Nullable
    public QuestBoardRankTierJson rankTier(@Nonnull String rankId) {
        String id = rankId.trim();
        for (QuestBoardRankTierJson tier : ranks()) {
            if (id.equalsIgnoreCase(tier.idOrEmpty())) {
                return tier;
            }
        }
        return null;
    }

    public int defaultXpRewardForRank(@Nonnull String rankId) {
        QuestBoardRankTierJson tier = rankTier(rankId);
        return tier != null ? tier.xpReward() : 0;
    }

    @Nullable
    public String iconForRank(@Nonnull String rankId) {
        QuestBoardRankTierJson tier = rankTier(rankId);
        return tier != null ? tier.icon() : null;
    }
}
