package com.hexvane.aetherhaven.jewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.herolias.tooltips.api.ItemVisualOverrides;
import org.herolias.tooltips.api.TooltipData;
import org.herolias.tooltips.api.TooltipPriority;
import org.herolias.tooltips.api.TooltipProvider;

/** Per-instance jewelry tooltips via DynamicTooltipsLib. */
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
