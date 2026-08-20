package com.hexvane.aetherhaven.community;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Client-side validation mirrors for community prop ids. */
public final class CommunityPropValidator {
    public static final String ID_PREFIX = "prop_community_";
    private static final Pattern ID_PATTERN = Pattern.compile("^prop_community_[a-z0-9_]{8,80}$");

    private CommunityPropValidator() {}

    public static boolean isValidCommunityPropId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return ID_PATTERN.matcher(id.trim().toLowerCase(Locale.ROOT)).matches();
    }

    @Nullable
    public static String normalizeCommunityPropId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id =
            raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_");
        if (!id.startsWith(ID_PREFIX)) {
            return null;
        }
        return isValidCommunityPropId(id) ? id : null;
    }

    @Nonnull
    public static String assignCatalogId(
        @Nullable String localPropId,
        @Nullable String displayName,
        @Nonnull UUID creatorUuid
    ) {
        String existing = normalizeCommunityPropId(localPropId);
        if (existing != null) {
            return existing;
        }
        String shortUuid = creatorUuid.toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
        String slugSource =
            displayName != null && !displayName.isBlank()
                ? displayName
                : (localPropId != null ? localPropId : "prop");
        String slug =
            slugSource
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
        if (slug.startsWith("prop_")) {
            slug = slug.substring("prop_".length());
        }
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("_+$", "");
        }
        if (slug.isEmpty()) {
            slug = "prop";
        }
        String id = ID_PREFIX + shortUuid + "_" + slug;
        return isValidCommunityPropId(id) ? id : ID_PREFIX + shortUuid + "_prop";
    }
}
