package com.hexvane.aetherhaven.equipment.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class EquipmentProfileDefinition {
    @SerializedName("id")
    private String id = "";

    @SerializedName("displayName")
    @Nullable
    private String displayName;

    @SerializedName("hireGoldCost")
    private long hireGoldCost;

    @SerializedName("armor")
    @Nullable
    private List<String> armor;

    @SerializedName("hotbar")
    @Nullable
    private List<HotbarSlot> hotbar;

    @SerializedName("offhand")
    @Nullable
    private String offhand;

    @SerializedName("guardNpcRole")
    @Nullable
    private String guardNpcRole;

    /**
     * Resident kinds allowed to hold this profile, such as {@code bard} for the lute. Empty means anyone.
     * A work spot, a building definition or a saved town can name any profile, so a prop that belongs to
     * one profession declares its owner here instead of relying on every spot being authored correctly.
     */
    @SerializedName("allowedResidentKinds")
    @Nullable
    private List<String> allowedResidentKinds;

    @Nonnull
    public List<String> getAllowedResidentKinds() {
        if (allowedResidentKinds == null || allowedResidentKinds.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String kind : allowedResidentKinds) {
            String normalized = normalizeResidentKind(kind);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        return out;
    }

    /** True when only the listed resident kinds may hold this profile. */
    public boolean isResidentRestricted() {
        return !getAllowedResidentKinds().isEmpty();
    }

    /** A visitor waiting at the inn counts as the profession they will take up. */
    public boolean permitsResidentKind(@Nullable String residentKind) {
        List<String> allowed = getAllowedResidentKinds();
        if (allowed.isEmpty()) {
            return true;
        }
        String normalized = normalizeResidentKind(residentKind);
        return normalized != null && allowed.contains(normalized);
    }

    @Nullable
    private static String normalizeResidentKind(@Nullable String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        String normalized = kind.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("visitor_")) {
            normalized = normalized.substring("visitor_".length());
        }
        return normalized.isEmpty() ? null : normalized;
    }

    /** The motion a worker repeats with this profile's tool in hand, such as a builder's hammering. */
    @SerializedName("workAnimation")
    @Nullable
    private WorkAnimation workAnimation;

    @Nullable
    public WorkAnimation getWorkAnimation() {
        return workAnimation != null && workAnimation.isUsable() ? workAnimation : null;
    }

    /** The tool in the lowest hotbar slot, which is the one a worker shows in hand. */
    @Nullable
    public String getHeldToolItemId() {
        HotbarSlot first = null;
        for (HotbarSlot slot : getHotbarSlots()) {
            if (!slot.getItemId().isEmpty() && (first == null || slot.getSlot() < first.getSlot())) {
                first = slot;
            }
        }
        return first != null ? first.getItemId() : null;
    }

    /** One clip of an item animation set, replayed every {@code intervalMs} while the work lasts. */
    public static final class WorkAnimation {
        @SerializedName("animationsId")
        private String animationsId = "";

        @SerializedName("animationId")
        private String animationId = "";

        @SerializedName("intervalMs")
        private long intervalMs;

        @Nonnull
        public String getAnimationsId() {
            return animationsId != null ? animationsId.trim() : "";
        }

        @Nonnull
        public String getAnimationId() {
            return animationId != null ? animationId.trim() : "";
        }

        /** Never faster than four passes a second, whatever a pack writes. */
        public long getIntervalMs() {
            return Math.max(250L, intervalMs);
        }

        boolean isUsable() {
            return !getAnimationsId().isEmpty() && !getAnimationId().isEmpty();
        }
    }

    /** Opt in for pack supplied guard classes. Armor must be visual only to avoid duplicate bonuses. */
    private boolean attributeCombat;
    private java.util.Map<String,Integer> attributeBonuses;
    public boolean usesAttributeCombat() { return attributeCombat; }
    public int attributeBonus(String attribute) {
        return attributeBonuses == null ? 0 : Math.clamp(attributeBonuses.getOrDefault(attribute,0),0,20);
    }

    public static final class HotbarSlot {
        @SerializedName("slot")
        private int slot;

        @SerializedName("itemId")
        private String itemId = "";

        public int getSlot() {
            return slot;
        }

        @Nonnull
        public String getItemId() {
            return itemId != null ? itemId.trim() : "";
        }
    }

    @Nonnull
    public String getId() {
        return id != null ? id.trim() : "";
    }

    @Nullable
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName.trim() : null;
    }

    public long getHireGoldCost() {
        return Math.max(0L, hireGoldCost);
    }

    @Nonnull
    public List<String> getArmorItemIds() {
        return listOrEmpty(armor);
    }

    @Nonnull
    public List<HotbarSlot> getHotbarSlots() {
        if (hotbar == null || hotbar.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(hotbar));
    }

    @Nullable
    public String getOffhandItemId() {
        return offhand != null && !offhand.isBlank() ? offhand.trim() : null;
    }

    @Nonnull
    public String getGuardNpcRole() {
        if (guardNpcRole != null && !guardNpcRole.isBlank()) {
            return guardNpcRole.trim();
        }
        return switch (getId()) {
            case "guard_archer" -> "Aetherhaven_Guard_Archer";
            case "guard_mage" -> "Aetherhaven_Guard_Mage";
            case "guard_rogue" -> "Aetherhaven_Guard_Rogue";
            default -> "Aetherhaven_Guard_Knight";
        };
    }

    @Nonnull
    private static List<String> listOrEmpty(@Nullable List<String> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}
