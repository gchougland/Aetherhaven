package com.hexvane.aetherhaven.townsfolk;

/** Separate priorities for workplace specializations sharing a profession skill. */
public enum TownsfolkWorkType {
    SMELTING("smelting", TownsfolkSkill.BLACKSMITHING),
    BLACKSMITHING("blacksmithing", TownsfolkSkill.BLACKSMITHING),
    ARMOR_CRAFTING("armorCrafting", TownsfolkSkill.BLACKSMITHING),
    RANCHING("ranching", TownsfolkSkill.RANCHING),
    FARMING("farming", TownsfolkSkill.FARMING),
    MINING("mining", TownsfolkSkill.MINING),
    BUILDING("building", TownsfolkSkill.BUILDING),
    ALCHEMY("alchemy", TownsfolkSkill.ALCHEMY),
    CULTIVATION("cultivation", TownsfolkSkill.FARMING),
    CRAFTING("crafting", TownsfolkSkill.CRAFTING),
    LEATHERWORKING("leatherworking", TownsfolkSkill.LEATHERWORKING),
    COOKING("cooking", TownsfolkSkill.COOKING),
    JEWELRY("jewelry", TownsfolkSkill.JEWELRY),
    LOGISTICS("logistics", TownsfolkSkill.LOGISTICS);

    public final String id;
    public final TownsfolkSkill skill;
    TownsfolkWorkType(String id, TownsfolkSkill skill) { this.id = id; this.skill = skill; }
    public boolean requiresWorkplace() { return this != LOGISTICS; }
    /** Kept for saved gardens and their deliveries, but retired from new work. */
    public boolean availableForNewWork() { return this != CULTIVATION; }
    public static TownsfolkWorkType byId(String id) {
        for (var type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
