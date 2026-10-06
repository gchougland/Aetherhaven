package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.inventory.transaction.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.bson.*;

/** Simulate on a copy, then commit under the native combined inventory lock. */
public final class HalloweenGameInventory {
    public static final String HEAD = "Aetherhaven_Halloween_Head";
    private static final String KEY = "AetherhavenHalloweenHead";
    private HalloweenGameInventory() {}
    public static ItemStack head(UUID town, HalloweenGameState.Game game) {
        return new ItemStack(HEAD, 1).withMetadata(KEY, new BsonDocument()
            .append("town", new BsonString(town.toString())).append("game", new BsonString(game.id().toString()))
            .append("player", new BsonString(game.player().toString())));
    }
    public static boolean matches(ItemStack stack, UUID town, HalloweenGameState.Game game) {
        if (game == null || ItemStack.isEmpty(stack) || !HEAD.equals(stack.getItemId()) || stack.getQuantity() != 1) return false;
        var data = stack.getFromMetadataOrNull(KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
        return data != null && data.equals(head(town, game).getFromMetadataOrNull(KEY, AetherhavenBsonCodecs.BSON_DOCUMENT));
    }
    public static boolean pickup(ItemContainer inventory, ItemStack head, BooleanSupplier commit) {
        return exchange(inventory, null, List.of(head), commit);
    }
    public static boolean reward(ItemContainer inventory, UUID town, HalloweenGameState.Game game, BooleanSupplier commit) {
        return exchange(inventory, stack -> matches(stack, town, game), List.of(
            new ItemStack("Aetherhaven_Spooky_Candy", HalloweenGameState.CANDIES),
            new ItemStack(AetherhavenConstants.ITEM_GOLD_COIN, HalloweenGameState.GOLD)), commit);
    }
    private static boolean exchange(ItemContainer inventory, java.util.function.Predicate<ItemStack> consume,
                                    List<ItemStack> rewards, BooleanSupplier commit) {
        if (inventory == null) return false;
        boolean[] initialized = {false}, accepted = {false};
        SimpleItemContainer[] next = {null};
        new LockedInventory(inventory).rewrite((slot, existing) -> {
            if (!initialized[0]) {
                initialized[0] = true;
                next[0] = new SimpleItemContainer(inventory.getCapacity());
                boolean consumed = consume == null;
                for (short s = 0; s < inventory.getCapacity(); s++) {
                    var item = inventory.getItemStack(s);
                    if (!consumed && consume.test(item)) { consumed = true; continue; }
                    if (!ItemStack.isEmpty(item)) next[0].setItemStackForSlot(s, item, false);
                }
                if (!consumed) return existing;
                for (var reward : rewards) {
                    var added = next[0].addItemStack(reward);
                    if (!added.succeeded() || !ItemStack.isEmpty(added.getRemainder())) return existing;
                }
                accepted[0] = commit.getAsBoolean();
            }
            return accepted[0] ? next[0].getItemStack(slot) : existing;
        });
        return accepted[0];
    }
    /** Native replaceAll skips empty slots. Rewards must atomically fill those slots too. */
    private static final class LockedInventory extends CombinedItemContainer {
        LockedInventory(ItemContainer inventory) { super(inventory); }
        void rewrite(SlotReplacementFunction replace) {
            var result = writeAction(() -> {
                var changes = new ArrayList<ItemStackSlotTransaction>();
                for (short slot = 0; slot < getCapacity(); slot++) {
                    var old = internal_getSlot(slot);
                    var value = replace.replace(slot,old);
                    if (Objects.equals(old,value)) continue;
                    internal_setSlot(slot,value);
                    changes.add(new ItemStackSlotTransaction(true,ActionType.REPLACE,slot,old,value,old,true,false,false,false,value,value));
                }
                return new ListTransaction<>(true,changes);
            });
            sendUpdate(result);
        }
    }
}
