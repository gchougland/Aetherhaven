package com.hexvane.aetherhaven.quest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Save wide per player story quest progress ({@code category: "player"}). */
public final class PlayerQuestProgress implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PlayerQuestProgress> CODEC =
        BuilderCodec.builder(PlayerQuestProgress.class, PlayerQuestProgress::new)
            .append(
                new KeyedCodec<>("ActiveQuestIds", Codec.STRING_ARRAY),
                (c, v) -> c.activeQuestIds = normalizedList(v),
                c -> c.activeQuestIds.toArray(String[]::new))
            .add()
            .append(
                new KeyedCodec<>("CompletedQuestIds", Codec.STRING_ARRAY),
                (c, v) -> c.completedQuestIds = normalizedList(v),
                c -> c.completedQuestIds.toArray(String[]::new))
            .add()
            .append(
                new KeyedCodec<>("CompletedObjectiveLines", Codec.STRING_ARRAY),
                (c, v) -> c.decodeCompletedObjectiveLines(v),
                PlayerQuestProgress::encodeCompletedObjectiveLines)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlayerQuestProgress> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(PlayerQuestProgress.class, "AetherhavenPlayerQuestProgress", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, PlayerQuestProgress> getComponentType() {
        ComponentType<EntityStore, PlayerQuestProgress> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlayerQuestProgress not registered");
        }
        return t;
    }

    @Nonnull
    private List<String> activeQuestIds = new ArrayList<>();

    @Nonnull
    private List<String> completedQuestIds = new ArrayList<>();

    /** questId -> objectiveId -> complete */
    @Nonnull
    private final Map<String, Map<String, Boolean>> questObjectiveProgress = new LinkedHashMap<>();

    @Nonnull
    public Set<String> activeQuestIdsSnapshot() {
        return new LinkedHashSet<>(activeQuestIds);
    }

    @Nonnull
    public Set<String> completedQuestIdsSnapshot() {
        return new LinkedHashSet<>(completedQuestIds);
    }

    public boolean hasQuestActive(@Nonnull String questId) {
        return activeQuestIds.contains(questId.trim());
    }

    public boolean hasQuestCompleted(@Nonnull String questId) {
        return completedQuestIds.contains(questId.trim());
    }

    public void addActiveQuest(@Nonnull String questId) {
        String id = questId.trim();
        if (id.isEmpty() || activeQuestIds.contains(id)) {
            return;
        }
        activeQuestIds.add(id);
    }

    public void removeActiveQuest(@Nonnull String questId) {
        activeQuestIds.remove(questId.trim());
    }

    public void markQuestCompleted(@Nonnull String questId) {
        String id = questId.trim();
        removeActiveQuest(id);
        if (!completedQuestIds.contains(id)) {
            completedQuestIds.add(id);
        }
        questObjectiveProgress.remove(id);
    }

    public void clearQuest(@Nonnull String questId) {
        String id = questId.trim();
        removeActiveQuest(id);
        completedQuestIds.remove(id);
        questObjectiveProgress.remove(id);
    }

    public void initQuestObjectiveProgress(@Nonnull String questId, @Nonnull List<String> objectiveIds) {
        Map<String, Boolean> row = questObjectiveProgress.computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        for (String oid : objectiveIds) {
            if (oid == null || oid.isBlank()) {
                continue;
            }
            row.putIfAbsent(oid.trim(), Boolean.FALSE);
        }
    }

    public boolean completeQuestObjective(@Nonnull String questId, @Nonnull String objectiveId) {
        Map<String, Boolean> row = questObjectiveProgress.computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        Boolean prev = row.put(objectiveId.trim(), Boolean.TRUE);
        return prev == null || !prev;
    }

    public boolean isQuestObjectiveComplete(@Nonnull String questId, @Nonnull String objectiveId) {
        Map<String, Boolean> row = questObjectiveProgress.get(questId.trim());
        if (row == null) {
            return false;
        }
        return Boolean.TRUE.equals(row.get(objectiveId.trim()));
    }

    private void decodeCompletedObjectiveLines(@Nullable String[] lines) {
        questObjectiveProgress.clear();
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int sep = line.indexOf(':');
            if (sep <= 0 || sep >= line.length() - 1) {
                continue;
            }
            String questId = line.substring(0, sep).trim();
            String objectiveId = line.substring(sep + 1).trim();
            if (questId.isEmpty() || objectiveId.isEmpty()) {
                continue;
            }
            questObjectiveProgress
                .computeIfAbsent(questId, k -> new LinkedHashMap<>())
                .put(objectiveId, Boolean.TRUE);
        }
    }

    @Nonnull
    private String[] encodeCompletedObjectiveLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, Boolean>> questEntry : questObjectiveProgress.entrySet()) {
            for (Map.Entry<String, Boolean> objEntry : questEntry.getValue().entrySet()) {
                if (Boolean.TRUE.equals(objEntry.getValue())) {
                    lines.add(questEntry.getKey() + ':' + objEntry.getKey());
                }
            }
        }
        return lines.toArray(String[]::new);
    }

    @Nonnull
    private static List<String> normalizedList(@Nullable String[] raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlayerQuestProgress copy = new PlayerQuestProgress();
        copy.activeQuestIds = new ArrayList<>(activeQuestIds);
        copy.completedQuestIds = new ArrayList<>(completedQuestIds);
        for (Map.Entry<String, Map<String, Boolean>> e : questObjectiveProgress.entrySet()) {
            copy.questObjectiveProgress.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        return copy;
    }
}
