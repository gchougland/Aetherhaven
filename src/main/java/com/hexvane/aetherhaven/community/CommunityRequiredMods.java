package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.prefab.PrefabJsonStream;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves external asset packs required by a community prefab (blocks, fluids, entity asset refs).
 * Excludes vanilla ({@code Hytale:Hytale}) and Aetherhaven plugin packs.
 */
public final class CommunityRequiredMods {
    private static final Gson GSON = new Gson();
    private static final Set<String> EXCLUDED_PACK_IDS = Set.of(
        DefaultAssetMap.DEFAULT_PACK_KEY,
        AetherhavenPluginIds.CORE.toString(),
        AetherhavenPluginIds.PATH_DESIGNER.toString(),
        AetherhavenPluginIds.PATROL_ROUTES.toString(),
        AetherhavenPluginIds.RTS.toString(),
        AetherhavenPluginIds.FLOATING_GIFTS.toString(),
        AetherhavenPluginIds.BARD.toString(),
        AetherhavenPluginIds.REPUTATION.toString(),
        AetherhavenPluginIds.CONSTRUCTION.toString(),
        AetherhavenPluginIds.PRODUCTION.toString(),
        AetherhavenPluginIds.PLOT_CREATOR.toString(),
        AetherhavenPluginIds.QUESTS.toString(),
        AetherhavenPluginIds.DIALOGUE.toString(),
        AetherhavenPluginIds.VILLAGERS.toString(),
        AetherhavenPluginIds.ECONOMY.toString(),
        AetherhavenPluginIds.COMMERCE.toString(),
        AetherhavenPluginIds.GUILD.toString(),
        AetherhavenPluginIds.JEWELRY.toString(),
        AetherhavenPluginIds.REPUTATION_UNLOCKS.toString(),
        AetherhavenPluginIds.ADMIN_TOOLS.toString()
    );

    private CommunityRequiredMods() {}

    /** One required external mod / asset pack. */
    public static final class RequiredMod {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        public RequiredMod() {}

        public RequiredMod(@Nonnull String id, @Nonnull String name) {
            this.id = id;
            this.name = name;
        }

        @Nonnull
        public String getId() {
            return id != null ? id : "";
        }

        @Nonnull
        public String getName() {
            return name != null && !name.isBlank() ? name : getId();
        }
    }

    /**
     * Scans prefab JSON bytes and returns distinct required external packs (never null).
     */
    @Nonnull
    public static List<RequiredMod> computeFromPrefabBytes(@Nonnull byte[] prefabBytes) {
        PrefabJsonStream.Scan scan = PrefabJsonStream.scan(prefabBytes);
        CommunityPrefabSafety.Result safety = CommunityPrefabSafety.validate(scan);
        if (!safety.isSafe()) {
            throw new IllegalArgumentException(safety.detail());
        }
        Set<String> candidateKeys = new LinkedHashSet<>();
        for (String name : scan.blockNames()) {
            candidateKeys.add(CommunityPrefabSafety.normalizeChanceName(name));
        }
        for (String name : scan.fluidNames()) {
            candidateKeys.add(CommunityPrefabSafety.normalizeChanceName(name));
        }
        candidateKeys.addAll(scan.entityAssetStrings());
        candidateKeys.addAll(safety.referencedBlocks());
        candidateKeys.addAll(safety.referencedFluids());
        return resolveRequiredMods(candidateKeys);
    }

    @Nonnull
    public static List<RequiredMod> computeFromPrefabJson(@Nonnull JsonObject root) {
        return computeFromPrefabJson(root, List.of(), List.of());
    }

    @Nonnull
    private static List<RequiredMod> computeFromPrefabJson(
        @Nonnull JsonObject root,
        @Nonnull Collection<String> migratedBlocks,
        @Nonnull Collection<String> validatedFluids
    ) {
        Set<String> candidateKeys = new LinkedHashSet<>();
        collectNamedAssets(root.get("blocks"), candidateKeys);
        collectNamedAssets(root.get("fluids"), candidateKeys);
        collectEntityAssetStrings(root.get("entities"), candidateKeys);
        candidateKeys.addAll(migratedBlocks);
        candidateKeys.addAll(validatedFluids);
        return resolveRequiredMods(candidateKeys);
    }

    @Nonnull
    private static List<RequiredMod> resolveRequiredMods(@Nonnull Set<String> candidateKeys) {
        LinkedHashMap<String, String> packNamesById = new LinkedHashMap<>();
        for (String key : candidateKeys) {
            AssetSource source = resolveAssetSource(key);
            String packId = source != null ? source.packId() : null;
            if (!shouldRequirePack(packId, isProvidedByExcludedPack(key, source))
                || packNamesById.containsKey(packId)) {
                continue;
            }
            packNamesById.put(packId, displayNameForPack(packId));
        }

        List<RequiredMod> out = new ArrayList<>(packNamesById.size());
        for (Map.Entry<String, String> e : packNamesById.entrySet()) {
            out.add(new RequiredMod(e.getKey(), e.getValue()));
        }
        return out;
    }

    /**
     * Injects {@code requiredMods} into a building.json payload for marketplace upload.
     *
     * @return UTF-8 building JSON bytes with requiredMods set (or empty array)
     */
    @Nonnull
    public static byte[] injectIntoBuildingJson(@Nonnull byte[] buildingBytes, @Nonnull byte[] prefabBytes) {
        List<RequiredMod> mods = computeFromPrefabBytes(prefabBytes);
        JsonObject building;
        try {
            building = GSON.fromJson(new String(buildingBytes, StandardCharsets.UTF_8), JsonObject.class);
        } catch (RuntimeException e) {
            building = null;
        }
        if (building == null) {
            building = new JsonObject();
        }
        building.add("requiredMods", GSON.toJsonTree(mods));
        return GSON.toJson(building).getBytes(StandardCharsets.UTF_8);
    }

    /** True when every required pack is currently loaded (empty/null list always satisfied). */
    public static boolean isSatisfied(@Nullable Collection<RequiredMod> requiredMods) {
        if (requiredMods == null || requiredMods.isEmpty()) {
            return true;
        }
        AssetModule assets = AssetModule.get();
        if (assets == null) {
            return false;
        }
        for (RequiredMod mod : requiredMods) {
            if (mod == null) {
                continue;
            }
            String id = mod.getId().trim();
            if (id.isEmpty()) {
                continue;
            }
            if (assets.getAssetPack(id) == null) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    public static List<String> missingPackNames(@Nullable Collection<RequiredMod> requiredMods) {
        if (requiredMods == null || requiredMods.isEmpty()) {
            return List.of();
        }
        AssetModule assets = AssetModule.get();
        List<String> missing = new ArrayList<>();
        for (RequiredMod mod : requiredMods) {
            if (mod == null) {
                continue;
            }
            String id = mod.getId().trim();
            if (id.isEmpty()) {
                continue;
            }
            if (assets == null || assets.getAssetPack(id) == null) {
                missing.add(mod.getName());
            }
        }
        return missing;
    }

    private static void collectNamedAssets(@Nullable JsonElement arrEl, @Nonnull Set<String> out) {
        if (arrEl == null || !arrEl.isJsonArray()) {
            return;
        }
        for (JsonElement el : arrEl.getAsJsonArray()) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonElement nameEl = el.getAsJsonObject().get("name");
            if (nameEl != null && nameEl.isJsonPrimitive() && nameEl.getAsJsonPrimitive().isString()) {
                String name = nameEl.getAsString().trim();
                if (!name.isEmpty()) {
                    out.add(CommunityPrefabSafety.normalizeChanceName(name));
                }
            }
        }
    }

    private static void collectEntityAssetStrings(@Nullable JsonElement entitiesEl, @Nonnull Set<String> out) {
        if (entitiesEl == null || !entitiesEl.isJsonArray()) {
            return;
        }
        for (JsonElement entity : entitiesEl.getAsJsonArray()) {
            collectStringLeaves(entity, out);
        }
    }

    private static void collectStringLeaves(@Nullable JsonElement el, @Nonnull Set<String> out) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString().trim();
            if (!s.isEmpty() && s.length() <= 128 && looksLikeAssetKey(s)) {
                out.add(s);
            }
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                collectStringLeaves(child, out);
            }
            return;
        }
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                collectStringLeaves(e.getValue(), out);
            }
        }
    }

    /** Skip obvious non-asset noise (UUIDs, binary markers, very short tokens). */
    private static boolean looksLikeAssetKey(@Nonnull String s) {
        if (s.length() < 3) {
            return false;
        }
        if (s.indexOf(' ') >= 0 || s.indexOf('\n') >= 0) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z'
                || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9'
                || c == '_'
                || c == '-'
                || c == ':'
                || c == '.'
                || c == '/')) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static AssetSource resolveAssetSource(@Nonnull String assetKey) {
        String pack = BlockType.getAssetMap().getAssetPack(assetKey);
        if (pack != null && !pack.isBlank()) {
            return new AssetSource(pack.trim(), AssetKind.BLOCK);
        }
        pack = Item.getAssetMap().getAssetPack(assetKey);
        if (pack != null && !pack.isBlank()) {
            return new AssetSource(pack.trim(), AssetKind.ITEM);
        }
        pack = Fluid.getAssetMap().getAssetPack(assetKey);
        if (pack != null && !pack.isBlank()) {
            return new AssetSource(pack.trim(), AssetKind.FLUID);
        }
        return null;
    }

    private static boolean isExcludedPack(@Nonnull String packId) {
        return EXCLUDED_PACK_IDS.contains(packId);
    }

    static boolean shouldRequirePack(@Nullable String activePackId, boolean providedByExcludedPack) {
        return activePackId != null
            && !activePackId.isBlank()
            && !isExcludedPack(activePackId)
            && !providedByExcludedPack;
    }

    /**
     * An external pack may patch a vanilla/Aetherhaven asset and become the active provider. That
     * does not make the patching pack a hard dependency because the underlying asset still exists
     * when the patch is absent.
     */
    private static boolean isProvidedByExcludedPack(@Nonnull String assetKey, @Nullable AssetSource source) {
        if (source == null) {
            return false;
        }
        for (String packId : EXCLUDED_PACK_IDS) {
            Set<String> keys = switch (source.kind()) {
                case BLOCK -> BlockType.getAssetMap().getKeysForPack(packId);
                case ITEM -> Item.getAssetMap().getKeysForPack(packId);
                case FLUID -> Fluid.getAssetMap().getKeysForPack(packId);
            };
            if (keys != null && keys.contains(assetKey)) {
                return true;
            }
        }
        return false;
    }

    private enum AssetKind {
        BLOCK,
        ITEM,
        FLUID
    }

    private record AssetSource(@Nonnull String packId, @Nonnull AssetKind kind) {}

    @Nonnull
    private static String displayNameForPack(@Nonnull String packId) {
        AssetPack pack = AssetModule.get() != null ? AssetModule.get().getAssetPack(packId) : null;
        if (pack != null) {
            PluginManifest manifest = pack.getManifest();
            if (manifest != null) {
                String name = manifest.getName();
                if (name != null && !name.isBlank()) {
                    return name.trim();
                }
            }
            String packName = pack.getName();
            if (packName != null && !packName.isBlank()) {
                int colon = packName.indexOf(':');
                if (colon >= 0 && colon < packName.length() - 1) {
                    return packName.substring(colon + 1);
                }
                return packName;
            }
        }
        int colon = packId.indexOf(':');
        if (colon >= 0 && colon < packId.length() - 1) {
            return packId.substring(colon + 1);
        }
        return packId;
    }
}
