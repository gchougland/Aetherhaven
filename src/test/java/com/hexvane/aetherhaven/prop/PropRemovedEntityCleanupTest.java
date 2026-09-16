package com.hexvane.aetherhaven.prop;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("prop")
class PropRemovedEntityCleanupTest {
    @TempDir Path temp;

    private PropInstance instance(UUID id) {
        return new PropInstance(id, "test", 0, 0, 0, Rotation.None, java.util.List.of(), java.util.List.of());
    }

    @Test
    void pickupWhileCompanionUnloadedRemovesItAfterRegistrySaveAndReload() throws Exception {
        var registry = new ComponentRegistry<EntityStore>();
        AetherhavenPlacedInstance.register(new ComponentRegistryProxy<>(new ArrayList<>(), registry));
        var props = new PropRegistry(null);
        registry.registerSystem(new PropRemovedEntityCleanupSystem(ignored -> props));
        var store = registry.addStore(null, EmptyResourceStorage.get());
        try {
            UUID id = UUID.randomUUID();
            props.add(instance(id));
            var holder = registry.newHolder();
            holder.addComponent(AetherhavenPlacedInstance.getComponentType(),
                new AetherhavenPlacedInstance(id.toString(), AetherhavenPlacedInstance.Kind.PROP));
            var ref = store.addEntity(holder, AddReason.SPAWN);
            var unloaded = store.removeEntity(ref, RemoveReason.UNLOAD);
            PropEntityOps.removeLinkedEntities(store, id, new HashSet<>(), null);
            props.remove(id);

            Path filePath = temp.resolve("props.json");
            var file = PropWorldFile.fromInstances(props.all());
            file.setRemovedInstanceIds(props.removedInstanceIds());
            file.writeAtomic(filePath);
            var restored = PropWorldFile.readOrEmpty(filePath);
            props.replaceAll(PropWorldFile.toInstances(restored));
            props.restoreRemovedInstanceIds(restored.getRemovedInstanceIds());

            assertTrue(props.wasRemoved(id));
            var reloaded = store.addEntity(unloaded, AddReason.LOAD);
            assertNull(reloaded, "Hytale returns null when an add hook removes the new entity");
            assertEquals(0, store.getEntityCount());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void missingOrMalformedRegistryEntryNeverAuthorizesRemoval() throws Exception {
        var registry = new ComponentRegistry<EntityStore>();
        AetherhavenPlacedInstance.register(new ComponentRegistryProxy<>(new ArrayList<>(), registry));
        var props = new PropRegistry(null);
        registry.registerSystem(new PropRemovedEntityCleanupSystem(ignored -> props));
        var store = registry.addStore(null, EmptyResourceStorage.get());
        try {
            for (String id : java.util.List.of(UUID.randomUUID().toString(), "", "invalid-id")) {
                var holder = registry.newHolder();
                holder.addComponent(AetherhavenPlacedInstance.getComponentType(),
                    new AetherhavenPlacedInstance(id, AetherhavenPlacedInstance.Kind.PROP));
                assertTrue(store.addEntity(holder, AddReason.LOAD).isValid());
            }
            assertEquals(3, store.getEntityCount());
            Path legacy = temp.resolve("legacy.json");
            Files.writeString(legacy, "{\"props\": []}");
            assertTrue(PropWorldFile.readOrEmpty(legacy).getRemovedInstanceIds().isEmpty());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void activeInstanceWinsOverStaleTombstone() {
        var props = new PropRegistry(null);
        UUID id = UUID.randomUUID();
        props.add(instance(id));
        props.remove(id);
        assertTrue(props.wasRemoved(id));
        props.add(instance(id));
        assertFalse(props.wasRemoved(id));
        props.restoreRemovedInstanceIds(java.util.List.of(id));
        assertFalse(props.wasRemoved(id));
    }
}
