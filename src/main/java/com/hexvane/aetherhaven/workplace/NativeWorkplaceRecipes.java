package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import java.util.*;

/** Routes the same registered recipes used by native benches to their workplaces. */
final class NativeWorkplaceRecipes {
    private static final Map<String,TownsfolkWorkType> BENCHES=Map.of(
        "Workbench",TownsfolkWorkType.CRAFTING,
        "Weapon_Bench",TownsfolkWorkType.BLACKSMITHING,
        "Armor_Bench",TownsfolkWorkType.ARMOR_CRAFTING,
        "Furnace",TownsfolkWorkType.SMELTING,
        "Tannery",TownsfolkWorkType.LEATHERWORKING,
        "Cookingbench",TownsfolkWorkType.COOKING,
        "Alchemybench",TownsfolkWorkType.ALCHEMY);

    private NativeWorkplaceRecipes() {}

    static Map<String,WorkplaceRecipe> merge(Map<String,WorkplaceRecipe> configured,
                                            Map<String,CraftingRecipe> nativeRecipes) {
        var result=new LinkedHashMap<>(configured);
        var explicit=new HashSet<String>();
        configured.values().forEach(r->explicit.add(key(r.workplace(),r.sourceRecipe(),r.bench())));
        for(var entry:nativeRecipes.entrySet()) {
            var source=entry.getValue();
            if(source==null||source.getBenchRequirement()==null)continue;
            var output=source.getPrimaryOutput();
            if(output==null&&source.getOutputs()!=null&&source.getOutputs().length>0)output=source.getOutputs()[0];
            if(output==null||output.getItemId()==null)continue;
            for(var bench:source.getBenchRequirement()) {
                if(bench==null||bench.id==null)continue;
                var type=BENCHES.get(bench.id);
                if(type==null||(bench.type!=BenchType.Crafting&&bench.type!=BenchType.Processing))continue;
                if(explicit.contains(key(type.id,entry.getKey(),bench.id)))continue;
                int tier=Math.clamp(bench.requiredTierLevel,1,10);
                String id="native/"+type.id+"/"+entry.getKey()+"/"+bench.id;
                var existing=result.get(id);
                if(existing==null||tier<existing.benchTier())result.put(id,new WorkplaceRecipe(id,label(output.getItemId()),type.id,
                    entry.getKey(),bench.id,tier,Math.min(10,1+(tier-1)*2)));
            }
        }
        return result;
    }
    private static String key(String type,String recipe,String bench) {return type+"\n"+recipe+"\n"+bench;}
    private static String label(String itemId) {
        var store=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetStore();
        var item=store==null?null:store.getAssetMap().getAsset(itemId);
        var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String translated=item==null||i18n==null?null:i18n.getMessage("en-US",item.getTranslationKey());
        return translated==null||translated.isBlank()?itemId.replace('_',' '):translated;
    }
}
