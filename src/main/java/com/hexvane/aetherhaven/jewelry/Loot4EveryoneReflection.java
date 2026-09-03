package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reflection bridge to Loot4Everyone. Aetherhaven runs in an isolated classloader, so we must not import
 * {@code org.mimstar.plugin.*} from shipped classes.
 */
public final class Loot4EveryoneReflection {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PluginIdentifier PLUGIN_ID = new PluginIdentifier("MimStar", "Loot4Everyone");

    private static volatile boolean loggedResolveDetail;

    @Nullable
    private static volatile ResourceType<ChunkStore, ?> templateResourceType;
    @Nullable
    private static volatile Method hasTemplateMethod;
    @Nullable
    private static volatile Method getDropListMethod;

    private Loot4EveryoneReflection() {}

    public static boolean tryResolve() {
        if (templateResourceType != null && hasTemplateMethod != null) {
            return true;
        }
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            logResolveDetail("Loot4Everyone hook waiting: PluginManager not ready.");
            return false;
        }
        PluginBase base = manager.getPlugin(PLUGIN_ID);
        if (base == null) {
            logResolveDetail("Loot4Everyone hook waiting: MimStar:Loot4Everyone plugin not in PluginManager.");
            return false;
        }
        if (!base.isEnabled()) {
            logResolveDetail("Loot4Everyone hook waiting: Loot4Everyone plugin not enabled yet.");
            return false;
        }
        if (!(base instanceof JavaPlugin javaPlugin)) {
            logResolveDetail("Loot4Everyone hook failed: Loot4Everyone plugin is not a JavaPlugin.");
            return false;
        }
        try {
            Object instance = javaPlugin.getClass().getMethod("get").invoke(null);
            if (instance == null) {
                logResolveDetail("Loot4Everyone hook waiting: Loot4Everyone.get() returned null.");
                return false;
            }
            Object typeObj = instance.getClass().getMethod("getlootChestTemplateResourceType").invoke(instance);
            if (typeObj == null) {
                logResolveDetail("Loot4Everyone hook waiting: getlootChestTemplateResourceType returned null.");
                return false;
            }
            @SuppressWarnings("unchecked")
            ResourceType<ChunkStore, ?> typed = (ResourceType<ChunkStore, ?>) typeObj;
            Class<?> resourceClass = typed.getTypeClass();
            Method hasTemplate = resourceClass.getMethod("hasTemplate", int.class, int.class, int.class);
            Method getDropList = null;
            try {
                getDropList = resourceClass.getMethod("getDropList", int.class, int.class, int.class);
            } catch (NoSuchMethodException ignored) {
                // Optional
            }
            templateResourceType = typed;
            hasTemplateMethod = hasTemplate;
            getDropListMethod = getDropList;
            loggedResolveDetail = false;
            return true;
        } catch (NoSuchMethodException e) {
            logResolveDetail("Loot4Everyone hook failed: API method missing on " + javaPlugin.getClass().getName());
            return false;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logResolveDetail(
                "Loot4Everyone hook waiting: " + cause.getClass().getSimpleName() + ": " + cause.getMessage()
            );
            return false;
        } catch (ReflectiveOperationException e) {
            logResolveDetail(
                "Loot4Everyone hook failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()
            );
            return false;
        }
    }

    @Nullable
    public static ResourceType<ChunkStore, ?> getTemplateResourceType() {
        return templateResourceType;
    }

    public static boolean hasTemplate(@Nonnull World world, int x, int y, int z) {
        ResourceType<ChunkStore, ?> type = templateResourceType;
        Method hasTemplate = hasTemplateMethod;
        if (type == null || hasTemplate == null) {
            return false;
        }
        try {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            Object template = chunkStore.getResource(type);
            if (template == null) {
                return false;
            }
            Object result = hasTemplate.invoke(template, x, y, z);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static boolean hasTemplate(@Nonnull Store<ChunkStore> chunkStore, int x, int y, int z) {
        ResourceType<ChunkStore, ?> type = templateResourceType;
        Method hasTemplate = hasTemplateMethod;
        if (type == null || hasTemplate == null) {
            return false;
        }
        try {
            Object template = chunkStore.getResource(type);
            if (template == null) {
                return false;
            }
            Object result = hasTemplate.invoke(template, x, y, z);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Nullable
    public static String getDropList(@Nonnull World world, int x, int y, int z) {
        ResourceType<ChunkStore, ?> type = templateResourceType;
        Method getDropList = getDropListMethod;
        if (type == null || getDropList == null) {
            return null;
        }
        try {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            Object template = chunkStore.getResource(type);
            if (template == null) {
                return null;
            }
            Object result = getDropList.invoke(template, x, y, z);
            return result instanceof String s ? s : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void logResolveDetail(@Nonnull String message) {
        if (loggedResolveDetail) {
            return;
        }
        loggedResolveDetail = true;
        LOGGER.atInfo().log(message);
    }
}
