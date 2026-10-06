package com.hexvane.aetherhaven.townsfolk;

public enum TownsfolkAttribute {
    STRENGTH("strength", "strength.png"),
    INTELLIGENCE("intelligence", "open-book.png"),
    VITALITY("vitality", "vitality.png"),
    DEXTERITY("dexterity", "dexterity.png"),
    SPEED("speed", "speed.png");

    public final String id;
    public final String icon;
    TownsfolkAttribute(String id, String icon) { this.id = id; this.icon = icon; }
}
