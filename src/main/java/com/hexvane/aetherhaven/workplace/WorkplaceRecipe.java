package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

/** Pack-extensible routing/skill rules. Material costs remain owned by the source recipe. */
public record WorkplaceRecipe(String id, String name, String workplace, String sourceRecipe,
                              String bench, int benchTier, int skillLevel, java.util.List<Ingredient> inputs,
                              String output, int outputCount, float workSeconds, boolean requiresKnowledge, String jewelryRarity, String townRank) {
    public record Ingredient(String itemId, String resourceType, int count) {
        boolean valid() { return count > 0 && count <= 9999 && ((itemId != null && !itemId.isBlank()) != (resourceType != null && !resourceType.isBlank())); }
    }
    public WorkplaceRecipe(String id,String name,String workplace,String sourceRecipe,String bench,int benchTier,int skillLevel) {
        this(id,name,workplace,sourceRecipe,bench,benchTier,skillLevel,null,null,0,0,false);
    }
    public WorkplaceRecipe(String id,String name,String workplace,String sourceRecipe,String bench,int benchTier,int skillLevel,
                           java.util.List<Ingredient> inputs,String output,int outputCount,float workSeconds) {
        this(id,name,workplace,sourceRecipe,bench,benchTier,skillLevel,inputs,output,outputCount,workSeconds,false);
    }
    public WorkplaceRecipe(String id,String name,String workplace,String sourceRecipe,String bench,int benchTier,int skillLevel,
                           java.util.List<Ingredient> inputs,String output,int outputCount,float workSeconds,boolean requiresKnowledge) {
        this(id,name,workplace,sourceRecipe,bench,benchTier,skillLevel,inputs,output,outputCount,workSeconds,requiresKnowledge,null);
    }
    public WorkplaceRecipe(String id,String name,String workplace,String sourceRecipe,String bench,int benchTier,int skillLevel,
                           java.util.List<Ingredient> inputs,String output,int outputCount,float workSeconds,boolean requiresKnowledge,String jewelryRarity) {
        this(id,name,workplace,sourceRecipe,bench,benchTier,skillLevel,inputs,output,outputCount,workSeconds,requiresKnowledge,jewelryRarity,null);
    }
    public boolean rankUnlocked(int rankIndex) {
        return townRank==null || com.hexvane.aetherhaven.questboard.TownQuestBoardRank.rankOrder().contains(townRank)
            && rankIndex>=com.hexvane.aetherhaven.questboard.TownQuestBoardRank.rankIndex(townRank);
    }
    public String unlockIssue(com.hexvane.aetherhaven.town.TownRecord town) {
        var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
        int rank=town!=null&&plugin!=null?com.hexvane.aetherhaven.questboard.TownRankCapacity.rankIndexForTown(town,plugin.getQuestBoardCatalog()):0;
        if(!rankUnlocked(rank))return "Requires Town Rank "+townRank+".";
        if(requiresKnowledge&&(town==null||!town.sharesCraftRecipeItem("workplace:"+id)))return "Earn this town crafting recipe through villager reputation.";
        return null;
    }
    public com.hexvane.aetherhaven.jewelry.JewelryRarity rarity() {
        if(jewelryRarity==null)return null;
        try{return com.hexvane.aetherhaven.jewelry.JewelryRarity.valueOf(jewelryRarity.toUpperCase(java.util.Locale.ROOT));}
        catch(IllegalArgumentException invalid){return null;}
    }
    /** Maintain targets distinguish jewelry quality, even when several grades share the same item asset. */
    public long stored(com.hexvane.aetherhaven.warehouse.TownWarehouse stock,String itemId) {
        if(rarity()==null)return stock.count(com.hexvane.aetherhaven.warehouse.TownWarehouse.Page.GENERAL,itemId)
            +stock.count(com.hexvane.aetherhaven.warehouse.TownWarehouse.Page.OUTPUT,itemId)+stock.smelting().count(itemId);
        long total=0;
        for(var page:com.hexvane.aetherhaven.warehouse.TownWarehouse.Page.values())for(short slot=0;slot<stock.slots(page);slot++) {
            var stack=stock.stack(page,slot);
            if(!com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)&&itemId.equals(stack.getItemId())
                &&rarity()==com.hexvane.aetherhaven.jewelry.JewelryMetadata.readRarity(stack))total+=stack.getQuantity();
        }
        return total;
    }
    public long incoming(com.hexvane.aetherhaven.warehouse.TownWarehouse stock,String itemId) {
        if(rarity()==null)return stock.incoming(itemId);
        return stock.batches().stream().filter(b->!b.cancelled()&&id.equals(b.recipeId()))
            .flatMap(b->b.outputItems().stream()).filter(s->itemId.equals(s.getItemId())).mapToLong(s->s.getQuantity()).sum();
    }
    public boolean unlocked(com.hexvane.aetherhaven.town.TownRecord town) {
        return unlockIssue(town)==null;
    }
    public String nativeUnlockIssue(com.hexvane.aetherhaven.town.TownRecord town, com.hypixel.hytale.server.core.universe.world.World world) {
        return nativeUnlockIssue(town, WorkplaceEquipment.memoriesLevel(world));
    }
    public String nativeUnlockIssue(com.hexvane.aetherhaven.town.TownRecord town, int memoriesLevel) {
        var source=source();
        if(source==null)return "Recipe unavailable.";
        var primary=source.getPrimaryOutput();
        if(source.isKnowledgeRequired()&&(primary==null||primary.getItemId()==null||town==null||!town.sharesCraftRecipeItem(primary.getItemId())))
            return "This recipe needs its town crafting unlock.";
        return source.getRequiredMemoriesLevel()>Math.max(1,memoriesLevel)
            ? "Requires Memories level "+source.getRequiredMemoriesLevel()+"." : null;
    }
    public TownsfolkWorkType type() { return TownsfolkWorkType.byId(workplace); }
    /** Horticultural crafting runs at a Crafting workplace but uses the worker's Farming skill. */
    public com.hexvane.aetherhaven.townsfolk.TownsfolkSkill skill() {
        return type()==TownsfolkWorkType.CRAFTING && output!=null
            && (output.startsWith("Plant_Seeds_") || output.startsWith("Plant_Sapling_"))
            ? com.hexvane.aetherhaven.townsfolk.TownsfolkSkill.FARMING : type().skill;
    }
    public boolean valid() { return (townRank==null||com.hexvane.aetherhaven.questboard.TownQuestBoardRank.rankOrder().contains(townRank)) && id != null && !id.isBlank() && name != null && !name.isBlank() && type()!=null && type().requiresWorkplace()
        && sourceRecipe!=null && !sourceRecipe.isBlank() && bench!=null && !bench.isBlank()
        && benchTier>=1 && benchTier<=10 && skillLevel>=1 && skillLevel<=10
        && (!requiresKnowledge || townOnly())
        && (type()!=TownsfolkWorkType.CULTIVATION || validCultivation())
        && (jewelryRarity==null || rarity()!=null && type()==TownsfolkWorkType.JEWELRY && townOnly() && outputCount==1
            && com.hexvane.aetherhaven.jewelry.JewelryItemIds.isJewelry(output)
            && com.hexvane.aetherhaven.jewelry.JewelryGem.fromItemId(output)!=null)
        && (!townOnly() || inputs!=null && !inputs.isEmpty() && inputs.stream().allMatch(i->i!=null&&i.valid())
            && output!=null && !output.isBlank() && outputCount>=1 && outputCount<=100 && Float.isFinite(workSeconds) && workSeconds>=1 && workSeconds<=3600); }
    public boolean townOnly() { return sourceRecipe != null && sourceRecipe.startsWith("workplace:"); }
    private boolean validCultivation() {
        var crop=AlchemicalCrop.fromBlock(output);
        return townOnly()&&crop!=null&&crop.item().equals(output)&&outputCount==1&&workSeconds>=12
            &&"Farmingbench".equals(bench)&&skillLevel>=crop.skill()&&inputs!=null&&inputs.size()==1
            &&inputs.getFirst()!=null&&crop.seed().equals(inputs.getFirst().itemId())
            &&inputs.getFirst().resourceType()==null&&inputs.getFirst().count()==1;
    }
    public boolean nativeCharcoal() { return type()==TownsfolkWorkType.SMELTING&&"native:furnace_charcoal".equals(sourceRecipe); }
    public CraftingRecipe source() {
        if (townOnly()) {
            if (!valid()) return null;
            if(jewelryRarity!=null && com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(output)==null)return null;
            var materials=inputs.stream().map(i->new com.hypixel.hytale.server.core.inventory.MaterialQuantity(i.itemId(),i.resourceType(),null,i.count(),null))
                .toArray(com.hypixel.hytale.server.core.inventory.MaterialQuantity[]::new);
            var result=new com.hypixel.hytale.server.core.inventory.MaterialQuantity(output,null,null,outputCount,null);
            return new CraftingRecipe(materials,result,new com.hypixel.hytale.server.core.inventory.MaterialQuantity[]{result},outputCount,null,workSeconds,false,0);
        }
        if(!nativeCharcoal())return CraftingRecipe.getAssetMap().getAsset(sourceRecipe);
        // Planning description only. The native furnace burns fuel and produces its own byproduct.
        var wood=new com.hypixel.hytale.server.core.inventory.MaterialQuantity(null,"Wood_Trunk",null,2,null);
        var charcoal=new com.hypixel.hytale.server.core.inventory.MaterialQuantity("Ingredient_Charcoal",null,null,1,null);
        return new CraftingRecipe(new com.hypixel.hytale.server.core.inventory.MaterialQuantity[]{wood},charcoal,
            new com.hypixel.hytale.server.core.inventory.MaterialQuantity[]{charcoal},1,null,0,false,1);
    }
    public String equipmentIssue(ApprenticeWorkplace site, WorkplaceEquipment.Snapshot equipment) {
        if (site.type()!=type()) return "This task needs a " + WorkplaceRules.label(type()) + " workplace.";
        String issue=equipment.requirement(bench,benchTier);
        if(issue!=null)return issue;
        return equipment.benches().stream().anyMatch(this::acceptsBench) ? null : "Needs the recipe's bench upgrades inside the workplace.";
    }
    public boolean acceptsBench(WorkplaceEquipment.Bench candidate) {
        if(!bench.equals(candidate.id())||candidate.tier()<benchTier)return false;
        if(townOnly()||nativeCharcoal())return true;
        return acceptsNativeBench(source(),candidate);
    }
    static boolean acceptsNativeBench(CraftingRecipe source,WorkplaceEquipment.Bench candidate) {
        if(source==null||source.getBenchRequirement()==null)return false;
        for(var requirement:source.getBenchRequirement())
            if(requirement!=null&&candidate.id().equals(requirement.id)&&requirement.requiredTierLevel<=candidate.tier()
                &&(requirement.type==com.hypixel.hytale.protocol.BenchType.Crafting||requirement.type==com.hypixel.hytale.protocol.BenchType.Processing)
                &&(requirement.requiredAugmentTags==null||candidate.augmentTags().containsAll(java.util.Arrays.asList(requirement.requiredAugmentTags))))return true;
        return false;
    }
}
