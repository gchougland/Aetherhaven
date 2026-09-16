package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import javax.annotation.Nullable;

/** Stable prefab-safe IDs. Each variant inherits Gaia's interaction and collision footprint. */
public enum GaiaStatueAppearance {
    LIGHT("", "Furniture_Temple_Light_Statue"),
    ANCIENT("Ancient", "Furniture_Ancient_Statue"),
    BROKEN("Broken", "Furniture_Human_Ruins_Statue_Broken"),
    KWEEBEC("Kweebec", "Furniture_Kweebec_Statue"),
    DARK_OWL("Dark_Owl", "Furniture_Temple_Dark_Statue"),
    DARK_GAIA("Dark_Gaia", "Furniture_Temple_Dark_Statue_Gaia"),
    EMERALD("Emerald", "Furniture_Temple_Emerald_Statue"),
    SCARAK("Scarak", "Furniture_Temple_Scarak_Statue"),
    WIND("Wind", "Furniture_Temple_Wind_Statue"),
    SANDSTONE("Sandstone", "Furniture_Temple_Wind_Statue_Gaia"),
    VILLAGE("Village", "Furniture_Village_Statue");

    private final String blockTypeId;
    private final String vanillaItemId;

    GaiaStatueAppearance(String suffix, String vanillaItemId) {
        this.blockTypeId = AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID + (suffix.isEmpty() ? "" : "_" + suffix);
        this.vanillaItemId = vanillaItemId;
    }

    public String blockTypeId() { return blockTypeId; }
    public String vanillaItemId() { return vanillaItemId; }
    public String nameKey() { return "server.items." + vanillaItemId + ".name"; }
    public String iconPath() { return "Icons/ItemsGenerated/" + vanillaItemId + ".png"; }

    @Nullable
    public static GaiaStatueAppearance fromBlockTypeId(@Nullable String id) {
        if (id == null) return null;
        for (GaiaStatueAppearance appearance : values()) {
            if (appearance.blockTypeId.equalsIgnoreCase(id.trim())) return appearance;
        }
        return null;
    }

    public static boolean isGaiaStatue(@Nullable String id) {
        return fromBlockTypeId(id) != null;
    }

    public static boolean sameStatueFamily(@Nullable String a, @Nullable String b) {
        return isGaiaStatue(a) && isGaiaStatue(b);
    }
}
