package com.hexvane.aetherhaven.townsfolk;

/** Separate priorities for workplace specializations sharing a profession skill. */
public enum TownsfolkWorkType {
    SMELTING("smelting", TownsfolkSkill.BLACKSMITHING, "furnace.png"),
    BLACKSMITHING("blacksmithing", TownsfolkSkill.BLACKSMITHING),
    ARMOR_CRAFTING("armorCrafting", TownsfolkSkill.BLACKSMITHING, "armor.png"),
    RANCHING("ranching", TownsfolkSkill.RANCHING),
    FARMING("farming", TownsfolkSkill.FARMING),
    /** Grower sites under the Druid. Both use Farming skill and the same machinery with different plans. */
    MYCOLOGY("mycology", TownsfolkSkill.FARMING, "mushroom.png"),
    BERRIES("berries", TownsfolkSkill.FARMING, "berries.png"),
    MINING("mining", TownsfolkSkill.MINING),
    BUILDING("building", TownsfolkSkill.BUILDING),
    /** Makes the Builder's Workbench recipes to order. The Builder's yard above builds the town itself. */
    BLOCK_WORKSHOP("blockWorkshop", TownsfolkSkill.BUILDING, "blocks.png"),
    ALCHEMY("alchemy", TownsfolkSkill.ALCHEMY),
    CULTIVATION("cultivation", TownsfolkSkill.FARMING),
    CRAFTING("crafting", TownsfolkSkill.CRAFTING),
    LEATHERWORKING("leatherworking", TownsfolkSkill.LEATHERWORKING),
    COOKING("cooking", TownsfolkSkill.COOKING),
    JEWELRY("jewelry", TownsfolkSkill.JEWELRY),
    SALVAGING("salvaging", TownsfolkSkill.CRAFTING),
    LOGISTICS("logistics", TownsfolkSkill.LOGISTICS),
    /** Staffing a player shop's counter. The shop is chosen at its Town Records Shelf; this sets when it comes first. */
    COMMERCE("commerce", TownsfolkSkill.COMMERCE);

    public final String id;
    public final TownsfolkSkill skill;
    /** Shown for this kind of work. Several kinds share one skill, so some have a picture of their own. */
    public final String icon;
    TownsfolkWorkType(String id, TownsfolkSkill skill) { this(id, skill, skill.icon); }
    TownsfolkWorkType(String id, TownsfolkSkill skill, String icon) { this.id = id; this.skill = skill; this.icon = icon; }
    public boolean requiresWorkplace() { return this != LOGISTICS && this != COMMERCE; }
    /** Kept for saved gardens and their deliveries, but retired from new work. */
    public boolean availableForNewWork() { return this != CULTIVATION; }
    public static TownsfolkWorkType byId(String id) {
        for (var type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
