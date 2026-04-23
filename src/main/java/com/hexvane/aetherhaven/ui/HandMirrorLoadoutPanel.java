package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.jewelry.JewelryGem;
import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import com.hexvane.aetherhaven.jewelry.JewelryStatTuning;
import com.hexvane.aetherhaven.jewelry.JewelryTooltipText;
import com.hexvane.aetherhaven.jewelry.PlayerJewelryLoadout;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Custom UI: list jewelry in loadout slots. */
public final class HandMirrorLoadoutPanel {

    private static final String C_SUB = "#a89878";
    private static final String C_DIM = "#6B7080";
    private static final String C_STAT = "#C9B6E8";
    private HandMirrorLoadoutPanel() {}

    @Nonnull
    public static Message forLoadout(@Nonnull PlayerJewelryLoadout loadout) {
        ItemStack r1 = prepare(loadout.getRing1());
        ItemStack r2 = prepare(loadout.getRing2());
        ItemStack n = prepare(loadout.getNecklace());
        Message m = oneSlot("server.aetherhaven.ui.handmirror.ring1", r1);
        m = m.insert(Message.raw("\n\n"));
        m = m.insert(oneSlot("server.aetherhaven.ui.handmirror.ring2", r2));
        m = m.insert(Message.raw("\n\n"));
        m = m.insert(oneSlot("server.aetherhaven.ui.handmirror.necklace", n));
        return m;
    }

    @Nullable
    private static ItemStack prepare(@Nullable ItemStack st) {
        if (st == null || ItemStack.isEmpty(st) || !JewelryItemIds.isJewelry(st.getItemId())) {
            return null;
        }
        return JewelryMetadata.syncInstanceDescriptionForTooltip(JewelryMetadata.ensureRolled(st));
    }

    @Nonnull
    private static Message oneSlot(@Nonnull String slotTitleKey, @Nullable ItemStack stack) {
        Message m = Message.translation(slotTitleKey).color(C_SUB).insert(Message.raw("\n"));
        if (stack == null || ItemStack.isEmpty(stack)) {
            return m.insert(Message.translation("server.aetherhaven.ui.handmirror.traitsEmptySlot").color(C_DIM));
        }
        Item it = stack.getItem();
        Message itemLine =
            it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()
                ? Message.translation(it.getTranslationKey())
                : Message.raw(stack.getItemId());
        m = m.insert(itemLine.color(C_STAT)).insert(Message.raw("\n"));
        if (!JewelryMetadata.hasJewelryMeta(stack)) {
            return m.insert(
                Message.translation("server.aetherhaven.ui.handmirror.traitsUnattuned").color(C_DIM));
        }
        JewelryRarity rarity = JewelryMetadata.readRarity(stack);
        String rKey = rarity != null ? rarity.wireName() : "COMMON";
        m = m
            .insert(Message.translation("server.aetherhaven.ui.handmirror.traitsRarityLine")
                .param(
                    "rarity",
                    Message.translation("server.aetherhaven.jewelry.rarity." + rKey)
                        .color(JewelryTooltipText.rarityColorHex(rKey)))
                .color(C_DIM))
            .insert(Message.raw("\n"));
        if (!JewelryMetadata.isAppraised(stack)) {
            m = m
                .insert(Message.translation("server.aetherhaven.ui.handmirror.tooltipUnappraised").color(C_DIM))
                .insert(Message.raw("\n"));
            List<JewelryMetadata.RolledTrait> traits = JewelryMetadata.readTraits(stack);
            int lines = !traits.isEmpty() ? traits.size() : (rarity != null ? rarity.traitCount() : 1);
            for (int i = 0; i < lines; i++) {
                m = m.insert(Message.translation("server.aetherhaven.ui.handmirror.traitHidden").color(C_DIM));
                if (i + 1 < lines) {
                    m = m.insert(Message.raw("\n"));
                }
            }
            return m;
        }
        JewelryGem gem = JewelryGem.fromItemId(stack.getItemId());
        for (JewelryMetadata.RolledTrait rt : JewelryMetadata.readTraits(stack)) {
            m = m.insert(traitLine(rt, gem)).insert(Message.raw("\n"));
        }
        return m;
    }

    @Nonnull
    private static Message traitLine(@Nonnull JewelryMetadata.RolledTrait rt, @Nullable JewelryGem gem) {
        String n = JewelryStatTuning.formatForDisplay(rt.statId(), rt.amount());
        String sign = rt.amount() >= 0f ? "+" : "";
        Message statName = Message.translation("server.aetherhaven.jewelry.stat." + rt.statId()).color(C_STAT);
        Message line = Message.raw(sign + n + " ").insert(statName);
        if (gem != null) {
            int facet = rt.gemTraitIndex() + 1;
            line = line
                .insert(Message.raw("  "))
                .insert(
                    Message.translation("server.aetherhaven.ui.handmirror.traitFrom")
                        .param(
                            "gem",
                            Message.translation("server.aetherhaven.jewelry.gem." + gem.name()).color(C_DIM))
                        .param("facet", Message.raw(String.valueOf(facet)).color(C_DIM))
                        .color(C_DIM));
        }
        return line;
    }
}
