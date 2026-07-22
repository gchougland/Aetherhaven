package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Normalizes creative or legacy Gaia's Draught stacks that lack BSON metadata (default 1 sip, not asset max). */
public final class GaiaDraughtInventoryNormalizeSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    public GaiaDraughtInventoryNormalizeSystem() {
        super(InventoryChangeEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InventoryChangeEvent event
    ) {
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack st = inv.getItemStack(slot);
            if (st == null || st.isEmpty() || !id.equals(st.getItemId())) {
                continue;
            }
            if (GaiaDraughtMetadata.hasPersistedProgress(st)) {
                continue;
            }
            ItemStack normalized = GaiaDraughtMetadata.ensureInitialized(st);
            if (stacksMatch(st, normalized)) {
                continue;
            }
            GaiaDraughtService.replaceStackInCombinedSlot(inv, slot, st, normalized);
        }
    }

    private static boolean stacksMatch(@Nonnull ItemStack a, @Nonnull ItemStack b) {
        return Math.round(a.getDurability()) == Math.round(b.getDurability())
            && Math.round(a.getMaxDurability()) == Math.round(b.getMaxDurability())
            && GaiaDraughtMetadata.getCapacity(a) == GaiaDraughtMetadata.getCapacity(b);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
