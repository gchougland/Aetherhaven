package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Optional bridge to [LootrHytale](https://github.com/LootrMinecraft/LootrHytale) when that mod is present. */
public final class LootrIntegration {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile boolean systemsRegistered;
    @Nullable
    private static volatile ComponentType<ChunkStore, ?> lootComponentType;

    private LootrIntegration() {}

    public static boolean isAvailable() {
        return lootComponentType != null;
    }

    public static boolean isHooked() {
        return systemsRegistered;
    }

    @Nullable
    public static ComponentType<ChunkStore, ?> getLootComponentType() {
        return lootComponentType;
    }

    public static boolean tryInitialize() {
        if (lootComponentType != null) {
            return true;
        }
        ComponentType<ChunkStore, ?> type = LootrReflection.resolveLootContainerType();
        if (type == null) {
            return false;
        }
        lootComponentType = type;
        return true;
    }

    /** Registers Lootr hook systems once Lootr is ready. Safe to call repeatedly until hooked. */
    public static void registerIfAvailable(@Nonnull AetherhavenPlugin plugin) {
        if (systemsRegistered) {
            return;
        }
        if (!tryInitialize()) {
            return;
        }
        ComponentType<ChunkStore, ?> type = lootComponentType;
        if (type == null) {
            return;
        }
        try {
            plugin.getChunkStoreRegistry().registerSystem(new LootChestLootrWorldLootMarkSystem(type));
            systemsRegistered = true;
            LOGGER.atInfo().log("Lootr compatibility enabled.");
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Lootr compatibility failed while registering hook systems.");
        }
    }
}
