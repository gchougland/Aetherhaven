package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;

/** Resolves jewelry tooltip content. */
public final class JewelryDynamicTooltipContent {

    public record Result(
        @Nonnull String hashInput,
        @Nonnull List<String> descriptionLines,
        int itemQualityIndex,
        @Nullable String itemQualityLabelOverride) {}

    private JewelryDynamicTooltipContent() {}

    @Nullable
    public static Result resolve(
        @Nonnull String itemId, @Nullable String metadata, @Nullable String locale) {
        if (!JewelryItemIds.isJewelry(itemId)) {
            return null;
        }
        if (metadata == null || !metadata.contains(JewelryMetadata.BSON_KEY)) {
            return null;
        }
        BsonDocument doc = parseMetadata(metadata);
        if (doc == null) {
            return null;
        }
        // Full root metadata so readRarity / ItemQuality line up with in-world stacks
        ItemStack stack = new ItemStack(itemId, 1, doc);
        List<String> lines = JewelryTooltipText.dynamicDescriptionLines(stack);
        if (lines.isEmpty()) {
            return null;
        }
        int qIdx = JewelryItemQualityIndex.forStack(stack);
        JewelryRarity r = JewelryMetadata.readRarity(stack);
        String qLabel = r == JewelryRarity.MYTHIC ? "Mythic" : null;
        return new Result("aetherhaven.jewelry:" + stableMetadataHash(metadata), lines, qIdx, qLabel);
    }

    @Nullable
    private static BsonDocument parseMetadata(@Nonnull String json) {
        try {
            return BsonDocument.parse(json);
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    private static String stableMetadataHash(@Nonnull String metadata) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(metadata.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(metadata.hashCode());
        }
    }
}
