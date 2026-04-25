package com.hexvane.aetherhaven.villager.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Villager definitions from {@code Server/Aetherhaven/Villagers/} (per-pack merge; later pack wins on same
 * {@link VillagerDefinition#getNpcRoleId()}).
 */
public final class VillagerDefinitionCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, VillagerDefinition> byNpcRoleId;
    private final Map<String, VillagerDefinition> byDialogueVillagerKind;

    private VillagerDefinitionCatalog(
        @Nonnull Map<String, VillagerDefinition> byNpcRoleId,
        @Nonnull Map<String, VillagerDefinition> byDialogueVillagerKind
    ) {
        this.byNpcRoleId = byNpcRoleId;
        this.byDialogueVillagerKind = byDialogueVillagerKind;
    }

    @Nonnull
    public static VillagerDefinitionCatalog empty() {
        return new VillagerDefinitionCatalog(Map.of(), Map.of());
    }

    @Nonnull
    public static VillagerDefinitionCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, VillagerDefinition> byRole = new LinkedHashMap<>();
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.VILLAGERS);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    loadFromStream(gson, in, f.packName() + ":" + f.absolutePath(), byRole);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load villager definition %s", f.absolutePath());
                }
            }
            LOGGER.atInfo().log("Loaded %s villager definition(s) from asset packs under %s", byRole.size(), AetherhavenAssetPaths.VILLAGERS);
        } else {
            List<String> paths = ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.villagersPrefix());
            for (String path : paths) {
                try (InputStream in = classLoader.getResourceAsStream(path)) {
                    if (in == null) {
                        continue;
                    }
                    loadFromStream(gson, in, path, byRole);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load villager definition %s", path);
                }
            }
            LOGGER.atInfo().log("Loaded %s villager definition(s) from classpath %s", byRole.size(), AetherhavenAssetPaths.villagersPrefix());
        }
        return buildCatalog(byRole);
    }

    private static void loadFromStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, VillagerDefinition> byRole
    ) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull()) {
                return;
            }
            if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                if (o.has("villagers") && o.get("villagers").isJsonArray()) {
                    JsonArray arr = o.getAsJsonArray("villagers");
                    for (JsonElement el : arr) {
                        if (el != null && el.isJsonObject()) {
                            putDefinition(gson, el.getAsJsonObject().toString(), label, byRole);
                        }
                    }
                    return;
                }
                VillagerDefinition def = gson.fromJson(o, VillagerDefinition.class);
                putDefinition(def, label, byRole);
                return;
            }
            LOGGER.atWarning().log("Unexpected villager JSON root in %s", label);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse villager definition %s", label);
        }
    }

    private static void putDefinition(@Nonnull Gson gson, @Nonnull String json, @Nonnull String label, @Nonnull Map<String, VillagerDefinition> byRole) {
        VillagerDefinition def = gson.fromJson(json, VillagerDefinition.class);
        putDefinition(def, label, byRole);
    }

    private static void putDefinition(@Nullable VillagerDefinition def, @Nonnull String label, @Nonnull Map<String, VillagerDefinition> byRole) {
        if (def == null) {
            return;
        }
        String id = def.getNpcRoleId();
        if (id.isEmpty()) {
            LOGGER.atWarning().log("Skipping villager definition with empty npcRoleId (%s)", label);
            return;
        }
        if (byRole.containsKey(id)) {
            LOGGER.atInfo().log("Villager definition npcRoleId %s overridden by later asset (%s)", id, label);
        }
        byRole.put(id, def);
        LOGGER.atInfo().log("Loaded villager definition: %s (%s)", id, label);
    }

    @Nonnull
    private static VillagerDefinitionCatalog buildCatalog(@Nonnull Map<String, VillagerDefinition> byRole) {
        Map<String, VillagerDefinition> byKind = new LinkedHashMap<>();
        for (VillagerDefinition d : byRole.values()) {
            String k = d.getDialogueVillagerKind();
            if (!k.isEmpty()) {
                byKind.put(k, d);
            }
        }
        return new VillagerDefinitionCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(byRole)), Map.copyOf(byKind));
    }

    @Nullable
    public VillagerDefinition byNpcRoleId(@Nonnull String npcRoleId) {
        return byNpcRoleId.get(npcRoleId.trim());
    }

    @Nullable
    public VillagerDefinition byDialogueVillagerKind(@Nonnull String kind) {
        return byDialogueVillagerKind.get(kind.trim());
    }

    @Nonnull
    public Map<String, VillagerDefinition> allByNpcRoleId() {
        return byNpcRoleId;
    }

    /**
     * Schedules: file-based registry, then override with embedded {@link VillagerDefinition#getWeeklySchedule()} when
     * non-null and non-empty.
     */
    @Nullable
    public VillagerScheduleDefinition effectiveSchedule(
        @Nonnull String npcRoleId,
        @Nonnull VillagerScheduleRegistry fileRegistry
    ) {
        VillagerDefinition def = byNpcRoleId(npcRoleId);
        if (def != null) {
            String key = def.effectiveScheduleRoleId();
            VillagerScheduleDefinition embedded = def.getWeeklySchedule();
            if (embedded != null && !embedded.getTransitions().isEmpty()) {
                return embedded;
            }
            VillagerScheduleDefinition fromFile = fileRegistry.getOrLoad(key);
            if (fromFile != null) {
                return fromFile;
            }
        }
        return fileRegistry.getOrLoad(npcRoleId.trim());
    }

    @Nonnull
    public List<ReputationRewardCatalog.ReputationRewardDefinition> allReputationMilestones() {
        List<ReputationRewardCatalog.ReputationRewardDefinition> out = new ArrayList<>();
        for (VillagerDefinition d : byNpcRoleId.values()) {
            String roleId = d.getNpcRoleId();
            for (VillagerReputationMilestoneJson m : d.getReputationMilestones()) {
                if (m.getRewardId().isEmpty()) {
                    continue;
                }
                out.add(
                    new ReputationRewardCatalog.ReputationRewardDefinition(
                        m.getRewardId(),
                        roleId,
                        m.getMinReputation(),
                        m.getItemId(),
                        m.getItemCount(),
                        m.getDialogueNodeId(),
                        m.getLearnRecipeItemId()
                    )
                );
            }
        }
        return out;
    }

    @Nonnull
    public List<InnPoolEntry> innPoolEntriesSorted() {
        List<VillagerDefinition> pool = new ArrayList<>();
        for (VillagerDefinition d : byNpcRoleId.values()) {
            if (d.isInnPoolEligible()) {
                pool.add(d);
            }
        }
        pool.sort(Comparator.comparingInt(VillagerDefinition::getInnPoolOrder).thenComparing(VillagerDefinition::getNpcRoleId));
        List<InnPoolEntry> out = new ArrayList<>();
        for (VillagerDefinition d : pool) {
            String vk = d.getVisitorBindingKind();
            if (vk == null) {
                LOGGER.atWarning().log("Inn pool villager %s missing visitorBindingKind; skipped", d.getNpcRoleId());
                continue;
            }
            out.add(new InnPoolEntry(d.getNpcRoleId(), vk, d.getInnPoolOrder()));
        }
        return out;
    }
}
