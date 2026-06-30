package com.hexvane.aetherhaven.shopspot;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Gives shop items to a player, spawning leftovers at the stall when inventory is full. */
public final class ShopSpotItemDelivery {

    public record Result(boolean deliveredToInventory, boolean droppedOnGround) {
        public boolean succeeded() {
            return deliveredToInventory || droppedOnGround;
        }
    }

    private ShopSpotItemDelivery() {}

    @Nonnull
    public static Result grantAtShop(
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ItemStack stack,
        @Nonnull Vector3i shopBlock
    ) {
        if (ItemStack.isEmpty(stack)) {
            return new Result(false, false);
        }
        Vector3d dropPos = new Vector3d(shopBlock.x + 0.5, shopBlock.y + 0.5, shopBlock.z + 0.5);
        ItemStackTransaction tx = player.giveItem(stack, playerRef, accessor);
        List<ItemStack> overflow = new ArrayList<>();
        boolean deliveredToInventory = false;
        if (!tx.succeeded()) {
            overflow.add(stack);
        } else {
            ItemStack remainder = tx.getRemainder();
            if (ItemStack.isEmpty(remainder)) {
                deliveredToInventory = true;
            } else if (remainder.getQuantity() < stack.getQuantity()) {
                deliveredToInventory = true;
                overflow.add(remainder);
            } else {
                overflow.add(remainder);
            }
        }
        boolean dropped = false;
        if (!overflow.isEmpty()) {
            dropped = spawnItemDrops(accessor, commandBuffer, overflow, dropPos);
        }
        return new Result(deliveredToInventory, dropped);
    }

    /**
     * Spawns overflow item entities. Pass the interaction {@link CommandBuffer} when the store
     * processing lock may already be held; never call {@link Store#forEachChunk} from that path.
     * When only a {@link Store} is available, attempts a direct spawn and defers via {@link World#execute}
     * if the store rejects the write while processing.
     */
    private static boolean spawnItemDrops(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull List<ItemStack> stacks,
        @Nonnull Vector3d dropPosition
    ) {
        if (stacks.isEmpty()) {
            return false;
        }
        if (commandBuffer != null) {
            Holder<EntityStore>[] holders =
                ItemComponent.generateItemDrops(commandBuffer, stacks, dropPosition, Rotation3f.ZERO);
            if (holders.length == 0) {
                return false;
            }
            commandBuffer.addEntities(holders, AddReason.SPAWN);
            return true;
        }
        if (accessor instanceof Store<EntityStore> store) {
            try {
                return spawnItemDropsOnStore(store, stacks, dropPosition);
            } catch (IllegalStateException e) {
                if (!isStoreProcessingConflict(e)) {
                    throw e;
                }
                World world = store.getExternalData().getWorld();
                List<ItemStack> copy = List.copyOf(stacks);
                Vector3d pos = new Vector3d(dropPosition);
                world.execute(() -> {
                    Store<EntityStore> deferred = world.getEntityStore().getStore();
                    if (deferred != null) {
                        spawnItemDropsOnStore(deferred, copy, pos);
                    }
                });
                return true;
            }
        }
        return false;
    }

    private static boolean isStoreProcessingConflict(@Nonnull IllegalStateException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("Store is currently processing");
    }

    private static boolean spawnItemDropsOnStore(
        @Nonnull Store<EntityStore> store,
        @Nonnull List<ItemStack> stacks,
        @Nonnull Vector3d dropPosition
    ) {
        Holder<EntityStore>[] holders =
            ItemComponent.generateItemDrops(store, stacks, dropPosition, Rotation3f.ZERO);
        if (holders.length == 0) {
            return false;
        }
        for (Holder<EntityStore> holder : holders) {
            store.addEntity(holder, AddReason.SPAWN);
        }
        return true;
    }
}
