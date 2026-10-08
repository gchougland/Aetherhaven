package com.hexvane.aetherhaven.hunter;

import java.util.*;

/** Integration API: register a dedicated, lootless NPC role, never an ordinary world spawn role. */
public record HuntFamily(String id,String name,String description,String role,String icon,int unlockItems,
                         int unlockGold,Map<String,Integer> drops,String nativeDropList) {
    public HuntFamily(String id,String name,String description,String role,String icon,int unlockItems,int unlockGold,Map<String,Integer> drops) {
        this(id,name,description,role,icon,unlockItems,unlockGold,drops,null);
    }
    private static final Map<String,HuntFamily> FAMILIES=new LinkedHashMap<>();
    static {
        register(new HuntFamily("bone","Restless Bones","A lone skeleton drawn from a forgotten crypt.",
            "Aetherhaven_Hunt_Skeleton","Ingredient_Bone_Fragment",10,40,Map.of("Ingredient_Bone_Fragment",2,"Ingredient_Fabric_Scrap_Linen",2),"Drop_Skeleton_Fighter"));
        register(new HuntFamily("web","Silken Hollows","A cave spider lured from its sheltered nest.",
            "Aetherhaven_Hunt_Spider","Ingredient_Sac_Venom",10,75,Map.of("Ingredient_Sac_Venom",1),"Drop_Spider_Cave"));
        register(new HuntFamily("swarm","Scarak Burrows","A chitinous scavenger from the desert tunnels.",
            "Aetherhaven_Hunt_Scarak","Ingredient_Chitin_Sturdy",10,150,Map.of("Ingredient_Chitin_Sturdy",1),"Drop_Scarak_Louse"));
        register(new HuntFamily("frost","Frozen Crypts","Frost undead carry cloth, bones and Ice Essence.",
            "Aetherhaven_Hunt_Frost","Ingredient_Ice_Essence",10,175,Map.of("Ingredient_Bone_Fragment",2,"Ingredient_Fabric_Scrap_Linen",3,"Ingredient_Ice_Essence",2),"Drop_Skeleton_Frost_Fighter"));
        register(new HuntFamily("cinder","Cinder Legion","A burnt soldier carrying Cindercloth and Fire Essence.",
            "Aetherhaven_Hunt_Burnt","Ingredient_Fire_Essence",10,225,Map.of("Ingredient_Fabric_Scrap_Cindercloth",3,"Ingredient_Fire_Essence",2),"Drop_Skeleton_Burnt_Soldier"));
        register(new HuntFamily("incandescent","Incandescent Guard","A spear wielding fire undead with Cindercloth and Fire Essence.",
            "Aetherhaven_Hunt_Incandescent","Ingredient_Fabric_Scrap_Cindercloth",15,250,Map.of("Ingredient_Fabric_Scrap_Cindercloth",3,"Ingredient_Fire_Essence",2),"Drop_Skeleton_Incandescent_Footman"));
        register(new HuntFamily("outlander","Outlander Marauders","A hardened marauder carrying Shadoweave scraps.",
            "Aetherhaven_Hunt_Outlander","Ingredient_Fabric_Scrap_Shadoweave",15,250,Map.of("Ingredient_Fabric_Scrap_Shadoweave",4),"Drop_Outlander_Marauder"));
        register(new HuntFamily("void","Void Crawlers","A swift crawler carrying Void Essence and a rare Voidheart.",
            "Aetherhaven_Hunt_Void_Crawler","Ingredient_Void_Essence",15,300,Map.of("Ingredient_Void_Essence",2,"Ingredient_Voidheart",1),"Drop_Crawler_Void"));
        register(new HuntFamily("voidspawn","Greater Voidspawn","A hulking Void Spawn carrying more Void Essence, with a chance of a Voidheart.",
            "Aetherhaven_Hunt_Void_Spawn","Ingredient_Voidheart",5,400,Map.of("Ingredient_Void_Essence",9,"Ingredient_Voidheart",1),"Drop_Spawn_Void"));
    }
    public HuntFamily {
        if(id==null||id.isBlank()||name==null||description==null||role==null||role.isBlank()||icon==null
            ||unlockItems<=0||unlockGold<=0||drops==null||drops.isEmpty()||drops.size()>5
            ||drops.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<1||e.getValue()>32))
            throw new IllegalArgumentException("Invalid hunt family");
        drops=Collections.unmodifiableMap(new LinkedHashMap<>(drops));
    }
    public static synchronized void register(HuntFamily family) {
        if(FAMILIES.putIfAbsent(family.id(),family)!=null)throw new IllegalArgumentException("Duplicate hunt family: "+family.id());
    }
    public static synchronized HuntFamily get(String id) { return FAMILIES.get(id); }
    /** Keep the retired definition resolvable only for already-running saved encounters. */
    public static synchronized List<HuntFamily> all() { return FAMILIES.values().stream().filter(f->!"incandescent".equals(f.id())).toList(); }
    public static String activeId(String id) { return "incandescent".equals(id) ? "cinder" : id; }
}
