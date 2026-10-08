package com.hexvane.aetherhaven.backpack;

import java.util.*;

public final class BackpackUpgrades {
    private BackpackUpgrades(){}
    public static List<String> available(Backpack pack){
        var result=new ArrayList<String>();
        if(pack.tier==Backpack.Tier.SIMPLE)result.add("IRON");
        if(pack.tier==Backpack.Tier.IRON){result.add("COBALT");result.add("THORIUM");}
        if(pack.tier==Backpack.Tier.COBALT||pack.tier==Backpack.Tier.THORIUM)result.add("ADAMANTITE");
        if(pack.compression<3)result.add("Compression");if(!pack.magnet)result.add("Magnet");if(!pack.jukebox)result.add("Jukebox");return result;
    }
    public static Map<String,Integer> cost(Backpack pack,String option){return switch(option){
        case "IRON"->Map.of("Ingredient_Leather_Light",10,"Ingredient_Bar_Iron",10);
        case "COBALT"->Map.of("Ingredient_Leather_Medium",15,"Ingredient_Bar_Cobalt",15);
        case "THORIUM"->Map.of("Ingredient_Leather_Medium",15,"Ingredient_Bar_Thorium",15);
        case "ADAMANTITE"->Map.of("Ingredient_Leather_Heavy",20,"Ingredient_Bar_Adamantite",20);
        case "Compression"->Map.of("Ingredient_Leather_"+switch(pack.compression){case 0->"Light";case 1->"Medium";default->"Heavy";},10*(pack.compression+1));
        case "Magnet"->Map.of("Ingredient_Bar_Iron",15,"Ingredient_Bar_Cobalt",5);
        case "Jukebox"->Map.of("Ingredient_Bar_Iron",10,"Ingredient_Bar_Thorium",5);
        default->Map.of();};}
    public static boolean apply(Backpack pack,String option){
        if(!available(pack).contains(option))return false;
        switch(option){case "Compression"->pack.compression++;case "Magnet"->pack.magnet=true;case "Jukebox"->pack.jukebox=true;default->pack.tier=Backpack.Tier.valueOf(option);}return true;
    }
    public static String label(Backpack pack,String option){return switch(option){
        case "Compression"->"Compression "+(pack.compression+1)+": +1 stack per slot";
        case "Magnet"->"Magnet: collect nearby items";case "Jukebox"->"Jukebox: music follows you";
        default->{var tier=Backpack.Tier.valueOf(option);yield tier.label+": "+tier.slots+" slots, "+Backpack.stacksLabel(tier.stacks+pack.compression)+" each";}};}
}
