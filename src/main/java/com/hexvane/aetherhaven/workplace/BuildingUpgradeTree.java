package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.*;

/** Stable purchase identities and prerequisite edges, shared by rendering and purchase validation. */
public final class BuildingUpgradeTree {
    private BuildingUpgradeTree() {}
    public enum Kind { FOUNDATION, WORKPLACE, SLOT, SMELTING, PRODUCTION, RESTAURANT, ALCHEMY, WAREHOUSE }
    public record Node(String id, String title, String description, String icon, String item,
                       Kind kind, String branch, int tier, int level, int x, int y, List<String> parents) {}
    public static int bandTop(int level) { return level == 1 ? 0 : 350 + (level - 2) * 170; }
    public static int bandTop(int level, boolean compact) { return bandTop(level)-(compact&&level>1?180:0); }
    private static Node node(String id, String title, String description, String icon, String item,
                             Kind kind, String branch, int tier, int level, int x, int y, String... parents) {
        return new Node(id,title,description,icon,item,kind,branch,tier,level,x,y,List.of(parents));
    }
    public static List<Node> workplaces(String supervisor) {
        var nodes = new ArrayList<Node>();
        nodes.add(node("home","Resident mentor","This resident supervises your workplaces. Mark new work areas with the Building Staff.",
            "house_icon_256.png",null,Kind.FOUNDATION,"",0,1,233,48));
        var types = Arrays.stream(TownsfolkWorkType.values()).filter(t -> t.requiresWorkplace() && t.availableForNewWork() && WorkplaceRules.supervisor(t).equals(supervisor)).toList();
        for(int i=0;i<types.size();i++) {
            var t=types.get(i);
            String bench=switch(t) {case SMELTING -> "Bench_Furnace";case BLACKSMITHING -> "Bench_Weapon";case ARMOR_CRAFTING -> "Bench_Armour";default -> null;};
            nodes.add(node("work:"+t.id,WorkplaceRules.label(t),WorkplaceRules.description(t),bench==null?t.skill.icon:null,bench,
                Kind.WORKPLACE,t.id,1,1,types.size()==1?233:32+i*201,208,"home"));
        }
        for(int tier=1;tier<=32;tier++) {
            int level=Math.min(6,tier+1), offset=Math.max(0,tier-5), row=offset/2;
            int col=offset%2; if(row%2==1)col=1-col;
            int x=tier<5?32:32+col*201;
            int y=bandTop(level)+42+(tier<5?0:row*150);
            nodes.add(node("slot:"+tier,"Workplace slot "+(WorkplaceRules.initialSlots(supervisor)+tier),
                "Adds one workplace slot shared by every workplace type supervised by this villager.","hammer.png",null,
                Kind.SLOT,"",tier,level,x,y,tier==1?"home":"slot:"+(tier-1)));
        }
        if("blacksmith".equals(supervisor))for(int tier=1;tier<=8;tier++) {
            int level=Math.min(6,tier+1);
            nodes.add(node("smelt:"+tier,"Smelting storage "+tier,
                "Adds room for 100 more of each smelted resource. All smelteries share this limit.","crate.png",null,
                Kind.SMELTING,"",tier,level,434,bandTop(level)+42+Math.max(0,tier-5)*150,tier==1?"home":"smelt:"+(tier-1)));
        }
        return List.copyOf(nodes);
    }
    public static List<Node> production() {
        var nodes=new ArrayList<Node>();
        nodes.add(node("IRON:1","Second output","Adds a second production output slot.",null,"Ingredient_Bar_Iron",Kind.PRODUCTION,"IRON",1,1,233,48));
        for(int tier=1;tier<=3;tier++) {
            int level=tier+1, y=bandTop(level,true)+42;
            nodes.add(node("THORIUM:"+tier,"Production speed "+tier,"Adds 20% production speed.",null,"Ingredient_Bar_Thorium",Kind.PRODUCTION,"THORIUM",tier,level,32,y,tier==1?"IRON:1":"THORIUM:"+(tier-1)));
            nodes.add(node("COBALT:"+tier,"Output slot "+(tier+2),"Adds one production output slot.",null,"Ingredient_Bar_Cobalt",Kind.PRODUCTION,"COBALT",tier,level,434,y,tier==1?"IRON:1":"COBALT:"+(tier-1)));
            var parents=new ArrayList<>(List.of("THORIUM:"+tier,"COBALT:"+tier));
            if(tier>1)parents.add("ADAMANTITE:"+(tier-1));
            nodes.add(node("ADAMANTITE:"+tier,"Resource storage "+tier,"Adds 100% of the original storage capacity for each production output.",null,"Ingredient_Bar_Adamantite",Kind.PRODUCTION,"ADAMANTITE",tier,level+1,233,bandTop(level+1,true)+42,parents.toArray(String[]::new)));
        }
        return List.copyOf(nodes);
    }
    public static List<Node> restaurant() {
        var nodes=new ArrayList<Node>();
        nodes.add(node("home","Restaurant","Improve the meals and service at this restaurant.","food-tray.png",null,Kind.FOUNDATION,"",0,1,233,48));
        for(int tier=1;tier<=3;tier++) for(var branch:RestaurantUpgrades.Branch.values()) {
            boolean satiety=branch==RestaurantUpgrades.Branch.SATIETY;
            String id=branch.name()+":"+tier;
            nodes.add(node(id,(satiety?"Hearty meals ":"Table service ")+tier,
                satiety?"Residents stay satisfied for longer after eating.":"Residents finish meals faster and recover more hunger.",null,
                RestaurantUpgrades.ingredientCosts(branch,tier).getFirst().itemId(),Kind.RESTAURANT,branch.name(),tier,tier+1,
                satiety?32:434,bandTop(tier+1,true)+42,tier==1?"home":branch.name()+":"+(tier-1)));
        }
        return List.copyOf(nodes);
    }
    public static List<Node> alchemy() {
        return List.of(
            node("home","The Alchemist's cauldron","Brew a potion from one ingredient. Mix, discover and bottle it in a reusable Alchemy Flask.",null,"Alchemy_Cauldron_Big",Kind.FOUNDATION,"",0,1,233,48),
            node("alchemy:1","Second ingredient","Combine two ingredient properties in one flask. Requires Alchemist Mastery 3.",null,"Plant_Crop_Mana1",Kind.ALCHEMY,"",1,2,233,bandTop(2,true)+42,"home"),
            node("alchemy:2","Third ingredient","Combine up to three ingredient properties in one flask. Requires Alchemist Mastery 6.",null,"Plant_Crop_Mushroom_Boomshroom_Small",Kind.ALCHEMY,"",2,3,233,bandTop(3,true)+42,"alchemy:1")
        );
    }
    public static List<Node> warehouse() {
        var nodes=new ArrayList<Node>();
        nodes.add(node("home","Town warehouse","Shared storage for your town's supplies and crafted goods.","crate.png",null,Kind.FOUNDATION,"",0,1,233,48));
        for(int tier=1;tier<=com.hexvane.aetherhaven.warehouse.TownWarehouse.MAX_UPGRADES;tier++) {
            int level=Math.min(6,tier+1);
            nodes.add(node("warehouse:"+tier,"General storage "+tier,"Adds 18 general storage slots. Crafted goods and production buildings keep their own limits.",
                "crate.png",null,Kind.WAREHOUSE,"",tier,level,233,bandTop(level,true)+42+Math.max(0,tier-5)*150,
                tier==1?"home":"warehouse:"+(tier-1)));
        }
        return List.copyOf(nodes);
    }
}
