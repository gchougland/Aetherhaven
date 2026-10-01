package com.hexvane.aetherhaven.npc;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import java.lang.reflect.Proxy;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class AetherhavenNpcRoleLoaderTest {
    private static BuilderInfo role(int index, String name) {
        // Keep the real BuilderInfo/BuilderManager state transition without booting NPC assets.
        Builder<?> builder = (Builder<?>) Proxy.newProxyInstance(Builder.class.getClassLoader(),
            new Class<?>[] {Builder.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getDependencies" -> IntSets.EMPTY_SET;
                case "hasDynamicDependencies" -> false;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new BuilderInfo(index, name, builder, Path.of(name + ".json"));
    }

    @Test
    void reloadedCrossmodRoleIsVisibleWhenMemoryCatalogRefreshes() {
        Set<String> catalog = new HashSet<>();
        BuilderManager builders = new BuilderManager() {
            @Override public void onAllBuildersLoaded(Int2ObjectMap<BuilderInfo> loaded) {
                catalog.clear();
                // The memory provider filters out any BuilderInfo that is not valid yet.
                loaded.values().stream().filter(BuilderInfo::isValid).forEach(info -> catalog.add(info.getKeyName()));
            }
        };
        for (int reload = 0; reload < 2; reload++) {
            BuilderInfo fisherman = role(2, "CozyFishing_Fisherman");
            assertFalse(fisherman.isValid());
            Int2ObjectMap<BuilderInfo> loaded = new Int2ObjectOpenHashMap<>();
            loaded.put(fisherman.getIndex(), fisherman);
            AetherhavenNpcRoleLoader.finishLoading(builders, loaded);
            assertEquals(Set.of("CozyFishing_Fisherman"), catalog);
            assertTrue(fisherman.isValid());
        }
    }

    @Test
    void failedAssetValidationIsNotOverriddenWhenPublishing() {
        BuilderInfo invalid = role(2, "Invalid_Role");
        invalid.setValidated(false);
        Int2ObjectMap<BuilderInfo> loaded = new Int2ObjectOpenHashMap<>();
        loaded.put(invalid.getIndex(), invalid);
        boolean[] notified = {false};
        BuilderManager builders = new BuilderManager() {
            @Override public void onAllBuildersLoaded(Int2ObjectMap<BuilderInfo> roles) {
                notified[0] = true;
                assertFalse(roles.get(2).isValid());
            }
        };
        AetherhavenNpcRoleLoader.finishLoading(builders, loaded);
        assertTrue(notified[0]);
        assertFalse(invalid.isValid());
    }
}
