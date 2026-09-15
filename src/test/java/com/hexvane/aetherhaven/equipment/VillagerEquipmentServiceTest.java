package com.hexvane.aetherhaven.equipment;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.event.events.ecs.InventorySetActiveSlotEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class VillagerEquipmentServiceTest {
    // Event dispatch only forwards this identity; no world or entity lookup is needed.
    private final Ref<EntityStore> ref = new Ref<>(null);
    private final List<InventorySetActiveSlotEvent> events = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private ComponentAccessor<EntityStore> accessor() {
        return (ComponentAccessor<EntityStore>) Proxy.newProxyInstance(
            ComponentAccessor.class.getClassLoader(), new Class<?>[] {ComponentAccessor.class},
            (proxy, method, args) -> {
                if (method.getName().equals("invoke") && args.length == 2) {
                    assertSame(ref, args[0]);
                    events.add(assertInstanceOf(InventorySetActiveSlotEvent.class, args[1]));
                    return null;
                }
                throw new AssertionError("Unexpected ECS operation: " + method);
            });
    }

    @Test void refreshingTheHeldSlotDispatchesEventsAndRestoresSelection() {
        var hotbar = new InventoryComponent.Hotbar((short) 3);
        VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, (byte) 0, ref, accessor());
        assertEquals(0, hotbar.getActiveSlot());
        assertTrue(hotbar.consumeOutdatedEquipment());
        assertEquals(2, events.size());
        assertTransition(events.get(0), 0, 1);
        assertTransition(events.get(1), 1, 0);
    }

    @Test void selectingAnotherSlotDispatchesOneEvent() {
        var hotbar = new InventoryComponent.Hotbar((short) 3);
        VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, (byte) 2, ref, accessor());
        assertEquals(2, hotbar.getActiveSlot());
        assertTrue(hotbar.consumeOutdatedEquipment());
        assertEquals(1, events.size());
        assertTransition(events.getFirst(), 0, 2);
    }

    @Test void aSingleSlotHotbarRefreshesThroughTheInactiveSlot() {
        var hotbar = new InventoryComponent.Hotbar((short) 1);
        VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, (byte) 0, ref, accessor());
        assertEquals(0, hotbar.getActiveSlot());
        assertTrue(hotbar.consumeOutdatedEquipment());
        assertEquals(2, events.size());
        assertTransition(events.get(0), 0, -1);
        assertTransition(events.get(1), -1, 0);
    }

    @Test void missingAccessorFailsBeforeChangingTheHeldSlot() {
        var hotbar = new InventoryComponent.Hotbar((short) 3);
        assertThrows(NullPointerException.class,
            () -> VillagerEquipmentService.markHotbarEquipmentDirty(hotbar, (byte) 0, ref, null));
        assertEquals(0, hotbar.getActiveSlot());
        assertFalse(hotbar.consumeOutdatedEquipment());
    }

    private static void assertTransition(InventorySetActiveSlotEvent event, int previous, int next) {
        assertEquals(InventoryComponent.HOTBAR_SECTION_ID, event.getInventorySectionId());
        assertEquals(previous, event.getPreviousSlot());
        assertEquals(next, event.getNewSlot());
    }
}
