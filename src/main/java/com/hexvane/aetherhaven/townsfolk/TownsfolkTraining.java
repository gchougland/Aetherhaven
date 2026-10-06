package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.UUID;

/** Shared profile transaction. Call on the town world thread and persist only a TRAINED result. */
public final class TownsfolkTraining {
    private TownsfolkTraining() {}
    public enum Result { TRAINED, FORBIDDEN, STALE, LOCKED, NO_POINTS, MISSING_MATERIALS }

    public static Result train(TownRecord town,UUID player,UUID resident,TownsfolkProgress progress,
            TownsfolkAttribute attribute,TownsfolkSkill skill,int shownLevel,boolean usePoint,ItemContainer inventory) {
        if(progress==null||!TownsfolkProgressService.canTrain(town,player,resident))return Result.FORBIDDEN;
        if((attribute==null)==(skill==null))return Result.LOCKED;
        if(shownLevel!=(attribute!=null?progress.attribute(attribute):progress.skill(skill)))return Result.STALE;
        if((attribute!=null?progress.attributeCost(attribute):progress.skillCost(skill))<=0)return Result.LOCKED;
        if(usePoint)return (attribute!=null?progress.spendMasteryPoint(attribute):progress.spendMasteryPoint(skill))
            ?Result.TRAINED:Result.NO_POINTS;
        String item=attribute!=null?AetherhavenConstants.ITEM_SHARD_OF_GAIA:AetherhavenConstants.ITEM_VERDANT_CATALYST;
        if(inventory==null||InventoryMaterials.count(inventory,item)<1
            ||!inventory.removeItemStack(new ItemStack(item,1)).succeeded())return Result.MISSING_MATERIALS;
        if(attribute!=null)progress.train(attribute);else progress.train(skill);
        return Result.TRAINED;
    }
}
