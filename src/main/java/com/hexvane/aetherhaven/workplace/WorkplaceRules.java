package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;

public final class WorkplaceRules {
    private WorkplaceRules() {}
    public static String supervisor(TownsfolkWorkType type) {
        return switch (type) {
            case SMELTING, BLACKSMITHING, ARMOR_CRAFTING -> "blacksmith";
            case FARMING -> "farmer";
            case RANCHING -> "rancher";
            case MINING -> "miner";
            case BUILDING -> "builder";
            case ALCHEMY -> "alchemist";
            case CULTIVATION -> "druid";
            case CRAFTING, SALVAGING -> "crafter";
            case LEATHERWORKING -> "leatherworker";
            case COOKING -> "chef";
            case JEWELRY -> "merchant";
            case LOGISTICS -> "warehouse_manager";
            case COMMERCE -> "merchant";
        };
    }
    public static int initialSlots(String supervisor) { return "blacksmith".equals(supervisor) ? 2 : 1; }
    public static String mainBench(TownsfolkWorkType type) {
        if (type == null) return "Choose a workplace type to see its main bench.";
        return "Main workplace bench: " + switch (type) {
            case SMELTING -> "Furnace";
            case BLACKSMITHING -> "Blacksmith's Anvil";
            case ARMOR_CRAFTING -> "Armorer's Workbench";
            case FARMING, CULTIVATION -> "Farmer's Workbench";
            case RANCHING -> "Feeding Trough";
            case ALCHEMY -> "Alchemist's Workbench";
            case CRAFTING -> "Workbench";
            case MINING -> "Workbench. Use it to open the mineshaft map and plan tunnels.";
            case BUILDING -> WorkplaceEquipment.label(WorkplaceEquipment.BUILDERS_BENCH);
            case LEATHERWORKING -> "Tanning Rack";
            case SALVAGING -> "Salvager's Workbench";
            case COOKING -> "Chef's Stove";
            case JEWELRY -> "Jewelry Crafting Bench";
            case LOGISTICS, COMMERCE -> "None";
        };
    }
    public static String label(TownsfolkWorkType type) {
        return switch (type) {
            case ARMOR_CRAFTING -> "Armor Crafting";
            case JEWELRY -> "Jewelry Crafting";
            case CULTIVATION -> "Herb Cultivation";
            default -> Character.toUpperCase(type.id.charAt(0)) + type.id.substring(1);
        };
    }
    public static String description(TownsfolkWorkType type) {
        return switch (type) {
            case SMELTING -> "Apprentices load furnaces, replenish your chosen fuel and collect finished materials.";
            case BLACKSMITHING -> "Apprentices forge weapons and tools at a blacksmith's anvil.";
            case ARMOR_CRAFTING -> "Apprentices make armor and guard kits at an armorer's workbench.";
            case FARMING -> "Tending crops provides ten food points. This site produces no items.";
            case RANCHING -> "Place a feeding trough and leave clear floor space around it. Workplace animals appear automatically and stay nearby. Tending them provides ten food points and no items.";
            case MINING -> "Mark a base around a Workbench. Use the bench to plan connected tunnels and stairs on a map, switch floors and manage digging. Workers carry the drops to core mine storage.";
            case BUILDING -> "Queue started town construction at a Builder's Workbench. Apprentices travel to the project and build reachable pieces. Better Building skill and equipment improve their pace.";
            case ALCHEMY -> "Apprentices brew potions from gathered and cultivated ingredients.";
            case CULTIVATION -> "Grow up to six alchemical plants on crystal soil. One Apprentice plants and harvests at a time. Growing plants need no worker and provide no food support.";
            case CRAFTING -> "Apprentices make supplies and useful tools at a workbench.";
            case LEATHERWORKING -> "Apprentices tan hides into leather and prepare leather goods.";
            case SALVAGING -> "Apprentices take apart old weapons, armor and tools at a salvager's workbench and send the recovered materials to Crafted Goods.";
            case COOKING -> "Apprentices cook food items for you. This site does not increase town food support.";
            case JEWELRY -> "Apprentices craft jewelry. An appraisal bench lets new jewelry arrive appraised.";
            case LOGISTICS -> "Couriers carry supplies between town stores and workplaces. Set their priorities in the town journal. No workplace is needed.";
            case COMMERCE -> "A clerk serves customers at a player shop's counter during the day. Choose the clerk at the shop's Town Records Shelf and set the priority in the town journal. No workplace is needed.";
        };
    }
}
