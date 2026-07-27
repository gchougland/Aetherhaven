package com.hexvane.aetherhaven.territory;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.map.TownBorderMapOverlayService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownExpansionClaimService {
    private TownExpansionClaimService() {}

    @Nullable
    public static String tryClaimChunk(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        int chunkX,
        int chunkZ
    ) {
        if (!town.playerCanClaimTerritoryExpansion(playerUuid)) {
            return "aetherhaven_town.aetherhaven.ui.expansion.err.noPermission";
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var cfg = plugin.getConfig().get();
        if (TownTerritoryClaims.expansionClaimLimitReached(town, cfg)) {
            return "aetherhaven_town.aetherhaven.ui.expansion.err.claimLimit";
        }
        if (!TownTerritoryClaims.canClaimBlock(town, chunkX, chunkZ, tm.allTowns(), cfg)) {
            return "aetherhaven_town.aetherhaven.ui.expansion.err.notClaimable";
        }
        long cost = TownTerritoryClaims.nextClaimBlockCostGold(town, cfg);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return "aetherhaven_common.aetherhaven.common.pluginNotLoaded";
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
        boolean allowTreasury = town.playerCanSpendTreasuryGold(playerUuid);
        if (GoldCoinPayment.totalAvailable(town, inv, allowTreasury) < cost) {
            return "aetherhaven_town.aetherhaven.ui.expansion.err.notEnoughGold";
        }
        if (GoldCoinPayment.trySpendReturningBreakdown(town, inv, cost, allowTreasury) == null) {
            return "aetherhaven_town.aetherhaven.ui.expansion.err.notEnoughGold";
        }
        if (!TownTerritoryClaims.addClaimBlock(town, chunkX, chunkZ)) {
            return "aetherhaven_town.aetherhaven.ui.expansion.err.notClaimable";
        }
        tm.updateTown(town);
        TownBorderMapOverlayService.invalidateOverlaysForWorld(world);
        TownBorderMapOverlayService.refreshPlayer(world, playerUuid);
        return null;
    }
}
