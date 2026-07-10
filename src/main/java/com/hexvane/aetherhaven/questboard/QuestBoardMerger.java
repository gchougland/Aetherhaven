package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.questboard.data.QuestBoardDefinitionJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardFetchEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardHuntEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardQuestTypeWeightJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidEntryJson;
import com.hexvane.aetherhaven.questboard.data.QuestBoardVillagerJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Deep-merges quest board definitions for crossmod additive pools. */
public final class QuestBoardMerger {
    private QuestBoardMerger() {}

    /**
     * Merges {@code overlay} into a copy of {@code base}. Later entries with the same id replace earlier ones;
     * new ids are appended. Scalar fields ({@code schemaVersion}, {@code slotCount}, {@code ranks}) from the
     * overlay replace the base when present/non-empty.
     */
    @Nonnull
    public static QuestBoardDefinitionJson merge(
        @Nullable QuestBoardDefinitionJson base,
        @Nullable QuestBoardDefinitionJson overlay
    ) {
        if (base == null && overlay == null) {
            return new QuestBoardDefinitionJson();
        }
        if (base == null) {
            return copy(overlay);
        }
        if (overlay == null) {
            return copy(base);
        }
        QuestBoardDefinitionJson out = copy(base);
        applyOverlay(out, overlay);
        return out;
    }

    private static void applyOverlay(@Nonnull QuestBoardDefinitionJson out, @Nonnull QuestBoardDefinitionJson overlay) {
        if (overlay.schemaVersion() > 0) {
            out.setSchemaVersion(overlay.schemaVersion());
        }
        if (overlay.rawSlotCount() > 0) {
            out.setSlotCount(overlay.rawSlotCount());
        }
        if (!overlay.ranksOrEmpty().isEmpty()) {
            out.setRanks(new ArrayList<>(overlay.ranksOrEmpty()));
        }
        if (!overlay.questTypesOrEmpty().isEmpty()) {
            Map<String, QuestBoardQuestTypeWeightJson> types = new LinkedHashMap<>(out.questTypesOrEmpty());
            types.putAll(overlay.questTypesOrEmpty());
            out.setQuestTypes(types);
        }
        if (!overlay.villagersOrEmpty().isEmpty()) {
            Map<String, QuestBoardVillagerJson> villagers = new LinkedHashMap<>();
            for (var e : out.villagersOrEmpty().entrySet()) {
                villagers.put(e.getKey(), copyVillager(e.getValue()));
            }
            for (var e : overlay.villagersOrEmpty().entrySet()) {
                QuestBoardVillagerJson existing = villagers.get(e.getKey());
                if (existing == null) {
                    villagers.put(e.getKey(), copyVillager(e.getValue()));
                } else {
                    mergeVillager(existing, e.getValue());
                }
            }
            out.setVillagers(villagers);
        }
    }

    @Nonnull
    private static QuestBoardDefinitionJson copy(@Nonnull QuestBoardDefinitionJson src) {
        QuestBoardDefinitionJson out = new QuestBoardDefinitionJson();
        out.setSchemaVersion(src.schemaVersion());
        out.setSlotCount(src.rawSlotCount());
        out.setRanks(new ArrayList<>(src.ranksOrEmpty()));
        out.setQuestTypes(new LinkedHashMap<>(src.questTypesOrEmpty()));
        Map<String, QuestBoardVillagerJson> villagers = new LinkedHashMap<>();
        for (var e : src.villagersOrEmpty().entrySet()) {
            villagers.put(e.getKey(), copyVillager(e.getValue()));
        }
        out.setVillagers(villagers);
        return out;
    }

    @Nonnull
    private static QuestBoardVillagerJson copyVillager(@Nonnull QuestBoardVillagerJson src) {
        QuestBoardVillagerJson out = new QuestBoardVillagerJson();
        out.setFetchEntries(new ArrayList<>(src.fetchEntriesOrEmpty()));
        out.setHuntEntries(new ArrayList<>(src.huntEntriesOrEmpty()));
        out.setRaidEntries(new ArrayList<>(src.raidEntriesOrEmpty()));
        return out;
    }

    private static void mergeVillager(@Nonnull QuestBoardVillagerJson target, @Nonnull QuestBoardVillagerJson overlay) {
        target.setFetchEntries(mergeById(target.fetchEntriesOrEmpty(), overlay.fetchEntriesOrEmpty(), QuestBoardFetchEntryJson::id));
        target.setHuntEntries(mergeById(target.huntEntriesOrEmpty(), overlay.huntEntriesOrEmpty(), QuestBoardHuntEntryJson::id));
        target.setRaidEntries(mergeById(target.raidEntriesOrEmpty(), overlay.raidEntriesOrEmpty(), QuestBoardRaidEntryJson::id));
    }

    @Nonnull
    private static <T> List<T> mergeById(
        @Nonnull List<T> base,
        @Nonnull List<T> overlay,
        @Nonnull Function<T, String> idFn
    ) {
        if (overlay.isEmpty()) {
            return new ArrayList<>(base);
        }
        LinkedHashMap<String, T> byId = new LinkedHashMap<>();
        List<T> noId = new ArrayList<>();
        for (T entry : base) {
            String id = normalizeId(idFn.apply(entry));
            if (id == null) {
                noId.add(entry);
            } else {
                byId.put(id, entry);
            }
        }
        for (T entry : overlay) {
            String id = normalizeId(idFn.apply(entry));
            if (id == null) {
                noId.add(entry);
            } else {
                byId.put(id, entry);
            }
        }
        List<T> out = new ArrayList<>(byId.size() + noId.size());
        out.addAll(byId.values());
        out.addAll(noId);
        return out;
    }

    @Nullable
    private static String normalizeId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim();
    }
}
