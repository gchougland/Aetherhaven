package com.hexvane.aetherhaven.blockpalette;

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

/** Unlocks a block palette for the player's active town. */
public final class BlockPaletteUnlockService {
    public enum Result {
        UNLOCKED,
        ALREADY_UNLOCKED,
        NO_TOWN,
        UNKNOWN_ITEM
    }

    private BlockPaletteUnlockService() {}

    @Nonnull
    public static Result tryUnlock(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull String paletteId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return Result.UNKNOWN_ITEM;
        }
        BlockPaletteDefinition def = plugin.getBlockPaletteCatalog().get(paletteId);
        if (def == null) {
            return Result.UNKNOWN_ITEM;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveActiveTown(world, store, playerEntityRef, tm);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation("aetherhaven_block_palettes.aetherhaven.blockPalette.unlock.noTown"),
                NotificationStyle.Danger
            );
            return Result.NO_TOWN;
        }
        if (town.hasBlockPaletteUnlocked(def.getId())) {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.translation("aetherhaven_block_palettes.aetherhaven.blockPalette.unlock.already")
                    .param("name", def.getDisplayName()),
                NotificationStyle.Warning
            );
            return Result.ALREADY_UNLOCKED;
        }
        town.unlockBlockPalette(def.getId());
        tm.updateTown(town);
        UiSoundEffects.play2dUi(
            playerEntityRef,
            store,
            com.hexvane.aetherhaven.AetherhavenConstants.SFX_WORKBENCH_UPGRADE_COMPLETE
        );
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            Message.translation("aetherhaven_block_palettes.aetherhaven.blockPalette.unlock.success")
                .param("name", def.getDisplayName()),
            NotificationStyle.Success
        );
        return Result.UNLOCKED;
    }

    /**
     * Unlocks every catalog palette for the player's active town. Returns how many were newly unlocked, or {@code -1}
     * when the player has no town.
     */
    public static int unlockAllForPlayerTown(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return -1;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveActiveTown(world, store, playerEntityRef, tm);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) {
            return -1;
        }
        int added = 0;
        for (BlockPaletteDefinition def : plugin.getBlockPaletteCatalog().allById().values()) {
            if (town.unlockBlockPalette(def.getId())) {
                added++;
            }
        }
        if (added > 0) {
            tm.updateTown(town);
        }
        return added;
    }
}
