package com.hexvane.aetherhaven.rts;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collection;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Hides the commander model from other players while in RTS mode.
 * The commander stays visible to themselves so per-viewer entity updates on their
 * own character (e.g. armor-driven dynamic light) keep working during command mode.
 */
public final class RtsCommanderVisibility {
    private RtsCommanderVisibility() {}

    public static void hideCommander(@Nonnull World world, @Nonnull Ref<EntityStore> commanderRef) {
        UUID commanderUuid = commanderUuid(commanderRef);
        if (commanderUuid == null) {
            return;
        }
        for (PlayerRef viewer : playerRefs(world)) {
            if (commanderUuid.equals(viewer.getUuid())) {
                continue;
            }
            viewer.getHiddenPlayersManager().hidePlayer(commanderUuid);
        }
    }

    public static void showCommander(@Nonnull World world, @Nonnull Ref<EntityStore> commanderRef) {
        UUID commanderUuid = commanderUuid(commanderRef);
        if (commanderUuid == null) {
            return;
        }
        for (PlayerRef viewer : playerRefs(world)) {
            if (commanderUuid.equals(viewer.getUuid())) {
                continue;
            }
            viewer.getHiddenPlayersManager().showPlayer(commanderUuid);
        }
    }

    @Nonnull
    private static Collection<PlayerRef> playerRefs(@Nonnull World world) {
        return world.getPlayerRefs();
    }

    private static UUID commanderUuid(@Nonnull Ref<EntityStore> commanderRef) {
        if (!commanderRef.isValid()) {
            return null;
        }
        UUIDComponent uc = commanderRef.getStore().getComponent(commanderRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }
}
