package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-world {@link PropRegistry} cache, following {@link com.hexvane.aetherhaven.town.AetherhavenWorldRegistries}'s
 * {@code getOrCreate} pattern. Kept as a standalone registry (rather than editing
 * {@code AetherhavenWorldRegistries} directly) so the parent plugin can fold this in later.
 */
public final class PropWorldRegistries {
    private static final ConcurrentHashMap<String, PropRegistry> PROP_REGISTRIES = new ConcurrentHashMap<>();

    private PropWorldRegistries() {}

    @Nonnull
    public static PropRegistry getOrCreatePropRegistry(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        return PROP_REGISTRIES.computeIfAbsent(world.getName(), n -> {
            PropRegistry r = new PropRegistry(world);
            PropPersistence.load(world, plugin, r);
            r.setPersistCallback(() -> PropPersistence.save(world, plugin, r));
            return r;
        });
    }

    /** Existing prop registry only; does not load or create. Safe for read-only lookups (break protection, HUD). */
    @Nullable
    public static PropRegistry getPropRegistryIfLoaded(@Nonnull World world) {
        return PROP_REGISTRIES.get(world.getName());
    }

    public static void unloadWorld(@Nonnull World world) {
        PropRegistry r = PROP_REGISTRIES.remove(world.getName());
        if (r != null && !PersistentWorldSupport.isTemporaryInstance(world)) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin != null) {
                PropPersistence.save(world, plugin, r);
            }
        }
    }

    /** Save all loaded prop registries (e.g. server shutdown). */
    public static void saveAll() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        for (PropRegistry r : PROP_REGISTRIES.values()) {
            PropPersistence.save(r.getWorld(), plugin, r);
        }
    }
}
