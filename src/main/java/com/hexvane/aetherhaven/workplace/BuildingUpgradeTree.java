package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.*;

/** Stable purchase identities and prerequisite edges, shared by rendering and purchase validation. */
public final class BuildingUpgradeTree {
    private BuildingUpgradeTree() {}
    public enum Kind { FOUNDATION, WORKPLACE, SLOT, SMELTING, PRODUCTION, RESTAURANT, ALCHEMY, WAREHOUSE, WAREHOUSE_STACKS, INN, GARDEN, FARM }
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
            String bench=switch(t) {case SMELTING -> "Bench_Furnace";case BLACKSMITHING -> "Bench_Weapon";case ARMOR_CRAFTING -> "Bench_Armour";case SALVAGING -> "Bench_Salvage";case BLOCK_WORKSHOP -> "Bench_Builders";case MYCOLOGY -> "Aetherhaven_Mushroom_Log";case BERRIES -> "Plant_Fruit_Berries_Red";default -> null;};
            nodes.add(node("work:"+t.id,WorkplaceRules.label(t),WorkplaceRules.description(t),bench==null?t.icon:null,bench,
                Kind.WORKPLACE,t.id,1,1,types.size()==1?233:32+i*201,208,"home"));
        }
        for(int tier=1;tier<=35;tier++) {
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
                "Adds room for "+com.hexvane.aetherhaven.warehouse.SmeltingOutputStore.UPGRADE_LIMIT+" more of each smelted resource. All smelteries share this limit.","crate.png",null,
                Kind.SMELTING,"",tier,level,434,bandTop(level)+42+Math.max(0,tier-5)*150,tier==1?"home":"smelt:"+(tier-1)));
        }
        return List.copyOf(nodes);
    }
    public static List<Node> production() {
        return production(false);
    }
    public static List<Node> production(boolean barn) {
        var nodes=new ArrayList<Node>();
        nodes.add(node("IRON:1",barn?"Second animal pen":"Second output",barn?"Keeps a second owned animal active in the barn.":"Adds a second production output slot.",null,"Ingredient_Bar_Iron",Kind.PRODUCTION,"IRON",1,1,233,48));
        for(int tier=1;tier<=3;tier++) {
            int level=tier+1, y=bandTop(level,true)+42;
            if(!barn)nodes.add(node("THORIUM:"+tier,"Production speed "+tier,"Adds 20% production speed.",null,"Ingredient_Bar_Thorium",Kind.PRODUCTION,"THORIUM",tier,level,32,y,tier==1?"IRON:1":"THORIUM:"+(tier-1)));
            nodes.add(node("COBALT:"+tier,(barn?"Animal pen ":"Output slot ")+(tier+2),barn?"Keeps one more owned animal active in the barn.":"Adds one production output slot.",null,"Ingredient_Bar_Cobalt",Kind.PRODUCTION,"COBALT",tier,level,434,y,tier==1?"IRON:1":"COBALT:"+(tier-1)));
            var parents=new ArrayList<>(barn?List.of("COBALT:"+tier):List.of("THORIUM:"+tier,"COBALT:"+tier));
            if(tier>1)parents.add("ADAMANTITE:"+(tier-1));
            nodes.add(node("ADAMANTITE:"+tier,"Resource storage "+tier,barn?"Adds room for 10 more of every animal resource.":"Adds 100% of the original storage capacity for each production output.",null,"Ingredient_Bar_Adamantite",Kind.PRODUCTION,"ADAMANTITE",tier,level+1,233,bandTop(level+1,true)+42,parents.toArray(String[]::new)));
        }
        return List.copyOf(nodes);
    }
    /**
     * The Conservatory's own upgrades: one row for each track, bought from left to right with Life Essence and the
     * plants Rowan grows. Levels and costs come from the Spirit Garden's rules, never from ingots.
     */
    public static List<Node> garden() {
        var rules=com.hexvane.aetherhaven.production.SpiritGardenRules.get();
        var nodes=new ArrayList<Node>();int row=0;
        for(var track:com.hexvane.aetherhaven.production.GardenUpgrades.Track.values()) {
            // Three nodes fit across the tree, and four rows fit its window without scrolling.
            int levels=Math.min(3,com.hexvane.aetherhaven.production.GardenUpgrades.maxLevel(track));
            for(int tier=1;tier<=levels;tier++) {
                double value=com.hexvane.aetherhaven.production.GardenUpgrades.value(track,tier);
                double base=com.hexvane.aetherhaven.production.GardenUpgrades.value(track,0);
                String amount=value==Math.rint(value)?Long.toString(Math.round(value)):Double.toString(value);
                String title=switch(track) {case PLANTERS -> "More planters ";case RITE -> "Stronger rite ";case BOUNTY -> "Bountiful harvest ";case STORES -> "Wider stores ";}+tier;
                String description=switch(track) {
                    case PLANTERS -> "Rowan tends "+amount+" Spirit Planters.";
                    case RITE -> "Plants rest "+amount+" hours between rites instead of "+Math.round(base)+", so they ripen sooner.";
                    case BOUNTY -> "Each harvest gives "+amount+" plants, with more petals to match.";
                    case STORES -> "The Conservatory holds "+amount+" times as much of every plant and petal.";
                };
                nodes.add(node(track.name()+":"+tier,title,description,null,rules.track(track).icon(),Kind.GARDEN,track.name(),tier,1,
                    32+(tier-1)*201,38+row*124,tier==1?new String[0]:new String[]{track.name()+":"+(tier-1)}));
            }
            row++;
        }
        return List.copyOf(nodes);
    }
    /**
     * The farm's own upgrades: six tracks of two levels, each track a column bought from the top down with Life
     * Essence and the crops the Farmer grows. Levels and costs come from the Scarecrow Fields' rules, never from ingots.
     */
    public static List<Node> farm() {
        var rules=com.hexvane.aetherhaven.production.FarmFieldRules.get();
        var nodes=new ArrayList<Node>();var tracks=com.hexvane.aetherhaven.production.FarmUpgrades.Track.values();
        for(int i=0;i<tracks.length;i++) {
            var track=tracks[i];
            // Three tracks side by side, twice: twelve nodes fill the tree's window without scrolling.
            int column=i%3,top=i<3?38:310,levels=Math.min(2,com.hexvane.aetherhaven.production.FarmUpgrades.maxLevel(track));
            for(int tier=1;tier<=levels;tier++) {
                double value=com.hexvane.aetherhaven.production.FarmUpgrades.value(track,tier);
                double before=com.hexvane.aetherhaven.production.FarmUpgrades.value(track,tier-1);
                String amount=Long.toString(Math.round(value));
                String title=switch(track) {case SCARECROWS -> "More Scarecrows ";case REACH -> "Wider reach ";case WATERING -> "Better watering ";
                    case SEEDS -> "Seed saving ";case COMPOST -> "Richer compost ";case ALMANAC -> "Almanac insight ";}+tier;
                String description=switch(track) {
                    case SCARECROWS -> "Irienne tends "+amount+" Scarecrow fields beside the farm's own field. She shares her day between all her fields.";
                    case REACH -> "Every Scarecrow field reaches "+amount+" blocks from its Scarecrow in each direction, up from "+Math.round(before)+".";
                    case WATERING -> tier==1?"Each watering also wets the planned cells next to the one Irienne stands at."
                        :"Each watering wets every planned cell within "+amount+" blocks of Irienne.";
                    case SEEDS -> value>=1?"Every harvest gives its seed back."
                        :"A harvest gives its seed back "+Math.round(value*100)+" times in 100, up from "+Math.round(before*100)+".";
                    case COMPOST -> value<before?amount+" spare crops make one compost, down from "+Math.round(before)+"."
                        :"Compost also makes Good soil Rich, so well fed fields give double harvests without rotating crops.";
                    case ALMANAC -> value>before?"The Almanac favours "+amount+" crops each week, up from "+Math.round(before)+"."
                        :"The Almanac shows "+rules.weeksShown(tier)+" weeks of favoured crops, a whole season, so you can plan ahead.";
                };
                nodes.add(node(track.name()+":"+tier,title,description,null,rules.track(track).icon(),Kind.FARM,track.name(),tier,1,
                    32+column*201,top+(tier-1)*124,tier==1?new String[0]:new String[]{track.name()+":"+(tier-1)}));
            }
        }
        return List.copyOf(nodes);
    }
    public static List<Node> restaurant() {
        var nodes=new ArrayList<Node>();
        nodes.add(node("home","Restaurant","Improve the meals and service at this restaurant.","food-tray.png",null,Kind.FOUNDATION,"",0,1,233,48));
        // Three columns through Town Levels 2 to 4: Satiety, Menu, Service.
        for(int tier=1;tier<=3;tier++) for(var branch:List.of(RestaurantUpgrades.Branch.SATIETY,RestaurantUpgrades.Branch.MENU,RestaurantUpgrades.Branch.SERVICE)) {
            boolean satiety=branch==RestaurantUpgrades.Branch.SATIETY, menu=branch==RestaurantUpgrades.Branch.MENU;
            String id=branch.name()+":"+tier;
            nodes.add(node(id,(satiety?"Hearty meals ":menu?"Chef's menu ":"Table service ")+tier,
                satiety?"Residents stay satisfied for longer after eating."
                    :menu?"Adds new dishes to the menu. A resident who eats here gets a short perk from one of them."
                    :"Residents finish meals faster and recover more hunger.",null,
                RestaurantUpgrades.ingredientCosts(branch,tier).getFirst().itemId(),Kind.RESTAURANT,branch.name(),tier,tier+1,
                satiety?32:menu?233:434,bandTop(tier+1,true)+42,tier==1?"home":branch.name()+":"+(tier-1)));
        }
        // Provisions matter once the town outgrows its first food buildings, so they sit in the last two bands.
        for(int tier=1;tier<=3;tier++) {
            var branch=RestaurantUpgrades.Branch.PROVISIONS;
            int level=tier==1?5:6;
            nodes.add(node(branch.name()+":"+tier,"Town provisions "+tier,
                "Adds "+RestaurantUpgrades.PROVISIONS_FOOD_PER_LEVEL+" Food Production. Every "+com.hexvane.aetherhaven.town.TownPopulation.FOOD_PER_RESIDENT
                    +" Food feeds one resident. The kitchen cooks what your farms and ranches bring in, so it never adds more food than they do.",null,
                RestaurantUpgrades.ingredientCosts(branch,tier).getFirst().itemId(),Kind.RESTAURANT,branch.name(),tier,level,
                tier==3?434:233,bandTop(level,true)+42,tier==1?"home":branch.name()+":"+(tier-1)));
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
    public static List<Node> inn() {
        return List.of(
            node("home","Town Inn","A welcoming place for visiting villagers.","house_icon_256.png",null,Kind.FOUNDATION,"",0,1,233,48),
            node("outreach","Guest book","Choose an available guest to visit the Inn. Each invitation costs 50 gold from your inventory or treasury.","open-book.png",null,Kind.INN,"",1,3,233,bandTop(3,true)+42,"home")
        );
    }
    public static List<Node> warehouse() {
        var nodes=new ArrayList<Node>();
        nodes.add(node("home","Town warehouse","Shared storage for your town's supplies and crafted goods.","crate.png",null,Kind.FOUNDATION,"",0,1,233,48));
        for(int tier=1;tier<=com.hexvane.aetherhaven.warehouse.TownWarehouse.MAX_UPGRADES;tier++) {
            int level=Math.min(6,tier+1);
            nodes.add(node("warehouse:"+tier,"General storage "+tier,"Adds 20 general storage slots. Crafted goods and production buildings keep their own limits.",
                "crate.png",null,Kind.WAREHOUSE,"",tier,level,233,bandTop(level,true)+42+Math.max(0,tier-5)*150,
                tier==1?"home":"warehouse:"+(tier-1)));
        }
        // A side branch, like the backpack's compression: the same slots, each holding more of one item.
        for(int tier=1;tier<=com.hexvane.aetherhaven.warehouse.TownWarehouse.MAX_STACK_UPGRADES;tier++) {
            int level=Math.min(6,tier+2);
            nodes.add(node("stacks:"+tier,"Deep shelves "+tier,"Every general storage slot holds "+(tier+1)+" stacks of the same item. Crafted goods and production buildings keep their own limits.",
                "sack.png",null,Kind.WAREHOUSE_STACKS,"",tier,level,434,bandTop(level,true)+42,
                tier==1?"home":"stacks:"+(tier-1)));
        }
        return List.copyOf(nodes);
    }
}
