package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Grants placeable blocks for substep placement and revokes them when the player steps back. */
public final class PlotCreatorSubstepGrants {
    private PlotCreatorSubstepGrants() {}

    @Nonnull
    public static List<ItemStack> stacksFor(
        @Nonnull PlotCreatorSubstepType type,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement requirement
    ) {
        int qty = grantQuantity(type, requirement);
        if (qty <= 0) {
            return List.of();
        }
        return switch (type) {
            case MANAGEMENT_BLOCK -> List.of(new ItemStack(AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID, qty));
            case PRODUCTION_STORAGE -> List.of(new ItemStack(AetherhavenConstants.BLOCK_PRODUCTION_STORAGE, qty));
            case TREASURY_BLOCK -> List.of(new ItemStack(AetherhavenConstants.TREASURY_BLOCK_TYPE_ID, qty));
            case SHOP_SAFE_BLOCK -> List.of(new ItemStack(AetherhavenConstants.SHOP_SAFE_ITEM_ID, qty));
            case INN_BELL_BLOCK -> List.of(new ItemStack(AetherhavenConstants.INN_BELL_BLOCK_TYPE_ID, qty));
            case PLANNING_DESK_POI -> List.of(new ItemStack("Aetherhaven_Town_Planning_Desk", qty));
            case SHOP_SPOT -> List.of(new ItemStack(AetherhavenConstants.SHOP_SPOT_ITEM_ID, qty));
            case TOURIST_PORTAL_BLOCK -> List.of(new ItemStack(AetherhavenConstants.TOURIST_PORTAL_ITEM_ID, qty));
            case QUEST_BOARD_POI -> List.of(new ItemStack(AetherhavenConstants.QUEST_BOARD_ITEM_ID, qty));
            default -> List.of();
        };
    }

    /** One placeable block per required substep; optional substeps (minCount 0) grant nothing. */
    private static int grantQuantity(
        @Nonnull PlotCreatorSubstepType type,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement requirement
    ) {
        if (requirement.minCount() <= 0) {
            return 0;
        }
        if (type == PlotCreatorSubstepType.SHOP_SPOT) {
            return AetherhavenConstants.SHOP_SPOT_ITEM_MAX_STACK;
        }
        if (type == PlotCreatorSubstepType.TOURIST_PORTAL_BLOCK) {
            return AetherhavenConstants.TOURIST_PORTAL_ITEM_MAX_STACK;
        }
        return 1;
    }

    public static void grantCurrentSubstep(
        @Nonnull PlotCreatorSession session,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        int idx = session.getDraft().getSubstepIndex();
        if (session.getSubstepGrants().containsKey(idx)) {
            return;
        }
        PlotBuildingKindRequirements.SubstepRequirement sub = PlotCreatorService.currentSubstep(session.getDraft());
        if (sub == null) {
            return;
        }
        List<ItemStack> stacks = stacksFor(sub.type(), sub);
        if (stacks.isEmpty()) {
            return;
        }
        Map<String, Integer> granted = new HashMap<>();
        for (ItemStack stack : stacks) {
            player.giveItem(stack, ref, store);
            granted.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
        }
        session.getSubstepGrants().put(idx, granted);
    }

    public static void revokeSubstepIndex(
        @Nonnull PlotCreatorSession session,
        @Nonnull Player player,
        int substepIndex
    ) {
        Map<String, Integer> granted = session.getSubstepGrants().remove(substepIndex);
        if (granted == null || granted.isEmpty()) {
            return;
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : granted.entrySet()) {
            removeGrantedItemId(inv, entry.getKey(), entry.getValue());
        }
    }

    /** Removes plot-creator grant items by id (partial ok when blocks were placed from the stack). */
    private static void removeGrantedItemId(
        @Nonnull ItemContainer inv,
        @Nonnull String itemId,
        int quantity
    ) {
        if (quantity <= 0) {
            return;
        }
        int remaining = quantity;
        for (short i = 0; i < inv.getCapacity() && remaining > 0; i++) {
            ItemStack stack = inv.getItemStack(i);
            if (ItemStack.isEmpty(stack) || !itemId.equals(stack.getItemId())) {
                continue;
            }
            int take = Math.min(remaining, stack.getQuantity());
            ItemStackSlotTransaction tx = inv.removeItemStackFromSlot(i, take);
            if (tx.succeeded()) {
                remaining -= take;
            }
        }
    }

    public static void revokeAll(@Nonnull PlotCreatorSession session, @Nonnull Player player) {
        ObjectArrayList<Integer> indices = new ObjectArrayList<>(session.getSubstepGrants().keySet());
        for (int idx : indices) {
            revokeSubstepIndex(session, player, idx);
        }
    }

    public static void revokeAllIfPresent(
        @Nonnull PlotCreatorSession session,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        if (ref == null || store == null) {
            session.getSubstepGrants().clear();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            session.getSubstepGrants().clear();
            return;
        }
        revokeAll(session, player);
    }
}
