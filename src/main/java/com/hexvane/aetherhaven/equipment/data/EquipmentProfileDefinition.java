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
