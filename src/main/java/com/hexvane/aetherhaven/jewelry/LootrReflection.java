package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reflection bridge to LootrHytale types. Aetherhaven runs in an isolated classloader, so we must not import
 * {@code noobanidus.*} from shipped classes.
 */
public final class LootrReflection {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PluginIdentifier LOOTR_PLUGIN_ID = new PluginIdentifier("Lootr", "Lootr");
    private static final String EMPTY_CONTAINER_CLASS = "noobanidus.mods.lootr.container.EmptySimpleItemContainer";

    private static volatile boolean loggedResolveDetail;

    private LootrReflection() {}

    @Nullable
    public static ComponentType<ChunkStore, ?> resolveLootContainerType() {
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            logResolveDetail("Lootr hook waiting: PluginManager not ready.");
            return null;
        }
        PluginBase base = manager.getPlugin(LOOTR_PLUGIN_ID);
        if (base == null) {
            logResolveDetail("Lootr hook waiting: Lootr:Lootr plugin not in PluginManager.");
            return null;
        }
        if (!base.isEnabled()) {
            logResolveDetail("Lootr hook waiting: Lootr plugin not enabled yet.");
            return null;
        }
        if (!(base instanceof JavaPlugin javaPlugin)) {
            logResolveDetail("Lootr hook failed: Lootr plugin is not a JavaPlugin.");
            return null;
        }
        try {
            Object typeObj = javaPlugin.getClass().getMethod("getLootContainerType").invoke(javaPlugin);
            if (typeObj == null) {
                logResolveDetail("Lootr hook waiting: getLootContainerType returned null.");
                return null;
            }
            @SuppressWarnings("unchecked")
            ComponentType<ChunkStore, ?> typed = (ComponentType<ChunkStore, ?>) typeObj;
            loggedResolveDetail = false;
            return typed;
        } catch (NoSuchMethodException e) {
            logResolveDetail("Lootr hook failed: getLootContainerType missing on " + javaPlugin.getClass().getName());
            return null;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logResolveDetail("Lootr hook waiting: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return null;
        } catch (ReflectiveOperationException e) {
            logResolveDetail("Lootr hook failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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

    public static boolean isPlaceholderContainer(@Nonnull SimpleItemContainer inv) {
        return EMPTY_CONTAINER_CLASS.equals(inv.getClass().getName());
    }

    @Nullable
    public static String getDroplist(@Nonnull Object lootBlock) {
        try {
            Object value = lootBlock.getClass().getMethod("getDroplist").invoke(lootBlock);
            if (value instanceof String s) {
                return s;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through
        }
        return null;
    }

    public static boolean hasWorldLootDroplist(@Nonnull Object lootBlock) {
        String droplist = getDroplist(lootBlock);
        return droplist != null && !droplist.isEmpty();
    }

    @Nullable
    public static String getOriginalBlockId(@Nonnull Object lootBlock) {
        try {
            Field field = lootBlock.getClass().getDeclaredField("originalBlock");
            field.setAccessible(true);
            Object value = field.get(lootBlock);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through
        }
        return null;
    }

    /** True for dungeon or spawner chests converted by Lootr (not blank player storage). */
    public static boolean isSpawnerConvertedLootChest(@Nonnull Object lootBlock) {
        return hasWorldLootDroplist(lootBlock) || getOriginalBlockId(lootBlock) != null;
    }

    public static boolean isWorldLootChest(
        @Nonnull Store<ChunkStore> store,
        @Nonnull Ref<ChunkStore> blockEntityRef,
        @Nonnull Object lootBlock
    ) {
        if (LootChestWorldGenerated.isWorldLootChest(store, blockEntityRef)) {
            return true;
        }
        return isSpawnerConvertedLootChest(lootBlock);
    }

    @Nullable
    public static String resolveEligibleBlockTypeId(
        @Nonnull Object lootBlock,
        @Nonnull Store<ChunkStore> chunkStore,
        @Nonnull BlockModule.BlockStateInfo state
    ) {
        String original = getOriginalBlockId(lootBlock);
        if (original != null) {
            return original;
        }
        return LootChestBonusInjectSystem.resolveBlockTypeIdForState(chunkStore, state);
    }
}
