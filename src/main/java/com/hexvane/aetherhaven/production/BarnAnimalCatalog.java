package com.hexvane.aetherhaven.production;

import java.util.*;

/** Barn species and native resource identities. Purchasing creates one individually named animal. */
public final class BarnAnimalCatalog {
    public enum Rarity {
        COMMON("Common", 1, 100), UNCOMMON("Uncommon", 4, 250), RARE("Rare", 7, 500);
        public final String label; public final int mastery, gold;
        Rarity(String label, int mastery, int gold) { this.label=label; this.mastery=mastery; this.gold=gold; }
    }
    public record Species(String id, String model, Rarity rarity, String food, int farmingTier, Map<String,Integer> products) {
        public Species { products=Collections.unmodifiableMap(new LinkedHashMap<>(products)); }
        public String role() { return "Aetherhaven_Barn_"+id; }
        public String icon() { return "Sheep".equals(id)?"Icons/ModelsGenerated/Aetherhaven_Barn_Sheep.png":"Icons/ModelsGenerated/"+model+".png"; }
    }
    private static final Map<String,Species> SPECIES=new LinkedHashMap<>();
    static {
        add("Chicken","Chicken",Rarity.COMMON,"Chicken","Light",1,"Food_Egg",1);
        add("Pig","Pig",Rarity.COMMON,"Pork","Light",1);
        add("Rabbit","Rabbit",Rarity.COMMON,"Wildmeat","Light",1);
        add("Sheep","Sheep",Rarity.COMMON,"Wildmeat","Light",1,"Ingredient_Fabric_Scrap_Wool",2);
        add("Goat","Goat",Rarity.COMMON,"Wildmeat","Light",1);
        add("Cow","Cow",Rarity.UNCOMMON,"Beef","Medium",1,ProductionWithdrawal.MILK_BUCKET_ITEM_ID,1);
        add("Boar","Boar",Rarity.UNCOMMON,"Wildmeat","Medium",1);
        add("Deer","Deer_Doe",Rarity.UNCOMMON,"Wildmeat","Light",1);
        add("Tetrabird","Tetrabird",Rarity.UNCOMMON,"Wildmeat",null,1,"Ingredient_Feathers_Dark",2);
        add("Skrill","Skrill",Rarity.UNCOMMON,"Wildmeat","Light",1,"Food_Egg",1);
        add("Camel","Camel",Rarity.UNCOMMON,"Beef","Light",2);
        add("Bear","Bear_Grizzly",Rarity.RARE,"Wildmeat","Heavy",2);
        add("Mosshorn","Mosshorn",Rarity.RARE,"Wildmeat","Heavy",4);
        add("Moose","Moose_Bull",Rarity.RARE,"Wildmeat","Medium",2);
        add("Armadillo","Armadillo",Rarity.RARE,"Wildmeat",null,1,"Ingredient_Chitin_Sturdy",1);
    }
    private BarnAnimalCatalog() {}
    private static void add(String id,String model,Rarity rarity,String meat,String hide,int meatAmount,Object... extra) {
        var products=new LinkedHashMap<String,Integer>();
        products.put("Food_"+meat+"_Raw",meatAmount);
        if(hide!=null)products.put("Ingredient_Hide_"+hide,1);
        for(int i=0;i<extra.length;i+=2)products.put((String)extra[i],(Integer)extra[i+1]);
        String crop=switch(id) {
            case "Chicken"->"Corn"; case "Pig"->"Turnip"; case "Rabbit"->"Carrot";
            case "Sheep"->"Lettuce"; case "Goat"->"Cauliflower";
            case "Cow", "Deer"->"Pumpkin"; case "Boar", "Camel"->"Aubergine";
            case "Tetrabird"->"Tomato"; case "Skrill"->"Chilli";
            case "Mosshorn"->"Onion"; case "Moose", "Armadillo"->"Potato";
            case "Bear"->null; default->throw new IllegalArgumentException(id);
        };
        int tier=crop==null?0:switch(crop) {
            case "Lettuce"->1; case "Corn", "Carrot"->2; case "Turnip", "Cauliflower"->3;
            case "Pumpkin", "Aubergine"->4; case "Tomato", "Chilli"->5;
            case "Onion", "Potato"->7; default->throw new IllegalArgumentException(crop);
        };
        String food=crop==null?"Food_Wildmeat_Raw":"Plant_Crop_"+crop+"_Item";
        SPECIES.put(id,new Species(id,model,rarity,food,tier,products));
    }
    /** One storage line per resource, shared by the barn and warehouse views. */
    public static List<String> storageItems(PlotProductionState state) {
        var items=new TreeSet<String>();
        state.getAmounts().forEach((item,count)->{if(count>0)items.add(item);});
        for(var animal:state.herd().animals()){
            var species=get(animal.species());if(species!=null)items.addAll(species.products().keySet());
        }
        if(!state.herd().animals().isEmpty())items.add("Ingredient_Poop");
        return List.copyOf(items);
    }
    public static Species get(String id) { return SPECIES.get(id); }
    public static List<Species> all() { return List.copyOf(SPECIES.values()); }
    public static boolean food(String item) { return SPECIES.values().stream().anyMatch(s->s.food().equals(item)); }
    public static boolean output(String item) { return "Ingredient_Poop".equals(item)||SPECIES.values().stream().anyMatch(s->s.products.containsKey(item)); }
}
