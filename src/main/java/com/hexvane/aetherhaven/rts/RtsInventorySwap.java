package com.hexvane.aetherhaven.rts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

public final class RtsInventorySwap {
    private static final Gson GSON = new GsonBuilder().create();

    private RtsInventorySwap() {}

    @Nonnull
    public static String saveHotbar(@Nonnull Ref<EntityStore> playerRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return GSON.toJson(new HotbarSnapshot((byte) 0, List.of()));
        }
        List<HotbarSlotSnapshot> slots = new ArrayList<>();
        short cap = hotbar.getInventory().getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack stack = hotbar.getInventory().getItemStack(i);
            if (ItemStack.isEmpty(stack)) {
                slots.add(new HotbarSlotSnapshot(i, null));
            } else {
                slots.add(new HotbarSlotSnapshot(i, encodeStack(stack)));
            }
        }
        return GSON.toJson(new HotbarSnapshot(hotbar.getActiveSlot(), slots));
    }

    public static void equipCommandTools(@Nonnull Ref<EntityStore> playerRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return;
        }
        for (short i = 0; i < hotbar.getInventory().getCapacity(); i++) {
            hotbar.getInventory().setItemStackForSlot(i, ItemStack.EMPTY);
        }
        setTool(hotbar, 0, AetherhavenConstants.RTS_FLAG_ITEM_ID);
        setTool(hotbar, 1, AetherhavenConstants.RTS_SWORD_ITEM_ID);
        setTool(hotbar, 2, AetherhavenConstants.RTS_SELECT_ALL_ITEM_ID);
        setTool(hotbar, 3, AetherhavenConstants.RTS_SELECT_KNIGHT_ITEM_ID);
        setTool(hotbar, 4, AetherhavenConstants.RTS_SELECT_ARCHER_ITEM_ID);
        setTool(hotbar, 5, AetherhavenConstants.RTS_SELECT_MAGE_ITEM_ID);
        setTool(hotbar, 6, AetherhavenConstants.RTS_STANCE_BANNER_ITEM_ID);
        setTool(hotbar, 7, AetherhavenConstants.RTS_FREE_ITEM_ID);
        setTool(hotbar, 8, AetherhavenConstants.RTS_EXIT_ITEM_ID);
        hotbar.setActiveSlot((byte) 0, playerRef, accessor);
        accessor.putComponent(playerRef, InventoryComponent.Hotbar.getComponentType(), hotbar);
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            RtsHotbarSync.syncHotbarRewriteToClient(pr, hotbar);
        }
    }

    public static void restoreHotbar(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable String savedJson
    ) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return;
        }
        for (short i = 0; i < hotbar.getInventory().getCapacity(); i++) {
            hotbar.getInventory().setItemStackForSlot(i, ItemStack.EMPTY);
        }
        byte restoreSlot = 0;
        if (savedJson != null && !savedJson.isBlank()) {
            try {
                HotbarSnapshot snapshot = GSON.fromJson(savedJson, HotbarSnapshot.class);
                if (snapshot != null) {
                    restoreSlot = snapshot.activeSlot;
                    if (snapshot.slots != null) {
                        for (HotbarSlotSnapshot snap : snapshot.slots) {
                            if (snap == null || snap.slot < 0 || snap.slot >= hotbar.getInventory().getCapacity()) {
                                continue;
                            }
                            ItemStack restored = snap.toItemStack();
                            if (restored != null && !ItemStack.isEmpty(restored)) {
                                hotbar.getInventory().setItemStackForSlot(snap.slot, restored);
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                try {
                    HotbarSlotSnapshot[] legacySlots = GSON.fromJson(savedJson, HotbarSlotSnapshot[].class);
                    if (legacySlots != null) {
                        for (HotbarSlotSnapshot snap : legacySlots) {
                            if (snap == null || snap.slot < 0 || snap.slot >= hotbar.getInventory().getCapacity()) {
                                continue;
                            }
                            ItemStack restored = snap.toItemStack();
                            if (restored != null && !ItemStack.isEmpty(restored)) {
                                hotbar.getInventory().setItemStackForSlot(snap.slot, restored);
                            }
                        }
                    }
                } catch (RuntimeException ignoredLegacy) {
                }
            }
        }
        if (restoreSlot >= 0 && restoreSlot < hotbar.getInventory().getCapacity()) {
            hotbar.setActiveSlot(restoreSlot, playerRef, accessor);
        }
        accessor.putComponent(playerRef, InventoryComponent.Hotbar.getComponentType(), hotbar);
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            RtsHotbarSync.syncHotbarRewriteToClient(pr, hotbar);
        }
    }

    @Nonnull
    private static String encodeStack(@Nonnull ItemStack stack) {
        BsonValue encoded = ItemStack.CODEC.encode(stack, new ExtraInfo());
        if (encoded.isDocument()) {
            return encoded.asDocument().toJson();
        }
        return encoded.toString();
    }

    @Nullable
    private static ItemStack decodeStack(@Nullable String stackJson) {
        if (stackJson == null || stackJson.isBlank()) {
            return null;
        }
        try {
            BsonDocument doc = BsonDocument.parse(stackJson);
            return ItemStack.CODEC.decode(doc, new ExtraInfo());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void setTool(@Nonnull InventoryComponent.Hotbar hotbar, int slot, @Nonnull String itemId) {
        hotbar.getInventory().setItemStackForSlot((short) slot, new ItemStack(itemId, 1));
    }

    private static final class HotbarSnapshot {
        @SerializedName("activeSlot")
        byte activeSlot;

        @SerializedName("slots")
        List<HotbarSlotSnapshot> slots;

        HotbarSnapshot(byte activeSlot, List<HotbarSlotSnapshot> slots) {
            this.activeSlot = activeSlot;
            this.slots = slots;
        }
    }

    private static final class HotbarSlotSnapshot {
        @SerializedName("slot")
        short slot;

        /** Full {@link ItemStack} payload (metadata, durability, etc.). */
        @SerializedName("stack")
        @Nullable
        String stack;

        /** Legacy saves before v2.0.1 only stored id and quantity. */
        @SerializedName("itemId")
        @Nullable
        String itemId;

        @SerializedName("quantity")
        int quantity;

        HotbarSlotSnapshot(short slot, @Nullable String stack) {
            this.slot = slot;
            this.stack = stack;
        }

        @Nullable
        ItemStack toItemStack() {
            ItemStack fromCodec = decodeStack(stack);
            if (fromCodec != null && !ItemStack.isEmpty(fromCodec)) {
                return fromCodec;
            }
            if (itemId == null || itemId.isBlank() || quantity <= 0) {
                return null;
            }
            return new ItemStack(itemId.trim(), quantity);
        }
    }
}
