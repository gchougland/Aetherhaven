package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Town Journal Settings tab: operators or {@link AetherhavenConstants#PERMISSION_JOURNAL_SETTINGS} may open server
 * tuning and repair tools.
 */
public final class JournalSettingsAccess {
    private JournalSettingsAccess() {}

    public static boolean canOpen(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        UUID uuid = uc.getUuid();
        if (PermissionsModule.get().getGroupsForUser(uuid).contains(HytalePermissionsProvider.OP_GROUP)) {
            return true;
        }
        return player.hasPermission(AetherhavenConstants.PERMISSION_JOURNAL_SETTINGS, false);
    }
}
