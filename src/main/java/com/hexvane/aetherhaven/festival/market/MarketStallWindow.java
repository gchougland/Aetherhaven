package com.hexvane.aetherhaven.festival.market;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Shared town stall chest. Saves contents when the player closes it. */
public final class MarketStallWindow extends ContainerWindow {
    @Nonnull
    private final UUID townId;

    public MarketStallWindow(@Nonnull ItemContainer itemContainer, @Nonnull UUID townId) {
        super(itemContainer);
        this.townId = townId;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        super.onClose0(ref, componentAccessor);
        ItemContainer container = getItemContainer();
        if (container instanceof SimpleItemContainer simple) {
            MarketSession session = MarketSessionIndex.get(townId);
            if (session != null) {
                session.snapshotFromContainer(simple);
            }
        }
        if (!ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world != null) {
            world.execute(() -> MarketStallService.syncPlayerDisplays(world, townId));
        }
    }
}
