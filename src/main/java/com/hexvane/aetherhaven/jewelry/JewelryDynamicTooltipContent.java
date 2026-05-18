package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/** Resolves jewelry tooltip content. */
public final class JewelryDynamicTooltipContent {

    private static final int METADATA_DFS_MAX_DEPTH = 18;

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

        String rawForHash = metadata == null ? "" : metadata;
        if (metadata == null || metadata.isBlank()) {
            return fallbackWhenNoWireMetadata(itemId, rawForHash);
        }

        BsonDocument doc = parseMetadata(metadata);
        if (doc == null || doc.isEmpty()) {
            return fallbackWhenNoWireMetadata(itemId, rawForHash);
        }

        BsonDocument stackShape = elevateToItemStackMetadata(doc);
        Result jewelry = resolveFromJewelryBson(itemId, metadata, stackShape);
        if (jewelry != null) {
            return jewelry;
        }

        Result synced = resolveFromSyncedDescription(itemId, metadata, stackShape);
        if (synced != null) {
            return synced;
        }

        return fallbackWhenNoWireMetadata(itemId, rawForHash);
    }

    /**
     * {@link ItemStack} metadata must carry {@link JewelryMetadata#BSON_KEY} at the top level. Some serializers/packet
     * paths nest that document; relocate it so {@link JewelryMetadata#hasJewelryMeta} works.
     */
    @Nonnull
    private static BsonDocument elevateToItemStackMetadata(@Nonnull BsonDocument parsed) {
        if (jewelryBsonValueIsDocument(parsed)) {
            return parsed.clone();
        }

        BsonDocument host =
            dfsFirstDocument(parsed, JewelryDynamicTooltipContent::jewelryBsonValueIsDocument, 0, METADATA_DFS_MAX_DEPTH);
        if (host != null) {
            BsonDocument out = rebuildRootFromHost(host);
            mergeUsableTranslationProperties(parsed, out);
            return out;
        }

        BsonDocument rollDoc =
            dfsFirstDocument(parsed, JewelryDynamicTooltipContent::looksLikeEmbeddedJewelryRoll, 0, METADATA_DFS_MAX_DEPTH);
        if (rollDoc != null) {
            BsonDocument out = new BsonDocument(JewelryMetadata.BSON_KEY, rollDoc.clone());
            mergeUsableTranslationProperties(parsed, out);
            return out;
        }

        BsonDocument shell = normalizeItemMetadataRoot(parsed.clone());
        mergeUsableTranslationProperties(parsed, shell);
        return shell;
    }

    private static boolean jewelryBsonValueIsDocument(@Nonnull BsonDocument doc) {
        BsonValue v = doc.get(JewelryMetadata.BSON_KEY);
        return v != null && v.isDocument();
    }

    @Nonnull
    private static BsonDocument rebuildRootFromHost(@Nonnull BsonDocument host) {
        return new BsonDocument(
            JewelryMetadata.BSON_KEY, host.get(JewelryMetadata.BSON_KEY).asDocument().clone());
    }

    /** Rolled traits without the {@code AetherhavenJewelry} wrapper (nested under unknown container keys). */
    private static boolean looksLikeEmbeddedJewelryRoll(@Nonnull BsonDocument doc) {
        if (doc.containsKey(JewelryMetadata.BSON_KEY)) {
            return false;
        }
        BsonValue traits = doc.get("traits");
        return traits != null && traits.isArray();
    }

    /** Mirror DynamicTooltipsLib Custom UI serialization: unwrap a root {@code Metadata} document when present. */
    @Nonnull
    private static BsonDocument normalizeItemMetadataRoot(@Nonnull BsonDocument parsed) {
        if (parsed.containsKey(JewelryMetadata.BSON_KEY)
            || parsed.containsKey(JewelryMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY)) {
            return parsed;
        }
        BsonValue inner = parsed.get("Metadata");
        if (inner != null && inner.isDocument()) {
            BsonDocument nested = inner.asDocument();
            if (nested.containsKey(JewelryMetadata.BSON_KEY)
                || nested.containsKey(JewelryMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY)) {
                return nested.clone();
            }
        }
        return parsed;
    }

    private static void mergeUsableTranslationProperties(@Nonnull BsonDocument tree, @Nonnull BsonDocument out) {
        if (hasUsableTranslationProperties(out)) {
            return;
        }
        BsonDocument holder =
            dfsFirstDocument(tree, JewelryDynamicTooltipContent::hasUsableTranslationProperties, 0, METADATA_DFS_MAX_DEPTH);
        if (holder != null && holder.containsKey(JewelryMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY)) {
            out.put(JewelryMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY, holder.get(JewelryMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY));
        }
    }

    private static boolean hasUsableTranslationProperties(@Nonnull BsonDocument doc) {
        BsonValue tpv = doc.get(JewelryMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY);
        if (!usableTranslationDoc(tpv)) {
            return false;
        }
        BsonValue dv = tpv.asDocument().get("Description");
        return dv != null && dv.isString() && !isAssetDescriptionCatalogKey(dv.asString().getValue());
    }

    private static boolean usableTranslationDoc(@Nullable BsonValue tpv) {
        return tpv != null && tpv.isDocument();
    }

    /**
     * Item JSON uses {@code TranslationProperties.Description} as a language key (e.g.
     * {@code aetherhaven_jewelry_geode.items.Aetherhaven_Jewelry.genericDescription}). Only plain instance text from
     * {@link JewelryMetadata#syncInstanceDescriptionForTooltip} should drive tooltips here.
     */
    private static boolean isAssetDescriptionCatalogKey(@Nonnull String value) {
        String t = value.trim();
        if (t.isEmpty()) {
            return true;
        }
        if (t.startsWith("<")) {
            return false;
        }
        return t.indexOf(' ') < 0 && t.indexOf('.') >= 0;
    }

    @Nullable
    private static BsonDocument dfsFirstDocument(
        @Nonnull BsonDocument root,
        @Nonnull Predicate<BsonDocument> pred,
        int depth,
        int maxDepth
    ) {
        if (depth > maxDepth) {
            return null;
        }
        if (pred.test(root)) {
            return root;
        }
        for (String k : root.keySet()) {
            BsonValue v = root.get(k);
            if (!v.isDocument()) {
                continue;
            }
            BsonDocument hit = dfsFirstDocument(v.asDocument(), pred, depth + 1, maxDepth);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    @Nullable
    private static Result resolveFromJewelryBson(
        @Nonnull String itemId, @Nonnull String rawMetadata, @Nonnull BsonDocument rootForStack) {
        if (!rootForStack.containsKey(JewelryMetadata.BSON_KEY)) {
            return null;
        }

        ItemStack stack = new ItemStack(itemId, 1, rootForStack);
        List<String> lines = JewelryTooltipText.dynamicDescriptionLines(stack);
        if (lines.isEmpty()) {
            String plain = JewelryTooltipMessages.toPlainEnglishDescription(stack);
            if (plain == null || plain.isBlank()) {
                return null;
            }
            lines = splitNonEmptyLines(plain);
        }
        if (lines.isEmpty()) {
            return null;
        }
        int qIdx = JewelryItemQualityIndex.forStack(stack);
        JewelryRarity r = JewelryMetadata.readRarity(stack);
        String qLabel = r == JewelryRarity.MYTHIC ? "Mythic" : null;
        return new Result("aetherhaven.jewelry:" + stableMetadataHash(rawMetadata), lines, qIdx, qLabel);
    }

    @Nullable
    private static Result resolveFromSyncedDescription(
        @Nonnull String itemId, @Nonnull String rawMetadata, @Nonnull BsonDocument rootForStack) {

        BsonValue tpv = rootForStack.get(JewelryMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY);
        if (tpv == null || !tpv.isDocument()) {
            return null;
        }
        BsonValue descV = tpv.asDocument().get("Description");
        if (descV == null || !descV.isString()) {
            return null;
        }
        String plain = descV.asString().getValue();
        if (isAssetDescriptionCatalogKey(plain)) {
            return null;
        }
        List<String> lines = splitNonEmptyLines(plain);
        if (lines.isEmpty()) {
            return null;
        }

        ItemStack bare = new ItemStack(itemId, 1);
        int qIdx = JewelryItemQualityIndex.forStack(bare);
        return new Result("aetherhaven.jewelry:tp:" + stableMetadataHash(rawMetadata), lines, qIdx, null);
    }

    /**
     * When inventory packets omit metadata or we cannot parse jewelry state, still return tooltip data so
     * DynamicTooltipsLib replaces the shared {@code items.Aetherhaven_Jewelry.genericDescription} line.
     */
    @Nonnull
    private static Result fallbackWhenNoWireMetadata(@Nonnull String itemId, @Nonnull String rawForHash) {
        ItemStack stub = new ItemStack(itemId, 1);
        List<String> lines = new ArrayList<>(JewelryTooltipText.dynamicDescriptionLines(stub));
        if (lines.isEmpty()) {
            String plain = JewelryTooltipMessages.toPlainEnglishDescription(stub);
            lines.addAll(splitNonEmptyLines(plain));
        }
        if (lines.isEmpty()) {
            lines.add(
                "<color is=\"#8A8F98\">No per-piece data arrived with this tooltip. Equip via the hand mirror or appraise to refresh.</color>");
        }
        String hashSeed = stableMetadataHash(rawForHash.isEmpty() ? itemId + ":jewelry-wire-empty" : rawForHash + itemId);
        int qIdx = JewelryItemQualityIndex.forStack(stub);
        return new Result("aetherhaven.jewelry:fallback:" + hashSeed, lines, qIdx, null);
    }

    @Nonnull
    private static List<String> splitNonEmptyLines(@Nonnull String plain) {
        List<String> out = new ArrayList<>();
        for (String segment : plain.split("\\R")) {
            String t = segment.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
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
