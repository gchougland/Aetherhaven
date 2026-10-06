package com.hexvane.aetherhaven.prop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBufferBlockEntry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3i;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("prop")
class PropPrefabOpsTest {
    private static BlockStore registeredStore;

    @BeforeAll
    static void registerBlockMap() {
        if (AssetRegistry.getAssetStore(BlockType.class) == null) {
            registeredStore = AssetRegistry.register(new BlockStore.Config().build());
        }
    }

    @AfterAll
    static void unregisterBlockMap() throws ReflectiveOperationException {
        if (registeredStore != null) {
            AssetRegistry.unregister(registeredStore);
            var field = BlockType.class.getDeclaredField("ASSET_STORE");
            field.setAccessible(true);
            if (field.get(null) == registeredStore) field.set(null, null);
        }
    }

    @Test
    void distantBreakDoesNotTraverseAnyPrefabVoxels() {
        var calls = new AtomicInteger();
        var buffer = countTraversals(sparsePrefab(), calls);
        assertFalse(PropPrefabOps.blockBelongsToProp(new Vector3i(100, 80, -100), Rotation.Ninety,
            buffer, 200, 80, -100));
        assertEquals(0, calls.get(), "Distant terrain must not scan this prop's cells");
    }

    @ParameterizedTest
    @CsvSource({
        "None, -3, 2, 4, -1",
        "Ninety, 2, 3, -1, -4",
        "OneEighty, 3, -2, -4, 1",
        "TwoSeventy, -2, -3, 1, 4"
    })
    void rotatedNegativeLocalCellsKeepExactMembership(Rotation yaw, int firstX, int firstZ,
            int secondX, int secondZ) {
        var origin = new Vector3i(-100, 80, -200);
        var buffer = sparsePrefab();
        // Two asymmetric points straddle local zero, including a negative height.
        assertTrue(PropPrefabOps.blockBelongsToProp(origin, yaw, buffer,
            origin.x + firstX, 78, origin.z + firstZ));
        assertTrue(PropPrefabOps.blockBelongsToProp(origin, yaw, buffer,
            origin.x + secondX, 81, origin.z + secondZ));
        // These positions lie inside the reserved box, but air and filler are not origin solids.
        assertFalse(PropPrefabOps.blockBelongsToProp(origin, yaw, buffer,
            origin.x + firstX, 79, origin.z + firstZ));
        assertFalse(PropPrefabOps.blockBelongsToProp(origin, yaw, buffer,
            origin.x + firstX, 80, origin.z + firstZ));
        assertFalse(PropPrefabOps.blockBelongsToProp(origin, yaw, buffer, origin.x, 80, origin.z));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyAndEntityOnlyPrefabsDoNotReserveTerrainBlocks() {
        var origin = new Vector3i(-10, 80, 20);
        assertFalse(PropPrefabOps.blockBelongsToProp(origin, Rotation.None,
            PrefabBuffer.newBuilder().build().newAccess(), origin.x, origin.y, origin.z));
        var builder = PrefabBuffer.newBuilder();
        // A nonempty entity column with no voxel entries, matching an entity-only native prefab.
        // The null holder keeps the test independent of game entity component registration.
        builder.addColumn(0, 0, PrefabBufferBlockEntry.EMPTY_ARRAY, new Holder[]{null});
        assertFalse(PropPrefabOps.blockBelongsToProp(origin, Rotation.None,
            builder.build().newAccess(), origin.x, origin.y, origin.z));
    }

    private static IPrefabBuffer sparsePrefab() {
        var builder = PrefabBuffer.newBuilder();
        var filler = new PrefabBufferBlockEntry(0, 1, "Unknown");
        filler.filler = 1;
        builder.addColumn(-3, 2, new PrefabBufferBlockEntry[]{
            new PrefabBufferBlockEntry(-2, 1, "Unknown"), new PrefabBufferBlockEntry(-1), filler
        }, null);
        builder.addColumn(4, -1, new PrefabBufferBlockEntry[]{
            new PrefabBufferBlockEntry(1, 1, "Unknown")
        }, null);
        return builder.build().newAccess();
    }

    private static IPrefabBuffer countTraversals(IPrefabBuffer delegate, AtomicInteger count) {
        return (IPrefabBuffer) Proxy.newProxyInstance(IPrefabBuffer.class.getClassLoader(),
            new Class<?>[]{IPrefabBuffer.class}, (proxy, method, args) -> {
                if (method.getName().equals("forEach")) count.incrementAndGet();
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    /** Block ID normalization needs a real asset map, but no running server or file monitors. */
    private static final class BlockStore extends AssetStore<String, BlockType, BlockTypeAssetMap<String, BlockType>> {
        private final EventBus events = new EventBus(false);
        private BlockStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, BlockType> updated, AssetUpdateQuery query) {}
        private static final class Config extends Builder<String, BlockType, BlockTypeAssetMap<String, BlockType>, Config> {
            private Config() {
                super(String.class, BlockType.class, new BlockTypeAssetMap<>(BlockType[]::new, BlockType::getGroup));
                setPath("Item/Block/Blocks");
                setCodec(BlockType.CODEC);
                setKeyFunction(BlockType::getId);
                setReplaceOnRemove(BlockType::getUnknownFor);
            }
            @Override public BlockStore build() { return new BlockStore(this); }
        }
    }
}
