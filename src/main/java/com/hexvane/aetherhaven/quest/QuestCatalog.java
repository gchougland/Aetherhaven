package com.hexvane.aetherhaven.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Quest definitions from {@code Server/Aetherhaven/Quests/} under each asset pack (plus classpath fallback for tests).
 */
public final class QuestCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, QuestDefinition> byId;

    private QuestCatalog(@Nonnull Map<String, QuestDefinition> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static QuestCatalog empty() {
        return new QuestCatalog(Collections.emptyMap());
    }

    /**
     * Prefers {@link com.hypixel.hytale.server.core.asset.AssetModule} pack roots; falls back to the mod classpath when
     * no pack files are found (e.g. unit tests). Later packs in the module list override earlier definitions for the
     * same quest id.
     */
    @Nonnull
    public static QuestCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, QuestDefinition> map = new LinkedHashMap<>();
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.QUESTS);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    loadJsonFromStream(gson, in, f.packName() + ":" + f.absolutePath(), map);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load quest file %s", f.absolutePath());
                }
            }
            LOGGER.atInfo().log("Loaded %s quest definition(s) from asset packs under %s", map.size(), AetherhavenAssetPaths.QUESTS);
        } else {
            List<String> paths = ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.questsPrefix());
            for (String path : paths) {
                loadPathFromClasspath(classLoader, gson, path, map);
            }
            LOGGER.atInfo().log("Loaded %s quest definition(s) from classpath %s", map.size(), AetherhavenAssetPaths.questsPrefix());
        }
        return new QuestCatalog(map);
    }

    private static void loadJsonFromStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String resourceLabel,
        @Nonnull Map<String, QuestDefinition> map
    ) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement root = gson.fromJson(reader, JsonElement.class);
            if (root == null) {
                return;
            }
            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        ingestObject(gson, el.getAsJsonObject(), map, resourceLabel);
                    }
                }
            } else if (root.isJsonObject()) {
                ingestObject(gson, root.getAsJsonObject(), map, resourceLabel);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse quest JSON %s", resourceLabel);
        }
    }

    private static void loadPathFromClasspath(
        @Nonnull ClassLoader classLoader,
        @Nonnull Gson gson,
        @Nonnull String resourcePath,
        @Nonnull Map<String, QuestDefinition> map
    ) {
        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.atWarning().log("Quest file not found: %s", resourcePath);
                return;
            }
            loadJsonFromStream(gson, in, resourcePath, map);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load quest file %s", resourcePath);
        }
    }

    private static void ingestObject(
        @Nonnull Gson gson,
        @Nonnull JsonObject o,
        @Nonnull Map<String, QuestDefinition> map,
        @Nonnull String resourcePath
    ) {
        QuestDefinition def = gson.fromJson(o, QuestDefinition.class);
        if (def == null || def.idOrEmpty().isBlank()) {
            LOGGER.atWarning().log("Skipping quest JSON without id: %s", resourcePath);
            return;
        }
        if (def.schemaVersion() != QuestDefinition.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.atSevere().log(
                "Unsupported quest schemaVersion %s for %s (expected %s)",
                def.schemaVersion(),
                def.idOrEmpty(),
                QuestDefinition.SUPPORTED_SCHEMA_VERSION
            );
            return;
        }
        validateQuest(def, resourcePath);
        String id = def.idOrEmpty();
        if (map.containsKey(id)) {
            LOGGER.atInfo().log("Quest id %s overridden by later asset (%s)", id, resourcePath);
        }
        map.put(id, def);
    }

    private static void validateQuest(@Nonnull QuestDefinition def, @Nonnull String resourcePath) {
        for (QuestObjective o : def.objectivesOrEmpty()) {
            String k = o.kind();
            if (k == null || k.isBlank()) {
                LOGGER.atWarning().log("Quest %s has objective missing kind (%s)", def.idOrEmpty(), resourcePath);
                continue;
            }
            String kind = k.trim();
            if (!isKnownObjectiveKind(kind)) {
                LOGGER.atWarning().log("Unknown objective kind %s in quest %s", kind, def.idOrEmpty());
            }
            if ("entity_kills".equalsIgnoreCase(kind)) {
                if (o.killCount() <= 0) {
                    LOGGER.atWarning().log("Quest %s entity_kills objective needs killCount > 0 (%s)", def.idOrEmpty(), resourcePath);
                }
                if (!o.hasEntityKillFilters()) {
                    LOGGER.atWarning().log(
                        "Quest %s entity_kills objective needs entityTagsAny, entityIdsAny, or entityTagsAll (%s)",
                        def.idOrEmpty(),
                        resourcePath
                    );
                }
            }
        }
        for (QuestReward r : def.rewardsOrEmpty()) {
            String k = r.kind();
            if (k == null || k.isBlank()) {
                LOGGER.atWarning().log("Quest %s has reward missing kind (%s)", def.idOrEmpty(), resourcePath);
                continue;
            }
            if (!isKnownRewardKind(k.trim())) {
                LOGGER.atWarning().log("Unknown reward kind %s in quest %s", k.trim(), def.idOrEmpty());
            }
        }
    }

    private static boolean isKnownObjectiveKind(@Nonnull String kind) {
        return switch (kind) {
            case "journal", "construction_built", "dialogue_turn_in", "assign_house_resident", "custom", "entity_kills" -> true;
            default -> false;
        };
    }

    private static boolean isKnownRewardKind(@Nonnull String kind) {
        return switch (kind) {
            case "reputation", "item", "currency", "unlock", "learn_recipe" -> true;
            default -> false;
        };
    }

    @Nullable
    public QuestDefinition get(@Nonnull String questId) {
        return byId.get(questId.trim());
    }

    @Nonnull
    public Map<String, QuestDefinition> all() {
        return Collections.unmodifiableMap(byId);
    }

    /**
     * Quest ids whose {@code assignNpcRoleId} matches {@code npcRoleId}, sorted lexicographically for stable ordering.
     */
    @Nonnull
    public List<String> listQuestIdsAssignedToRole(@Nonnull String npcRoleId) {
        String r = npcRoleId.trim();
        if (r.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, QuestDefinition> e : byId.entrySet()) {
            String a = e.getValue().assignNpcRoleId();
            if (a != null && r.equals(a)) {
                out.add(e.getKey());
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * @return first quest id whose {@code assignNpcRoleId} matches catalog order, or null
     */
    @Nullable
    public String findQuestIdByAssignNpcRole(@Nonnull String npcRoleId) {
        List<String> list = listQuestIdsAssignedToRole(npcRoleId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** First reputation reward with {@code grantTo} {@code quest_beneficiary_npc}, if any. */
    @Nullable
    public QuestReputationGrant findQuestBeneficiaryReputation(@Nonnull String questId) {
        QuestDefinition def = get(questId);
        if (def == null) {
            return null;
        }
        QuestRewardService.ReputationRewardPreview preview = QuestRewardService.firstReputationReward(
            def.rewardsOrEmpty(),
            QuestRewardService.GRANT_TO_QUEST_BENEFICIARY_NPC
        );
        if (preview == null || preview.npcRoleId() == null || preview.npcRoleId().isBlank()) {
            return null;
        }
        return new QuestReputationGrant(preview.amount(), preview.npcRoleId());
    }

    public record QuestReputationGrant(int amount, @Nonnull String beneficiaryRoleId) {}

    /** First {@code item} reward in catalog order, for journal preview. */
    public record FirstItemReward(@Nonnull String itemId, int count) {}

    /**
     * @return first reward with kind {@code item} and a non blank item id, or null
     */
    @Nullable
    public FirstItemReward firstItemReward(@Nonnull String questId) {
        QuestDefinition def = get(questId);
        if (def == null) {
            return null;
        }
        for (QuestReward r : def.rewardsOrEmpty()) {
            if (r.kind() == null || !"item".equalsIgnoreCase(r.kind().trim())) {
                continue;
            }
            String id = r.itemId();
            if (id == null || id.isBlank()) {
                continue;
            }
            return new FirstItemReward(id.trim(), r.count());
        }
        return null;
    }

    @Nonnull
    public String displayName(@Nonnull String questId) {
        return titleMessage(questId).getAnsiMessage();
    }

    @Nonnull
    public Message titleMessage(@Nonnull String questId) {
        QuestDefinition def = get(questId);
        if (def != null && def.titleLangKey() != null && !def.titleLangKey().isBlank()) {
            return Message.translation(def.titleLangKey().trim());
        }
        if (def != null) {
            return Message.raw(def.titleOrId());
        }
        String trimmed = questId.trim();
        return Message.raw(trimmed.isEmpty() ? "" : trimmed);
    }

    @Nonnull
    public Message descriptionMessage(@Nonnull String questId) {
        QuestDefinition def = get(questId);
        if (def != null && def.descriptionLangKey() != null && !def.descriptionLangKey().isBlank()) {
            return Message.translation(def.descriptionLangKey().trim());
        }
        if (def != null) {
            return Message.raw(def.descriptionOrDefault());
        }
        return Message.translation("aetherhaven_story_quests.aetherhaven.storyQuests._meta.noDescription");
    }

    @Nonnull
    public Message journalTitle(
        @Nonnull String questId,
        @Nonnull TownRecord town,
        @Nullable Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        QuestDefinition def = get(questId);
        Message base = titleMessage(questId);
        if (def == null || !def.assignByEntity()) {
            return base;
        }
        String targetName = QuestAssigneeDisplay.targetName(def, town, store, plugin);
        if (targetName == null || targetName.isBlank()) {
            return base;
        }
        return Message
            .translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.questForName")
            .param("title", base)
            .param("name", Message.raw(targetName.trim()));
    }

    @Nonnull
    public Message journalDescription(
        @Nonnull String questId,
        @Nonnull TownRecord town,
        @Nullable Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        QuestDefinition def = get(questId);
        Message base = descriptionMessage(questId);
        if (def == null || !def.assignByEntity()) {
            return base;
        }
        String targetName = QuestAssigneeDisplay.targetName(def, town, store, plugin);
        if (targetName == null || targetName.isBlank()) {
            return base;
        }
        return Message
            .translation("aetherhaven_ui_shell.aetherhaven.ui.questJournal.questDescriptionForName")
            .param("body", base)
            .param("name", Message.raw(targetName.trim()));
    }

    @Nonnull
    public String description(@Nonnull String questId) {
        return descriptionMessage(questId).getAnsiMessage();
    }

    @Nonnull
    public Message objectivesMessage(
        @Nonnull String questId,
        @Nullable TownRecord town,
        @Nullable Store<EntityStore> store,
        @Nullable AetherhavenPlugin plugin
    ) {
        QuestDefinition def = get(questId);
        if (def == null) {
            return Message.raw("");
        }
        List<QuestObjective> lines = def.objectivesOrEmpty();
        if (lines.isEmpty()) {
            return Message.raw("");
        }
        String qid = questId.trim();
        String targetName = null;
        if (town != null && plugin != null && def.assignByEntity()) {
            targetName = QuestAssigneeDisplay.targetName(def, town, store, plugin);
        }
        Message out = Message.raw("");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out = out.insert(Message.raw("\n"));
            }
            QuestObjective o = lines.get(i);
            Message lineMsg = objectiveLineMessage(o, targetName);
            out = out.insert(Message.raw(String.valueOf(i + 1))).insert(Message.raw(". ")).insert(lineMsg);
            if (town != null
                && o.kind() != null
                && "entity_kills".equalsIgnoreCase(o.kind().trim())
                && o.id() != null
                && !o.id().isBlank()) {
                int cur = town.getQuestKillCount(qid, o.id().trim());
                int need = Math.max(1, o.killCount());
                out = out.insert(
                    Message.raw(" (" + Math.min(cur, need) + "/" + need + ")")
                );
            }
        }
        return out;
    }

    @Nonnull
    private static Message objectiveLineMessage(@Nonnull QuestObjective o, @Nullable String targetName) {
        Message lineMsg;
        if (o.textLangKey() != null && !o.textLangKey().isBlank()) {
            lineMsg = Message.translation(o.textLangKey().trim());
        } else {
            lineMsg = Message.raw(o.text() != null ? o.text().trim() : "");
        }
        if (targetName != null && !targetName.isBlank() && o.text() != null && !o.text().isBlank()) {
            String personalized = QuestAssigneeDisplay.personalizeObjectiveLine(o.text().trim(), targetName);
            if (o.textLangKey() == null || o.textLangKey().isBlank()) {
                lineMsg = Message.raw(personalized);
            }
        }
        return lineMsg;
    }

    @Nonnull
    public String objectivesText(@Nonnull String questId) {
        return objectivesText(questId, null);
    }

    @Nonnull
    public String objectivesText(@Nonnull String questId, @Nullable TownRecord town) {
        return objectivesText(questId, town, null, null);
    }

    @Nonnull
    public String objectivesText(
        @Nonnull String questId,
        @Nullable TownRecord town,
        @Nullable Store<EntityStore> store,
        @Nullable AetherhavenPlugin plugin
    ) {
        return objectivesMessage(questId, town, store, plugin).getAnsiMessage();
    }

    @Nonnull
    public String detailBody(@Nonnull String questId) {
        return detailBody(questId, null);
    }

    @Nonnull
    public String detailBody(@Nonnull String questId, @Nullable TownRecord town) {
        String d = description(questId);
        String o = objectivesText(questId, town);
        if (o.isEmpty()) {
            return d;
        }
        return d + "\n\nObjectives:\n" + o;
    }
}
