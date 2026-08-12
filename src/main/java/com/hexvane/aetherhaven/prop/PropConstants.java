package com.hexvane.aetherhaven.prop;

/**
 * Hardcoded string constants for the props system. Mirrors the values the parent plugin will expose as
 * {@code AetherhavenConstants.PACKAGING_WAND_ITEM_ID} / {@code PAGE_PROP_PLACEMENT} / {@code PAGE_PROP_PREFAB_BROWSER}
 * / {@code PERMISSION_PROP_BREAK} once those constants are wired in ({@link PropItemMetadata#PROP_ITEM_ID} and
 * {@link PropBoundsUtil#PROP_BOUNDS_PADDING} hold the other two).
 */
public final class PropConstants {
    public static final String PACKAGING_WAND_ITEM_ID = "Aetherhaven_Packaging_Wand";
    public static final String PAGE_PROP_PLACEMENT = "AetherhavenPropPlacement";
    public static final String PAGE_PROP_PREFAB_BROWSER = "AetherhavenPropPrefabBrowser";
    public static final String PERMISSION_PROP_BREAK = "aetherhaven.prop.break";

    private PropConstants() {}
}
