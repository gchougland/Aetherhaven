package com.hexvane.aetherhaven.townsfolk;

public enum TownsfolkSkill {
    BLACKSMITHING("blacksmithing", "forge.png"),
    RANCHING("ranching", "bull.png"),
    FARMING("farming", "farming.png"),
    MINING("mining", "pickaxe.png"),
    BUILDING("building", "hammer.png"),
    ALCHEMY("alchemy", "alchemy.png"),
    CRAFTING("crafting", "woodworking.png"),
    LEATHERWORKING("leatherworking", "leather.png"),
    COOKING("cooking", "food-tray.png"),
    LOGISTICS("logistics", "crate.png"),
    COMMERCE("commerce", "dollar_coin_icon_256.png"),
    JEWELRY("jewelry", "jewelery.png");

    public final String id;
    public final String icon;
    TownsfolkSkill(String id, String icon) { this.id = id; this.icon = icon; }
}
