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
@Tag("town")
class VillagerEquipmentServiceTest {
    // Event dispatch only forwards this identity; no world or entity lookup is needed.
    private final Ref<EntityStore> ref = new Ref<>(null);
    private final List<InventorySetActiveSlotEvent> events = new ArrayList<>();

    @Test void civilianReturnClearsArmorEvenWhenInsertionFiltersRejectChanges() {
        var armor = new InventoryComponent.Armor((short) 4);
        var inventory = armor.getInventory();
        for (short slot = 0; slot < 4; slot++)
            inventory.setItemStackForSlot(slot, new com.hypixel.hytale.server.core.inventory.ItemStack("test_armor", 1) {
                @Override public com.hypixel.hytale.server.core.asset.type.item.config.Item getItem() {
                    return com.hypixel.hytale.server.core.asset.type.item.config.Item.UNKNOWN;
                }
            }, false);
        inventory.setGlobalFilter(com.hypixel.hytale.server.core.inventory.container.filter.FilterType.DENY_ALL);
        VillagerEquipmentService.clearArmorInventory(armor);
        for (short slot = 0; slot < 4; slot++)
            assertTrue(com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(inventory.getItemStack(slot)));
        assertTrue(armor.consumeOutdatedEquipment());
    }

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

    @Test void offhandEquipmentActivatesAnUnselectedUtilitySlot() {
        var utility = new InventoryComponent.Utility((short) 2);
        utility.setActiveSlot((byte) -1, ref, accessor());
        events.clear();
        VillagerEquipmentService.markOffhandEquipmentDirty(utility, ref, accessor());
        assertEquals(0, utility.getActiveSlot());
        assertTrue(utility.consumeOutdatedEquipment());
        assertEquals(1, events.size());
        assertEquals(InventoryComponent.UTILITY_SECTION_ID, events.getFirst().getInventorySectionId());
        assertEquals(-1, events.getFirst().getPreviousSlot());
        assertEquals(0, events.getFirst().getNewSlot());
    }

    @Test void aCivilianWithoutUtilitySlotsCanReceiveAShieldAfterChangingDuty() {
        var utility = new InventoryComponent.Utility((short) 0);
        assertSame(utility, VillagerEquipmentService.prepareOffhandInventory(utility));
        assertEquals(1, utility.getInventory().getCapacity());
        assertEquals(1, VillagerEquipmentService.prepareOffhandInventory(null).getInventory().getCapacity());
        var existing = new InventoryComponent.Utility((short) 3);
        assertSame(existing, VillagerEquipmentService.prepareOffhandInventory(existing));
        assertEquals(3, existing.getInventory().getCapacity());
    }

    @Test void swappingShieldsRefreshesAnAlreadySelectedUtilitySlot() {
        var utility = new InventoryComponent.Utility((short) 2);
        utility.setActiveSlot((byte) 0, ref, accessor());
        events.clear();
        VillagerEquipmentService.markOffhandEquipmentDirty(utility, ref, accessor());
        assertEquals(0, utility.getActiveSlot());
        assertTrue(utility.consumeOutdatedEquipment());
        assertEquals(2, events.size());
        assertEquals(-1, events.getFirst().getNewSlot());
        assertEquals(0, events.getLast().getNewSlot());
        assertTrue(events.stream().allMatch(e -> e.getInventorySectionId() == InventoryComponent.UTILITY_SECTION_ID));
    }
}
