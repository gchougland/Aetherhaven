package com.hexvane.aetherhaven.community;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Client-side validation mirrors for community building ids and submission payloads. */
public final class CommunityBuildingValidator {
    public static final String ID_PREFIX = "plot_community_";
    private static final Pattern ID_PATTERN = Pattern.compile("^plot_community_[a-z0-9_]{8,80}$");

    private CommunityBuildingValidator() {}

    public static boolean isValidCommunityId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return ID_PATTERN.matcher(id.trim().toLowerCase(Locale.ROOT)).matches();
    }

    /** Same rules as marketplace {@code normalizeCommunityId}. */
    @Nullable
    public static String normalizeCommunityId(@Nullable String raw) {
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
        return isValidCommunityId(id) ? id : null;
    }

    /**
     * Assigns the community catalog id the marketplace uses on upload ({@code assignCommunityCatalogId}).
     */
    @Nonnull
    public static String assignCatalogId(
        @Nullable String localBuildingId,
        @Nullable String displayName,
        @Nonnull UUID creatorUuid
    ) {
        String existing = normalizeCommunityId(localBuildingId);
        if (existing != null) {
            return existing;
        }
        String name = displayName != null ? displayName.trim() : "";
        String localId = localBuildingId != null ? localBuildingId.trim() : "";
        String slugSource = name;
        if (slugSource.isEmpty()) {
            slugSource =
                localId.replaceFirst("^plot_", "")
                    .replaceAll("(?i)[^a-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
        }
        if (slugSource.isEmpty()) {
            slugSource = "building";
        }
        return proposeId(creatorUuid.toString(), slugSource);
    }

    @Nonnull
    public static String prefabPathKeyForCommunityId(@Nonnull String communityId) {
        return communityId.trim() + ".prefab.json";
    }

    @Nonnull
    public static String proposeId(@Nonnull String creatorUuid, @Nonnull String slugOrDisplayName) {
        String shortUuid = creatorUuid.replace("-", "").toLowerCase(Locale.ROOT);
        if (shortUuid.length() > 8) {
            shortUuid = shortUuid.substring(0, 8);
        }
        String slug = slugOrDisplayName
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        if (slug.isBlank()) {
            slug = "building";
        }
        String id = ID_PREFIX + shortUuid + "_" + slug;
        return isValidCommunityId(id) ? id : ID_PREFIX + shortUuid + "_building";
    }
}
