package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotBlockInteractSupport {
    private ShopSpotBlockInteractSupport() {}

    @Nullable
    public static ShopSpotRecord resolveRecord(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3i targetBlock
    ) {
        ShopSpotRegistry registry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
        ShopSpotRecord record = registry.getAtBlock(targetBlock.x, targetBlock.y, targetBlock.z);
        if (record == null) {
            UUID spotId = ShopSpotBlockUtil.spotIdAt(world, targetBlock);
            if (spotId != null) {
                record = registry.get(spotId);
            }
        }
        return record;
    }

    public static boolean isCreative(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        return player != null && player.getGameMode() == GameMode.Creative;
    }

    public static boolean isConfiguringPendingSpot(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ShopSpotRecord record
    ) {
        ShopSpotPlayerComponent st = store.getComponent(playerRef, ShopSpotPlayerComponent.getComponentType());
        UUID pending = st != null ? st.getPendingSpotId() : null;
        return pending != null && pending.equals(record.getSpotId());
    }

    public static boolean isShopSpotBlock(@Nonnull World world, @Nonnull Vector3i targetBlock) {
        return AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID.equals(ChunkSectionBlockUtil.blockType(world, targetBlock).getId());
    }
}
