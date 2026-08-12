package com.hexvane.aetherhaven.prefab;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nonnull;

/** Background warm of early-game construction prefabs so first placement is not a cold parse hitch. */
public final class CorePrefabWarmup {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final List<String> CORE_CONSTRUCTION_IDS = List.of(
        AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE,
        AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL,
        AetherhavenConstants.CONSTRUCTION_PLOT_INN,
        AetherhavenConstants.CONSTRUCTION_PLOT_FARM,
        AetherhavenConstants.CONSTRUCTION_PLOT_BUILDERS_HUT,
        AetherhavenConstants.CONSTRUCTION_PLOT_PARK,
        AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE
    );

    private CorePrefabWarmup() {}

    public static void warmAsync(@Nonnull ConstructionCatalog catalog, @Nonnull ExecutorService executor) {
        executor.execute(() -> warmNow(catalog));
    }

    private static void warmNow(@Nonnull ConstructionCatalog catalog) {
        int warmed = 0;
        for (String id : CORE_CONSTRUCTION_IDS) {
            ConstructionDefinition def = catalog.get(id);
            if (def == null) {
                continue;
            }
            String prefabPath = def.getPrefabPath();
            if (prefabPath == null || prefabPath.isBlank()) {
                continue;
            }
            Path path = PrefabResolveUtil.resolvePrefabPath(prefabPath);
            if (path == null) {
                continue;
            }
            try {
                PrefabBufferUtil.getCached(path);
                warmed++;
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Core prefab warm failed for %s (%s)", id, prefabPath);
            }
        }
        LOGGER.atInfo().log("Warmed %s core construction prefab(s)", warmed);
    }
}
