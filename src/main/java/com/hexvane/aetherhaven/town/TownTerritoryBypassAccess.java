package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Server operators, {@link AetherhavenConstants#PERMISSION_TOWN_TERRITORY_BYPASS}, or
 * {@link AetherhavenConstants#PERMISSION_TOWN_ADMIN} may break, place, and use blocks inside any town claim.
 */
public final class TownTerritoryBypassAccess {
    private TownTerritoryBypassAccess() {}

    public static boolean canModifyAnyTownClaim(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return false;
        }
        return canModifyAnyTownClaim(playerRef, player);
    }

    public static boolean canModifyAnyTownClaim(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return false;
        }
        if (PermissionsModule.get().getGroupsForUser(uuid).contains(HytalePermissionsProvider.GROUP_ADMIN)) {
            return true;
        }
        return playerRef.hasPermission(AetherhavenConstants.PERMISSION_TOWN_TERRITORY_BYPASS, false)
            || playerRef.hasPermission(AetherhavenConstants.PERMISSION_TOWN_ADMIN, false);
    }
}
