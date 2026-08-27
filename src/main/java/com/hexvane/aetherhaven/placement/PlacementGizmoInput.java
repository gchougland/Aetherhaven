package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Commits placement gizmo drags when the player releases the mouse button. */
public final class PlacementGizmoInput {
    private PlacementGizmoInput() {}

    public static void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.register(PlayerMouseButtonEvent.class, PlacementGizmoInput::onMouseButton);
    }

    private static void onMouseButton(@Nonnull PlayerMouseButtonEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }
        MouseButtonType type = event.getMouseButton().mouseButtonType;
        MouseButtonState state = event.getMouseButton().state;
        if (type != MouseButtonType.Left || state != MouseButtonState.Released) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !PlacementGizmoService.isGizmoMoveActive(playerRef.getUuid())) {
            return;
        }
        PlacementGizmoService.commitDrag(playerRef, ref, store);
    }
}
