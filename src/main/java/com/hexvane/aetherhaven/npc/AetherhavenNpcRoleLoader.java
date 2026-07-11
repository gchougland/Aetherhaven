package com.hexvane.aetherhaven.npc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Loads crossmod NPC roles from {@link AetherhavenAssetPaths#NPC_ROLES} and optional models from
 * {@link AetherhavenAssetPaths#NPC_MODELS}. Those paths are not scanned by Hytale's NPC/Model loaders, so other
 * mods can ship villagers that only register when Aetherhaven is present.
 *
 * <p>Runs after {@link NPCPlugin#PRIORITY_LOAD_NPC} so {@code OpenAetherhavenDialogue}, attitude groups, and
 * {@code Aetherhaven_Human} (and other engine assets) already exist.
 */
public final class AetherhavenNpcRoleLoader {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * After {@link NPCPlugin#PRIORITY_LOAD_NPC} ({@code -8}) and after registry assets (models under
     * {@code Server/Models}, attitude groups) have loaded.
     */
    public static final short PRIORITY_LOAD_AETHERHAVEN_NPC_ROLES = -6;

    private AetherhavenNpcRoleLoader() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        plugin
            .getEventRegistry()
            .register(PRIORITY_LOAD_AETHERHAVEN_NPC_ROLES, LoadAssetEvent.class, event -> loadAllPacks());
        plugin.getEventRegistry().register(AssetPackRegisterEvent.class, event -> loadPack(event.getAssetPack()));
    }

    public static void loadAllPacks() {
        AssetModule module = AssetModule.get();
        if (module == null) {
            return;
        }
        for (AssetPack pack : module.getAssetPacks()) {
            loadPack(pack);
        }
    }

    public static void loadPack(@Nonnull AssetPack pack) {
        loadModels(pack);
        loadRoles(pack);
    }

    private static void loadModels(@Nonnull AssetPack pack) {
        Path dir = pack.getRoot().resolve(AetherhavenAssetPaths.NPC_MODELS);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try {
            var result = ModelAsset.getAssetStore().loadAssetsFromDirectory(pack.getName(), dir);
            int count = result != null && result.getLoadedAssets() != null ? result.getLoadedAssets().size() : 0;
            LOGGER.atInfo().log(
                "Loaded %s model(s) from %s in pack %s",
                count,
                AetherhavenAssetPaths.NPC_MODELS,
                pack.getName()
            );
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log(
                "Failed to load models from %s in pack %s",
                AetherhavenAssetPaths.NPC_MODELS,
                pack.getName()
            );
        }
    }

    private static void loadRoles(@Nonnull AssetPack pack) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return;
        }
        Path dir = pack.getRoot().resolve(AetherhavenAssetPaths.NPC_ROLES);
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files = listRoleJsonFiles(dir);
        if (files.isEmpty()) {
            return;
        }
        // Components before spawnable roles so Reference: Component_* resolves.
        files.sort(
            Comparator
                .comparing((Path p) -> !pathContainsComponents(p))
                .thenComparing(p -> p.toString().replace('\\', '/'))
        );
        BuilderManager builders = npc.getBuilderManager();
        List<String> errors = new ObjectArrayList<>();
        Int2ObjectOpenHashMap<BuilderInfo> loaded = new Int2ObjectOpenHashMap<>();
        int ok = 0;
        for (Path file : files) {
            try {
                int index = builders.loadFile(file, false, errors);
                if (index < 0) {
                    continue;
                }
                BuilderInfo info = builders.tryGetBuilderInfo(index);
                if (info != null) {
                    loaded.put(index, info);
                    ok++;
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load Aetherhaven NPC role %s", file);
            }
        }
        for (String error : errors) {
            LOGGER.atSevere().log("Aetherhaven NpcRoles: %s", error);
        }
        errors.clear();
        if (!loaded.isEmpty()) {
            builders.validateAllLoadedBuilders(loaded, true, errors);
            for (String error : errors) {
                LOGGER.atSevere().log("Aetherhaven NpcRoles validate: %s", error);
            }
            builders.onAllBuildersLoaded(loaded);
        }
        LOGGER.atInfo().log(
            "Loaded %s NPC role file(s) from %s in pack %s",
            ok,
            AetherhavenAssetPaths.NPC_ROLES,
            pack.getName()
        );
    }

    @Nonnull
    private static List<Path> listRoleJsonFiles(@Nonnull Path dir) {
        List<Path> out = new ArrayList<>();
        try (var walk = Files.walk(dir)) {
            walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().startsWith("!"))
                .forEach(out::add);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to walk %s", dir);
        }
        return out;
    }

    private static boolean pathContainsComponents(@Nonnull Path path) {
        String s = path.toString().replace('\\', '/');
        return s.contains("/Components/") || s.contains("/components/");
    }
}
