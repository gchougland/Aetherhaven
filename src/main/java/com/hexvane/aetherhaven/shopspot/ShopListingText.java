package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.alchemy.AlchemyFlask;
import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipMessages;
import com.hexvane.aetherhaven.ui.UiMaterialLabels;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.util.MessageUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What a buyer is told about a listing: the name and the details the item's own tooltip shows for that very stack,
 * so nobody pays for a flask without seeing what is in it or for a tool without seeing how worn it is.
 */
public final class ShopListingText {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop.listing";
    private static final double WEAR_EPS = 1e-6;

    private ShopListingText() {}

    /** The name the stack's own tooltip shows: its own name when it carries one, otherwise its item's. */
    @Nonnull
    public static Message name(@Nonnull ItemStack stack) {
        ItemDisplayMetadata display = display(stack);
        if (display != null && display.getName() != null) {
            return display.getName();
        }
        return UiMaterialLabels.itemNameMessage(stack.getItemId());
    }

    /** The listing's name, for stalls that keep a whole stack and for those that only know an item id. */
    @Nonnull
    public static Message name(@Nonnull ShopSpotRecord record) {
        ItemStack listed = listed(record);
        if (listed != null) {
            return name(listed);
        }
        String itemId = record.getItemId();
        return itemId != null ? UiMaterialLabels.itemNameMessage(itemId) : Message.raw("");
    }

    /**
     * What sets this stack apart from any other of its item: its own description (a flask's charges and effects, a
     * backpack's contents), a ring's rolled stats, and how worn it is. Null for an ordinary item in mint condition,
     * whose name already says everything.
     */
    @Nullable
    public static Message details(@Nonnull ItemStack stack) {
        Message body = null;
        if (JewelryItemIds.isJewelry(stack.getItemId())) {
            body = JewelryTooltipMessages.forStack(stack);
        } else {
            ItemDisplayMetadata display = display(stack);
            if (display != null && display.getDescription() != null) {
                body = display.getDescription();
            }
        }
        // A flask counts its charges as durability and already says so in its own description.
        if (!isWorn(stack) || AlchemyFlask.isFlask(stack)) {
            return body;
        }
        Message wear =
            Message.translation(MSG + ".durability")
                .param("left", String.valueOf(Math.round(stack.getDurability())))
                .param("full", String.valueOf(Math.round(stack.getMaxDurability())));
        return body == null ? wear : Message.join(body, Message.raw("\n"), wear);
    }

    @Nullable
    public static Message details(@Nonnull ShopSpotRecord record) {
        ItemStack listed = listed(record);
        return listed != null ? details(listed) : null;
    }

    /** A tool or weapon that has lost durability. Unbreakable items and items without durability are never worn. */
    public static boolean isWorn(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || stack.isUnbreakable()) {
            return false;
        }
        double full = stack.getMaxDurability();
        return full > WEAR_EPS && stack.getDurability() < full - WEAR_EPS;
    }

    /** Text without colours, for a hover tooltip. Translations resolve to English, as other page tooltips do. */
    @Nonnull
    public static String plain(@Nullable Message message) {
        return message == null ? "" : MessageUtil.formatMessageToPlainString(message.getFormattedMessage());
    }

    /**
     * The stack a buyer would receive, one item of it. For a stall that only knows an item id this is rebuilt the
     * way the sale itself rebuilds it, so rolled jewelry shows the stats it will really have.
     */
    @Nullable
    public static ItemStack listed(@Nonnull ShopSpotRecord record) {
        String itemId = record.getItemId();
        if (itemId == null) {
            return null;
        }
        ItemStack whole = ShopListing.stack(record, 1);
        if (whole != null) {
            return whole;
        }
        // Without saved stats a ring is rolled at the moment it is sold; there is nothing true to show before that.
        if (JewelryItemIds.isJewelry(itemId) && record.getJewelryMetaJson() == null) {
            return null;
        }
        return ShopSpotJewelrySupport.buildListingStack(itemId, 1, record);
    }

    @Nullable
    private static ItemDisplayMetadata display(@Nonnull ItemStack stack) {
        try {
            return stack.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
