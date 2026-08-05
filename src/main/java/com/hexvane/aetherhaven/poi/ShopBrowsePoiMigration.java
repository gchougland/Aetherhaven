package com.hexvane.aetherhaven.poi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Splits legacy merchant desks tagged both {@code WORK} and {@code SHOP} into a work desk plus a customer
 * browse POI ({@code SHOP} only), so villagers can shop without using the work station.
 */
public final class ShopBrowsePoiMigration {
    private ShopBrowsePoiMigration() {}

    /**
     * @return migrated list (may be the same instance if nothing changed)
     */
    @Nonnull
    public static List<PoiEntry> migrate(@Nonnull List<PoiEntry> entries) {
        Set<UUID> plotsWithShopOnly = new HashSet<>();
        for (PoiEntry e : entries) {
            if (e.getPlotId() == null) {
                continue;
            }
            Set<String> tags = e.getTags();
            if (tags.contains("SHOP") && !tags.contains("WORK")) {
                plotsWithShopOnly.add(e.getPlotId());
            }
        }

        List<PoiEntry> out = new ArrayList<>(entries.size());
        List<PoiEntry> clones = new ArrayList<>();
        boolean changed = false;
        for (PoiEntry e : entries) {
            Set<String> tags = e.getTags();
            if (!tags.contains("WORK") || !tags.contains("SHOP")) {
                out.add(e);
                continue;
            }
            changed = true;
            Set<String> workTags = new HashSet<>(tags);
            workTags.remove("SHOP");
            out.add(copyWithTagsKindAndWork(e, workTags, e.getInteractionKind(), e.getWorkResidentKind()));

            UUID plotId = e.getPlotId();
            if (plotId == null || plotsWithShopOnly.contains(plotId)) {
                continue;
            }
            plotsWithShopOnly.add(plotId);
            int bx = e.getInteractionTargetX() != null ? (int) Math.floor(e.getInteractionTargetX()) : e.getX();
            int by = e.getInteractionTargetY() != null ? (int) Math.floor(e.getInteractionTargetY()) : e.getY();
            int bz = e.getInteractionTargetZ() != null ? (int) Math.floor(e.getInteractionTargetZ()) : e.getZ();
            Set<String> shopTags = new HashSet<>();
            shopTags.add("SHOP");
            clones.add(
                new PoiEntry(
                    UUID.randomUUID(),
                    e.getTownId(),
                    bx,
                    by,
                    bz,
                    shopTags,
                    Math.max(1, e.getCapacity()),
                    plotId,
                    e.getBlockTypeId(),
                    PoiInteractionKind.SIT,
                    true,
                    null,
                    (double) e.getX() + 0.5,
                    (double) e.getY(),
                    (double) e.getZ() + 0.5,
                    e.getInteractionTargetYawRadians() != null
                        ? e.getInteractionTargetYawRadians() + (float) Math.PI
                        : null,
                    null
                )
            );
        }
        if (!changed) {
            return entries;
        }
        out.addAll(clones);
        return out;
    }

    @Nonnull
    private static PoiEntry copyWithTagsKindAndWork(
        @Nonnull PoiEntry e,
        @Nonnull Set<String> tags,
        @Nonnull PoiInteractionKind kind,
        @Nullable String workResidentKind
    ) {
        return new PoiEntry(
            e.getId(),
            e.getTownId(),
            e.getX(),
            e.getY(),
            e.getZ(),
            tags,
            e.getCapacity(),
            e.getPlotId(),
            e.getBlockTypeId(),
            kind,
            e.isMountOnUse(),
            e.getEquipmentProfileId(),
            e.getInteractionTargetX(),
            e.getInteractionTargetY(),
            e.getInteractionTargetZ(),
            e.getInteractionTargetYawRadians(),
            workResidentKind
        );
    }
}
