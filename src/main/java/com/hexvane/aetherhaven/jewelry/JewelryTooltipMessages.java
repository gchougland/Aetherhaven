package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Tooltip / callout text for jewelry stacks (traits when BSON is present; otherwise a short hint). */
public final class JewelryTooltipMessages {
    private JewelryTooltipMessages() {}

    @Nonnull
    public static Message forStack(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
            return Message.raw("");
        }
        if (!JewelryMetadata.hasJewelryMeta(stack)) {
            return Message.translation("server.aetherhaven.ui.handmirror.tooltipUnattuned");
        }
        JewelryRarity rarity = JewelryMetadata.readRarity(stack);
        String rarityKey = rarity != null ? rarity.wireName() : "COMMON";
        Message header =
            Message.translation("server.aetherhaven.ui.handmirror.tooltipHeader")
                .param("rarity", Message.translation("server.aetherhaven.jewelry.rarity." + rarityKey));
        if (!JewelryMetadata.isAppraised(stack)) {
            header = header.insert(Message.raw("\n")).insert(Message.translation("server.aetherhaven.ui.handmirror.tooltipUnappraised"));
        }
        Message body = header;
        for (JewelryMetadata.RolledTrait rt : JewelryMetadata.readTraits(stack)) {
            Message line =
                Message.translation("server.aetherhaven.jewelry.traitLine")
                    .param("stat", Message.translation("server.aetherhaven.jewelry.stat." + rt.statId()))
                    .param("amount", (double) rt.amount());
            body = body.insert(Message.raw("\n")).insert(line);
        }
        return body;
    }
}
