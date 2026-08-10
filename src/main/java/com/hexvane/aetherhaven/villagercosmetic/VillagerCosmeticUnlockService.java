package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.UiSoundEffects;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Unlocks a villager cosmetic for the player's active town. */
public final class VillagerCosmeticUnlockService {
    public enum Result {
        UNLOCKED,
        ALREADY_UNLOCKED,
        NO_TOWN,
        UNKNOWN_ITEM
    }

    private VillagerCosmeticUnlockService() {}

    @Nonnull
    public static Result tryUnlock(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull String itemId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return Result.UNKNOWN_ITEM;
        }
        VillagerCosmeticDefinition def = plugin.getVillagerCosmeticCatalog().byUnlockItemId(itemId);
        if (def == null) {
            return Result.UNKNOWN_ITEM;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveActiveTown(world, store, playerEntityRef, tm);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation("aetherhaven_villager_cosmetics.aetherhaven.villagerCosmetic.unlock.noTown"),
                NotificationStyle.Danger
            );
            return Result.NO_TOWN;
        }
        if (town.hasVillagerCosmeticUnlocked(def.id())) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation("aetherhaven_villager_cosmetics.aetherhaven.villagerCosmetic.unlock.already")
                    .param("name", Message.translation(def.displayNameKey())),
                NotificationStyle.Warning
            );
            return Result.ALREADY_UNLOCKED;
        }
        town.unlockVillagerCosmetic(def.id());
        tm.updateTown(town);
        UiSoundEffects.play2dUi(
            playerEntityRef,
            store,
            com.hexvane.aetherhaven.AetherhavenConstants.SFX_WORKBENCH_UPGRADE_COMPLETE
        );
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            Message.translation("aetherhaven_villager_cosmetics.aetherhaven.villagerCosmetic.unlock.success")
                .param("name", Message.translation(def.displayNameKey())),
            NotificationStyle.Success
        );
        return Result.UNLOCKED;
    }
}
