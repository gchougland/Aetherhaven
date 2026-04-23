package com.hexvane.aetherhaven.jewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.herolias.tooltips.api.ItemVisualOverrides;
import org.herolias.tooltips.api.TooltipData;
import org.herolias.tooltips.api.TooltipPriority;
import org.herolias.tooltips.api.TooltipProvider;

/**
 * Per-instance jewelry tooltips via DynamicTooltipsLib; registered from {@link TooltipBridge}. Also applies
 * {@link org.herolias.tooltips.api.ItemVisualOverrides} so each virtual item id uses a {@code qualityIndex} (and a
 * custom label for mythic→epic tier) matching rolled {@link JewelryRarity}, per
 * <a href="https://github.com/Herolias/DynamicTooltipsLib#visual-overrides-reference">DynamicTooltipsLib visual overrides</a>.
 */
public final class AetherhavenJewelryDynamicTooltipProvider implements TooltipProvider {

    @Override
    @Nonnull
    public String getProviderId() {
        return "aetherhaven:jewelry";
    }

    @Override
    public int getPriority() {
        return TooltipPriority.DEFAULT;
    }

    @Override
    @Nullable
    public TooltipData getTooltipData(@Nonnull String itemId, @Nullable String metadata) {
        return getTooltipData(itemId, metadata, "en-US");
    }

    @Override
    @Nullable
    public TooltipData getTooltipData(
        @Nonnull String itemId, @Nullable String metadata, @Nullable String locale) {
        JewelryDynamicTooltipContent.Result r = JewelryDynamicTooltipContent.resolve(itemId, metadata, locale);
        if (r == null) {
            return null;
        }
        TooltipData.Builder b = TooltipData.builder().hashInput(r.hashInput());
        for (String line : r.descriptionLines()) {
            b.addLineOverride(line);
        }
        ItemVisualOverrides.Builder vb = ItemVisualOverrides.builder().qualityIndex(r.itemQualityIndex());
        if (r.itemQualityLabelOverride() != null && !r.itemQualityLabelOverride().isBlank()) {
            vb.qualityLabel(r.itemQualityLabelOverride());
        }
        b.visualOverrides(vb.build());
        return b.build();
    }
}
