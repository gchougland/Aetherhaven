package com.hexvane.aetherhaven.blockpalette;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Cross-mod remapping family: related block type prefixes that swap while keeping trailing shape/suffix text.
 *
 * <p>Example: {@code MyMod_Oak_Wall_Stairs} with prefix {@code MyMod_Oak_Wall} remaps to
 * {@code MyMod_Birch_Wall_Stairs} when Birch is selected.
 */
public final class BlockPaletteRemapGroup {
    private final String id;
    private final String category;
    private final List<Variant> variants;

    public BlockPaletteRemapGroup(
        @Nonnull String id,
        @Nonnull String category,
        @Nonnull List<Variant> variants
    ) {
        this.id = id.trim();
        this.category = category.trim();
        this.variants = List.copyOf(variants);
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getCategory() {
        return category;
    }

    @Nonnull
    public List<Variant> getVariants() {
        return variants;
    }

    @Nullable
    public Variant findVariantByPaletteId(@Nonnull String paletteId) {
        String want = paletteId.trim();
        for (Variant v : variants) {
            if (v.paletteId().equals(want)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Longest matching {@link Variant#blockPrefix()} for this block type id, or null if none match.
     */
    @Nullable
    public Match matchBlockTypeId(@Nonnull String blockTypeId) {
        String typeId = blockTypeId.startsWith("*") ? blockTypeId.substring(1) : blockTypeId;
        Variant best = null;
        int bestLen = -1;
        for (Variant v : variants) {
            String prefix = v.blockPrefix();
            if (prefix.isEmpty()) {
                continue;
            }
            if (!typeId.equals(prefix) && !typeId.startsWith(prefix)) {
                continue;
            }
            if (prefix.length() <= bestLen) {
                continue;
            }
            best = v;
            bestLen = prefix.length();
        }
        if (best == null) {
            return null;
        }
        String suffix = typeId.substring(best.blockPrefix().length());
        return new Match(best, suffix);
    }

    /** One unlockable look inside a remap group. */
    public record Variant(
        @Nonnull String paletteId,
        @Nonnull String displayName,
        @Nonnull String familyKey,
        @Nonnull String iconBlockId,
        @Nonnull String blockPrefix
    ) {
        public Variant {
            paletteId = paletteId.trim();
            displayName = displayName.trim();
            familyKey = familyKey.trim();
            iconBlockId = iconBlockId.trim();
            blockPrefix = blockPrefix.trim();
        }

        @Nonnull
        public BlockPaletteDefinition toDefinition(@Nonnull String category, @Nonnull String remapGroupId) {
            return new BlockPaletteDefinition(
                paletteId, category, displayName, familyKey, iconBlockId, remapGroupId);
        }
    }

    public record Match(@Nonnull Variant variant, @Nonnull String suffix) {}

    @Nonnull
    public static Builder builder(@Nonnull String id, @Nonnull String category) {
        return new Builder(id, category);
    }

    public static final class Builder {
        private final String id;
        private final String category;
        private final List<Variant> variants = new ArrayList<>();

        private Builder(@Nonnull String id, @Nonnull String category) {
            this.id = id;
            this.category = category;
        }

        /** Adds a variant; family key defaults to {@code groupId:paletteId}. */
        @Nonnull
        public Builder variant(
            @Nonnull String paletteId,
            @Nonnull String displayName,
            @Nonnull String iconBlockId,
            @Nonnull String blockPrefix
        ) {
            String familyKey = id.trim() + ":" + paletteId.trim();
            variants.add(new Variant(paletteId, displayName, familyKey, iconBlockId, blockPrefix));
            return this;
        }

        @Nonnull
        public Builder variant(
            @Nonnull String paletteId,
            @Nonnull String displayName,
            @Nonnull String familyKey,
            @Nonnull String iconBlockId,
            @Nonnull String blockPrefix
        ) {
            variants.add(new Variant(paletteId, displayName, familyKey, iconBlockId, blockPrefix));
            return this;
        }

        @Nonnull
        public BlockPaletteRemapGroup build() {
            return new BlockPaletteRemapGroup(id, category, variants);
        }
    }
}
