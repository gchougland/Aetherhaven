package com.hexvane.aetherhaven.shop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class ShopSafeService {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop.safe";

    private ShopSafeService() {}

    public static void withdrawForPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        GoldAccount account = AetherhavenEconomy.account(playerRef, store);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (uc == null || account == null || pr == null) {
            return;
        }
        UUID playerUuid = uc.getUuid();
        long bal = town.getPlayerShopSafeGold(playerUuid);
        if (bal <= 0L) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".empty"),
                NotificationStyle.Warning
            );
            return;
        }
        long give = Math.min(bal, 9999L);
        if (!account.deposit(give)) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".makeRoom"),
                NotificationStyle.Warning
            );
            return;
        }
        town.withdrawPlayerShopSafeGold(playerUuid, give);
        tm.updateTown(town);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation(MSG + ".collected").param("gold", String.valueOf(give)),
            NotificationStyle.Success
        );
    }
}
