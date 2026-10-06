package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.mastery.CoreProductionRequirements;
import com.hexvane.aetherhaven.production.NativeTimberGrowth;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Read-only comparison with loaded native recipe assets. Never changes towns, items or progression. */
final class AetherhavenMasteryAssetsTestCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOG=HytaleLogger.forEnclosingClass();
    AetherhavenMasteryAssetsTestCommand(){super("masteryassets","Audit native wood tiers and seed crafting requirements");setPermissionGroups("hytale:WorldEditor");}
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var failures=new ArrayList<String>();var benchIds=new TreeSet<String>();
        for(var species:NativeTimberGrowth.species()) {
            var nativeRecipe=recipe(species.sapling(),benchIds);
            if(Item.getAssetMap().getAsset(species.sapling())==null)failures.add(species.sapling()+": missing sapling item");
            int tier=nativeRecipe==null?10:tier(nativeRecipe);
            var actual=CoreProductionRequirements.requirement("logger",species.output());
            int tool=tier>=7?4:tier>=5?3:tier>=4?2:tier>=3?1:0;
            if(actual.mastery()!=Math.clamp(tier,1,10)||actual.toolTier()!=tool)
                failures.add(species.output()+": native tier "+tier+", resolved "+actual);
            LOG.atInfo().log("Mastery wood audit: %s sapling=%s tier=%s mastery=%s toolTier=%s source=%s",species.output(),species.sapling(),tier,actual.mastery(),actual.toolTier(),nativeRecipe==null?"provisional fallback":"native recipe");
        }
        var recipes=AetherhavenPlugin.get().getApprenticeRecipes().all().stream().filter(r->r.id().startsWith("grow_plant_")).toList();
        if(recipes.size()!=108)failures.add("Expected 108 bundled seed and sapling recipes; found "+recipes.size());
        for(var rule:recipes) {
            if(rule.output()==null||Item.getAssetMap().getAsset(rule.output())==null){failures.add(rule.id()+": missing output item");continue;}
            var source=recipe(rule.output(),benchIds);
            if(source==null&&rule.output().startsWith("Plant_Seeds_"))
                source=recipe(rule.output().replace("Plant_Seeds_","Plant_Sapling_"),benchIds);
            int skill=source==null?10:Math.clamp(tier(source),1,10),essence=0;
            if(source!=null&&source.getInput()!=null)for(var material:source.getInput())
                if("Ingredient_Life_Essence".equals(material.getItemId()))essence+=material.getQuantity();
            int quantity=source==null||source.getPrimaryOutput()==null?1:Math.max(1,source.getPrimaryOutput().getQuantity());
            int cost=essence>0?Math.max(1,(essence+quantity-1)/quantity):5*skill;
            var alchemy=java.util.regex.Pattern.compile("Plant_Seeds_(Health|Mana|Stamina)([123])").matcher(rule.output());
            if(alchemy.matches()) {
                int cropTier=Integer.parseInt(alchemy.group(2));skill=switch(cropTier){case 1->1;case 2->4;default->7;};
                cost=switch(cropTier){case 1->12;case 2->24;default->48;};
            }
            if(rule.type()!=TownsfolkWorkType.CRAFTING||rule.skill()!=TownsfolkSkill.FARMING||rule.skillLevel()!=skill)
                failures.add(rule.id()+": expected Farming "+skill+" at a Crafting workplace, got "+rule.skillLevel());
            if(rule.inputs()==null||rule.inputs().size()!=1||!"Ingredient_Life_Essence".equals(rule.inputs().getFirst().itemId())
                ||rule.inputs().getFirst().resourceType()!=null||rule.inputs().getFirst().count()!=cost)
                failures.add(rule.id()+": expected only "+cost+" Life Essence, got "+rule.inputs());
            if(rule.outputCount()!=1)failures.add(rule.id()+": each crafting cycle must produce one seed or sapling");
        }
        for(String failure:failures)LOG.atWarning().log("Mastery asset audit: %s",failure);
        String result="Mastery asset check "+(failures.isEmpty()?"PASSED":"FAILED")+": "+NativeTimberGrowth.species().size()
            +" timber species, "+recipes.size()+" seed recipes, "+failures.size()+" failures. Native bench IDs: "+benchIds+".";
        LOG.atInfo().log(result);ctx.sendMessage(Message.raw(result));
        for(String failure:failures.stream().limit(8).toList())ctx.sendMessage(Message.raw(failure));
        return CompletableFuture.completedFuture(null);
    }
    private static CraftingRecipe recipe(String itemId,Set<String> benchIds) {
        var item=itemId==null?null:Item.getAssetMap().getAsset(itemId);if(item==null)return null;
        var recipes=new ArrayList<CraftingRecipe>();item.collectRecipesToGenerate(recipes);
        for(var recipe:recipes)if(recipe.getBenchRequirement()!=null)for(var bench:recipe.getBenchRequirement())
            if(bench.id!=null&&"Farmingbench".equalsIgnoreCase(bench.id))benchIds.add(bench.id);
        return recipes.stream().filter(r->tier(r)!=Integer.MAX_VALUE).min(Comparator.comparingInt(AetherhavenMasteryAssetsTestCommand::tier)).orElse(null);
    }
    private static int tier(CraftingRecipe recipe) {
        int tier=Integer.MAX_VALUE;
        if(recipe.getBenchRequirement()!=null)for(var bench:recipe.getBenchRequirement())
            if("Farmingbench".equalsIgnoreCase(bench.id))tier=Math.min(tier,Math.max(0,bench.requiredTierLevel));
        return tier;
    }
}
