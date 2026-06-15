package com.hexvane.aetherhaven.construction;

import com.hypixel.hytale.math.vector.Rotation3f;

import com.hypixel.hytale.math.vector.Vector3fUtil;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Tracks and mutates per-plot deposited construction materials. */
public final class PlotMaterialDepositService {
    private PlotMaterialDepositService() {}

    public static int depositedCount(@Nonnull PlotInstance plot, @Nonnull MaterialRequirement line) {
        if (line.getResourceTypeId() != null && !line.getResourceTypeId().isBlank()) {
            String rt = line.getResourceTypeId().trim();
            int sum = 0;
            for (MaterialRequirement d : plot.getDepositedMaterials()) {
                String itemId = d.getItemId();
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                if (InventoryMaterials.itemStackHasResourceType(new ItemStack(itemId, 1), rt)) {
                    sum += d.getCount();
                }
            }
            return sum;
        }
        for (MaterialRequirement d : plot.getDepositedMaterials()) {
            if (matchesLine(d, line)) {
                return d.getCount();
            }
        }
        return 0;
    }

    public static boolean allDeposited(@Nonnull PlotInstance plot, @Nonnull List<MaterialRequirement> required) {
        for (MaterialRequirement line : required) {
            if (line.getCount() <= 0) {
                continue;
            }
            if (depositedCount(plot, line) < line.getCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves up to {@code remaining} of {@code line} from {@code source} into the plot deposit list.
     *
     * @return amount actually deposited
     */
    public static int depositFromContainer(
        @Nonnull PlotInstance plot,
        @Nonnull MaterialRequirement line,
        @Nonnull CombinedItemContainer source
    ) {
        int need = line.getCount() - depositedCount(plot, line);
        if (need <= 0) {
            return 0;
        }
        if (line.getResourceTypeId() != null && !line.getResourceTypeId().isBlank()) {
            return depositResourceType(plot, line.getResourceTypeId().trim(), need, source);
        }
        int available = InventoryMaterials.count(source, line);
        int take = Math.min(need, available);
        if (take <= 0) {
            return 0;
        }
        if (line.getItemId() != null && !line.getItemId().isBlank()) {
            source.removeItemStack(new ItemStack(line.getItemId(), take));
            addDeposited(plot, line, take);
        }
        return take;
    }

    private static int depositResourceType(
        @Nonnull PlotInstance plot,
        @Nonnull String resourceTypeId,
        int need,
        @Nonnull CombinedItemContainer source
    ) {
        int deposited = 0;
        for (short i = 0; i < source.getCapacity() && deposited < need; i++) {
            ItemStack stack = source.getItemStack(i);
            if (ItemStack.isEmpty(stack) || !InventoryMaterials.itemStackHasResourceType(stack, resourceTypeId)) {
                continue;
            }
            int take = Math.min(need - deposited, stack.getQuantity());
            var tx = source.removeItemStackFromSlot(i, take);
            if (!tx.succeeded()) {
                continue;
            }
            addDeposited(plot, MaterialRequirement.ofItem(stack.getItemId(), take), take);
            deposited += take;
        }
        return deposited;
    }

    public static void clearDeposits(@Nonnull PlotInstance plot) {
        plot.setDepositedMaterials(List.of());
    }

    /** Returns materials refunded (for giving back to player). */
    @Nonnull
    public static List<MaterialRequirement> refundAll(@Nonnull PlotInstance plot) {
        List<MaterialRequirement> copy = List.copyOf(plot.getDepositedMaterials());
        clearDeposits(plot);
        return copy;
    }

    /** Returns deposited materials to the player; overflow spawns as item drops at {@code dropPosition}. */
    public static void refundToPlayer(
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<MaterialRequirement> mats,
        @Nonnull Vector3d dropPosition
    ) {
        List<ItemStack> overflow = new ArrayList<>();
        for (MaterialRequirement m : mats) {
            if (m.getCount() <= 0) {
                continue;
            }
            String itemId = m.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            int left = m.getCount();
            while (left > 0) {
                int chunk = Math.min(left, 999);
                ItemStack stack = new ItemStack(itemId, chunk);
                ItemStackTransaction tx = player.giveItem(stack, ref, store);
                if (!tx.succeeded()) {
                    overflow.add(stack);
                    break;
                }
                ItemStack remainder = tx.getRemainder();
                int notAdded = ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity();
                if (notAdded >= chunk) {
                    overflow.add(stack);
                    break;
                }
                left -= chunk - notAdded;
                if (notAdded > 0) {
                    overflow.add(remainder);
                }
            }
        }
        if (!overflow.isEmpty()) {
            spawnItemDrops(store, ref, overflow, dropPosition);
        }
    }

    /** Gives item stacks to the player; overflow spawns as item drops at {@code dropPosition} (preserves stack metadata). */
    public static void refundItemStacksToPlayer(
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<ItemStack> stacks,
        @Nonnull Vector3d dropPosition
    ) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            ItemStackTransaction tx = player.giveItem(stack, ref, store);
            if (!tx.succeeded()) {
                overflow.add(stack);
                continue;
            }
            ItemStack remainder = tx.getRemainder();
            if (!ItemStack.isEmpty(remainder)) {
                overflow.add(remainder);
            }
        }
        if (!overflow.isEmpty()) {
            spawnItemDrops(store, ref, overflow, dropPosition);
        }
    }

    private static void spawnItemDrops(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull List<ItemStack> stacks,
        @Nonnull Vector3d dropPosition
    ) {
        if (stacks.isEmpty()) {
            return;
        }
        UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (playerUuid == null) {
            return;
        }
        UUID uuid = playerUuid.getUuid();
        store.forEachChunk(
            Query.and(Player.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent u = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (u == null || !uuid.equals(u.getUuid())) {
                        continue;
                    }
                    Holder<EntityStore>[] holders =
                        ItemComponent.generateItemDrops(commandBuffer, stacks, dropPosition, Rotation3f.ZERO);
                    if (holders.length > 0) {
                        commandBuffer.addEntities(holders, AddReason.SPAWN);
                    }
                    return;
                }
            }
        );
    }

    private static void addDeposited(@Nonnull PlotInstance plot, @Nonnull MaterialRequirement line, int amount) {
        List<MaterialRequirement> list = new ArrayList<>(plot.getDepositedMaterials());
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            MaterialRequirement d = list.get(i);
            if (matchesLine(d, line)) {
                int next = d.getCount() + amount;
                list.set(i, copyLine(line, next));
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(copyLine(line, amount));
        }
        plot.setDepositedMaterials(list);
    }

    @Nonnull
    private static MaterialRequirement copyLine(@Nonnull MaterialRequirement line, int count) {
        if (line.getResourceTypeId() != null && !line.getResourceTypeId().isBlank()) {
            return MaterialRequirement.ofResourceType(line.getResourceTypeId(), count);
        }
        String itemId = line.getItemId();
        return MaterialRequirement.ofItem(itemId != null ? itemId : "", count);
    }

    private static boolean matchesLine(@Nonnull MaterialRequirement a, @Nonnull MaterialRequirement b) {
        if (a.getResourceTypeId() != null && !a.getResourceTypeId().isBlank()) {
            return a.getResourceTypeId().equals(b.getResourceTypeId());
        }
        if (b.getResourceTypeId() != null && !b.getResourceTypeId().isBlank()) {
            return false;
        }
        String ai = a.getItemId();
        String bi = b.getItemId();
        return ai != null && bi != null && ai.equals(bi);
    }

}
