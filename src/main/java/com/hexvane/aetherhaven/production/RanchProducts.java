package com.hexvane.aetherhaven.production;

import java.util.*;
import com.hexvane.aetherhaven.workplace.RanchWorkService;

/** Requested barn products. Each needs a live, town-raised animal of an explicitly registered family. */
public final class RanchProducts {
    private RanchProducts() {}
    public record Product(String family,String output,int amount,boolean slaughter,String tool) {
        public Product {
            if(family==null||family.isBlank()||output==null||output.isBlank()||amount<1||amount>16||tool==null||tool.isBlank())throw new IllegalArgumentException("Invalid ranch product");
        }
    }
    private static final Map<String,Product> PRODUCTS=new java.util.concurrent.ConcurrentHashMap<>();
    static {
        register(new Product("Cow",ProductionWithdrawal.MILK_BUCKET_ITEM_ID,1,false,"Container_Bucket"));
        register(new Product("Sheep","Ingredient_Fabric_Scrap_Wool",2,false,"Tool_Shears_Basic"));
        register(new Product("Chicken","Food_Egg",1,false,"Tool_Feedbag"));
        register(new Product("Chicken","Ingredient_Feathers_Light",2,false,"Tool_Shears_Basic"));
        register(new Product("Mosshorn","Ingredient_Hide_Heavy",1,true,"Tool_Hatchet_Crude"));
        register(new Product("Skrill","Ingredient_Feathers_Red",2,false,"Tool_Shears_Basic"));
        register(new Product("Skrill","Food_Egg",1,false,"Tool_Feedbag"));
        register(new Product("Tetrabird","Ingredient_Feathers_Dark",2,false,"Tool_Feedbag"));
        register(new Product("Bluebird","Ingredient_Feathers_Blue",1,false,"Tool_Feedbag"));
        register(new Product("Bluebird","Food_Wildmeat_Raw",1,true,"Tool_Hatchet_Crude"));
        for(String family:List.of("Cow","Pig","Sheep","Goat","Chicken")) {
            String meat=switch(family){case "Cow"->"Beef";case "Pig"->"Pork";case "Chicken"->"Chicken";default->"Wildmeat";};
            register(new Product(family,"Food_"+meat+"_Raw",2,true,"Tool_Hatchet_Crude"));
            register(new Product(family,"Ingredient_Hide_"+(family.equals("Cow")?"Medium":"Light"),1,true,"Tool_Hatchet_Crude"));
        }
        for(String family:List.of("Mosshorn","Skrill","Tetrabird"))
            register(new Product(family,"Food_Wildmeat_Raw",family.equals("Mosshorn")?4:2,true,"Tool_Hatchet_Crude"));
        register(new Product("Skrill","Ingredient_Hide_Light",1,true,"Tool_Hatchet_Crude"));
        for(String family:List.of("Cow","Pig","Sheep","Goat","Chicken","Mosshorn","Skrill","Tetrabird","Bluebird"))
            register(new Product(family,"Ingredient_Poop",1,false,"Tool_Shovel_Crude"));
    }
    public static void register(Product product) {
        Objects.requireNonNull(product);String key=product.family()+"/"+product.output();
        var old=PRODUCTS.putIfAbsent(key,product);
        if(old!=null&&!old.equals(product))throw new IllegalArgumentException("Ranch product already registered: "+key);
    }
    public static List<String> families(){return PRODUCTS.values().stream().map(Product::family).distinct().sorted().toList();}
    public static String family(String role) {
        var nativeFamily=RanchWorkService.family(role);if(nativeFamily==null)return null;
        for(String family:families()) {
            var registered=RanchWorkService.family(family);
            if(registered!=null&&registered.grownRole().equals(nativeFamily.grownRole()))return family;
        }
        return null;
    }
    public static boolean supported(String output){return PRODUCTS.values().stream().anyMatch(p->p.output().equals(output));}
    public static Product product(String family,String output){return PRODUCTS.get(family+"/"+output);}
    public static List<Product> selected(String family,PlotProductionState production,ProductionCatalog.Entry entry) {
        var selected=new LinkedHashSet<Product>();
        for(int slot=0;slot<WorkplaceProductionUpgrades.slotCount(production);slot++) {
            var product=PRODUCTS.get(family+"/"+entry.itemAtCursor(production.getSlotCursor(slot)));
            if(product!=null)selected.add(product);
        }
        return List.copyOf(selected);
    }
}
