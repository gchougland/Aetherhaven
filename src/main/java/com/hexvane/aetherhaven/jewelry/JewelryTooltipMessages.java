package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.util.MessageUtil;
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
            return Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.tooltipUnattuned");
        }
        JewelryRarity rarity = JewelryMetadata.readRarity(stack);
        String rarityKey = rarity != null ? rarity.wireName() : "COMMON";
        Message header =
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.tooltipHeader")
                .param(
                    "rarity",
                    Message.translation("aetherhaven_jewelry_geode.aetherhaven.jewelry.rarity." + rarityKey)
                        .color(JewelryTooltipText.rarityColorHex(rarityKey)));
        if (!JewelryMetadata.isAppraised(stack)) {
            header = header
                .insert(Message.raw("\n"))
                .insert(Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.tooltipUnappraised").color("#8A8F98"));
        }
        Message body = header;
        var traits = JewelryMetadata.readTraits(stack);
        if (!JewelryMetadata.isAppraised(stack)) {
            int lines = !traits.isEmpty() ? traits.size() : (rarity != null ? rarity.traitCount() : 1);
            for (int i = 0; i < lines; i++) {
                body = body
                    .insert(Message.raw("\n"))
                    .insert(Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.handmirror.traitHidden").color("#6B7080"));
            }
        } else {
            for (JewelryMetadata.RolledTrait rt : traits) {
                String n = JewelryStatTuning.formatForDisplay(rt.statId(), rt.amount());
                String amountParam = (rt.amount() < 0f) ? n : ("+" + n);
                Message line =
                    Message.translation("aetherhaven_jewelry_geode.aetherhaven.jewelry.traitLine")
                        .param("stat", Message.translation("aetherhaven_jewelry_geode.aetherhaven.jewelry.stat." + rt.statId()).color("#C9B6E8"))
                        .param("amount", Message.raw(amountParam).color("#5EFFC8"));
                body = body.insert(Message.raw("\n")).insert(line);
            }
        }
        return body;
    }

    /**
     * Plain text for {@code ItemStack} instance metadata ({@code TranslationProperties.Description}) so the default
     * item tooltip description can match rolled/appraised state (same role as {@code items.Aetherhaven_Jewelry.genericDescription} on the asset).
     */
    @Nonnull
    public static String toPlainEnglishDescription(@Nullable ItemStack stack) {
        return MessageUtil.formatMessageToPlainString(forStack(stack).getFormattedMessage());
    }

    /** Plain instance description (no Hytale markup) for static tooltip merge; amounts are rounded. */
    @Nonnull
    public static String toPlainEnglishForLocale(@Nullable ItemStack stack, @Nonnull String locale) {
        if (locale == null || locale.isBlank() || "en-US".equalsIgnoreCase(locale)) {
            return toPlainEnglishDescription(stack);
        }
        return toPlainEnglishDescription(stack);
    }
}
