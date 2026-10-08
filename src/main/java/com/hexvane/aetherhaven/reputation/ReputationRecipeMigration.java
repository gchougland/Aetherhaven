package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.workplace.*;
import java.util.*;

/** Claimed item rewards from the 4.0 preview now teach their recipes without granting more items. */
public final class ReputationRecipeMigration {
    private ReputationRecipeMigration() {}
    public static boolean repairWorkplaceRecipes(TownRecord town,UUID player,Collection<WorkplaceRecipe> recipes) {
        return repairWorkplaceRecipes(town,player,recipes,ReputationRewardCatalog.allDefinitions());
    }
    static boolean repairWorkplaceRecipes(TownRecord town,UUID player,Collection<WorkplaceRecipe> recipes,
                                         Collection<ReputationRewardCatalog.ReputationRewardDefinition> rewards) {
        boolean changed=false;
        for(var reward:rewards) {
            String item=reward.learnRecipeItemId();
            if(item==null||!item.startsWith("Aetherhaven_")||!item.endsWith("_Kit")
                ||!VillagerReputationService.hasPlayerClaimedRewardId(town,player,reward.rewardId()))continue;
            for(var recipe:recipes)if(recipe.townOnly()&&item.equals(recipe.output())) {
                String id=WorkplaceRecipeUnlocks.PREFIX+recipe.id();
                if(!town.sharesCraftRecipeItem(id)){town.addTownSharedCraftRecipeItemId(id);changed=true;}
            }
        }
        return changed;
    }
}
